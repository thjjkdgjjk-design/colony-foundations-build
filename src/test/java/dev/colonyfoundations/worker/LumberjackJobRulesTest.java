package dev.colonyfoundations.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LumberjackJobRulesTest {
    @Test
    void searchRadiusGrowsWithLevelButStaysBounded() {
        assertEquals(8, LumberjackJobRules.searchRadiusForLevel(1));
        assertEquals(10, LumberjackJobRules.searchRadiusForLevel(2));
        assertEquals(16, LumberjackJobRules.searchRadiusForLevel(5));
        assertEquals(8, LumberjackJobRules.searchRadiusForLevel(0));
        assertEquals(16, LumberjackJobRules.searchRadiusForLevel(9));
    }

    @Test
    void onlySupportedSoilAndThreeToThirtyTwoConnectedLogsFormAnEligibleTrunk() {
        assertTrue(LumberjackJobRules.isSupportedTreeTrunk(3, "minecraft:dirt"));
        assertTrue(LumberjackJobRules.isSupportedTreeTrunk(9, "minecraft:grass_block"));
        assertTrue(LumberjackJobRules.isSupportedTreeTrunk(32, "minecraft:podzol"));
        assertFalse(LumberjackJobRules.isSupportedTreeTrunk(2, "minecraft:dirt"));
        assertFalse(LumberjackJobRules.isSupportedTreeTrunk(33, "minecraft:dirt"));
        assertFalse(LumberjackJobRules.isSupportedTreeTrunk(8, "minecraft:stone"));
        assertFalse(LumberjackJobRules.isSupportedTreeTrunk(8, null));
        assertTrue(LumberjackJobRules.hasSufficientCanopy(3));
        assertFalse(LumberjackJobRules.hasSufficientCanopy(2));
        assertFalse(LumberjackJobRules.isTreeGroundBlockId("minecraft:oak_planks"));
        assertFalse(LumberjackJobRules.isTreeGroundBlockId("minecraft:oak_log"));
    }

    @Test
    void cuttingRequiresAnAxeClaimProtectionAndInventorySpace() {
        assertTrue(LumberjackJobRules.canHarvestLog(true, true, true));
        assertFalse(LumberjackJobRules.canHarvestLog(false, true, true));
        assertFalse(LumberjackJobRules.canHarvestLog(true, false, true));
        assertFalse(LumberjackJobRules.canHarvestLog(true, true, false));
    }
}
