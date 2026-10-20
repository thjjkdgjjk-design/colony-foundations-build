package dev.colonyfoundations.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.colonyfoundations.ColonyFoundations;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads Colony Foundations JSON blueprints and Structurize v1 compressed-NBT
 * blueprints. Only the palette and block grid are imported; tile entities,
 * entities, and special Structurize substitutions are deliberately not executed.
 */
public final class BlueprintRegistry implements SimpleSynchronousResourceReloadListener {
    private static final String RESOURCE_FOLDER = "colony_blueprints";
    private static final int JSON_FORMAT_VERSION = 1;
    private static final int MAX_BLOCKS = 131_072;
    private static final int MAX_DIMENSION = 128;
    private static final long MAX_NBT_BYTES = 64L * 1024L * 1024L;
    private static volatile Map<Identifier, BlueprintDefinition> BLUEPRINTS = Map.of();

    private BlueprintRegistry() { }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BlueprintRegistry());
    }

    public static Optional<BlueprintDefinition> getDefinition(Identifier id) {
        return Optional.ofNullable(BLUEPRINTS.get(id));
    }

    public static BlueprintDefinition readNativeNbt(Identifier id, Resource resource) throws IOException {
        return readStructurizeNbt(id, resource);
    }

    public static List<BlueprintDefinition> all() {
        return BLUEPRINTS.values().stream().sorted(Comparator.comparing(BlueprintDefinition::title)).toList();
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(ColonyFoundations.MOD_ID, "blueprint_resources");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, BlueprintDefinition> loaded = new HashMap<>();
        manager.findResources(RESOURCE_FOLDER, id -> id.getPath().endsWith(".json") || id.getPath().endsWith(".nbt"))
                .forEach((fileId, resource) -> {
                    String prefix = RESOURCE_FOLDER + "/";
                    String resourcePath = fileId.getPath();
                    if (!resourcePath.startsWith(prefix)) return;

                    String extension = resourcePath.endsWith(".nbt") ? ".nbt" : ".json";
                    if (!resourcePath.endsWith(extension)) return;
                    String relativePath = resourcePath.substring(prefix.length(), resourcePath.length() - extension.length());
                    if (relativePath.isBlank()) return;
                    Identifier blueprintId = Identifier.of(fileId.getNamespace(), relativePath);

                    try {
                        BlueprintDefinition definition = extension.equals(".nbt")
                                ? readStructurizeNbt(blueprintId, resource)
                                : readJson(blueprintId, resource);
                        if (definition != null && !definition.placements().isEmpty()) {
                            loaded.put(blueprintId, definition);
                            ColonyFoundations.LOGGER.info("Loaded blueprint {} ({} blocks, {}x{}x{})",
                                    blueprintId, definition.placements().size(),
                                    definition.sizeX(), definition.sizeY(), definition.sizeZ());
                        }
                    } catch (IOException | RuntimeException exception) {
                        ColonyFoundations.LOGGER.warn("Ignoring invalid blueprint {}: {}",
                                blueprintId, exception.getMessage());
                    }
                });

        BLUEPRINTS = Map.copyOf(loaded);
        ColonyFoundations.LOGGER.info("Loaded {} construction blueprints", BLUEPRINTS.size());
    }

    private static BlueprintDefinition readJson(Identifier id, Resource resource) throws IOException {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root JSON value must be an object");
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("format") || root.get("format").getAsInt() != JSON_FORMAT_VERSION) {
                throw new IllegalArgumentException("unsupported JSON format version");
            }

            JsonArray blocks = root.getAsJsonArray("blocks");
            if (blocks == null || blocks.isEmpty()) throw new IllegalArgumentException("blocks must be a non-empty array");
            if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("blueprint exceeds block safety limit");

            List<CottageBlueprint.Placement> placements = new ArrayList<>(blocks.size());
            Set<String> compatibilityWarnings = new LinkedHashSet<>();
            int maxX = 0, maxY = 0, maxZ = 0;
            BlockPos primaryOffset = BlockPos.ORIGIN;
            if (root.has("anchor")) {
                JsonArray anchor = root.getAsJsonArray("anchor");
                if (anchor == null || anchor.size() != 3) {
                    throw new IllegalArgumentException("anchor must be [x,y,z]");
                }
                int ax = anchor.get(0).getAsInt();
                int ay = anchor.get(1).getAsInt();
                int az = anchor.get(2).getAsInt();
                validatePosition(ax, ay, az);
                primaryOffset = new BlockPos(ax, ay, az);
            }
            for (JsonElement element : blocks) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("each blocks entry must be an object");
                JsonObject entry = element.getAsJsonObject();
                JsonArray coordinates = entry.getAsJsonArray("pos");
                if (coordinates == null || coordinates.size() != 3 || !entry.has("block")) {
                    throw new IllegalArgumentException("expected pos: [x,y,z] and block");
                }
                int x = coordinates.get(0).getAsInt();
                int y = coordinates.get(1).getAsInt();
                int z = coordinates.get(2).getAsInt();
                validatePosition(x, y, z);
                BlockState state = readBlockState(
                        entry.get("block").getAsString(), entry.getAsJsonObject("properties"), compatibilityWarnings);
                if (!state.isAir()) placements.add(new CottageBlueprint.Placement(new BlockPos(x, y, z), state));
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            return new BlueprintDefinition(id, displayTitle(id), maxX + 1, maxY + 1, maxZ + 1,
                    primaryOffset, placements, List.copyOf(compatibilityWarnings));
        }
    }

    private static BlueprintDefinition readStructurizeNbt(Identifier id, Resource resource) throws IOException {
        NbtCompound root;
        try (var input = resource.getInputStream()) {
            root = NbtIo.readCompressed(input, NbtSizeTracker.of(MAX_NBT_BYTES));
        }
        short sx = root.getShort("size_x").orElse((short) 0);
        short sy = root.getShort("size_y").orElse((short) 0);
        short sz = root.getShort("size_z").orElse((short) 0);
        int sizeX = Short.toUnsignedInt(sx);
        int sizeY = Short.toUnsignedInt(sy);
        int sizeZ = Short.toUnsignedInt(sz);
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1
                || sizeX > MAX_DIMENSION || sizeY > MAX_DIMENSION || sizeZ > MAX_DIMENSION
                || volume > MAX_BLOCKS) {
            throw new IllegalArgumentException("invalid or excessive Structurize blueprint dimensions");
        }

        NbtList paletteTag = root.getList("palette")
                .orElseThrow(() -> new IllegalArgumentException("palette list is missing"));
        if (paletteTag.isEmpty() || paletteTag.size() > 65_535) {
            throw new IllegalArgumentException("palette is empty or too large");
        }
        Set<String> compatibilityWarnings = new LinkedHashSet<>();
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            NbtCompound entry = paletteTag.getCompound(i)
                    .orElseThrow(() -> new IllegalArgumentException("palette contains a non-compound entry"));
            palette.add(readNbtBlockState(entry, compatibilityWarnings));
        }

        int[] packedBlocks = root.getIntArray("blocks").orElseThrow(
                () -> new IllegalArgumentException("packed block array is missing"));
        if (packedBlocks.length * 2L < volume) throw new IllegalArgumentException("packed block array is truncated");

        List<CottageBlueprint.Placement> placements = new ArrayList<>((int) Math.min(volume, MAX_BLOCKS));
        int linear = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int packed = packedBlocks[linear / 2];
                    int paletteIndex = PackedPaletteRules.indexAt(packed, linear, palette.size());
                    linear++;
                    BlockState state = palette.get(paletteIndex);
                    // Structurize air cells are voids, not instructions to erase terrain.
                    if (!state.isAir()) placements.add(new CottageBlueprint.Placement(new BlockPos(x, y, z), state));
                }
            }
        }
        return new BlueprintDefinition(id, displayTitle(id), sizeX, sizeY, sizeZ,
                readPrimaryOffset(root), placements, List.copyOf(compatibilityWarnings));
    }

    private static BlockPos readPrimaryOffset(NbtCompound root) {
        NbtCompound optionalData = root.getCompound("optional_data").orElse(new NbtCompound());
        NbtCompound structurizeData = optionalData.getCompound("structurize").orElse(new NbtCompound());
        NbtCompound offset = structurizeData.getCompound("primary_offset").orElse(new NbtCompound());
        return new BlockPos(
                offset.getInt("x").orElse(0),
                offset.getInt("y").orElse(0),
                offset.getInt("z").orElse(0));
    }

    private static final int MAX_COMPATIBILITY_WARNINGS = 64;

    private static BlockState readNbtBlockState(NbtCompound tag, Set<String> compatibilityWarnings) {
        String name = tag.getString("Name")
                .orElseThrow(() -> new IllegalArgumentException("palette block is missing Name"));
        NbtCompound properties = tag.getCompound("Properties").orElse(new NbtCompound());
        JsonObject propsJson = new JsonObject();
        for (String key : properties.getKeys()) {
            properties.getString(key).ifPresent(value -> propsJson.addProperty(key, value));
        }
        return readBlockState(name, propsJson, compatibilityWarnings);
    }

    private static BlockState readBlockState(
            String rawId, JsonObject properties, Set<String> compatibilityWarnings) {
        Identifier blockId = Identifier.tryParse(rawId);
        if (blockId != null && SchematicSafetyRules.isUnsafeBlockId(blockId.toString())) {
            addCompatibilityWarning(compatibilityWarnings,
                    "Служебный блок " + rawId + " запрещён и заменён воздухом");
            ColonyFoundations.LOGGER.warn("Небезопасный блок {} в схеме заменён воздухом", rawId);
            return Blocks.AIR.getDefaultState();
        }
        if (blockId == null || !Registries.BLOCK.containsId(blockId)) {
            addCompatibilityWarning(compatibilityWarnings,
                    "Блок " + rawId + " отсутствует и заменён воздухом");
            ColonyFoundations.LOGGER.warn("Отсутствующий блок {} в схеме заменён воздухом", rawId);
            return Blocks.AIR.getDefaultState();
        }
        Block block = Registries.BLOCK.get(blockId);
        BlockState state = block.getDefaultState();
        if (properties == null) return state;

        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            String valueText = entry.getValue().getAsString();
            Property<?> property = state.getBlock().getStateManager().getProperty(propertyName);
            if (property == null) {
                addCompatibilityWarning(compatibilityWarnings,
                        "Свойство " + propertyName + " блока " + rawId + " не поддерживается");
                ColonyFoundations.LOGGER.warn("Неизвестное свойство {} блока {}", propertyName, blockId);
                continue;
            }
            state = withProperty(state, property, valueText, compatibilityWarnings, rawId, propertyName);
        }
        return state;
    }

    private static void addCompatibilityWarning(Set<String> warnings, String warning) {
        if (warnings.size() < MAX_COMPATIBILITY_WARNINGS) warnings.add(warning);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, Property property, String valueText,
                                           Set<String> compatibilityWarnings, String blockName, String propertyName) {
        Optional value = property.parse(valueText);
        if (value.isEmpty()) {
            addCompatibilityWarning(compatibilityWarnings,
                    "Значение " + propertyName + "=" + valueText + " блока " + blockName + " не поддерживается");
            ColonyFoundations.LOGGER.warn("Недопустимое значение свойства {}={} блока {}",
                    propertyName, valueText, blockName);
            return state;
        }
        return state.with(property, (Comparable) value.get());
    }

    private static void validatePosition(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= MAX_DIMENSION || y >= MAX_DIMENSION || z >= MAX_DIMENSION) {
            throw new IllegalArgumentException("position must be inside a blueprint of max dimension " + MAX_DIMENSION);
        }
    }

    private static String displayTitle(Identifier id) {
        String path = id.getPath();
        String[] parts = path.split("/");
        if (parts.length >= 5 && parts[parts.length - 2].equals("citizen")) {
            String style = parts[parts.length - 4];
            String tier = parts[parts.length - 1].replaceAll("[^0-9]", "");
            return styleTitle(style) + " — дом жителя " + (tier.isBlank() ? "" : tier);
        }
        if (path.equals("cottage")) return "Стартовый коттедж";
        return path.replace('/', ' ').replace('_', ' ');
    }

    private static String styleTitle(String style) {
        return switch (style) {
            case "birch" -> "Берёза";
            case "darkoak" -> "Тёмный дуб";
            case "defaultwooden" -> "Деревянный";
            case "stone" -> "Каменный";
            case "taiga" -> "Тайга";
            default -> style;
        };
    }
}
