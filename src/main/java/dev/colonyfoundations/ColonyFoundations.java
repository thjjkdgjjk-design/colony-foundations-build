package dev.colonyfoundations;

import dev.colonyfoundations.block.ModBlockEntities;
import dev.colonyfoundations.block.ModBlocks;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.item.BuilderRecruitment;
import dev.colonyfoundations.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colony Foundations is a clean-room, experimental gameplay prototype.
 * Its blueprint loader is an original data-pack system, not an official
 * implementation or replacement for MineColonies or Structurize.
 */
public final class ColonyFoundations implements ModInitializer {
    public static final String MOD_ID = "colony_foundations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        BuilderRecruitment.initialize();
        BlueprintRegistry.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ModBlocks.TOWN_HALL);
            entries.add(ModItems.BUILDER_CONTRACT);
        });

        LOGGER.info("Colony Foundations initialized with data-pack blueprint support.");
    }
}
