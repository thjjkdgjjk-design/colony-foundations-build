package dev.colonyfoundations.worker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FarmerJobRulesTest {
    @Test
    void wheatHarvestProducesWheatAndSeeds() {
        List<FarmerJobRules.Drop> drops = FarmerJobRules.harvestItemsFor("wheat");
        assertEquals(List.of(
                new FarmerJobRules.Drop("minecraft:wheat", 1),
                new FarmerJobRules.Drop("minecraft:wheat_seeds", 2)), drops);
    }

    @Test
    void supportedVegetablesProduceTheirActualItems() {
        assertEquals(List.of(new FarmerJobRules.Drop("minecraft:carrot", 3)),
                FarmerJobRules.harvestItemsFor("carrots"));
        assertEquals(List.of(new FarmerJobRules.Drop("minecraft:potato", 3)),
                FarmerJobRules.harvestItemsFor("potatoes"));
        assertEquals(List.of(
                new FarmerJobRules.Drop("minecraft:beetroot", 1),
                new FarmerJobRules.Drop("minecraft:beetroot_seeds", 2)),
                FarmerJobRules.harvestItemsFor("beetroots"));
    }

    @Test
    void unsupportedCropsAreNotRemovedOrReplacedWithInventedDrops() {
        assertTrue(FarmerJobRules.harvestItemsFor("pumpkin").isEmpty());
        assertTrue(FarmerJobRules.harvestItemsFor("sweet_berry_bush").isEmpty());
    }

    @Test
    void invalidDropsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new FarmerJobRules.Drop("", 1));
        assertThrows(IllegalArgumentException.class, () -> new FarmerJobRules.Drop("minecraft:wheat", 0));
    }
}
