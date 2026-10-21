package dev.colonyfoundations.block;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ColonyCitizenAssignmentTest {
    private static final RegistryKey<World> OVERWORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));

    @Test
    void assignmentDirectoryIsIdempotentAndRefreshesLastKnownPosition() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID citizenId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos farmerHut = new BlockPos(8, 64, 8);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        assertTrue(state.registerBuilding(colonyId, OVERWORLD, farmerHut,
                "colony_foundations:hut_farmer", 1));

        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, farmerHut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", new BlockPos(1, 64, 1)));
        assertTrue(state.hasActiveCitizenAssignment(citizenId));
        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, farmerHut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", new BlockPos(2, 64, 2)));
        assertEquals(new BlockPos(2, 64, 2).asLong(),
                state.citizenAssignment(citizenId).orElseThrow().lastKnownPosition());
    }

    @Test
    void activeCitizenCannotBeStolenByAnotherBuildingButCanBeReassignedAfterRelease() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID citizenId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos firstHut = new BlockPos(8, 64, 8);
        BlockPos secondHut = new BlockPos(16, 64, 16);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        assertTrue(state.registerBuilding(colonyId, OVERWORLD, firstHut,
                "colony_foundations:hut_farmer", 1));
        assertTrue(state.registerBuilding(colonyId, OVERWORLD, secondHut,
                "colony_foundations:hut_deliveryman", 1));

        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, firstHut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", firstHut));
        assertFalse(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, secondHut,
                "colony_foundations:worker", "colony_foundations:hut_deliveryman", secondHut));
        assertTrue(state.releaseCitizenAssignment(citizenId, OVERWORLD, firstHut));
        assertFalse(state.hasActiveCitizenAssignment(citizenId));
        assertEquals("released", state.citizenAssignment(citizenId).orElseThrow().status());
        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, secondHut,
                "colony_foundations:worker", "colony_foundations:hut_deliveryman", secondHut));
    }

    @Test
    void workOrderDirectoryTracksProgressAndRejectsUnanchoredSites() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        BlockPos site = new BlockPos(5, 63, -3);
        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                orderId.toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());

        var stored = state.upsertWorkOrder(queued).orElseThrow();
        assertEquals(1L, stored.createdSequence());
        assertEquals("queued", state.workOrder(orderId).orElseThrow().status());
        var active = state.upsertWorkOrder(stored.withStatus("active", "").withProgress(2, 3)).orElseThrow();
        assertEquals(2, active.progress());
        assertEquals(1, state.workOrdersForColony(colonyId).size());

        ColonyClaimState.WorkOrderRecord invalidSite = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(300, 63, 0).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        assertTrue(state.upsertWorkOrder(invalidSite).isEmpty());
    }

    @Test
    void queuedWorkOrdersSortByPriorityThenSequenceAndUseDifferentSites() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);

        ColonyClaimState.WorkOrderRecord normal = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(5, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        ColonyClaimState.WorkOrderRecord urgent = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(20, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 20, -3, 5, 0, "queued", 0, 3, "", ownerId.toString());

        var first = state.upsertWorkOrder(normal).orElseThrow();
        var second = state.upsertWorkOrder(urgent).orElseThrow();
        var queue = state.workOrdersForColony(colonyId);

        assertEquals(1, first.createdSequence());
        assertEquals(2, second.createdSequence());
        assertEquals(second.orderId(), queue.get(0).orderId());
        assertEquals(first.orderId(), queue.get(1).orderId());
        assertTrue(state.upsertWorkOrder(queue.get(0).withStatus("active", "")).isPresent());
        assertEquals(5, state.workOrder(UUID.fromString(second.orderId())).orElseThrow().priority());
    }

    @Test
    void rejectedSameSiteOrderDoesNotPartiallyCancelEarlierQueuedWork() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos site = new BlockPos(5, 63, -3);
        ColonyClaimState.ClaimRecord claim = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", townHall.asLong(), ownerId.toString(), List.of(),
                colonyId.toString(), "Test colony", "Owner", 1);

        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 1, "queued", 0, 3, "", ownerId.toString());
        ColonyClaimState.WorkOrderRecord active = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 2, "active", 1, 3, "", ownerId.toString());

        Map<String, ColonyClaimState.WorkOrderRecord> savedOrders = new LinkedHashMap<>();
        savedOrders.put(queued.orderId(), queued);
        savedOrders.put(active.orderId(), active);
        ColonyClaimState state = new ColonyClaimState(
                Map.of("claim", claim), Map.of(), Map.of(), savedOrders);
        ColonyClaimState.WorkOrderRecord requested = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:other_cottage", "build",
                0, 1, 1, 0, 5, -3, 1, 0, "queued", 0, 3, "", ownerId.toString());

        assertTrue(state.upsertWorkOrder(requested).isEmpty(),
                "An active/progressed order must reject the conflicting request.");
        assertEquals("queued", state.workOrder(UUID.fromString(queued.orderId())).orElseThrow().status(),
                "The rejected request must not have cancelled the earlier queued order.");
        assertEquals("active", state.workOrder(UUID.fromString(active.orderId())).orElseThrow().status());
        assertEquals(2, state.workOrdersForColony(colonyId).size());
    }

    @Test
    void queuedWorkOrderCanBeReprioritizedButStartedOrderCannot() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(5, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        var order = state.upsertWorkOrder(queued).orElseThrow();

        assertEquals(8, state.reprioritizeQueuedWorkOrder(colonyId, order.createdSequence(), 8)
                .orElseThrow().priority());
        assertTrue(state.reprioritizeQueuedWorkOrder(colonyId, order.createdSequence(), 11).isEmpty());

        var active = state.upsertWorkOrder(state.workOrder(UUID.fromString(order.orderId())).orElseThrow()
                .withStatus("active", "")).orElseThrow();
        assertTrue(state.reprioritizeQueuedWorkOrder(colonyId, active.createdSequence(), 1).isEmpty());
    }

    @Test
    void workOrderStatusTransitionsRejectImpossibleShortcutsWithoutMutation() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(5, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        var initial = state.upsertWorkOrder(queued).orElseThrow();

        assertTrue(state.upsertWorkOrder(initial.withStatus("completed", "")).isEmpty());
        assertEquals("queued", state.workOrder(UUID.fromString(initial.orderId())).orElseThrow().status());

        var active = state.upsertWorkOrder(initial.withStatus("active", "")).orElseThrow();
        assertTrue(state.upsertWorkOrder(active.withStatus("queued", "")).isEmpty());
        assertEquals("active", state.workOrder(UUID.fromString(initial.orderId())).orElseThrow().status());

        var paused = state.upsertWorkOrder(active.withStatus("paused", "Paused by user")).orElseThrow();
        assertEquals("paused", paused.status());
        assertTrue(state.upsertWorkOrder(paused.withStatus("completed", "")).isEmpty());
        assertEquals("paused", state.workOrder(UUID.fromString(initial.orderId())).orElseThrow().status());

        assertTrue(ColonyWorkOrderStatus.BLOCKED.canTransitionTo(ColonyWorkOrderStatus.ACTIVE));
        assertTrue(ColonyWorkOrderStatus.BLOCKED.canTransitionTo(ColonyWorkOrderStatus.CANCELLED));
        assertFalse(ColonyWorkOrderStatus.COMPLETED.canTransitionTo(ColonyWorkOrderStatus.ACTIVE));
        assertFalse(ColonyWorkOrderStatus.CANCELLED.canTransitionTo(ColonyWorkOrderStatus.QUEUED));
    }

    @Test
    void queueCancelStateRuleDoesNotPermitActiveOrProgressedOrders() {
        assertTrue(ColonyWorkOrderStatus.QUEUED.isTerminal() == false);
        assertFalse(ColonyWorkOrderStatus.ACTIVE.isTerminal());
        assertTrue(ColonyWorkOrderStatus.CANCELLED.isTerminal());
        // State-level cancellation accepts queued/blocked records only; the Town Hall wrapper
        // additionally clears its own selected UUID when cancelling a not-yet-started selection.
    }

    @Test
    void onlyZeroProgressQueuedOrBlockedOrdersCanBeCancelled() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(5, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        var order = state.upsertWorkOrder(queued).orElseThrow();

        assertEquals("cancelled", state.cancelUnstartedWorkOrder(colonyId, order.createdSequence())
                .orElseThrow().status());
        assertTrue(state.cancelUnstartedWorkOrder(colonyId, order.createdSequence()).isEmpty());

        ColonyClaimState.WorkOrderRecord active = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), new BlockPos(20, 63, -3).asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 20, -3, 0, 0, "active", 1, 3, "", ownerId.toString());
        var storedActive = state.upsertWorkOrder(active).orElseThrow();
        assertTrue(state.cancelUnstartedWorkOrder(colonyId, storedActive.createdSequence()).isEmpty());
    }

    @Test
    void terminalWorkOrdersCannotBeReactivatedAndSequencesStayStable() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        BlockPos site = new BlockPos(5, 63, -3);
        ColonyClaimState.WorkOrderRecord queued = new ColonyClaimState.WorkOrderRecord(
                orderId.toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:cottage", "build",
                0, 1, 1, 0, 5, -3, 0, 0, "queued", 0, 3, "", ownerId.toString());
        var stored = state.upsertWorkOrder(queued).orElseThrow();
        var active = state.upsertWorkOrder(stored.withStatus("active", "")).orElseThrow();
        var completed = state.upsertWorkOrder(active.withProgress(3, 3)
                .withStatus("completed", "")).orElseThrow();
        assertEquals("completed", completed.status());
        assertTrue(state.upsertWorkOrder(completed.withStatus("active", "")).isEmpty());
    }

    @Test
    void persistentCodecRoundTripsQueuedWorkOrdersAndReadsLegacyClaimSaves() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos site = new BlockPos(5, 63, -3);
        ColonyClaimState.ClaimRecord claim = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", townHall.asLong(), ownerId.toString(), List.of(),
                colonyId.toString(), "Codec test colony", "Owner", 2);
        ColonyClaimState.WorkOrderRecord order = new ColonyClaimState.WorkOrderRecord(
                orderId.toString(), colonyId.toString(), "minecraft:overworld",
                townHall.asLong(), site.asLong(), "colony_foundations:cottage", "build",
                0, 1, 2, 1, 5, -3, 6, 8, "queued", 0, 3, "", ownerId.toString());
        ColonyClaimState before = new ColonyClaimState(
                Map.of("claim", claim), Map.of(), Map.of(), Map.of(orderId.toString(), order));

        NbtElement encoded = ColonyClaimState.persistenceCodec()
                .encodeStart(NbtOps.INSTANCE, before).result().orElseThrow();
        ColonyClaimState restored = ColonyClaimState.persistenceCodec()
                .parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        ColonyClaimState.WorkOrderRecord restoredOrder =
                restored.workOrder(orderId).orElseThrow();

        assertEquals(1, restored.workOrdersForColony(colonyId).size());
        assertEquals(8L, restoredOrder.createdSequence());
        assertEquals(6, restoredOrder.priority());
        assertEquals("queued", restoredOrder.status());
        assertEquals(site.asLong(), restoredOrder.sitePosition());
        assertEquals("colony_foundations:cottage", restoredOrder.blueprintId());

        // Simulate a prior save version that predates the citizen, building and order maps,
        // and predates the colony-identity fields on each claim record.
        NbtCompound legacySave = (NbtCompound) encoded.copy();
        legacySave.remove("citizens");
        legacySave.remove("buildings");
        legacySave.remove("work_orders");
        NbtCompound claims = legacySave.getCompound("claims").orElseThrow();
        NbtCompound oldClaim = claims.getCompound("claim").orElseThrow();
        for (String key : List.of("colony_id", "colony_name", "owner_name", "colony_level", "member_permissions")) {
            oldClaim.remove(key);
        }
        claims.put("claim", oldClaim);
        legacySave.put("claims", claims);

        ColonyClaimState migrated = ColonyClaimState.persistenceCodec()
                .parse(NbtOps.INSTANCE, legacySave).result().orElseThrow();
        assertEquals(1, migrated.nearbyClaims("minecraft:overworld", new BlockPos(79, 70, -64)).size());
        assertTrue(migrated.colonies().isEmpty(),
                "Legacy claims stay readable; the deterministic identity is assigned during Town Hall re-registration.");
        assertTrue(migrated.workOrdersForColony(colonyId).isEmpty());
    }

    @Test
    void assignmentsRequireARegisteredBuildingWithTheMatchingJobType() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID citizenId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos hut = new BlockPos(8, 64, 8);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);

        assertFalse(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, hut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", hut));
        assertTrue(state.registerBuilding(colonyId, OVERWORLD, hut, "colony_foundations:hut_farmer", 1));
        assertFalse(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, hut,
                "colony_foundations:worker", "colony_foundations:hut_deliveryman", hut));
        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, hut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", hut));
    }

    @Test
    void removingABuildingReleasesItsAssignedCitizenAndRemovesItFromTheDirectory() {
        UUID colonyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID citizenId = UUID.randomUUID();
        BlockPos townHall = new BlockPos(0, 64, 0);
        BlockPos hut = new BlockPos(8, 64, 8);
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);
        assertTrue(state.registerBuilding(colonyId, OVERWORLD, hut, "colony_foundations:hut_farmer", 1));
        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, hut,
                "colony_foundations:worker", "colony_foundations:hut_farmer", hut));

        assertTrue(state.unregisterBuilding(OVERWORLD, hut));
        assertTrue(state.findBuilding("minecraft:overworld", hut).isEmpty());
        assertFalse(state.hasActiveCitizenAssignment(citizenId));
        assertEquals("released", state.citizenAssignment(citizenId).orElseThrow().status());
    }

    @Test
    void releasedAssignmentTombstonesCanBeConsumedButActiveAssignmentsCannot() {
        UUID citizenId = UUID.randomUUID();
        UUID colonyId = UUID.randomUUID();
        ColonyClaimState.CitizenAssignment released = new ColonyClaimState.CitizenAssignment(
                citizenId.toString(), colonyId.toString(), "minecraft:overworld", 0,
                "colony_foundations:worker", "colony_foundations:hut_farmer", 0, "released");
        ColonyClaimState.CitizenAssignment active = released.withStatus("active");
        ColonyClaimState state = new ColonyClaimState(Map.of(), Map.of(citizenId.toString(), active));

        assertFalse(state.forgetReleasedCitizenAssignment(citizenId));
        state = new ColonyClaimState(Map.of(), Map.of(citizenId.toString(), released));
        assertTrue(state.forgetReleasedCitizenAssignment(citizenId));
        assertTrue(state.citizenAssignment(citizenId).isEmpty());
    }

    @Test
    void staleOrInvalidRecordsDoNotBlockRecovery() {
        UUID citizenId = UUID.randomUUID();
        UUID colonyId = UUID.randomUUID();
        ColonyClaimState.CitizenAssignment invalid = new ColonyClaimState.CitizenAssignment(
                citizenId.toString(), colonyId.toString(), "bad dimension", 0,
                "not a role id", "", 0, "active");
        ColonyClaimState.CitizenAssignment replacement = new ColonyClaimState.CitizenAssignment(
                citizenId.toString(), colonyId.toString(), "minecraft:overworld", 0,
                "colony_foundations:worker", "colony_foundations:hut_farmer", 0, "active");

        assertFalse(invalid.isValid());
        assertTrue(ColonyClaimState.mayReplaceCitizenAssignment(invalid, replacement));
    }

    private static ColonyClaimState stateWithColony(UUID colony, UUID owner, BlockPos townHall) {
        ColonyClaimState.ClaimRecord record = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", townHall.asLong(), owner.toString(), List.of(),
                colony.toString(), "Test colony", "Owner", 1);
        ColonyClaimState state = new ColonyClaimState(Map.of("test-colony", record));
        assertTrue(state.registerBuilding(colony, OVERWORLD, townHall,
                "colony_foundations:town_hall", 1));
        return state;
    }
}
