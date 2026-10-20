package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Dedicated farmer field/scarecrow block, separate from ordinary worker huts. */
public final class ModField {
    private static final Identifier ID = Identifier.of(ColonyFoundations.MOD_ID, "scarecrow");
    private static final RegistryKey<net.minecraft.block.Block> BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, ID);
    private static final RegistryKey<Item> ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, ID);

    public static final FarmFieldBlock BLOCK = Registry.register(
            Registries.BLOCK, ID,
            new FarmFieldBlock(AbstractBlock.Settings.copy(Blocks.WHEAT).registryKey(BLOCK_KEY).strength(0.5f))
    );
    public static final Item ITEM = Registry.register(
            Registries.ITEM, ID,
            new BlockItem(BLOCK, new Item.Settings().registryKey(ITEM_KEY).useBlockPrefixedTranslationKey())
    );

    private ModField() { }

    public static void initialize() {
        // Loading this class performs its registrations.
    }
}
