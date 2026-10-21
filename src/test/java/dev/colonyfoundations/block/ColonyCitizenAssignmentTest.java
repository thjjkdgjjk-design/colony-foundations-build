package dev.colonyfoundations.block;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        var completed = state.upsertWorkOrder(stored.withProgress(3, 3)
                .withStatus("completed", "")).orElseThrow();
        assertEquals("completed", completed.status());
        assertTrue(state.upsertWorkOrder(completed.withStatus("active", "")).isEmpty());
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
