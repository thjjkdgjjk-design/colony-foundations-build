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

public final class ModBlocks {
    private static final Identifier TOWN_HALL_ID = Identifier.of(ColonyFoundations.MOD_ID, "town_hall");
    private static final RegistryKey<net.minecraft.block.Block> TOWN_HALL_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, TOWN_HALL_ID);
    private static final RegistryKey<Item> TOWN_HALL_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, TOWN_HALL_ID);

    public static final TownHallBlock TOWN_HALL = Registry.register(
            Registries.BLOCK,
            TOWN_HALL_ID,
            new TownHallBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE)
                    .registryKey(TOWN_HALL_KEY)
                    .strength(4.0f, 6.0f))
    );

    public static final Item TOWN_HALL_ITEM = Registry.register(
            Registries.ITEM,
            TOWN_HALL_ID,
            new BlockItem(TOWN_HALL, new Item.Settings()
                    .registryKey(TOWN_HALL_ITEM_KEY)
                    .useBlockPrefixedTranslationKey())
    );

    private ModBlocks() { }

    public static void initialize() {
        // Loading this class performs the registrations above.
    }
}
