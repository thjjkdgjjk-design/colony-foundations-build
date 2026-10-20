package dev.colonyfoundations.item;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {
    private static final Identifier BUILDER_CONTRACT_ID =
            Identifier.of(ColonyFoundations.MOD_ID, "builder_contract");
    private static final RegistryKey<Item> BUILDER_CONTRACT_KEY =
            RegistryKey.of(RegistryKeys.ITEM, BUILDER_CONTRACT_ID);

    public static final Item BUILDER_CONTRACT = Registry.register(
            Registries.ITEM,
            BUILDER_CONTRACT_ID,
            new Item(new Item.Settings().maxCount(16).registryKey(BUILDER_CONTRACT_KEY))
    );

    private ModItems() { }

    public static void initialize() {
        // Loading this class performs the registrations above.
    }
}
