package dev.colonyfoundations.worker;

import dev.colonyfoundations.block.ColonyClaimState;
import dev.colonyfoundations.block.ColonyHutBlockEntity;
import dev.colonyfoundations.block.FarmFieldBlockEntity;
import dev.colonyfoundations.block.WorkerRoleRules;
import dev.colonyfoundations.logistics.ColonyRequestRules;
import dev.colonyfoundations.logistics.ColonyRequestState;
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
            case "hut_deliveryman" -> tickCourier(world, hutPos, hut, worker);
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
            if (action == null) {
                FarmerJobRules.CropSpec spec = FarmerJobRules.cropForBlockId(field.cropBlockId()).orElse(null);
                if (spec != null && !hut.hasItem(spec.plantingItemId())) {
                    maybeRequestStock(world, hutPos, spec.plantingItemId(), 16, 5);
                }
                continue;
            }

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


    private static final int COURIER_SEARCH_RADIUS = 16;

    private static void maybeRequestStock(ServerWorld world, BlockPos targetHut,
                                          String itemId, int amount, int priority) {
        Identifier item = Identifier.tryParse(itemId);
        if (item == null || !Registries.ITEM.containsId(item)) return;
        String dimension = world.getRegistryKey().getValue().toString();
        var owner = ColonyClaimState.get(world).ownerAt(dimension, targetHut).orElse(null);
        if (owner == null) return;

        ColonyRequestState requests = ColonyRequestState.get(world);
        if (requests.hasActiveRequest(owner, dimension, targetHut, item)) return;
        requests.createOrMerge(owner, world.getRegistryKey(), targetHut, item, amount, priority, world.getTime());
    }

    private static void tickCourier(ServerWorld world, BlockPos courierHutPos,
                                    ColonyHutBlockEntity courierHut, VillagerEntity courier) {
        ColonyRequestState requests = ColonyRequestState.get(world);
        ColonyClaimState claims = ColonyClaimState.get(world);
        String dimension = world.getRegistryKey().getValue().toString();
        var owner = claims.ownerAt(dimension, courierHutPos).orElse(null);
        if (owner == null) return;

        if (!courierHut.hasCourierTask()) {
            if (courierHut.courierCargoCount() > 0) return;
            for (ColonyRequestState.RequestRecord request : requests.openRequests(owner, dimension)) {
                BlockPos target = request.target();
                if (!world.isChunkLoaded(target)
                        || !(world.getBlockEntity(target) instanceof ColonyHutBlockEntity)) continue;
                if (!claims.ownerAt(dimension, target).filter(owner::equals).isPresent()) continue;
                BlockPos warehouse = findWarehouse(world, courierHutPos, owner, request.itemId());
                if (warehouse == null) continue;
                if (requests.reserve(request.requestId(), courier.getUuid(), world.getTime()).isEmpty()) continue;
                if (!courierHut.beginCourierTask(request.requestId(), warehouse, target)) {
                    requests.release(request.requestId(), courier.getUuid(), world.getTime());
                    return;
                }
                break;
            }
        }

        if (!courierHut.hasCourierTask()) return;
        String requestId = courierHut.courierRequestId();
        ColonyRequestState.RequestRecord request = requests.getRequest(requestId).orElse(null);
        if (request == null || !request.colonyOwnerUuid().equals(owner.toString())
                || !request.dimension().equals(dimension)) {
            courierHut.clearCourierTask();
            return;
        }

        // Recover a saved cargo stack if a restart happened between pickup and stage update.
        if (courierHut.courierCargoCount() > 0 && courierHut.courierStage().equals("to_warehouse")) {
            courierHut.setCourierStage("to_target");
        }
        if (courierHut.courierStage().equals("to_warehouse")) {
            tickCourierPickup(world, courierHut, courier, requests, request, owner, dimension);
        } else if (courierHut.courierStage().equals("to_target")) {
            tickCourierDelivery(world, courierHut, courier, requests, request, owner, dimension);
        }
    }

    private static BlockPos findWarehouse(ServerWorld world, BlockPos courierPos, java.util.UUID owner,
                                          String itemId) {
        String dimension = world.getRegistryKey().getValue().toString();
        ColonyClaimState claims = ColonyClaimState.get(world);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -COURIER_SEARCH_RADIUS; dx <= COURIER_SEARCH_RADIUS; dx++) {
            for (int dz = -COURIER_SEARCH_RADIUS; dz <= COURIER_SEARCH_RADIUS; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos candidate = courierPos.add(dx, dy, dz);
                    if (!world.isChunkLoaded(candidate)) continue;
                    if (!(world.getBlockEntity(candidate) instanceof ColonyHutBlockEntity warehouse)
                            || !warehouse.hutId().equals("hut_warehouse")
                            || !warehouse.hasItem(itemId)) continue;
                    if (!claims.ownerAt(dimension, candidate).filter(owner::equals).isPresent()) continue;
                    double distance = candidate.getSquaredDistance(courierPos);
                    if (distance < bestDistance) {
                        best = candidate.toImmutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static void tickCourierPickup(ServerWorld world, ColonyHutBlockEntity courierHut,
                                          VillagerEntity courier, ColonyRequestState requests,
                                          ColonyRequestState.RequestRecord request,
                                          java.util.UUID owner, String dimension) {
        BlockPos warehousePos = courierHut.courierWarehousePosition();
        if (!world.isChunkLoaded(warehousePos)
                || !(world.getBlockEntity(warehousePos) instanceof ColonyHutBlockEntity warehouse)
                || !warehouse.hutId().equals("hut_warehouse")
                || !ColonyClaimState.get(world).ownerAt(dimension, warehousePos).filter(owner::equals).isPresent()) {
            requests.release(request.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierTask();
            return;
        }

        if (courier.squaredDistanceTo(warehousePos.getX() + 0.5, warehousePos.getY() + 0.5,
                warehousePos.getZ() + 0.5) > 9.0) {
            if (WorkerNavigationRules.shouldRequestPath(
                    courier.getNavigation().isIdle(), world.getTime(), courier.getId())) {
                courier.getNavigation().startMovingTo(
                        warehousePos.getX() + 0.5, warehousePos.getY(), warehousePos.getZ() + 0.5, 1.15);
            }
            return;
        }

        ColonyRequestState.RequestRecord current = requests.getRequest(request.requestId()).orElse(null);
        if (current == null || !current.status().equals(ColonyRequestRules.Status.RESERVED.id())
                || !current.courierUuid().equals(courier.getUuid().toString())) {
            courierHut.clearCourierTask();
            return;
        }
        Identifier itemId = Identifier.tryParse(current.itemId());
        if (itemId == null || !Registries.ITEM.containsId(itemId)) {
            requests.release(current.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierTask();
            return;
        }
        int maxStack = Math.min(64, Registries.ITEM.get(itemId).getDefaultStack().getMaxCount());
        ItemStack cargo = warehouse.withdrawItem(current.itemId(), Math.min(maxStack, current.remainingCount()));
        if (cargo.isEmpty()) {
            requests.release(current.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierTask();
            return;
        }

        if (!courierHut.canStoreItems(List.of(cargo.copy()))) {
            warehouse.storeItems(List.of(cargo));
            requests.release(current.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierTask();
            return;
        }
        if (!courierHut.storeItems(List.of(cargo.copy())) || !courierHut.setCourierCargo(current.itemId(), cargo.getCount())) {
            if (courierHut.countItem(current.itemId()) >= cargo.getCount()) {
                courierHut.consumeItem(current.itemId(), cargo.getCount());
            }
            warehouse.storeItems(List.of(cargo));
            requests.release(current.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierCargo();
            courierHut.clearCourierTask();
            return;
        }
        if (requests.beginDelivery(current.requestId(), courier.getUuid(), world.getTime()).isEmpty()) {
            courierHut.consumeItem(current.itemId(), cargo.getCount());
            warehouse.storeItems(List.of(cargo));
            courierHut.clearCourierCargo();
            courierHut.clearCourierTask();
            return;
        }
        courierHut.setCourierStage("to_target");
    }

    private static void tickCourierDelivery(ServerWorld world, ColonyHutBlockEntity courierHut,
                                            VillagerEntity courier, ColonyRequestState requests,
                                            ColonyRequestState.RequestRecord request,
                                            java.util.UUID owner, String dimension) {
        ColonyRequestState.RequestRecord current = requests.getRequest(request.requestId()).orElse(null);
        if (current == null) {
            courierHut.clearCourierTask();
            return;
        }
        if (current.status().equals(ColonyRequestRules.Status.RESERVED.id())
                && courierHut.courierCargoCount() > 0) {
            if (requests.beginDelivery(current.requestId(), courier.getUuid(), world.getTime()).isPresent()) {
                current = requests.getRequest(request.requestId()).orElse(current);
            }
        }
        if (!current.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id())
                || !current.courierUuid().equals(courier.getUuid().toString())) {
            courierHut.clearCourierTask();
            return;
        }

        BlockPos targetPos = courierHut.courierTargetPosition();
        if (!world.isChunkLoaded(targetPos)
                || !(world.getBlockEntity(targetPos) instanceof ColonyHutBlockEntity target)
                || !ColonyClaimState.get(world).ownerAt(dimension, targetPos).filter(owner::equals).isPresent()) {
            return; // Never force-load a destination chunk.
        }
        if (courier.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) > 9.0) {
            if (WorkerNavigationRules.shouldRequestPath(
                    courier.getNavigation().isIdle(), world.getTime(), courier.getId())) {
                courier.getNavigation().startMovingTo(
                        targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.15);
            }
            return;
        }

        String itemId = courierHut.courierCargoItemId();
        int count = courierHut.courierCargoCount();
        if (itemId.isBlank() || count < 1 || courierHut.countItem(itemId) < count
                || !current.itemId().equals(itemId)) {
            requests.release(current.requestId(), courier.getUuid(), world.getTime());
            courierHut.clearCourierCargo();
            courierHut.clearCourierTask();
            return;
        }
        Identifier parsedItem = Identifier.tryParse(itemId);
        if (parsedItem == null || !Registries.ITEM.containsId(parsedItem)) return;
        ItemStack delivery = new ItemStack(Registries.ITEM.get(parsedItem), count);
        if (!target.canStoreItems(List.of(delivery)) || !target.storeItems(List.of(delivery.copy()))) return;

        if (requests.recordDelivery(current.requestId(), courier.getUuid(), count, world.getTime()).isEmpty()) {
            target.consumeItem(itemId, count);
            return;
        }
        courierHut.consumeItem(itemId, count);
        courierHut.clearCourierCargo();
        courierHut.clearCourierTask();
    }
}
