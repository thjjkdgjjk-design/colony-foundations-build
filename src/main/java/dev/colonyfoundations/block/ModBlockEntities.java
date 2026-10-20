package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    public static final BlockEntityType<TownHallBlockEntity> TOWN_HALL = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(ColonyFoundations.MOD_ID, "town_hall"),
            FabricBlockEntityTypeBuilder.create(TownHallBlockEntity::new, ModBlocks.TOWN_HALL).build()
    );

    public static final BlockEntityType<ColonyHutBlockEntity> COLONY_HUT = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(ColonyFoundations.MOD_ID, "colony_hut"),
            FabricBlockEntityTypeBuilder.create(
                    ColonyHutBlockEntity::new,
                    ModHuts.blocks().toArray(ColonyHutBlock[]::new)
            ).build()
    );

    public static final BlockEntityType<FarmFieldBlockEntity> FARM_FIELD = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(ColonyFoundations.MOD_ID, "scarecrow"),
            FabricBlockEntityTypeBuilder.create(FarmFieldBlockEntity::new, ModField.BLOCK).build()
    );

    private ModBlockEntities() { }

    public static void initialize() {
        // Loading this class performs the registrations above.
    }
}
