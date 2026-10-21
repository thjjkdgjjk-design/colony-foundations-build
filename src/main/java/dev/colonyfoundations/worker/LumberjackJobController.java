package dev.colonyfoundations.worker;

import dev.colonyfoundations.block.ColonyClaimState;
import dev.colonyfoundations.block.ColonyHutBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * First conservative Lumberjack job loop.
 *
 * Unlike MineColonies' connected-tree finder, this implementation only cuts vertical
 * trunks with a visible canopy, within the owning colony and in already-loaded chunks.
 */
public final class LumberjackJobController {
    private LumberjackJobController() {
    }

    public static void tick(ServerWorld world, BlockPos hutPos, ColonyHutBlockEntity hut,
                            VillagerEntity worker, ColonyClaimState.ClaimRecord colony) {
        if (!hut.hasItemPathSuffix("_axe")) {
            CitizenJobController.maybeRequestStock(world, hutPos, "minecraft:stone_axe", 1, 4);
            return;
        }

        BlockPos base = hut.lumberjackTreeBase().orElse(null);
        if (base != null && !isValidSavedBase(world, colony, base)) {
            hut.clearLumberjackTree();
            base = null;
        }
        if (base == null) {
            base = findNearestTreeBase(world, hutPos, hut.buildingLevel(), colony);
            if (base == null || !hut.beginLumberjackTree(base)) return;
        }

        BlockPos top = findTrunkTop(world, base);
        if (top == null || !colony.contains(top)) {
            hut.clearLumberjackTree();
            return;
        }

        if (worker.squaredDistanceTo(top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5) > 9.0) {
            if (WorkerNavigationRules.shouldRequestPath(
                    worker.getNavigation().isIdle(), world.getTime(), worker.getId())) {
                worker.getNavigation().startMovingTo(
                        top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1.0);
            }
            return;
        }

        BlockState oldState = world.getBlockState(top);
        if (!oldState.isIn(BlockTags.LOGS)) {
            hut.clearLumberjackTree();
            return;
        }
        var logId = Registries.BLOCK.getId(oldState.getBlock());
        if (!Registries.ITEM.containsId(logId)) return;
        ItemStack logDrop = new ItemStack(Registries.ITEM.get(logId), 1);

        boolean insideColony = colony.contains(base) && colony.contains(top);
        if (!LumberjackJobRules.canHarvestLog(
                hut.hasItemPathSuffix("_axe"), insideColony, hut.canStoreItems(List.of(logDrop)))) {
            return;
        }

        // Avoid duplicate world drops: commit exactly one log to hut storage after a successful
        // world mutation. If insertion fails unexpectedly, restore the removed block.
        if (!world.setBlockState(top, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) return;
        if (!hut.storeItems(List.of(logDrop))) {
            world.setBlockState(top, oldState, Block.NOTIFY_ALL);
            return;
        }
        if (top.equals(base)) hut.clearLumberjackTree();
    }

    private static BlockPos findNearestTreeBase(ServerWorld world, BlockPos hutPos, int buildingLevel,
                                                ColonyClaimState.ClaimRecord colony) {
        int radius = LumberjackJobRules.searchRadiusForLevel(buildingLevel);
        BlockPos bestBase = null;
        double bestDistance = Double.MAX_VALUE;
        String dimension = world.getRegistryKey().getValue().toString();
        ColonyClaimState directory = ColonyClaimState.get(world);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 8; y++) {
                    BlockPos candidate = hutPos.add(x, y, z);
                    BlockPos soil = candidate.down();
                    if (!world.isChunkLoaded(candidate) || !world.isChunkLoaded(soil)
                            || !world.getBlockState(candidate).isIn(BlockTags.LOGS)
                            || !colony.contains(candidate)) continue;

                    String groundId = Registries.BLOCK.getId(world.getBlockState(soil).getBlock()).toString();
                    if (!LumberjackJobRules.isTreeGroundBlockId(groundId)) continue;
                    BlockPos top = findTrunkTop(world, candidate);
                    if (top == null) continue;
                    int verticalLogs = top.getY() - candidate.getY() + 1;
                    if (!LumberjackJobRules.isSupportedTreeTrunk(verticalLogs, groundId)
                            || !hasNearbyLeaves(world, top) || !colony.contains(top)) continue;
                    if (directory.findColonyAt(dimension, candidate)
                            .filter(record -> record.colonyId().equals(colony.colonyId())).isEmpty()) continue;

                    double distance = candidate.getSquaredDistance(hutPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestBase = candidate.toImmutable();
                    }
                }
            }
        }
        return bestBase;
    }

    private static boolean hasNearbyLeaves(ServerWorld world, BlockPos trunkTop) {
        int found = 0;
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos candidate = trunkTop.add(x, y, z);
                    if (!world.isChunkLoaded(candidate)) continue;
                    if (world.getBlockState(candidate).isIn(BlockTags.LEAVES)
                            && ++found >= LumberjackJobRules.MIN_CANOPY_LEAVES) return true;
                }
            }
        }
        return false;
    }

    private static boolean isValidSavedBase(ServerWorld world,
                                            ColonyClaimState.ClaimRecord colony, BlockPos base) {
        BlockPos soil = base.down();
        if (!world.isChunkLoaded(base) || !world.isChunkLoaded(soil)
                || !colony.contains(base) || !world.getBlockState(base).isIn(BlockTags.LOGS)) return false;
        String groundId = Registries.BLOCK.getId(world.getBlockState(soil).getBlock()).toString();
        return LumberjackJobRules.isTreeGroundBlockId(groundId);
    }

    /** Find the highest connected log; unobserved or oversized trunks fail closed. */
    private static BlockPos findTrunkTop(ServerWorld world, BlockPos base) {
        if (!world.isChunkLoaded(base) || !world.getBlockState(base).isIn(BlockTags.LOGS)) return null;
        BlockPos top = base;
        for (int height = 1; height < LumberjackJobRules.MAX_TREE_HEIGHT; height++) {
            BlockPos next = base.up(height);
            if (!world.isChunkLoaded(next)) return null;
            if (!world.getBlockState(next).isIn(BlockTags.LOGS)) return top;
            top = next;
        }
        BlockPos above = top.up();
        if (world.isChunkLoaded(above) && world.getBlockState(above).isIn(BlockTags.LOGS)) return null;
        return top;
    }
}
