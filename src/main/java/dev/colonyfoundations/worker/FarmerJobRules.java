package dev.colonyfoundations.worker;

import java.util.List;

/**
 * Pure data table for supported crop yields. Kept independent of Minecraft registry
 * initialization so the mapping can be regression-tested without booting a game.
 */
public final class FarmerJobRules {
    public record Drop(String itemId, int count) {
        public Drop {
            if (itemId == null || itemId.isBlank() || count < 1) {
                throw new IllegalArgumentException("A crop drop needs an item identifier and positive count");
            }
        }
    }

    private FarmerJobRules() { }

    public static List<Drop> harvestItemsFor(String blockPath) {
        return switch (blockPath) {
            case "wheat" -> List.of(new Drop("minecraft:wheat", 1),
                    new Drop("minecraft:wheat_seeds", 2));
            case "carrots" -> List.of(new Drop("minecraft:carrot", 3));
            case "potatoes" -> List.of(new Drop("minecraft:potato", 3));
            case "beetroots" -> List.of(new Drop("minecraft:beetroot", 1),
                    new Drop("minecraft:beetroot_seeds", 2));
            default -> List.of();
        };
    }
}
