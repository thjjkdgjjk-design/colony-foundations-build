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
    void ownerLookupRejectsAmbiguousOverlappingClaims() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        ColonyClaimState.ClaimRecord a = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", new BlockPos(0, 64, 0).asLong(), ownerA.toString(), List.of());
        ColonyClaimState.ClaimRecord b = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", new BlockPos(64, 64, 0).asLong(), ownerB.toString(), List.of());
        ColonyClaimState overlapping = new ColonyClaimState(Map.of("a", a, "b", b));
        assertTrue(overlapping.ownerAt("minecraft:overworld", new BlockPos(32, 64, 0)).isEmpty());

        ColonyClaimState nonOverlapping = new ColonyClaimState(Map.of(
                "a", a,
                "b", new ColonyClaimState.ClaimRecord("minecraft:overworld",
                        new BlockPos(256, 64, 0).asLong(), ownerB.toString(), List.of())));
        assertTrue(nonOverlapping.ownerAt("minecraft:overworld", new BlockPos(0, 64, 0))
                .filter(ownerA::equals).isPresent());
    }

    @Test
    void membershipDoesNotGrantEveryTerritoryPermissionByDefault() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        long position = new BlockPos(0, 64, 0).asLong();
        ColonyClaimState.ClaimRecord noGrants = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", position, owner.toString(), List.of(member.toString()),
                UUID.randomUUID().toString(), "Test", "Owner", 1);

        assertTrue(noGrants.canPerform(owner, ColonyPermission.BREAK_BLOCKS));
        assertFalse(noGrants.canPerform(member, ColonyPermission.BREAK_BLOCKS));
        assertFalse(noGrants.canPerform(member, ColonyPermission.USE_BLOCKS));
        assertFalse(noGrants.canPerform(stranger, ColonyPermission.BREAK_BLOCKS));

        ColonyClaimState.ClaimRecord specificGrants = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", position, owner.toString(), List.of(member.toString()),
                noGrants.colonyId(), "Test", "Owner", 1,
                Map.of(member.toString(), List.of("break_blocks", "build_projects", "unknown_action")));
        assertTrue(specificGrants.canPerform(member, ColonyPermission.BREAK_BLOCKS));
        assertTrue(specificGrants.canPerform(member, ColonyPermission.BUILD_PROJECTS));
        assertFalse(specificGrants.canPerform(member, ColonyPermission.PLACE_BLOCKS));
        assertFalse(specificGrants.canPerform(member, ColonyPermission.USE_BLOCKS));
        assertFalse(specificGrants.memberPermissions().get(member.toString()).contains("unknown_action"));
    }

    @Test
    void membershipRulesStillRecognizeOwnerAndInvitedMembers() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        assertTrue(ColonyTerritoryRules.canModify(owner, Set.of(member), owner));
        assertTrue(ColonyTerritoryRules.canModify(owner, Set.of(member), member));
        assertFalse(ColonyTerritoryRules.canModify(owner, Set.of(member), stranger));
        assertFalse(ColonyTerritoryRules.canModify(null, Set.of(member), stranger));
    }
}
