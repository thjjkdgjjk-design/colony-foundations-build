package dev.colonyfoundations;

import dev.colonyfoundations.block.ModBlockEntities;
import dev.colonyfoundations.block.ModBlocks;
import dev.colonyfoundations.item.BuilderRecruitment;
import dev.colonyfoundations.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colony Foundations is a clean-room, experimental gameplay prototype.
 * It is not an official MineColonies port and contains no upstream assets.
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

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ModBlocks.TOWN_HALL);
            entries.add(ModItems.BUILDER_CONTRACT);
        });

        LOGGER.info("Colony Foundations initialized. This build implements the town-hall + builder + cottage prototype.");
    }
}
