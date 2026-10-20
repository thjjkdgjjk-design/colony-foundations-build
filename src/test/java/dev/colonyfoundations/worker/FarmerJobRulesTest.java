package dev.colonyfoundations.worker;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FarmerJobRulesTest {
    @Test
    void wheatHarvestProducesWheatAndSeeds() {
        List<ItemStack> drops = FarmerJobRules.harvestItemsFor("wheat");
        assertEquals(2, drops.size());
        assertEquals(Items.WHEAT, drops.get(0).getItem());
        assertEquals(Items.WHEAT_SEEDS, drops.get(1).getItem());
        assertEquals(2, drops.get(1).getCount());
    }

    @Test
    void supportedVegetablesProduceTheirActualItems() {
        assertEquals(Items.CARROT, FarmerJobRules.harvestItemsFor("carrots").get(0).getItem());
        assertEquals(Items.POTATO, FarmerJobRules.harvestItemsFor("potatoes").get(0).getItem());
        assertEquals(Items.BEETROOT, FarmerJobRules.harvestItemsFor("beetroots").get(0).getItem());
    }

    @Test
    void unsupportedCropsAreNotRemovedOrReplacedWithInventedDrops() {
        assertTrue(FarmerJobRules.harvestItemsFor("pumpkin").isEmpty());
        assertTrue(FarmerJobRules.harvestItemsFor("sweet_berry_bush").isEmpty());
    }
}
