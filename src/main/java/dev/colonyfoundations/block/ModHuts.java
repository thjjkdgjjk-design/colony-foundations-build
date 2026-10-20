package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry layer for MineColonies-inspired building anchors and colony utility blocks.
 * IDs intentionally follow recognizable building-role names so future profession
 * implementations can bind to stable block identifiers.
 */
public final class ModHuts {
    public static final List<String> IDS = List.of(
            "hut_alchemist", "hut_archery", "hut_baker", "hut_barracks", "hut_barracks_tower",
            "hut_beekeeper", "hut_blacksmith", "hut_builder", "hut_chicken_herder", "hut_citizen",
            "hut_combat_academy", "hut_composter", "hut_concrete_mixer", "hut_cook", "hut_cowboy",
            "hut_crusher", "hut_deliveryman", "hut_dyer", "hut_enchanter", "hut_farmer",
            "hut_fisherman", "hut_fletcher", "hut_florist", "hut_gate_house", "hut_glassblower",
            "hut_graveyard", "hut_guard_tower", "hut_hospital", "hut_kitchen", "hut_library",
            "hut_lumberjack", "hut_mechanic", "hut_miner", "hut_mystical_site", "hut_nether_worker",
            "hut_plantation", "hut_rabbit_hutch", "hut_sawmill", "hut_school", "hut_shepherd",
            "hut_sifter", "hut_smeltery", "hut_stable", "hut_stone_smeltery", "hut_stonemason",
            "hut_swine_herder", "hut_tavern", "hut_university", "hut_warehouse",
            "simple_quarry", "medium_quarry", "large_quarry", "stash", "post_box", "scarecrow",
            "colony_rack", "plantation_field", "colony_flag_banner", "colony_flag_wall_banner",
            "construction_tape", "colony_gate", "colony_barrel"
    );

    private static final Map<String, ColonyHutBlock> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static {
        for (String id : IDS) {
            Identifier identifier = Identifier.of(ColonyFoundations.MOD_ID, id);
            RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);
            RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, identifier);

            ColonyHutBlock block = Registry.register(
                    Registries.BLOCK,
                    identifier,
                    new ColonyHutBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE)
                            .registryKey(blockKey)
                            .strength(3.0f, 6.0f))
            );
            Item item = Registry.register(
                    Registries.ITEM,
                    identifier,
                    new BlockItem(block, new Item.Settings()
                            .registryKey(itemKey)
                            .useBlockPrefixedTranslationKey())
            );
            BLOCKS.put(id, block);
            ITEMS.put(id, item);
        }
    }

    private ModHuts() { }

    public static void initialize() {
        // Calling this method initializes the static registries above.
    }

    public static List<ColonyHutBlock> blocks() {
        return Collections.unmodifiableList(new ArrayList<>(BLOCKS.values()));
    }

    public static List<Item> items() {
        return Collections.unmodifiableList(new ArrayList<>(ITEMS.values()));
    }

    public static ColonyHutBlock get(String id) {
        return BLOCKS.get(id);
    }

    public static Map<String, ColonyHutBlock> all() {
        return Collections.unmodifiableMap(BLOCKS);
    }
}
