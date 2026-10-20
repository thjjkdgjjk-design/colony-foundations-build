package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;

import static org.junit.jupiter.api.Assertions.*;

class ColonyTerritoryRulesTest {
    @Test
    void initialClaimIncludesCenterAndFourChunksInEachDirection() {
        assertTrue(ColonyTerritoryRules.containsInitialClaim(0, 0, 0, 0));
        assertTrue(ColonyTerritoryRules.containsInitialClaim(0, 0, 79, -64));
        assertTrue(ColonyTerritoryRules.containsInitialClaim(15, 15, -64, 79));
        assertFalse(ColonyTerritoryRules.containsInitialClaim(0, 0, 80, 0));
        assertFalse(ColonyTerritoryRules.containsInitialClaim(0, 0, 0, -81));
    }

    @Test
    void chunkMathWorksAcrossNegativeCoordinates() {
        assertTrue(ColonyTerritoryRules.containsInitialClaim(-1, -1, -65, -65));
        assertFalse(ColonyTerritoryRules.containsInitialClaim(-1, -1, -81, -1));
        assertTrue(ColonyTerritoryRules.containsInitialClaim(-17, 33, -1, 97));
    }

    @Test
    void persistedClaimsRemainIndexedAfterTheTownHallChunkUnloads() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ColonyClaimState.ClaimRecord claim = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", new BlockPos(0, 64, 0).asLong(),
                owner.toString(), List.of(member.toString()));
        ColonyClaimState state = new ColonyClaimState(Map.of("saved-claim", claim));

        assertEquals(List.of(claim), state.nearbyClaims("minecraft:overworld", new BlockPos(79, 70, -64)));
        assertTrue(claim.canModify(owner));
        assertTrue(claim.canModify(member));
        assertFalse(claim.canModify(UUID.randomUUID()));
        assertTrue(state.nearbyClaims("minecraft:the_nether", new BlockPos(1, 70, 1)).isEmpty());
    }

    @Test
    void malformedSavedClaimsAreNotIndexed() {
        ColonyClaimState.ClaimRecord invalidOwner = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", new BlockPos(0, 64, 0).asLong(), "not-a-uuid", List.of());
        ColonyClaimState.ClaimRecord invalidDimension = new ColonyClaimState.ClaimRecord(
                "not a dimension", new BlockPos(0, 64, 0).asLong(), UUID.randomUUID().toString(), List.of());
        ColonyClaimState state = new ColonyClaimState(Map.of(
                "bad-owner", invalidOwner,
                "bad-dimension", invalidDimension));

        assertTrue(state.nearbyClaims("minecraft:overworld", new BlockPos(0, 64, 0)).isEmpty());
    }

    @Test
    void onlyOwnerAndExplicitMembersMayModifyTerritory() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        assertTrue(ColonyTerritoryRules.canModify(owner, Set.of(member), owner));
        assertTrue(ColonyTerritoryRules.canModify(owner, Set.of(member), member));
        assertFalse(ColonyTerritoryRules.canModify(owner, Set.of(member), stranger));
        assertFalse(ColonyTerritoryRules.canModify(null, Set.of(member), stranger));
    }
}
