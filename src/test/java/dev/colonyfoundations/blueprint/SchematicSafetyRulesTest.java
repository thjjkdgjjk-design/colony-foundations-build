package dev.colonyfoundations.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchematicSafetyRulesTest {
    @Test
    void rejectsCommandAndStructureControlBlocks() {
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:chain_command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:repeating_command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:structure_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:jigsaw"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:test_block"));
    }

    @Test
    void keepsOrdinaryBuildingBlocksAllowed() {
        assertFalse(SchematicSafetyRules.isUnsafeBlockId("minecraft:stone"));
        assertFalse(SchematicSafetyRules.isUnsafeBlockId("minecraft:chest"));
        assertFalse(SchematicSafetyRules.isUnsafeBlockId("modded:decorative_block"));
        assertFalse(SchematicSafetyRules.isUnsafeBlockId(null));
    }
}
