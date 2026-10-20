package dev.colonyfoundations;

import dev.colonyfoundations.block.ModBlockEntities;
import dev.colonyfoundations.block.ModBlocks;
import dev.colonyfoundations.block.ModHuts;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.item.BuilderRecruitment;
import dev.colonyfoundations.network.ProjectNetworking;
import dev.colonyfoundations.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colony Foundations is a staged Fabric port/prototype. Its feature audit lists
 * what is ported and what is not; registration does not imply full parity with
 * the original MineColonies professions.
 */
public final class ColonyFoundations implements ModInitializer {
    public static final String MOD_ID = "colony_foundations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModHuts.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        BuilderRecruitment.initialize();
        BlueprintRegistry.register();
        ProjectNetworking.registerServer();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ModBlocks.TOWN_HALL);
            entries.add(ModItems.BUILDER_CONTRACT);
            entries.add(ModItems.WORKER_CONTRACT);
            ModHuts.items().forEach(entries::add);
        });

        LOGGER.info("Colony Foundations initialized: Town Hall, {} hut/utility blocks, worker contracts, construction queue and blueprint import.", ModHuts.IDS.size());
    }
}
