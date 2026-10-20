package dev.colonyschematics.blueprint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.colonyschematics.ColonySchematics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads Structurize v1 native compressed-NBT schematics.
 *
 * Preserves the palette/grid format, tile-entity NBT, entity NBT, required-mod
 * metadata, and Structurize primary anchor. Unknown blocks are replaced with
 * air; callers must still validate destination bounds and entity types.
 */
public final class SchematicLoader {
    private static final long MAX_NBT_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 128;
    private static final int MAX_VOLUME = 131_072;
    private static final int MAX_PALETTE = 65_535;
    private static final Pattern ALLOWED_PATH = Pattern.compile(
            "minecolonies/(birch|darkoak|defaultwooden|stone)/buildings/citizen/citizen[1-5]"
    );

    private SchematicLoader() { }

    public record BlockPlacement(BlockPos position, BlockState state) { }
    public record BlockEntityPlacement(BlockPos position, NbtCompound nbt) {
        public BlockEntityPlacement {
            position = position.toImmutable();
            nbt = nbt.copy();
        }
    }
    public record Schematic(
            int sizeX,
            int sizeY,
            int sizeZ,
            String name,
            BlockPos primaryOffset,
            List<BlockPlacement> blocks,
            List<BlockEntityPlacement> blockEntities,
            List<NbtCompound> entities,
            List<String> requiredMods
    ) {
        public Schematic {
            name = name == null || name.isBlank() ? "Unnamed schematic" : name;
            primaryOffset = primaryOffset == null ? BlockPos.ORIGIN : primaryOffset.toImmutable();
            blocks = List.copyOf(blocks);
            blockEntities = blockEntities.stream().map(entry ->
                    new BlockEntityPlacement(entry.position(), entry.nbt())).toList();
            entities = entities.stream().map(NbtCompound::copy).toList();
            requiredMods = List.copyOf(requiredMods);
        }
    }

    public static Schematic load(Identifier id) throws IOException {
        if (!id.getNamespace().equals(ColonySchematics.MOD_ID) || !ALLOWED_PATH.matcher(id.getPath()).matches()) {
            throw new IllegalArgumentException("Schematic identifier is not in the bundled catalog");
        }

        String resourcePath = "/assets/" + ColonySchematics.MOD_ID + "/schematics/" + id.getPath() + ".nbt";
        NbtCompound root;
        try (InputStream input = ColonySchematics.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Bundled schematic resource was not found: " + resourcePath);
            root = NbtIo.readCompressed(input, NbtSizeTracker.of(MAX_NBT_BYTES));
        }

        int sizeX = Short.toUnsignedInt(root.getShort("size_x").orElse((short) 0));
        int sizeY = Short.toUnsignedInt(root.getShort("size_y").orElse((short) 0));
        int sizeZ = Short.toUnsignedInt(root.getShort("size_z").orElse((short) 0));
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > MAX_DIMENSION || sizeY > MAX_DIMENSION
                || sizeZ > MAX_DIMENSION || volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Invalid or excessive schematic dimensions");
        }

        NbtList paletteTag = root.getList("palette")
                .orElseThrow(() -> new IllegalArgumentException("Palette list is missing"));
        if (paletteTag.isEmpty() || paletteTag.size() > MAX_PALETTE) {
            throw new IllegalArgumentException("Palette is empty or too large");
        }

        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            NbtCompound entry = paletteTag.getCompound(i)
                    .orElseThrow(() -> new IllegalArgumentException("Palette entry is not a compound"));
            palette.add(readBlockState(entry));
        }

        int[] packedBlocks = root.getIntArray("blocks")
                .orElseThrow(() -> new IllegalArgumentException("Packed block array is missing"));
        if (packedBlocks.length * 2L < volume) {
            throw new IllegalArgumentException("Packed block array is truncated");
        }

        List<BlockPlacement> placements = new ArrayList<>((int) Math.min(volume, MAX_VOLUME));
        int linear = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int packed = packedBlocks[linear / 2];
                    int paletteIndex = (linear & 1) == 0 ? (packed >>> 16) & 0xFFFF : packed & 0xFFFF;
                    linear++;
                    if (paletteIndex >= palette.size()) continue;
                    BlockState state = palette.get(paletteIndex);
                    if (!state.isAir()) placements.add(new BlockPlacement(new BlockPos(x, y, z), state));
                }
            }
        }

        List<BlockEntityPlacement> blockEntities = readBlockEntities(root, sizeX, sizeY, sizeZ);
        List<NbtCompound> entities = readEntities(root);
        List<String> requiredMods = readRequiredMods(root);
        BlockPos primaryOffset = readPrimaryOffset(root);
        String name = root.getString("name").orElse(displayName(id));

        ColonySchematics.LOGGER.info(
                "Loaded schematic {} ({} blocks, {} block entities, {} entities; {}x{}x{})",
                id, placements.size(), blockEntities.size(), entities.size(), sizeX, sizeY, sizeZ);

        return new Schematic(sizeX, sizeY, sizeZ, name, primaryOffset,
                placements, blockEntities, entities, requiredMods);
    }

    private static List<BlockEntityPlacement> readBlockEntities(
            NbtCompound root, int sizeX, int sizeY, int sizeZ) {
        NbtList list = root.getList("tile_entities")
                .or(() -> root.getList("block_entities"))
                .orElse(new NbtList());
        List<BlockEntityPlacement> result = new ArrayList<>(list.size());

        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i).orElse(null);
            if (tag == null) continue;
            BlockPos pos = new BlockPos(readNumber(tag, "x"), readNumber(tag, "y"), readNumber(tag, "z"));
            if (!inside(pos, sizeX, sizeY, sizeZ)) continue;
            result.add(new BlockEntityPlacement(pos, tag));
        }
        return List.copyOf(result);
    }

    private static List<NbtCompound> readEntities(NbtCompound root) {
        NbtList list = root.getList("entities").orElse(new NbtList());
        List<NbtCompound> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i).orElse(null);
            if (tag != null && !tag.isEmpty()) result.add(tag.copy());
        }
        return List.copyOf(result);
    }

    private static List<String> readRequiredMods(NbtCompound root) {
        NbtList list = root.getList("required_mods").orElse(new NbtList());
        List<String> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String modId = list.getString(i).orElse("");
            if (!modId.isBlank() && !result.contains(modId)) result.add(modId);
        }
        return List.copyOf(result);
    }

    private static BlockPos readPrimaryOffset(NbtCompound root) {
        NbtCompound optionalData = root.getCompound("optional_data").orElse(new NbtCompound());
        NbtCompound structurizeData = optionalData.getCompound("structurize").orElse(new NbtCompound());
        NbtCompound offset = structurizeData.getCompound("primary_offset").orElse(new NbtCompound());
        return new BlockPos(readNumber(offset, "x"), readNumber(offset, "y"), readNumber(offset, "z"));
    }

    private static boolean inside(BlockPos pos, int sx, int sy, int sz) {
        return pos.getX() >= 0 && pos.getX() < sx
                && pos.getY() >= 0 && pos.getY() < sy
                && pos.getZ() >= 0 && pos.getZ() < sz;
    }

    private static int readNumber(NbtCompound tag, String key) {
        Optional<Integer> intValue = tag.getInt(key);
        if (intValue.isPresent()) return intValue.get();
        Optional<Short> shortValue = tag.getShort(key);
        if (shortValue.isPresent()) return shortValue.get();
        Optional<Byte> byteValue = tag.getByte(key);
        return byteValue.orElse((byte) 0);
    }

    private static BlockState readBlockState(NbtCompound tag) {
        String name = tag.getString("Name").orElse("minecraft:air");
        Identifier blockId = Identifier.tryParse(name);
        if (blockId == null || !Registries.BLOCK.containsId(blockId)) {
            ColonySchematics.LOGGER.warn("Schematic block {} is missing; replacing it with air", name);
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = Registries.BLOCK.get(blockId).getDefaultState();
        NbtCompound properties = tag.getCompound("Properties").orElse(new NbtCompound());
        JsonObject propsJson = new JsonObject();
        for (String key : properties.getKeys()) {
            properties.getString(key).ifPresent(value -> propsJson.addProperty(key, value));
        }

        for (Map.Entry<String, JsonElement> entry : propsJson.entrySet()) {
            Property<?> property = state.getBlock().getStateManager().getProperty(entry.getKey());
            if (property != null) state = withProperty(state, property, entry.getValue().getAsString());
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, Property property, String valueText) {
        Optional value = property.parse(valueText);
        return value.isPresent() ? state.with(property, (Comparable) value.get()) : state;
    }

    private static String displayName(Identifier id) {
        String[] pieces = id.getPath().split("/");
        String style = switch (pieces.length > 1 ? pieces[1] : "") {
            case "birch" -> "Birch";
            case "darkoak" -> "Dark Oak";
            case "defaultwooden" -> "Wooden";
            case "stone" -> "Stone";
            default -> "MineColonies";
        };
        String tier = pieces[pieces.length - 1].replaceAll("[^0-9]", "");
        return style + " — Citizen House " + tier;
    }
}
