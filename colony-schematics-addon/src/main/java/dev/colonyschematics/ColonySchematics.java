package dev.colonyschematics;

import dev.colonyschematics.blueprint.SchematicLoader;
import dev.colonyschematics.network.PlaceSchematicPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

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
            BlockPos requestedAnchor = player.getBlockPos().offset(facing, 4);
            BlockPos anchorOffset = rotateBlockPos(schematic.primaryOffset(),
                    schematic.sizeX(), schematic.sizeZ(), turns);
            BlockPos origin = requestedAnchor.subtract(anchorOffset);

            int placed = 0;
            int skipped = 0;
            for (SchematicLoader.BlockPlacement placement : schematic.blocks()) {
                BlockPos local = rotateBlockPos(placement.position(),
                        schematic.sizeX(), schematic.sizeZ(), turns);
                BlockPos target = origin.add(local);
                if (!insideWorld(world, target)) {
                    skipped++;
                    continue;
                }

                BlockState state = placement.state().rotate(blockRotation);
                if (world.setBlockState(target, state, Block.NOTIFY_ALL)) {
                    placed++;
                }
            }

            int blockEntitiesPlaced = 0;
            int blockEntitiesSkipped = 0;
            for (SchematicLoader.BlockEntityPlacement placement : schematic.blockEntities()) {
                BlockPos local = rotateBlockPos(placement.position(),
                        schematic.sizeX(), schematic.sizeZ(), turns);
                BlockPos target = origin.add(local);
                if (!insideWorld(world, target)) {
                    blockEntitiesSkipped++;
                    continue;
                }
                BlockEntity blockEntity = world.getBlockEntity(target);
                if (blockEntity == null) {
                    blockEntitiesSkipped++;
                    continue;
                }
                NbtCompound nbt = placement.nbt().copy();
                nbt.putInt("x", target.getX());
                nbt.putInt("y", target.getY());
                nbt.putInt("z", target.getZ());
                blockEntity.read(nbt, world.getRegistryManager());
                blockEntity.markDirty();
                BlockState state = world.getBlockState(target);
                world.updateListeners(target, state, state, Block.NOTIFY_ALL);
                blockEntitiesPlaced++;
            }

            int entitiesPlaced = placeEntities(world, schematic, origin, turns);
            player.sendMessage(Text.translatable("message.colony_schematics.placed",
                    placed, schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), false);
            player.sendMessage(Text.translatable("message.colony_schematics.placement_details",
                    blockEntitiesPlaced, entitiesPlaced, skipped + blockEntitiesSkipped), false);
            if (!schematic.requiredMods().isEmpty()) {
                player.sendMessage(Text.translatable("message.colony_schematics.required_mods",
                        String.join(", ", schematic.requiredMods())), false);
            }
        } catch (Exception exception) {
            LOGGER.warn("Unable to place schematic {}: {}", payload.schematicId(),
                    exception.getMessage(), exception);
            player.sendMessage(Text.translatable("message.colony_schematics.failed"), true);
        }
    }

    private static int placeEntities(ServerWorld world, SchematicLoader.Schematic schematic,
                                     BlockPos origin, int turns) {
        int placed = 0;
        for (NbtCompound original : schematic.entities()) {
            NbtCompound data = original.copy();
            Identifier entityId = Identifier.tryParse(data.getString("id").orElse(""));
            if (entityId == null || entityId.getPath().equals("player")
                    || entityId.getPath().equals("command_block_minecart")) {
                continue;
            }

            transformEntityTree(data, origin, schematic.sizeX(), schematic.sizeZ(), turns);
            Entity entity = EntityType.loadEntityWithPassengers(data, world, SpawnReason.LOAD, Function.identity());
            if (entity == null || !entity.getType().isSaveable()) continue;
            if (world.getWorldBorder().contains(entity.getBlockPos()) && world.spawnEntity(entity)) placed++;
        }
        return placed;
    }

    private static void transformEntityTree(NbtCompound data, BlockPos origin, int sx, int sz, int turns) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position != null && position.size() >= 3) {
            double x = position.getDouble(0).orElse(0.0);
            double y = position.getDouble(1).orElse(0.0);
            double z = position.getDouble(2).orElse(0.0);
            double rx;
            double rz;
            switch (turns) {
                case 1 -> { rx = sz - z; rz = x; }
                case 2 -> { rx = sx - x; rz = sz - z; }
                case 3 -> { rx = z; rz = sx - x; }
                default -> { rx = x; rz = z; }
            }
            NbtList transformed = new NbtList();
            transformed.add(NbtDouble.of(origin.getX() + rx));
            transformed.add(NbtDouble.of(origin.getY() + y));
            transformed.add(NbtDouble.of(origin.getZ() + rz));
            data.put("Pos", transformed);
        }

        NbtList rotation = data.getList("Rotation").orElse(null);
        if (rotation != null && rotation.size() >= 2 && turns != 0) {
            float yaw = rotation.getFloat(0).orElse(0.0f) + 90.0f * turns;
            NbtList transformed = new NbtList();
            transformed.add(NbtFloat.of(yaw));
            transformed.add(NbtFloat.of(rotation.getFloat(1).orElse(0.0f)));
            data.put("Rotation", transformed);
        }

        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers != null) {
            for (int i = 0; i < passengers.size(); i++) {
                Optional<NbtCompound> passenger = passengers.getCompound(i);
                passenger.ifPresent(tag -> transformEntityTree(tag, origin, sx, sz, turns));
            }
        }
    }

    private static BlockPos rotateBlockPos(BlockPos pos, int sx, int sz, int turns) {
        return switch (turns) {
            case 1 -> new BlockPos(sz - 1 - pos.getZ(), pos.getY(), pos.getX());
            case 2 -> new BlockPos(sx - 1 - pos.getX(), pos.getY(), sz - 1 - pos.getZ());
            case 3 -> new BlockPos(pos.getZ(), pos.getY(), sx - 1 - pos.getX());
            default -> pos;
        };
    }

    private static boolean insideWorld(ServerWorld world, BlockPos pos) {
        return pos.getY() >= world.getBottomY()
                && pos.getY() < world.getBottomY() + world.getDimension().height()
                && world.getWorldBorder().contains(pos);
    }
}
