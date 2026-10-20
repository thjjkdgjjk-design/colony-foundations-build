package dev.colonyschematics.blueprint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchematicSafetyRulesTest {
    @Test
    void commandAndStructureControlBlocksAreDenied() {
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:chain_command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:repeating_command_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:structure_block"));
        assertTrue(SchematicSafetyRules.isUnsafeBlockId("minecraft:jigsaw"));
        assertFalse(SchematicSafetyRules.isUnsafeBlockId("minecraft:chest"));
    }

    @Test
    void blockEntityNbtIsOnlyAppliedToTheSameRegisteredType() {
        assertTrue(SchematicSafetyRules.blockEntityTypeMatches("minecraft:chest", "minecraft:chest"));
        assertTrue(SchematicSafetyRules.blockEntityTypeMatches("modded:custom_machine", "modded:custom_machine"));
        assertFalse(SchematicSafetyRules.blockEntityTypeMatches("minecraft:chest", "minecraft:barrel"));
        assertFalse(SchematicSafetyRules.blockEntityTypeMatches("invalid id", "minecraft:chest"));
        assertFalse(SchematicSafetyRules.blockEntityTypeMatches("minecraft:chest", null));
    }

    @Test
    void unsafePassengersAreRejectedEvenWhenTheRootEntityIsHarmless() {
        NbtCompound horse = new NbtCompound();
        horse.putString("id", "minecraft:horse");
        NbtCompound commandMinecart = new NbtCompound();
        commandMinecart.putString("id", "minecraft:command_block_minecart");
        NbtList passengers = new NbtList();
        passengers.add(commandMinecart);
        horse.put("Passengers", passengers);

        assertTrue(SchematicSafetyRules.containsUnsafeEntityTree(horse));
    }

    @Test
    void ordinaryEntityTreesRemainAllowed() {
        NbtCompound horse = new NbtCompound();
        horse.putString("id", "minecraft:horse");
        NbtCompound villager = new NbtCompound();
        villager.putString("id", "minecraft:villager");
        NbtList passengers = new NbtList();
        passengers.add(villager);
        horse.put("Passengers", passengers);

        assertFalse(SchematicSafetyRules.containsUnsafeEntityTree(horse));
    }

    @Test
    void excessivelyDeepPassengerTreesAreRejected() {
        NbtCompound root = entity("minecraft:horse");
        NbtCompound current = root;
        for (int i = 0; i <= SchematicSafetyRules.MAX_ENTITY_TREE_DEPTH; i++) {
            NbtCompound next = entity("minecraft:villager");
            NbtList child = new NbtList();
            child.add(next);
            current.put("Passengers", child);
            current = next;
        }
        assertTrue(SchematicSafetyRules.containsUnsafeEntityTree(root));
    }

    @Test
    void passengerTreesWithTooManyNodesAreRejected() {
        NbtCompound root = entity("minecraft:horse");
        NbtList passengers = new NbtList();
        for (int i = 0; i < SchematicSafetyRules.MAX_ENTITY_TREE_NODES; i++) {
            passengers.add(entity("minecraft:villager"));
        }
        root.put("Passengers", passengers);
        assertTrue(SchematicSafetyRules.containsUnsafeEntityTree(root));
    }

    private static NbtCompound entity(String id) {
        NbtCompound entity = new NbtCompound();
        entity.putString("id", id);
        return entity;
    }
}
