package dev.colonyschematics.blueprint;

import dev.colonyschematics.ColonySchematics;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Initial Fabric implementation of Structurize's scan/save path.
 * Captures a two-corner cuboid into a native Structurize-v1-style NBT blueprint
 * and puts it into a portable Blueprint Scroll item.
 */
public final class SchematicScanner {
    private static final int MAX_DIMENSION = 128;
    private static final int MAX_VOLUME = 32_768;
    private record FirstCorner(RegistryKey<World> dimension, BlockPos position) { }
    private static final Map<UUID, FirstCorner> FIRST_CORNERS = new HashMap<>();

    private SchematicScanner() { }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FIRST_CORNERS.remove(handler.player.getUuid()));
    }

    public static void selectCorner(ServerPlayerEntity player, BlockPos clicked) {
        ServerWorld world = player.getServerWorld();
        double distanceSquared = player.squaredDistanceTo(
                clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5);
        if (distanceSquared > 36.0
                || clicked.getY() < world.getBottomY()
                || clicked.getY() >= world.getBottomY() + world.getDimension().height()
                || !world.getWorldBorder().contains(clicked)
                || !world.isChunkLoaded(clicked)) {
            FIRST_CORNERS.remove(player.getUuid());
            player.sendMessage(Text.translatable("message.colony_schematics.scan_point_invalid"), true);
            return;
        }

        FirstCorner first = FIRST_CORNERS.remove(player.getUuid());
        if (first == null) {
            FIRST_CORNERS.put(player.getUuid(), new FirstCorner(world.getRegistryKey(), clicked.toImmutable()));
            player.sendMessage(Text.translatable("message.colony_schematics.scan_first_corner",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
            return;
        }
        if (!first.dimension().equals(world.getRegistryKey())) {
            FIRST_CORNERS.put(player.getUuid(), new FirstCorner(world.getRegistryKey(), clicked.toImmutable()));
            player.sendMessage(Text.translatable("message.colony_schematics.scan_dimension_changed"), true);
            return;
        }

        BlockPos firstPos = first.position();
        BlockPos min = new BlockPos(
                Math.min(firstPos.getX(), clicked.getX()),
                Math.min(firstPos.getY(), clicked.getY()),
                Math.min(firstPos.getZ(), clicked.getZ()));
        int sizeX = Math.abs(firstPos.getX() - clicked.getX()) + 1;
        int sizeY = Math.abs(firstPos.getY() - clicked.getY()) + 1;
        int sizeZ = Math.abs(firstPos.getZ() - clicked.getZ()) + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        if (sizeX > MAX_DIMENSION || sizeY > MAX_DIMENSION || sizeZ > MAX_DIMENSION || volume > MAX_VOLUME) {
            player.sendMessage(Text.translatable("message.colony_schematics.scan_too_large",
                    MAX_DIMENSION, MAX_VOLUME), true);
            return;
        }
        if (min.getY() < world.getBottomY()
                || min.getY() + sizeY > world.getBottomY() + world.getDimension().height()
                || !world.getWorldBorder().contains(min)
                || !world.getWorldBorder().contains(min.add(sizeX - 1, 0, sizeZ - 1))) {
            player.sendMessage(Text.translatable("message.colony_schematics.scan_outside_world"), true);
            return;
        }
        if (!areChunksLoaded(world, min, sizeX, sizeZ)) {
            player.sendMessage(Text.translatable("message.colony_schematics.scan_chunks_unloaded"), true);
            return;
        }

        try {
            String name = "scanned_" + min.getX() + "_" + min.getY() + "_" + min.getZ();
            NbtCompound blueprint = capture(world, min, sizeX, sizeY, sizeZ, name);
            ItemStack scroll = new ItemStack(ColonySchematics.BLUEPRINT_SCROLL);
            NbtCompound customData = new NbtCompound();
            customData.put("blueprint", blueprint);
            scroll.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));
            scroll.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Чертёж: " + name));

            if (!player.getInventory().insertStack(scroll)) {
                player.dropItem(scroll, false);
            }
            player.sendMessage(Text.translatable("message.colony_schematics.scan_saved",
                    sizeX, sizeY, sizeZ), false);
        } catch (Exception exception) {
            ColonySchematics.LOGGER.warn("Could not scan blueprint cuboid {} -> {}: {}",
                    firstPos, clicked, exception.getMessage(), exception);
            String key = exception instanceof IllegalArgumentException
                    && exception.getMessage() != null && exception.getMessage().contains("limit")
                    ? "message.colony_schematics.scan_too_complex"
                    : "message.colony_schematics.scan_failed";
            player.sendMessage(Text.translatable(key), true);
        }
    }

    private static boolean areChunksLoaded(ServerWorld world, BlockPos min, int sizeX, int sizeZ) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = (min.getX() + sizeX - 1) >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = (min.getZ() + sizeZ - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(new BlockPos(chunkX << 4, min.getY(), chunkZ << 4))) return false;
            }
        }
        return true;
    }

    private static NbtCompound capture(ServerWorld world, BlockPos min, int sizeX, int sizeY, int sizeZ,
                                       String name) {
        long volume = (long) sizeX * sizeY * sizeZ;
        NbtCompound root = new NbtCompound();
        root.putByte("version", (byte) 1);
        root.putShort("size_x", (short) sizeX);
        root.putShort("size_y", (short) sizeY);
        root.putShort("size_z", (short) sizeZ);
        root.putString("name", name);

        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        palette.add(Blocks.AIR.getDefaultState());
        paletteIndex.put(Blocks.AIR.getDefaultState(), 0);
        NbtList paletteNbt = new NbtList();
        paletteNbt.add(writeBlockState(Blocks.AIR.getDefaultState()));

        int[] packed = new int[(int) ((volume + 1) / 2)];
        NbtList blockEntities = new NbtList();
        Set<String> requiredMods = new HashSet<>();
        int linear = 0;

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos actual = min.add(x, y, z);
                    BlockState state = world.getBlockState(actual);

                    if (state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
                            || state.isOf(Blocks.REPEATING_COMMAND_BLOCK) || state.isOf(Blocks.STRUCTURE_BLOCK)
                            || state.isOf(Blocks.JIGSAW)) {
                        state = Blocks.AIR.getDefaultState();
                    }

                    Integer index = paletteIndex.get(state);
                    if (index == null) {
                        index = palette.size();
                        if (index >= 65_535) {
                            throw new IllegalArgumentException("Schematic palette limit exceeded");
                        }
                        palette.add(state);
                        paletteIndex.put(state, index);
                        paletteNbt.add(writeBlockState(state));
                    }

                    if ((linear & 1) == 0) {
                        packed[linear / 2] |= (index & 0xFFFF) << 16;
                    } else {
                        packed[linear / 2] |= index & 0xFFFF;
                    }
                    linear++;

                    Identifier blockId = Registries.BLOCK.getId(state.getBlock());
                    if (!state.isAir() && !blockId.getNamespace().equals("minecraft")
                            && !blockId.getNamespace().equals(ColonySchematics.MOD_ID)) {
                        requiredMods.add(blockId.getNamespace());
                    }

                    if (!state.isAir()) {
                        BlockEntity blockEntity = world.getBlockEntity(actual);
                        if (blockEntity != null) {
                            NbtCompound data = blockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
                            data.putShort("x", (short) x);
                            data.putShort("y", (short) y);
                            data.putShort("z", (short) z);
                            if (blockEntities.size() >= SchematicLoader.MAX_BLOCK_ENTITIES) {
                                throw new IllegalArgumentException("Block-entity count exceeds limit "
                                        + SchematicLoader.MAX_BLOCK_ENTITIES);
                            }
                            blockEntities.add(data);
                        }
                    }
                }
            }
        }

        root.put("palette", paletteNbt);
        root.putIntArray("blocks", packed);
        root.put("tile_entities", blockEntities);

        NbtList mods = new NbtList();
        requiredMods.stream().sorted().forEach(id -> mods.add(NbtString.of(id)));
        root.put("required_mods", mods);

        NbtList entities = captureEntities(world, min, sizeX, sizeY, sizeZ);
        root.put("entities", entities);

        NbtCompound primaryOffset = new NbtCompound();
        primaryOffset.putInt("x", sizeX / 2);
        primaryOffset.putInt("y", 0);
        primaryOffset.putInt("z", sizeZ / 2);
        NbtCompound structurize = new NbtCompound();
        structurize.put("primary_offset", primaryOffset);
        NbtCompound optional = new NbtCompound();
        optional.put("structurize", structurize);
        root.put("optional_data", optional);
        return root;
    }

    private static NbtCompound writeBlockState(BlockState state) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Name", Registries.BLOCK.getId(state.getBlock()).toString());
        NbtCompound properties = new NbtCompound();
        state.getEntries().forEach((property, value) ->
                properties.putString(property.getName(), value.toString()));
        if (!properties.isEmpty()) entry.put("Properties", properties);
        return entry;
    }

    private static NbtList captureEntities(ServerWorld world, BlockPos min, int sizeX, int sizeY, int sizeZ) {
        Box bounds = new Box(min.getX(), min.getY(), min.getZ(),
                min.getX() + sizeX, min.getY() + sizeY, min.getZ() + sizeZ);
        NbtList result = new NbtList();
        for (Entity entity : world.getOtherEntities(null, bounds, entity -> true)) {
            if (entity instanceof PlayerEntity || !entity.getType().isSaveable()) continue;
            NbtCompound data = new NbtCompound();
            if (!entity.saveNbt(data)) continue;

            // Convert every saved passenger position as well as the root position.
            makeEntityTreeRelative(data, min);
            data.put("TileX", NbtInt.of(entity.getBlockPos().getX() - min.getX()));
            data.put("TileY", NbtInt.of(entity.getBlockPos().getY() - min.getY()));
            data.put("TileZ", NbtInt.of(entity.getBlockPos().getZ() - min.getZ()));
            if (result.size() >= SchematicLoader.MAX_ENTITIES) {
                throw new IllegalArgumentException("Entity count exceeds limit " + SchematicLoader.MAX_ENTITIES);
            }
            result.add(data);
        }
        return result;
    }

    private static void makeEntityTreeRelative(NbtCompound data, BlockPos origin) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position != null && position.size() >= 3) {
            NbtList relative = new NbtList();
            relative.add(NbtDouble.of(position.getDouble(0).orElse(0.0) - origin.getX()));
            relative.add(NbtDouble.of(position.getDouble(1).orElse(0.0) - origin.getY()));
            relative.add(NbtDouble.of(position.getDouble(2).orElse(0.0) - origin.getZ()));
            data.put("Pos", relative);
        }
        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers == null) return;
        for (int i = 0; i < passengers.size(); i++) {
            NbtCompound passenger = passengers.getCompound(i).orElse(null);
            if (passenger != null) makeEntityTreeRelative(passenger, origin);
        }
    }
}
