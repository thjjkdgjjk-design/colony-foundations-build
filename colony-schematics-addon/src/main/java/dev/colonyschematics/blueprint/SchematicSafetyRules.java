package dev.colonyschematics.blueprint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.Set;

/** Prevent imported schematics from activating command/structure-control blocks or entities. */
public final class SchematicSafetyRules {
    static final int MAX_ENTITY_TREE_DEPTH = 32;
    static final int MAX_ENTITY_TREE_NODES = 2_048;

    private static final Set<String> UNSAFE_BLOCK_IDS = Set.of(
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:test_block",
            "minecraft:test_instance_block"
    );

    private SchematicSafetyRules() {
    }

    public static boolean isUnsafeBlockId(String id) {
        return id != null && UNSAFE_BLOCK_IDS.contains(id);
    }

    public static boolean blockEntityTypeMatches(String expectedId, String actualId) {
        if (expectedId == null || actualId == null) return false;
        net.minecraft.util.Identifier expected = net.minecraft.util.Identifier.tryParse(expectedId);
        net.minecraft.util.Identifier actual = net.minecraft.util.Identifier.tryParse(actualId);
        return expected != null && expected.equals(actual);
    }

    public static boolean isUnsafeBlockEntityId(String id) {
        return isUnsafeBlockId(id);
    }

    /** Recursively rejects unsafe, malformed, or excessively deep passenger trees. */
    public static boolean containsUnsafeEntityTree(NbtCompound entity) {
        return containsUnsafeEntityTree(entity, 0, new int[] {0});
    }

    private static boolean containsUnsafeEntityTree(NbtCompound entity, int depth, int[] totalNodes) {
        if (depth > MAX_ENTITY_TREE_DEPTH || ++totalNodes[0] > MAX_ENTITY_TREE_NODES) return true;
        String id = entity.getString("id").orElse("");
        if (id.isBlank() || isUnsafeEntityId(id)) return true;

        NbtList passengers = entity.getList("Passengers").orElse(null);
        if (passengers == null) return false;
        for (int i = 0; i < passengers.size(); i++) {
            NbtCompound passenger = passengers.getCompound(i).orElse(null);
            if (passenger == null || containsUnsafeEntityTree(passenger, depth + 1, totalNodes)) return true;
        }
        return false;
    }

    public static boolean isUnsafeEntityId(String rawId) {
        if (rawId == null || rawId.isBlank()) return true;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(rawId);
        if (id == null) return true;
        return id.getPath().equals("player") || id.getPath().equals("command_block_minecart");
    }
}
