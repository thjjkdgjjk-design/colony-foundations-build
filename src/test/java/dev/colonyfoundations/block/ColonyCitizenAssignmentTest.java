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
        ColonyClaimState state = stateWithColony(colonyId, ownerId, townHall);

        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, townHall,
                "colony_foundations:worker", "colony_foundations:hut_farmer", new BlockPos(1, 64, 1)));
        assertTrue(state.hasActiveCitizenAssignment(citizenId));
        assertTrue(state.registerCitizenAssignment(citizenId, colonyId, OVERWORLD, townHall,
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
        return new ColonyClaimState(Map.of("test-colony", record));
    }
}
