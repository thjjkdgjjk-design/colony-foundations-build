package dev.colonyfoundations.worker;

import dev.colonyfoundations.block.ColonyHutBlockEntity;
import dev.colonyfoundations.block.WorkerRoleRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Bounded server-side job loop for assigned citizens. Each work hut calls this
 * at a low frequency; the controller never forces chunk loads or edits unloaded terrain.
 */
public final class CitizenJobController {
    private static final int FARM_SCAN_RADIUS = 8;

    private CitizenJobController() { }

    public static void tick(ServerWorld world, BlockPos hutPos, ColonyHutBlockEntity hut) {
        if (!hut.enabled() || hut.assignedCitizen().isEmpty()) return;
        Entity assigned = world.getEntity(hut.assignedCitizen().orElseThrow());
        if (!(assigned instanceof VillagerEntity worker) || worker.isRemoved()) return;
        if (!hut.isAssignedWorker(worker)) return;
        if (worker.getCommandTags().stream().anyMatch(tag -> tag.startsWith(
                WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX))) return;

        switch (hut.hutId()) {
            case "hut_farmer" -> tickFarmer(world, hutPos, hut, worker);
            default -> {
                // Other profession routines are deliberately not faked; they remain
                // listed as unimplemented in PORT_AUDIT.md until their own work loop exists.
            }
        }
    }

    private static void tickFarmer(ServerWorld world, BlockPos hutPos,
                                   ColonyHutBlockEntity hut, VillagerEntity worker) {
        BlockPos target = findNearestMatureCrop(world, hutPos, worker);
        if (target == null) return;

        double distance = worker.squaredDistanceTo(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distance > 9.0) {
            if (worker.getNavigation().isIdle()) {
                worker.getNavigation().startMovingTo(
                        target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            }
            return;
        }

        if (!world.isChunkLoaded(target)) return;
        BlockState state = world.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) return;
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        List<ItemStack> drops = FarmerJobRules.harvestItemsFor(path);
        if (drops.isEmpty() || !hut.canStoreItems(drops)) return;

        // Replant in the same farmland cell. Inventory capacity is checked first,
        // so no harvest is lost when the building's storage is full.
        if (world.setBlockState(target, crop.getDefaultState(), net.minecraft.block.Block.NOTIFY_ALL)) {
            hut.storeItems(drops);
        }
    }

    private static BlockPos findNearestMatureCrop(ServerWorld world, BlockPos hutPos,
                                                   VillagerEntity worker) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -FARM_SCAN_RADIUS; x <= FARM_SCAN_RADIUS; x++) {
            for (int z = -FARM_SCAN_RADIUS; z <= FARM_SCAN_RADIUS; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos candidate = hutPos.add(x, y, z);
                    if (!world.isChunkLoaded(candidate)) continue;
                    BlockState state = world.getBlockState(candidate);
                    if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) continue;
                    String path = Registries.BLOCK.getId(state.getBlock()).getPath();
                    if (FarmerJobRules.harvestItemsFor(path).isEmpty()) continue;
                    double distance = worker.squaredDistanceTo(
                            candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
                    if (distance < bestDistance) {
                        best = candidate.toImmutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }
}
