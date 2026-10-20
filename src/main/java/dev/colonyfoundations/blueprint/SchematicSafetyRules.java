package dev.colonyfoundations.blueprint;

import java.util.Set;

/** Blocks that can execute commands or control structure placement are never imported from schematics. */
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
}
