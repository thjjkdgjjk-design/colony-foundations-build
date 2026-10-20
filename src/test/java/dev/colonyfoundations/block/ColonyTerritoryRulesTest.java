package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

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
