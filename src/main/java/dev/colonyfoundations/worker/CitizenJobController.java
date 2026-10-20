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
import net.minecraft.block.StemBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.state.property.Properties;
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

    private enum FieldActionType { TILL, PLANT, HARVEST, HARVEST_FRUIT, FERTILIZE }

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

        // A generated melon/pumpkin replaces StemBlock with AttachedStemBlock.
        // Handle both a mature free stem and the attached stem that points at its fruit.
        if (hut.hasItemPathSuffix("_axe")) {
            var fruitId = FarmerJobRules.fruitBlockIdForStem(field.cropBlockId());
            if (fruitId.isPresent()) {
                Block fruitBlock = blockForId(fruitId.get());
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos stemPos = fieldPos.add(x, 0, z);
                        if (stemPos.equals(fieldPos) || !world.isChunkLoaded(stemPos)) continue;
                        BlockState stemState = world.getBlockState(stemPos);

                        if (stemState.getBlock() == cropBlock
                                && stemState.getBlock() instanceof StemBlock
                                && stemState.contains(Properties.AGE_7)
                                && FarmerJobRules.isMatureStemAge(stemState.get(Properties.AGE_7))) {
                            for (BlockPos offset : List.of(
                                    new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
                                    new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0))) {
                                BlockPos fruitPos = stemPos.add(offset);
                                if (!world.isChunkLoaded(fruitPos)
                                        || !world.getBlockState(fruitPos).isOf(fruitBlock)) continue;
                                List<ItemStack> drops = resolveDrops(Registries.BLOCK.getId(fruitBlock).getPath());
                                if (!drops.isEmpty() && hut.canStoreItems(drops)) {
                                    return new FieldAction(FieldActionType.HARVEST_FRUIT,
                                            fruitPos.toImmutable(), drops);
                                }
                            }
                        } else if (stemState.getBlock() instanceof AttachedStemBlock
                                && stemState.contains(AttachedStemBlock.FACING)) {
                            BlockPos fruitPos = stemPos.offset(stemState.get(AttachedStemBlock.FACING));
                            if (!world.isChunkLoaded(fruitPos)
                                    || !world.getBlockState(fruitPos).isOf(fruitBlock)) continue;
                            List<ItemStack> drops = resolveDrops(Registries.BLOCK.getId(fruitBlock).getPath());
                            if (!drops.isEmpty() && hut.canStoreItems(drops)) {
                                return new FieldAction(FieldActionType.HARVEST_FRUIT,
                                        fruitPos.toImmutable(), drops);
                            }
                        }
                    }
                }
            } else {
                // Ordinary crops implement CropBlock; stems do not inherit from CropBlock.
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
        }

        // Bone meal supports both ordinary crops and the 0..7 age property on melon/pumpkin stems.
        if (hut.requestFertilizer() && hut.hasItem("minecraft:bone_meal")) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos cropPos = fieldPos.add(x, 0, z);
                    if (cropPos.equals(fieldPos) || !world.isChunkLoaded(cropPos)) continue;
                    BlockState cropState = world.getBlockState(cropPos);
                    if (cropState.getBlock() != cropBlock) continue;
                    boolean immature = false;
                    if (cropState.getBlock() instanceof CropBlock crop) {
                        immature = !crop.isMature(cropState);
                    } else if (cropState.getBlock() instanceof StemBlock
                            && cropState.contains(Properties.AGE_7)) {
                        immature = !FarmerJobRules.isMatureStemAge(cropState.get(Properties.AGE_7));
                    }
                    if (immature) {
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
            case HARVEST_FRUIT -> {
                String fruitId = FarmerJobRules.fruitBlockIdForStem(field.cropBlockId()).orElse(null);
                if (fruitId == null || !world.getBlockState(target).isOf(blockForId(fruitId))) return false;
                BlockState oldState = world.getBlockState(target);
                if (!hut.canStoreItems(action.drops())) return false;
                if (!world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) return false;
                if (!hut.storeItems(action.drops())) {
                    world.setBlockState(target, oldState, Block.NOTIFY_ALL);
                    return false;
                }
            }
            case FERTILIZE -> {
                BlockState oldState = world.getBlockState(target);
                if (!oldState.getBlock().equals(blockForId(field.cropBlockId()))
                        || !hut.hasItem("minecraft:bone_meal")) return false;

                BlockState grownState;
                if (oldState.getBlock() instanceof CropBlock crop) {
                    if (crop.isMature(oldState)) return false;
                    grownState = crop.withAge(crop.getAge(oldState) + 1);
                } else if (oldState.getBlock() instanceof StemBlock && oldState.contains(Properties.AGE_7)) {
                    int age = oldState.get(Properties.AGE_7);
                    if (FarmerJobRules.isMatureStemAge(age)) return false;
                    grownState = oldState.with(Properties.AGE_7, age + 1);
                } else {
                    return false;
                }

                if (!world.setBlockState(target, grownState, Block.NOTIFY_ALL)) return false;
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
            boolean taskStarted = false;
            for (ColonyRequestState.RequestRecord pending : requests.requestsForCourier(owner, dimension, courier.getUuid())) {
                if (pending.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id())) {
                    reconcileOrphanedInTransit(world, courier, requests, pending, owner, dimension);
                    continue;
                }
                if (!pending.status().equals(ColonyRequestRules.Status.RESERVED.id())) continue;
                BlockPos target = pending.target();
                if (!world.isChunkLoaded(target)
                        || !(world.getBlockEntity(target) instanceof ColonyHutBlockEntity)
                        || !claims.ownerAt(dimension, target).filter(owner::equals).isPresent()) continue;

                BlockPos warehouse;
                if (!pending.transferId().isBlank() || pending.courierWarehousePosition() != 0L) {
                    warehouse = BlockPos.fromLong(pending.courierWarehousePosition());
                    if (!world.isChunkLoaded(warehouse)
                            || !(world.getBlockEntity(warehouse) instanceof ColonyHutBlockEntity storage)
                            || !storage.hutId().equals("hut_warehouse")
                            || !claims.ownerAt(dimension, warehouse).filter(owner::equals).isPresent()) continue;
                    var withdrawn = storage.outboundTransferCount(pending.transferId(), pending.itemId());
                    if (!pending.transferId().isBlank() && withdrawn.isPresent()
                            && storage.hasDeliveryReceipt("refund:" + pending.transferId(), pending.itemId(), withdrawn.getAsInt())) {
                        requests.release(pending.requestId(), courier.getUuid(), pending.transferId(), world.getTime());
                        continue;
                    }
                    if (!storage.hasItem(pending.itemId()) && withdrawn.isEmpty()) {
                        if (!pending.transferId().isBlank()) requests.release(pending.requestId(), courier.getUuid(),
                                pending.transferId(), world.getTime());
                        continue;
                    }
                } else {
                    warehouse = findWarehouse(world, courierHutPos, owner, pending.itemId(), pending.transferId());
                    if (warehouse == null) continue;
                }
                ColonyRequestState.RequestRecord bound = requests.bindCourierTrip(
                        pending.requestId(), courier.getUuid(), pending.transferId(), warehouse, world.getTime()).orElse(null);
                if (bound != null && courierHut.beginCourierTask(
                        bound.requestId(), bound.transferId(), warehouse, target)) {
                    taskStarted = true;
                    break;
                }
            }

            if (!taskStarted && !courierHut.hasCourierTask()) {
                for (ColonyRequestState.RequestRecord request : requests.openRequests(owner, dimension)) {
                    BlockPos target = request.target();
                    if (!world.isChunkLoaded(target)
                            || !(world.getBlockEntity(target) instanceof ColonyHutBlockEntity)) continue;
                    if (!claims.ownerAt(dimension, target).filter(owner::equals).isPresent()) continue;
                    BlockPos warehouse = findWarehouse(world, courierHutPos, owner, request.itemId(), "");
                    if (warehouse == null) continue;
                    ColonyRequestState.RequestRecord reserved = requests.reserve(
                            request.requestId(), courier.getUuid(), warehouse, world.getTime()).orElse(null);
                    if (reserved == null) continue;
                    if (!courierHut.beginCourierTask(reserved.requestId(), reserved.transferId(), warehouse, target)) {
                        requests.release(reserved.requestId(), courier.getUuid(), reserved.transferId(), world.getTime());
                        return;
                    }
                    break;
                }
            }
        }

        if (!courierHut.hasCourierTask()) return;
        String requestId = courierHut.courierRequestId();
        ColonyRequestState.RequestRecord request = requests.getRequest(requestId).orElse(null);
        if (request == null || !request.colonyOwnerUuid().equals(owner.toString())
                || !request.dimension().equals(dimension)) {
            if (courierHut.courierCargoCount() > 0) {
                returnCourierCargoToWarehouse(world, courierHut, requests, requestId, courier, owner, dimension);
            } else {
                courierHut.clearCourierTask();
            }
            return;
        }

        courierHut.ensureCourierTransferId();
        String transferId = courierHut.courierTransferId();
        if (request.status().equals(ColonyRequestRules.Status.OPEN.id())
                && courierHut.courierCargoCount() > 0) {
            request = requests.reserve(requestId, courier.getUuid(), transferId,
                    courierHut.courierWarehousePosition(), world.getTime()).orElse(request);
        } else if ((request.status().equals(ColonyRequestRules.Status.RESERVED.id())
                || request.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id()))
                && request.transferId().isBlank()) {
            request = requests.bindCourierTrip(requestId, courier.getUuid(), transferId,
                    courierHut.courierWarehousePosition(), world.getTime()).orElse(request);
        }

        if (courierHut.courierCargoCount() > 0) {
            BlockPos targetPos = courierHut.courierTargetPosition();
            if (world.isChunkLoaded(targetPos)
                    && world.getBlockEntity(targetPos) instanceof ColonyHutBlockEntity target
                    && claims.ownerAt(dimension, targetPos).filter(owner::equals).isPresent()
                    && target.hasDeliveryReceipt(transferId, courierHut.courierCargoItemId(),
                            courierHut.courierCargoCount())) {
                tickCourierDelivery(world, courierHut, courier, requests, request, owner, dimension);
                return;
            }
            BlockPos warehousePos = courierHut.courierWarehousePosition();
            if (world.isChunkLoaded(warehousePos)
                    && world.getBlockEntity(warehousePos) instanceof ColonyHutBlockEntity warehouse
                    && warehouse.hutId().equals("hut_warehouse")
                    && claims.ownerAt(dimension, warehousePos).filter(owner::equals).isPresent()
                    && warehouse.hasDeliveryReceipt("refund:" + transferId,
                            courierHut.courierCargoItemId(), courierHut.courierCargoCount())) {
                returnCourierCargoToWarehouse(world, courierHut, requests, requestId, courier, owner, dimension);
                return;
            }
            if (request.status().equals(ColonyRequestRules.Status.OPEN.id())) {
                request = requests.reserve(requestId, courier.getUuid(), transferId,
                        courierHut.courierWarehousePosition(), world.getTime()).orElse(request);
            }
            if (request.status().equals(ColonyRequestRules.Status.RESERVED.id())
                    && request.courierUuid().equals(courier.getUuid().toString())
                    && request.transferId().equals(transferId)) {
                request = requests.beginDelivery(requestId, courier.getUuid(), transferId, world.getTime()).orElse(request);
            }
            if (!request.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id())
                    || !request.courierUuid().equals(courier.getUuid().toString())
                    || !request.transferId().equals(transferId)) {
                returnCourierCargoToWarehouse(world, courierHut, requests, requestId, courier, owner, dimension);
                return;
            }
        } else if (!request.transferId().isBlank() && !request.transferId().equals(transferId)) {
            courierHut.clearCourierTask();
            return;
        }

        if (courierHut.courierCargoCount() > 0 && courierHut.courierStage().equals("to_warehouse")) {
            courierHut.setCourierStage("to_target");
        }
        if (courierHut.courierStage().equals("to_warehouse")) {
            tickCourierPickup(world, courierHut, courier, requests, request, owner, dimension);
        } else if (courierHut.courierStage().equals("to_target")) {
            tickCourierDelivery(world, courierHut, courier, requests, request, owner, dimension);
        }
    }

    private static boolean reconcileOrphanedInTransit(ServerWorld world, VillagerEntity courier,
                                                        ColonyRequestState requests,
                                                        ColonyRequestState.RequestRecord request,
                                                        java.util.UUID owner, String dimension) {
        String transferId = request.transferId();
        if (transferId.isBlank()) return false;
        if (request.lastTransferId().equals(transferId)) return true;
        BlockPos targetPos = request.target();
        BlockPos warehousePos = BlockPos.fromLong(request.courierWarehousePosition());
        if (!world.isChunkLoaded(targetPos)
                || !(world.getBlockEntity(targetPos) instanceof ColonyHutBlockEntity target)
                || !ColonyClaimState.get(world).ownerAt(dimension, targetPos).filter(owner::equals).isPresent()) return false;

        var delivered = target.deliveryReceiptCount(transferId, request.itemId());
        if (delivered.isPresent()) {
            int amount = delivered.getAsInt();
            return amount <= request.remainingCount()
                    && requests.recordDelivery(request.requestId(), courier.getUuid(), transferId,
                            amount, world.getTime()).isPresent();
        }
        if (!world.isChunkLoaded(warehousePos)
                || !(world.getBlockEntity(warehousePos) instanceof ColonyHutBlockEntity warehouse)
                || !warehouse.hutId().equals("hut_warehouse")
                || !ColonyClaimState.get(world).ownerAt(dimension, warehousePos).filter(owner::equals).isPresent()) return false;
        var withdrawn = warehouse.outboundTransferCount(transferId, request.itemId());
        if (withdrawn.isPresent()) {
            Identifier item = Identifier.tryParse(request.itemId());
            if (item == null || !Registries.ITEM.containsId(item)) return false;
            if (!warehouse.storeItemsOnce("refund:" + transferId,
                    List.of(new ItemStack(Registries.ITEM.get(item), withdrawn.getAsInt())))) return false;
        }
        return requests.release(request.requestId(), courier.getUuid(), transferId, world.getTime()).isPresent();
    }

    private static BlockPos findWarehouse(ServerWorld world, BlockPos courierPos, java.util.UUID owner,
                                          String itemId, String transferId) {
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
                            || !warehouse.hutId().equals("hut_warehouse")) continue;
                    if (!warehouse.hasItem(itemId) && !warehouse.hasOutboundTransferReceipt(transferId, itemId)) continue;
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
            requests.release(request.requestId(), courier.getUuid(), courierHut.courierTransferId(), world.getTime());
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
                || !current.courierUuid().equals(courier.getUuid().toString())
                || !current.transferId().equals(courierHut.courierTransferId())) {
            courierHut.clearCourierTask();
            return;
        }
        Identifier itemId = Identifier.tryParse(current.itemId());
        if (itemId == null || !Registries.ITEM.containsId(itemId)) {
            requests.release(current.requestId(), courier.getUuid(), transferId, world.getTime());
            courierHut.clearCourierTask();
            return;
        }
        int maxStack = Math.min(64, Registries.ITEM.get(itemId).getDefaultStack().getMaxCount());
        courierHut.ensureCourierTransferId();
        String transferId = courierHut.courierTransferId();
        ItemStack cargo = warehouse.withdrawItemForTransfer(
                transferId, current.itemId(), Math.min(maxStack, current.remainingCount()));
        if (cargo.isEmpty()) {
            requests.release(current.requestId(), courier.getUuid(), transferId, world.getTime());
            courierHut.clearCourierTask();
            return;
        }

        if (!courierHut.receiveCourierCargo(transferId, cargo)) {
            if (warehouse.storeItemsOnce("refund:" + transferId, List.of(cargo.copy()))) {
                requests.release(current.requestId(), courier.getUuid(), transferId, world.getTime());
                courierHut.clearCourierTask();
            }
            return;
        }
        if (requests.beginDelivery(current.requestId(), courier.getUuid(), transferId, world.getTime()).isEmpty()) {
            if (warehouse.storeItemsOnce("refund:" + transferId, List.of(cargo.copy()))) {
                requests.release(current.requestId(), courier.getUuid(), transferId, world.getTime());
                courierHut.consumeItem(current.itemId(), cargo.getCount());
                courierHut.clearCourierCargo();
                courierHut.clearCourierTask();
            }
        }
    }

    private static void tickCourierDelivery(ServerWorld world, ColonyHutBlockEntity courierHut,
                                            VillagerEntity courier, ColonyRequestState requests,
                                            ColonyRequestState.RequestRecord request,
                                            java.util.UUID owner, String dimension) {
        ColonyRequestState.RequestRecord current = requests.getRequest(request.requestId()).orElse(null);
        if (current == null) {
            returnCourierCargoToWarehouse(world, courierHut, requests, request.requestId(), courier, owner, dimension);
            return;
        }

        BlockPos targetPos = courierHut.courierTargetPosition();
        if (!world.isChunkLoaded(targetPos)
                || !(world.getBlockEntity(targetPos) instanceof ColonyHutBlockEntity target)
                || !ColonyClaimState.get(world).ownerAt(dimension, targetPos).filter(owner::equals).isPresent()) {
            return; // Never force-load a destination chunk.
        }

        courierHut.ensureCourierTransferId();
        String transferId = courierHut.courierTransferId();
        String itemId = courierHut.courierCargoItemId();
        int count = courierHut.courierCargoCount();

        var receiptCount = target.deliveryReceiptCount(transferId, itemId);
        if (receiptCount.isPresent()) {
            int committedAmount = receiptCount.getAsInt();
            if (committedAmount != count) return;
            if (current.lastTransferId().equals(transferId)) {
                if (!itemId.isBlank() && count > 0 && courierHut.countItem(itemId) >= count) {
                    courierHut.consumeItem(itemId, count);
                }
                courierHut.clearCourierCargo();
                courierHut.clearCourierTask();
                return;
            }
            if (current.status().equals(ColonyRequestRules.Status.OPEN.id())) {
                current = requests.reserve(current.requestId(), courier.getUuid(), transferId,
                        courierHut.courierWarehousePosition(), world.getTime()).orElse(current);
            }
            if (current.status().equals(ColonyRequestRules.Status.RESERVED.id())
                    && current.courierUuid().equals(courier.getUuid().toString())
                    && current.transferId().equals(transferId)) {
                current = requests.beginDelivery(current.requestId(), courier.getUuid(), transferId,
                        world.getTime()).orElse(current);
            }
            if (current.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id())
                    && current.courierUuid().equals(courier.getUuid().toString())
                    && current.transferId().equals(transferId)
                    && requests.recordDelivery(current.requestId(), courier.getUuid(), transferId,
                            committedAmount, world.getTime()).isPresent()) {
                if (courierHut.countItem(itemId) >= count) courierHut.consumeItem(itemId, count);
                courierHut.clearCourierCargo();
                courierHut.clearCourierTask();
            }
            return;
        }

        if (current.status().equals(ColonyRequestRules.Status.RESERVED.id())
                && count > 0 && current.courierUuid().equals(courier.getUuid().toString())) {
            requests.beginDelivery(current.requestId(), courier.getUuid(), transferId, world.getTime());
            current = requests.getRequest(request.requestId()).orElse(current);
        }
        if (!current.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id())
                || !current.courierUuid().equals(courier.getUuid().toString())
                || !current.transferId().equals(transferId)) {
            returnCourierCargoToWarehouse(world, courierHut, requests, current.requestId(), courier, owner, dimension);
            return;
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

        if (itemId.isBlank() || count < 1 || courierHut.countItem(itemId) < count
                || !current.itemId().equals(itemId)) {
            returnCourierCargoToWarehouse(world, courierHut, requests, current.requestId(), courier, owner, dimension);
            return;
        }
        Identifier parsedItem = Identifier.tryParse(itemId);
        if (parsedItem == null || !Registries.ITEM.containsId(parsedItem)) return;
        ItemStack delivery = new ItemStack(Registries.ITEM.get(parsedItem), count);
        if (!target.hasDeliveryReceipt(transferId, itemId, count)
                && !target.storeItemsOnce(transferId, List.of(delivery))) return;

        // If request persistence failed, the next tick recognizes the saved destination receipt.
        if (requests.recordDelivery(current.requestId(), courier.getUuid(), transferId, count, world.getTime()).isEmpty()) return;
        courierHut.consumeItem(itemId, count);
        courierHut.clearCourierCargo();
        courierHut.clearCourierTask();
    }

    private static void returnCourierCargoToWarehouse(ServerWorld world, ColonyHutBlockEntity courierHut,
                                                      ColonyRequestState requests, String requestId,
                                                      VillagerEntity courier, java.util.UUID owner,
                                                      String dimension) {
        String itemId = courierHut.courierCargoItemId();
        int count = courierHut.courierCargoCount();
        if (itemId.isBlank() || count < 1) {
            courierHut.clearCourierTask();
            return;
        }

        BlockPos warehousePos = courierHut.courierWarehousePosition();
        if (!world.isChunkLoaded(warehousePos)
                || !(world.getBlockEntity(warehousePos) instanceof ColonyHutBlockEntity warehouse)
                || !warehouse.hutId().equals("hut_warehouse")
                || !ColonyClaimState.get(world).ownerAt(dimension, warehousePos).filter(owner::equals).isPresent()) {
            return; // Keep the cargo persisted until the owning warehouse is available again.
        }

        Identifier itemIdentifier = Identifier.tryParse(itemId);
        if (itemIdentifier == null || !Registries.ITEM.containsId(itemIdentifier)) return;
        String transferId = courierHut.courierTransferId();
        ItemStack cargo = new ItemStack(Registries.ITEM.get(itemIdentifier), count);
        if (!warehouse.storeItemsOnce("refund:" + transferId, List.of(cargo))) return;
        courierHut.consumeItem(itemId, count);
        courierHut.clearCourierCargo();
        requests.release(requestId, courier.getUuid(), transferId, world.getTime());
        courierHut.clearCourierTask();
    }
}
