package dev.colonyfoundations.worker;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

/** Supported crop outputs for the first server-authoritative farmer job implementation. */
public final class FarmerJobRules {
    private FarmerJobRules() { }

    public static List<ItemStack> harvestItemsFor(String blockPath) {
        return switch (blockPath) {
            case "wheat" -> List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS, 2));
            case "carrots" -> List.of(new ItemStack(Items.CARROT, 3));
            case "potatoes" -> List.of(new ItemStack(Items.POTATO, 3));
            case "beetroots" -> List.of(new ItemStack(Items.BEETROOT), new ItemStack(Items.BEETROOT_SEEDS, 2));
            default -> List.of();
        };
    }
}
