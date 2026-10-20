package dev.colonyschematics.blueprint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.Set;

/** Prevent imported schematics from activating command/structure-control blocks or entities. */
public final class SchematicSafetyRules {
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

    public static boolean isUnsafeBlockEntityId(String id) {
        return isUnsafeBlockId(id);
    }

    /** Recursively rejects command-block minecarts and player records, including nested passengers. */
    public static boolean containsUnsafeEntityTree(NbtCompound entity) {
        String id = entity.getString("id").orElse("");
        if (id.isBlank() || isUnsafeEntityId(id)) return true;

        NbtList passengers = entity.getList("Passengers").orElse(null);
        if (passengers == null) return false;
        for (int i = 0; i < passengers.size(); i++) {
            NbtCompound passenger = passengers.getCompound(i).orElse(null);
            if (passenger == null || containsUnsafeEntityTree(passenger)) return true;
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
