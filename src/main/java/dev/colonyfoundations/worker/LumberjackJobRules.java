package dev.colonyfoundations.worker;

import java.util.Set;

/**
 * Conservative lumberjack rules. Only a bounded, vertically connected trunk rooted in known
 * tree soil is eligible; full connected-tree detection and replanting remain open port tasks.
 */
public final class LumberjackJobRules {
    public static final int MIN_TREE_TRUNK_LOGS = 3;
    public static final int MAX_TREE_HEIGHT = 32;
    private static final int MIN_SEARCH_RADIUS = 8;
    private static final int MAX_SEARCH_RADIUS = 16;
    public static final int MIN_CANOPY_LEAVES = 3;

    private static final Set<String> TREE_GROUND_BLOCKS = Set.of(
            "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
            "minecraft:rooted_dirt", "minecraft:podzol", "minecraft:mycelium",
            "minecraft:moss_block", "minecraft:mud", "minecraft:muddy_mangrove_roots"
    );

    private LumberjackJobRules() {
    }

    public static int searchRadiusForLevel(int buildingLevel) {
        int level = Math.max(1, Math.min(5, buildingLevel));
        return Math.max(MIN_SEARCH_RADIUS,
                Math.min(MAX_SEARCH_RADIUS, MIN_SEARCH_RADIUS + (level - 1) * 2));
    }

    public static boolean isTreeGroundBlockId(String blockId) {
        return blockId != null && TREE_GROUND_BLOCKS.contains(blockId);
    }

    public static boolean isSupportedTreeTrunk(int verticalLogCount, String groundBlockId) {
        return verticalLogCount >= MIN_TREE_TRUNK_LOGS
                && verticalLogCount <= MAX_TREE_HEIGHT
                && isTreeGroundBlockId(groundBlockId);
    }

    public static boolean hasSufficientCanopy(int leafBlockCount) {
        return leafBlockCount >= MIN_CANOPY_LEAVES;
    }

    public static boolean canHarvestLog(boolean axeAvailable, boolean positionInsideColony,
                                        boolean inventoryHasCapacity) {
        return axeAvailable && positionInsideColony && inventoryHasCapacity;
    }
}
