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
    void vanillaLogIdsMapToTheirMatchingSaplingsAndMangrovePropagule() {
        assertEquals("minecraft:oak_sapling",
                LumberjackJobRules.saplingBlockIdForLog("minecraft:oak_log").orElseThrow());
        assertEquals("minecraft:birch_sapling",
                LumberjackJobRules.saplingBlockIdForLog("minecraft:stripped_birch_log").orElseThrow());
        assertEquals("minecraft:cherry_sapling",
                LumberjackJobRules.saplingBlockIdForLog("minecraft:cherry_wood").orElseThrow());
        assertEquals("minecraft:mangrove_propagule",
                LumberjackJobRules.saplingBlockIdForLog("minecraft:mangrove_log").orElseThrow());
        assertEquals("minecraft:pale_oak_sapling",
                LumberjackJobRules.saplingBlockIdForLog("minecraft:pale_oak_log").orElseThrow());
        assertTrue(LumberjackJobRules.saplingBlockIdForLog("modded:crystal_log").isEmpty());
        assertTrue(LumberjackJobRules.saplingBlockIdForLog("minecraft:oak_planks").isEmpty());
    }

    @Test
    void cuttingRequiresAnAxeClaimProtectionAndInventorySpace() {
        assertTrue(LumberjackJobRules.canHarvestLog(true, true, true));
        assertFalse(LumberjackJobRules.canHarvestLog(false, true, true));
        assertFalse(LumberjackJobRules.canHarvestLog(true, false, true));
        assertFalse(LumberjackJobRules.canHarvestLog(true, true, false));
    }
}
