package dev.colonyfoundations.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarmerFieldRulesTest {
    @Test
    void supportedItemsSelectMatchingCrops() {
        assertEquals("minecraft:wheat", FarmerJobRules.cropForItemId("minecraft:wheat_seeds").orElseThrow().blockId());
        assertEquals("minecraft:carrots", FarmerJobRules.cropForItemId("minecraft:carrot").orElseThrow().blockId());
        assertEquals("minecraft:potatoes", FarmerJobRules.cropForItemId("minecraft:potato").orElseThrow().blockId());
        assertEquals("minecraft:beetroots", FarmerJobRules.cropForItemId("minecraft:beetroot_seeds").orElseThrow().blockId());
        assertTrue(FarmerJobRules.cropForItemId("minecraft:stone").isEmpty());
    }

    @Test
    void configuredCropsRequireTheCorrectPlantingStock() {
        assertEquals("minecraft:wheat_seeds", FarmerJobRules.cropForBlockId("minecraft:wheat").orElseThrow().plantingItemId());
        assertEquals("minecraft:carrot", FarmerJobRules.cropForBlockId("minecraft:carrots").orElseThrow().plantingItemId());
        assertEquals("minecraft:potato", FarmerJobRules.cropForBlockId("minecraft:potatoes").orElseThrow().plantingItemId());
        assertEquals("minecraft:beetroot_seeds", FarmerJobRules.cropForBlockId("minecraft:beetroots").orElseThrow().plantingItemId());
        assertTrue(FarmerJobRules.cropForBlockId("minecraft:oak_log").isEmpty());
    }

    @Test
    void aFieldAllowsOnlyOneSuccessfulActionPerDay() {
        assertTrue(FarmerJobRules.canActOnDay(Long.MIN_VALUE, 250));
        assertFalse(FarmerJobRules.canActOnDay(250, 250));
        assertTrue(FarmerJobRules.canActOnDay(250, 251));
    }

    @Test
    void radiusAndTillableTerrainAreBounded() {
        assertEquals(1, FarmerJobRules.clampFieldRadius(-5));
        assertEquals(5, FarmerJobRules.clampFieldRadius(80));
        assertTrue(FarmerJobRules.isTillableBlockId("minecraft:grass_block"));
        assertTrue(FarmerJobRules.isTillableBlockId("minecraft:coarse_dirt"));
        assertFalse(FarmerJobRules.isTillableBlockId("minecraft:stone"));
    }
}
