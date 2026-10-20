package dev.colonyschematics;

import dev.colonyschematics.blueprint.SchematicLoader;
import dev.colonyschematics.network.PlaceSchematicPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.BlockRotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColonySchematics implements ModInitializer {
    public static final String MOD_ID = "colony_schematics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier TOOL_ID = Identifier.of(MOD_ID, "schematic_tool");
    private static final RegistryKey<Item> TOOL_KEY = RegistryKey.of(RegistryKeys.ITEM, TOOL_ID);

    public static final Item SCHEMATIC_TOOL = Registry.register(
            Registries.ITEM,
            TOOL_ID,
            new Item(new Item.Settings().maxCount(1).registryKey(TOOL_KEY))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(SCHEMATIC_TOOL));

        PayloadTypeRegistry.playC2S().register(PlaceSchematicPayload.ID, PlaceSchematicPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlaceSchematicPayload.ID, (payload, context) ->
                context.server().execute(() -> placeSchematic(context.player(), payload)));
        LOGGER.info("Colony Schematics initialized (separate mod: {})", MOD_ID);
    }

    private static void placeSchematic(ServerPlayerEntity player, PlaceSchematicPayload payload) {
        if (!player.getAbilities().creativeMode) {
            player.sendMessage(Text.translatable("message.colony_schematics.creative_only"), true);
            return;
        }

        try {
            SchematicLoader.Schematic schematic = SchematicLoader.load(payload.schematicId());
            int turns = Math.floorMod(payload.rotation(), 4);
            BlockRotation blockRotation = switch (turns) {
                case 1 -> BlockRotation.CLOCKWISE_90;
                case 2 -> BlockRotation.CLOCKWISE_180;
                case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
                default -> BlockRotation.NONE;
            };

            ServerWorld world = player.getServerWorld();
            Direction facing = player.getHorizontalFacing();
            BlockPos origin = player.getBlockPos().offset(facing, 4);
            int placed = 0;
            for (SchematicLoader.BlockPlacement placement : schematic.blocks()) {
                BlockPos local = placement.position();
                int x;
                int z;
                switch (turns) {
                    case 1 -> { x = schematic.sizeZ() - 1 - local.getZ(); z = local.getX(); }
                    case 2 -> { x = schematic.sizeX() - 1 - local.getX(); z = schematic.sizeZ() - 1 - local.getZ(); }
                    case 3 -> { x = local.getZ(); z = schematic.sizeX() - 1 - local.getX(); }
                    default -> { x = local.getX(); z = local.getZ(); }
                }

                BlockPos target = origin.add(x, local.getY(), z);
                if (target.getY() < world.getBottomY() || target.getY() >= world.getBottomY() + world.getDimension().height()
                        || !world.getWorldBorder().contains(target)) {
                    continue;
                }
                BlockState state = placement.state().rotate(blockRotation);
                if (world.setBlockState(target, state, Block.NOTIFY_ALL)) {
                    placed++;
                }
            }
            player.sendMessage(Text.translatable("message.colony_schematics.placed", placed,
                    schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), false);
        } catch (Exception exception) {
            LOGGER.warn("Unable to place schematic {}: {}", payload.schematicId(), exception.getMessage());
            player.sendMessage(Text.translatable("message.colony_schematics.failed"), true);
        }
    }
}
