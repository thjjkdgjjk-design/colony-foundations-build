package dev.colonyfoundations.worker;

import java.util.List;
import java.util.Optional;

/** Data-only crop rules, kept independent of initialized Minecraft registries for unit tests. */
public final class FarmerJobRules {
    public record Drop(String itemId, int count) {
        public Drop {
            if (itemId == null || itemId.isBlank() || count < 1) {
                throw new IllegalArgumentException("A crop drop needs an item identifier and positive count");
            }
        }
    }

    public record CropSpec(String blockId, String plantingItemId) {
        public CropSpec {
            if (blockId == null || blockId.isBlank()
                    || plantingItemId == null || plantingItemId.isBlank()) {
                throw new IllegalArgumentException("A field crop needs a block ID and planting item ID");
            }
        }
    }

    private static final int MIN_FIELD_RADIUS = 1;
    private static final int MAX_FIELD_RADIUS = 5;

    private FarmerJobRules() { }

    public static Optional<CropSpec> cropForItemId(String itemId) {
        if (itemId == null) return Optional.empty();
        return switch (itemId) {
            case "minecraft:wheat_seeds" -> Optional.of(new CropSpec("minecraft:wheat", itemId));
            case "minecraft:carrot" -> Optional.of(new CropSpec("minecraft:carrots", itemId));
            case "minecraft:potato" -> Optional.of(new CropSpec("minecraft:potatoes", itemId));
            case "minecraft:beetroot_seeds" -> Optional.of(new CropSpec("minecraft:beetroots", itemId));
            case "minecraft:melon_seeds" -> Optional.of(new CropSpec("minecraft:melon_stem", itemId));
            case "minecraft:pumpkin_seeds" -> Optional.of(new CropSpec("minecraft:pumpkin_stem", itemId));
            default -> Optional.empty();
        };
    }

    public static Optional<CropSpec> cropForBlockId(String blockId) {
        if (blockId == null) return Optional.empty();
        return switch (blockId) {
            case "minecraft:wheat" -> Optional.of(new CropSpec(blockId, "minecraft:wheat_seeds"));
            case "minecraft:carrots" -> Optional.of(new CropSpec(blockId, "minecraft:carrot"));
            case "minecraft:potatoes" -> Optional.of(new CropSpec(blockId, "minecraft:potato"));
            case "minecraft:beetroots" -> Optional.of(new CropSpec(blockId, "minecraft:beetroot_seeds"));
            case "minecraft:melon_stem" -> Optional.of(new CropSpec(blockId, "minecraft:melon_seeds"));
            case "minecraft:pumpkin_stem" -> Optional.of(new CropSpec(blockId, "minecraft:pumpkin_seeds"));
            default -> Optional.empty();
        };
    }

    public static Optional<String> fruitBlockIdForStem(String stemBlockId) {
        if (stemBlockId == null) return Optional.empty();
        return switch (stemBlockId) {
            case "minecraft:melon_stem" -> Optional.of("minecraft:melon");
            case "minecraft:pumpkin_stem" -> Optional.of("minecraft:pumpkin");
            default -> Optional.empty();
        };
    }

    public static boolean canActOnDay(long lastActionDay, long currentDay) {
        return lastActionDay != currentDay;
    }

    public static int clampFieldRadius(int requestedRadius) {
        return Math.max(MIN_FIELD_RADIUS, Math.min(MAX_FIELD_RADIUS, requestedRadius));
    }

    public static boolean isTillableBlockId(String blockId) {
        if (blockId == null) return false;
        return switch (blockId) {
            case "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
                    "minecraft:rooted_dirt", "minecraft:podzol", "minecraft:mycelium" -> true;
            default -> false;
        };
    }

    public static List<Drop> harvestItemsFor(String cropPath) {
        if (cropPath == null) return List.of();
        return switch (cropPath) {
            case "wheat" -> List.of(new Drop("minecraft:wheat", 1),
                    new Drop("minecraft:wheat_seeds", 2));
            case "carrots" -> List.of(new Drop("minecraft:carrot", 3));
            case "potatoes" -> List.of(new Drop("minecraft:potato", 3));
            case "beetroots" -> List.of(new Drop("minecraft:beetroot", 1),
                    new Drop("minecraft:beetroot_seeds", 2));
            case "melon" -> List.of(new Drop("minecraft:melon_slice", 4));
            case "pumpkin" -> List.of(new Drop("minecraft:pumpkin", 1));
            default -> List.of();
        };
    }
}
