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
        assertEquals("minecraft:melon_stem", FarmerJobRules.cropForItemId("minecraft:melon_seeds").orElseThrow().blockId());
        assertEquals("minecraft:pumpkin_stem", FarmerJobRules.cropForItemId("minecraft:pumpkin_seeds").orElseThrow().blockId());
        assertTrue(FarmerJobRules.cropForItemId("minecraft:stone").isEmpty());
    }

    @Test
    void configuredCropsRequireTheCorrectPlantingStock() {
        assertEquals("minecraft:wheat_seeds", FarmerJobRules.cropForBlockId("minecraft:wheat").orElseThrow().plantingItemId());
        assertEquals("minecraft:carrot", FarmerJobRules.cropForBlockId("minecraft:carrots").orElseThrow().plantingItemId());
        assertEquals("minecraft:potato", FarmerJobRules.cropForBlockId("minecraft:potatoes").orElseThrow().plantingItemId());
        assertEquals("minecraft:beetroot_seeds", FarmerJobRules.cropForBlockId("minecraft:beetroots").orElseThrow().plantingItemId());
        assertEquals("minecraft:melon_seeds", FarmerJobRules.cropForBlockId("minecraft:melon_stem").orElseThrow().plantingItemId());
        assertEquals("minecraft:pumpkin_seeds", FarmerJobRules.cropForBlockId("minecraft:pumpkin_stem").orElseThrow().plantingItemId());
        assertTrue(FarmerJobRules.cropForBlockId("minecraft:oak_log").isEmpty());
    }

    @Test
    void melonAndPumpkinStemsMapToSeparateFruitHarvestTargets() {
        assertEquals("minecraft:melon",
                FarmerJobRules.fruitBlockIdForStem("minecraft:melon_stem").orElseThrow());
        assertEquals("minecraft:pumpkin",
                FarmerJobRules.fruitBlockIdForStem("minecraft:pumpkin_stem").orElseThrow());
        assertTrue(FarmerJobRules.fruitBlockIdForStem("minecraft:wheat").isEmpty());
        assertFalse(FarmerJobRules.isMatureStemAge(6));
        assertTrue(FarmerJobRules.isMatureStemAge(7));
        assertTrue(FarmerJobRules.isMatureStemAge(8));
        assertEquals("minecraft:melon_slice",
                FarmerJobRules.harvestItemsFor("melon").get(0).itemId());
        assertEquals(4, FarmerJobRules.harvestItemsFor("melon").get(0).count());
        assertEquals("minecraft:pumpkin",
                FarmerJobRules.harvestItemsFor("pumpkin").get(0).itemId());
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
