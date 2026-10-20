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
}
