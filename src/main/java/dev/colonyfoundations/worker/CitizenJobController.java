package dev.colonyfoundations.worker;

import dev.colonyfoundations.block.ColonyHutBlockEntity;
import dev.colonyfoundations.block.FarmFieldBlockEntity;
import dev.colonyfoundations.block.WorkerRoleRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.List;

/** Bounded server-side job loops for assigned citizens; unloaded chunks are never force-loaded. */
public final class CitizenJobController {
    private static final int FIELD_DISCOVERY_RADIUS = 16;

    private CitizenJobController() { }

    public static void tick(ServerWorld world, BlockPos hutPos, ColonyHutBlockEntity hut) {
        if (!hut.enabled() || hut.assignedCitizen().isEmpty()) return;
        Entity assigned = world.getEntity(hut.assignedCitizen().orElseThrow());
        if (!(assigned instanceof VillagerEntity worker) || worker.isRemoved()) return;
        if (!hut.isAssignedWorker(worker)) return;
        if (worker.getCommandTags().contains(WorkerRoleRules.BUILDER_TAG)
                || worker.getCommandTags().stream().anyMatch(tag -> tag.startsWith(
                WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX))) return;

        switch (hut.hutId()) {
            case "hut_farmer" -> tickFarmer(world, hutPos, hut, worker);
            default -> {
                // Other profession routines remain explicitly unimplemented.
            }
        }
    }

    private enum FieldActionType { TILL, PLANT, HARVEST, FERTILIZE }

    private record FieldAction(FieldActionType type, BlockPos target, List<ItemStack> drops) { }

    private static void tickFarmer(ServerWorld world, BlockPos hutPos,
                                   ColonyHutBlockEntity hut, VillagerEntity worker) {
        // MineColonies' farmer expects the day/night cycle to be enabled.
        if (!world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;
        long day = Math.floorDiv(world.getTimeOfDay(), 24_000L);

        for (FarmFieldBlockEntity field : findFields(world, hutPos, hut.buildingLevel())) {
            if (!field.configured() || !field.canActOnDay(day)) continue;
            FieldAction action = findFieldAction(world, field, hut);
            if (action == null) continue;

            BlockPos target = action.target();
            double distance = worker.squaredDistanceTo(
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (distance > 9.0) {
                if (WorkerNavigationRules.shouldRequestPath(
                        worker.getNavigation().isIdle(), world.getTime(), worker.getId())) {
                    worker.getNavigation().startMovingTo(
                            target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
                }
                return;
            }
            if (executeFieldAction(world, field, hut, action, day)) return;
        }
    }

    private static List<FarmFieldBlockEntity> findFields(ServerWorld world, BlockPos hutPos, int buildingLevel) {
        List<FarmFieldBlockEntity> fields = new ArrayList<>();
        for (int x = -FIELD_DISCOVERY_RADIUS; x <= FIELD_DISCOVERY_RADIUS; x++) {
            for (int z = -FIELD_DISCOVERY_RADIUS; z <= FIELD_DISCOVERY_RADIUS; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos candidate = hutPos.add(x, y, z);
                    if (!world.isChunkLoaded(candidate)) continue;
                    if (world.getBlockEntity(candidate) instanceof FarmFieldBlockEntity field && field.configured()) {
                        fields.add(field);
                    }
                }
            }
        }
        fields.sort(java.util.Comparator.comparingDouble(field ->
                field.getPos().getSquaredDistance(hutPos)));
        return fields.stream().limit(Math.max(1, Math.min(5, buildingLevel))).toList();
    }

    private static FieldAction findFieldAction(ServerWorld world, FarmFieldBlockEntity field,
                                               ColonyHutBlockEntity hut) {
        FarmerJobRules.CropSpec spec = FarmerJobRules.cropForBlockId(field.cropBlockId()).orElse(null);
        if (spec == null) return null;
        Identifier cropId = Identifier.tryParse(spec.blockId());
        Identifier seedId = Identifier.tryParse(spec.plantingItemId());
        if (cropId == null || seedId == null
                || !Registries.BLOCK.containsId(cropId) || !Registries.ITEM.containsId(seedId)) return null;
        Block cropBlock = Registries.BLOCK.get(cropId);
        BlockPos fieldPos = field.getPos();
        int radius = field.fieldRadius();

        // The field block sits at crop height above its central farmland cell.
        // Mature harvest is its own daily action; it does not also replant.
        if (hut.hasItemPathSuffix("_axe")) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos cropPos = fieldPos.add(x, 0, z);
                    if (cropPos.equals(fieldPos) || !world.isChunkLoaded(cropPos)) continue;
                    BlockState cropState = world.getBlockState(cropPos);
                    if (cropState.getBlock() != cropBlock
                            || !(cropState.getBlock() instanceof CropBlock crop)
                            || !crop.isMature(cropState)) continue;
                    List<ItemStack> drops = resolveDrops(Registries.BLOCK.getId(cropBlock).getPath());
                    if (!drops.isEmpty() && hut.canStoreItems(drops)) {
                        return new FieldAction(FieldActionType.HARVEST, cropPos.toImmutable(), drops);
                    }
                }
            }
        }

        // Fertilizer runs as a distinct field action and consumes one bone meal per growth step.
        if (field.fertilizerEnabled() && hut.hasItem("minecraft:bone_meal")) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos cropPos = fieldPos.add(x, 0, z);
                    if (cropPos.equals(fieldPos) || !world.isChunkLoaded(cropPos)) continue;
                    BlockState cropState = world.getBlockState(cropPos);
                    if (cropState.getBlock() instanceof CropBlock crop
                            && cropState.getBlock() == cropBlock && !crop.isMature(cropState)) {
                        return new FieldAction(FieldActionType.FERTILIZE, cropPos.toImmutable(), List.of());
                    }
                }
            }
        }

        if (!hut.hasItem(spec.plantingItemId())) return null;

        if (hut.hasItemPathSuffix("_hoe")) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos cropPos = fieldPos.add(x, 0, z);
                    if (cropPos.equals(fieldPos) || !world.isChunkLoaded(cropPos)
                            || !world.getBlockState(cropPos).isAir()) continue;
                    BlockPos soil = cropPos.down();
                    if (!world.isChunkLoaded(soil)) continue;
                    String soilId = Registries.BLOCK.getId(world.getBlockState(soil).getBlock()).toString();
                    if (FarmerJobRules.isTillableBlockId(soilId)) {
                        return new FieldAction(FieldActionType.TILL, soil.toImmutable(), List.of());
                    }
                }
            }
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos cropPos = fieldPos.add(x, 0, z);
                if (cropPos.equals(fieldPos) || !world.isChunkLoaded(cropPos)
                        || !world.getBlockState(cropPos).isAir()) continue;
                BlockPos soil = cropPos.down();
                if (world.isChunkLoaded(soil) && world.getBlockState(soil).isOf(Blocks.FARMLAND)) {
                    return new FieldAction(FieldActionType.PLANT, cropPos.toImmutable(), List.of());
                }
            }
        }
        return null;
    }

    private static boolean executeFieldAction(ServerWorld world, FarmFieldBlockEntity field,
                                              ColonyHutBlockEntity hut, FieldAction action, long day) {
        BlockPos target = action.target();
        switch (action.type()) {
            case TILL -> {
                String soilId = Registries.BLOCK.getId(world.getBlockState(target).getBlock()).toString();
                if (!FarmerJobRules.isTillableBlockId(soilId)) return false;
                if (!world.setBlockState(target, Blocks.FARMLAND.getDefaultState(), Block.NOTIFY_ALL)) return false;
            }
            case PLANT -> {
                FarmerJobRules.CropSpec spec = FarmerJobRules.cropForBlockId(field.cropBlockId()).orElse(null);
                if (spec == null || !hut.hasItem(spec.plantingItemId())
                        || !world.getBlockState(target).isAir()) return false;
                Identifier cropId = Identifier.tryParse(spec.blockId());
                if (cropId == null || !Registries.BLOCK.containsId(cropId)) return false;
                if (!world.setBlockState(target, Registries.BLOCK.get(cropId).getDefaultState(), Block.NOTIFY_ALL)) {
                    return false;
                }
                if (!hut.consumeItem(spec.plantingItemId(), 1)) {
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    return false;
                }
            }
            case HARVEST -> {
                BlockState oldState = world.getBlockState(target);
                if (!(oldState.getBlock() instanceof CropBlock crop) || !crop.isMature(oldState)
                        || !oldState.getBlock().equals(blockForId(field.cropBlockId()))) return false;
                if (!hut.canStoreItems(action.drops())) return false;
                if (!world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) return false;
                if (!hut.storeItems(action.drops())) {
                    world.setBlockState(target, oldState, Block.NOTIFY_ALL);
                    return false;
                }
            }
            case FERTILIZE -> {
                BlockState oldState = world.getBlockState(target);
                if (!(oldState.getBlock() instanceof CropBlock crop) || crop.isMature(oldState)
                        || !oldState.getBlock().equals(blockForId(field.cropBlockId()))
                        || !hut.hasItem("minecraft:bone_meal")) return false;
                int age = crop.getAge(oldState);
                if (age >= crop.getMaxAge()) return false;
                if (!world.setBlockState(target, crop.withAge(age + 1), Block.NOTIFY_ALL)) return false;
                if (!hut.consumeItem("minecraft:bone_meal", 1)) {
                    world.setBlockState(target, oldState, Block.NOTIFY_ALL);
                    return false;
                }
            }
        }
        field.markActionDone(day);
        return true;
    }

    private static Block blockForId(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : Blocks.AIR;
    }

    private static List<ItemStack> resolveDrops(String cropPath) {
        List<FarmerJobRules.Drop> rules = FarmerJobRules.harvestItemsFor(cropPath);
        if (rules.isEmpty()) return List.of();
        List<ItemStack> stacks = new ArrayList<>(rules.size());
        for (FarmerJobRules.Drop drop : rules) {
            Identifier id = Identifier.tryParse(drop.itemId());
            if (id == null || !Registries.ITEM.containsId(id)) return List.of();
            stacks.add(new ItemStack(Registries.ITEM.get(id), drop.count()));
        }
        return List.copyOf(stacks);
    }
}
