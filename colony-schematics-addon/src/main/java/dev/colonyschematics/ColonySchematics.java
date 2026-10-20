package dev.colonyschematics;

import dev.colonyschematics.blueprint.SchematicLoader;
import dev.colonyschematics.blueprint.SchematicScanner;
import dev.colonyschematics.network.PlaceStoredBlueprintPayload;
import dev.colonyschematics.network.SelectSchematicCornerPayload;
import dev.colonyschematics.network.PlaceSchematicPayload;
import dev.colonyschematics.network.RequestSchematicCatalogPayload;
import dev.colonyschematics.network.SchematicCatalogPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
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

    private static final Identifier BLUEPRINT_SCROLL_ID = Identifier.of(MOD_ID, "blueprint_scroll");
    private static final RegistryKey<Item> BLUEPRINT_SCROLL_KEY =
            RegistryKey.of(RegistryKeys.ITEM, BLUEPRINT_SCROLL_ID);

    public static final Item BLUEPRINT_SCROLL = Registry.register(
            Registries.ITEM,
            BLUEPRINT_SCROLL_ID,
            new Item(new Item.Settings().maxCount(1).registryKey(BLUEPRINT_SCROLL_KEY))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(SCHEMATIC_TOOL);
            entries.add(BLUEPRINT_SCROLL);
        });
        PayloadTypeRegistry.playC2S().register(PlaceSchematicPayload.ID, PlaceSchematicPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestSchematicCatalogPayload.ID, RequestSchematicCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SchematicCatalogPayload.ID, SchematicCatalogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectSchematicCornerPayload.ID, SelectSchematicCornerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceStoredBlueprintPayload.ID, PlaceStoredBlueprintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlaceSchematicPayload.ID, (payload, context) ->
                context.server().execute(() -> placeSchematic(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RequestSchematicCatalogPayload.ID, (payload, context) ->
                context.server().execute(() -> sendCatalog(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SelectSchematicCornerPayload.ID, (payload, context) ->
                context.server().execute(() -> SchematicScanner.selectCorner(context.player(), payload.position())));
        ServerPlayNetworking.registerGlobalReceiver(PlaceStoredBlueprintPayload.ID, (payload, context) ->
                context.server().execute(() -> placeStoredBlueprint(context.player(), payload)));
        LOGGER.info("Colony Schematics initialized with catalog, scan/save, and portable blueprints.");
    }


    private static void sendCatalog(ServerPlayerEntity player, RequestSchematicCatalogPayload request) {
        String query = request.query() == null ? "" : request.query().strip().toLowerCase(java.util.Locale.ROOT);
        var all = SchematicLoader.available(player.getServerWorld().getServer().getResourceManager());
        var filtered = all.stream()
                .filter(entry -> query.isBlank()
                        || entry.title().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || entry.id().toString().toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList();

        int total = filtered.size();
        int totalPages = Math.max(1, (total + SchematicCatalogPayload.PAGE_SIZE - 1)
                / SchematicCatalogPayload.PAGE_SIZE);
        int page = Math.max(0, Math.min(request.page(), totalPages - 1));
        int start = page * SchematicCatalogPayload.PAGE_SIZE;
        int end = Math.min(total, start + SchematicCatalogPayload.PAGE_SIZE);
        var entries = new java.util.ArrayList<SchematicCatalogPayload.Entry>(end - start);
        for (var item : filtered.subList(start, end)) {
            entries.add(new SchematicCatalogPayload.Entry(item.id(), item.title()));
        }

        ServerPlayNetworking.send(player, new SchematicCatalogPayload(page, query, totalPages, total, entries));
    }

    private static void placeSchematic(ServerPlayerEntity player, PlaceSchematicPayload payload) {
        if (!player.getAbilities().creativeMode) {
            player.sendMessage(Text.translatable("message.colony_schematics.creative_only"), true);
            return;
        }

        try {
            SchematicLoader.Schematic schematic = SchematicLoader.load(payload.schematicId(),
                    player.getServerWorld().getServer().getResourceManager());
            placeLoadedSchematic(player, schematic, payload.rotation(), payload.mirror(), payload.schematicId());
        } catch (Exception exception) {
            LOGGER.warn("Unable to place schematic {}: {}", payload.schematicId(),
                    exception.getMessage(), exception);
            player.sendMessage(Text.translatable("message.colony_schematics.failed"), true);
        }
    }

    private static void placeStoredBlueprint(ServerPlayerEntity player, PlaceStoredBlueprintPayload payload) {
        if (!player.getAbilities().creativeMode) {
            player.sendMessage(Text.translatable("message.colony_schematics.creative_only"), true);
            return;
        }
        if (payload.handOrdinal() < 0 || payload.handOrdinal() >= Hand.values().length) {
            player.sendMessage(Text.translatable("message.colony_schematics.failed"), true);
            return;
        }

        Hand hand = Hand.values()[payload.handOrdinal()];
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(BLUEPRINT_SCROLL)) return;
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            player.sendMessage(Text.translatable("message.colony_schematics.scroll_empty"), true);
            return;
        }

        NbtCompound custom = component.copyNbt();
        NbtCompound blueprint = custom.getCompound("blueprint").orElse(null);
        if (blueprint == null) {
            player.sendMessage(Text.translatable("message.colony_schematics.scroll_empty"), true);
            return;
        }

        try {
            SchematicLoader.Schematic schematic = SchematicLoader.fromNbt(
                    Identifier.of(MOD_ID, "player_blueprint"), blueprint);
            placeLoadedSchematic(player, schematic, payload.rotation(), 0,
                    Identifier.of(MOD_ID, "player_blueprint"));
        } catch (Exception exception) {
            LOGGER.warn("Unable to place portable blueprint: {}", exception.getMessage(), exception);
            player.sendMessage(Text.translatable("message.colony_schematics.failed"), true);
        }
    }

    private static void placeLoadedSchematic(ServerPlayerEntity player, SchematicLoader.Schematic schematic,
                                             int requestedRotation, int requestedMirror, Identifier sourceId) {
        int turns = Math.floorMod(requestedRotation, 4);
        int mirrorMode = Math.floorMod(requestedMirror, 3);
        BlockMirror blockMirror = switch (mirrorMode) {
            case 1 -> BlockMirror.FRONT_BACK;
            case 2 -> BlockMirror.LEFT_RIGHT;
            default -> BlockMirror.NONE;
        };
        BlockRotation blockRotation = switch (turns) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };

        ServerWorld world = player.getServerWorld();
        Direction facing = player.getHorizontalFacing();
        BlockPos requestedAnchor = player.getBlockPos().offset(facing, 4);
        BlockPos anchorOffset = transformBlockPos(schematic.primaryOffset(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
        BlockPos origin = requestedAnchor.subtract(anchorOffset);

        int placed = 0;
        int skipped = 0;
        for (SchematicLoader.BlockPlacement placement : schematic.blocks()) {
            BlockPos local = transformBlockPos(placement.position(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
            BlockPos target = origin.add(local);
            if (!insideWorld(world, target)) {
                skipped++;
                continue;
            }

            BlockState state = placement.state().mirror(blockMirror).rotate(blockRotation);
            if (world.setBlockState(target, state, Block.NOTIFY_ALL)) {
                placed++;
            }
        }

        int blockEntitiesPlaced = 0;
        int blockEntitiesSkipped = 0;
        for (SchematicLoader.BlockEntityPlacement placement : schematic.blockEntities()) {
            BlockPos local = transformBlockPos(placement.position(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
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

            transformEntityTree(data, origin, schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
            Entity entity = EntityType.loadEntityWithPassengers(data, world, SpawnReason.LOAD, Function.identity());
            if (entity == null || !entity.getType().isSaveable()) continue;
            if (world.getWorldBorder().contains(entity.getBlockPos()) && world.spawnEntity(entity)) placed++;
        }
        return placed;
    }

    private static void transformEntityTree(NbtCompound data, BlockPos origin, int sx, int sz, int turns, int mirrorMode) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position != null && position.size() >= 3) {
            double x = position.getDouble(0).orElse(0.0);
            double y = position.getDouble(1).orElse(0.0);
            double z = position.getDouble(2).orElse(0.0);
            double mx = x;
            double mz = z;
            if (mirrorMode == 1) mz = sz - mz;
            if (mirrorMode == 2) mx = sx - mx;
            double rx;
            double rz;
            switch (turns) {
                case 1 -> { rx = sz - mz; rz = mx; }
                case 2 -> { rx = sx - mx; rz = sz - mz; }
                case 3 -> { rx = mz; rz = sx - mx; }
                default -> { rx = mx; rz = mz; }
            }
            NbtList transformed = new NbtList();
            transformed.add(NbtDouble.of(origin.getX() + rx));
            transformed.add(NbtDouble.of(origin.getY() + y));
            transformed.add(NbtDouble.of(origin.getZ() + rz));
            data.put("Pos", transformed);
        }

        NbtList rotation = data.getList("Rotation").orElse(null);
        if (rotation != null && rotation.size() >= 2 && turns != 0) {
            float yaw = rotation.getFloat(0).orElse(0.0f);
            if (mirrorMode == 1) yaw = -yaw;
            if (mirrorMode == 2) yaw = 180.0f - yaw;
            yaw += 90.0f * turns;
            NbtList transformed = new NbtList();
            transformed.add(NbtFloat.of(yaw));
            transformed.add(NbtFloat.of(rotation.getFloat(1).orElse(0.0f)));
            data.put("Rotation", transformed);
        }

        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers != null) {
            for (int i = 0; i < passengers.size(); i++) {
                Optional<NbtCompound> passenger = passengers.getCompound(i);
                passenger.ifPresent(tag -> transformEntityTree(tag, origin, sx, sz, turns, mirrorMode));
            }
        }
    }

    private static BlockPos transformBlockPos(BlockPos pos, int sx, int sz, int turns, int mirrorMode) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (mirrorMode == 1) z = sz - 1 - z;
        if (mirrorMode == 2) x = sx - 1 - x;
        return switch (turns) {
            case 1 -> new BlockPos(sz - 1 - z, y, x);
            case 2 -> new BlockPos(sx - 1 - x, y, sz - 1 - z);
            case 3 -> new BlockPos(z, y, sx - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    private static boolean insideWorld(ServerWorld world, BlockPos pos) {
        return pos.getY() >= world.getBottomY()
                && pos.getY() < world.getBottomY() + world.getDimension().height()
                && world.getWorldBorder().contains(pos);
    }
}
