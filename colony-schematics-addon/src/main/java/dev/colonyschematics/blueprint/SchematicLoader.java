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
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    static final int MAX_BLOCK_ENTITIES = 16_384;
    static final int MAX_ENTITIES = 2_048;
    static final int MAX_REQUIRED_MODS = 256;
    private static final Pattern BUNDLED_PATH = Pattern.compile(
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
            name = name == null || name.isBlank() ? "Безымянный чертёж" : name;
            primaryOffset = primaryOffset == null ? BlockPos.ORIGIN : primaryOffset.toImmutable();
            blocks = List.copyOf(blocks);
            blockEntities = blockEntities.stream().map(entry ->
                    new BlockEntityPlacement(entry.position(), entry.nbt())).toList();
            entities = entities.stream().map(NbtCompound::copy).toList();
            requiredMods = List.copyOf(requiredMods);
        }
    }

    public record CatalogEntry(Identifier id, String title) { }

    /**
     * Load a bundled catalog schematic. Datapack blueprints must be loaded using
     * the server ResourceManager overload so the server, not the client, decides
     * which construction resources are allowed.
     */
    public static Schematic load(Identifier id) throws IOException {
        validateBundledId(id);
        return loadBundled(id);
    }

    /** Load a bundled schematic or a blueprint supplied by an enabled server datapack. */
    public static Schematic load(Identifier id, ResourceManager manager) throws IOException {
        if (id == null || !id.getNamespace().equals(ColonySchematics.MOD_ID)
                || id.getPath().contains("..") || id.getPath().isBlank()) {
            throw new IllegalArgumentException("Schematic identifier is invalid");
        }

        if (!id.getPath().startsWith("blueprints/")) {
            validateBundledId(id);
            return loadBundled(id);
        }

        String resourcePath = id.getPath();
        for (String extension : List.of(".blueprint", ".nbt")) {
            Identifier resourceId = Identifier.of(ColonySchematics.MOD_ID, resourcePath + extension);
            Optional<Resource> resource = manager.getResource(resourceId);
            if (resource.isPresent()) {
                try (InputStream input = resource.get().getInputStream()) {
                    NbtCompound root = NbtIo.readCompressed(input, NbtSizeTracker.of(MAX_NBT_BYTES));
                    return fromNbt(id, root);
                }
            }
        }
        throw new IOException("No enabled datapack contains schematic " + id);
    }

    /** Build a bounded catalog from bundled defaults and all blueprint files in enabled datapacks. */
    public static List<CatalogEntry> available(ResourceManager manager) {
        Map<String, CatalogEntry> entries = new TreeMap<>();
        String[][] styles = {
                {"birch", "Берёзовый стиль"},
                {"darkoak", "Тёмный дуб"},
                {"defaultwooden", "Деревянный стиль"},
                {"stone", "Каменный стиль"}
        };
        for (String[] style : styles) {
            for (int tier = 1; tier <= 5; tier++) {
                Identifier id = Identifier.of(ColonySchematics.MOD_ID,
                        "minecolonies/" + style[0] + "/buildings/citizen/citizen" + tier);
                entries.put(id.toString(), new CatalogEntry(id, style[1] + " — Дом жителя, уровень " + tier));
            }
        }

        manager.findResources("blueprints", id -> id.getPath().endsWith(".blueprint")
                        || id.getPath().endsWith(".nbt"))
                .forEach((resourceId, resource) -> {
                    if (!resourceId.getNamespace().equals(ColonySchematics.MOD_ID)) return;
                    String path = resourceId.getPath();
                    String extension = path.endsWith(".blueprint") ? ".blueprint" : ".nbt";
                    String withoutExtension = path.substring(0, path.length() - extension.length());
                    if (!withoutExtension.startsWith("blueprints/") || withoutExtension.contains("..")) return;
                    Identifier schematicId = Identifier.of(ColonySchematics.MOD_ID, withoutExtension);
                    entries.put(schematicId.toString(), new CatalogEntry(schematicId, displayName(schematicId)));
                });

        return List.copyOf(entries.values());
    }

    private static void validateBundledId(Identifier id) {
        if (id == null || !id.getNamespace().equals(ColonySchematics.MOD_ID)
                || !BUNDLED_PATH.matcher(id.getPath()).matches()) {
            throw new IllegalArgumentException("Schematic identifier is not in the bundled catalog");
        }
    }

    private static Schematic loadBundled(Identifier id) throws IOException {
        String resourcePath = "/assets/" + ColonySchematics.MOD_ID + "/schematics/" + id.getPath() + ".nbt";
        NbtCompound root;
        try (InputStream input = ColonySchematics.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Bundled schematic resource was not found: " + resourcePath);
            root = NbtIo.readCompressed(input, NbtSizeTracker.of(MAX_NBT_BYTES));
        }
        return fromNbt(id, root);
    }

    /** Parse an already-decoded blueprint, including user-created scan-tool blueprints. */
    public static Schematic fromNbt(Identifier id, NbtCompound root) throws IOException {
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
                    int paletteIndex = PackedPaletteRules.indexAt(packed, linear, palette.size());
                    linear++;
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
        if (list.size() > MAX_BLOCK_ENTITIES) {
            throw new IllegalArgumentException("Block-entity count exceeds limit " + MAX_BLOCK_ENTITIES);
        }
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
        if (list.size() > MAX_ENTITIES) {
            throw new IllegalArgumentException("Entity count exceeds limit " + MAX_ENTITIES);
        }
        List<NbtCompound> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i).orElse(null);
            if (tag != null && !tag.isEmpty()) result.add(tag.copy());
        }
        return List.copyOf(result);
    }

    private static List<String> readRequiredMods(NbtCompound root) {
        NbtList list = root.getList("required_mods").orElse(new NbtList());
        if (list.size() > MAX_REQUIRED_MODS) {
            throw new IllegalArgumentException("Required-mod metadata exceeds limit " + MAX_REQUIRED_MODS);
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String modId = list.getString(i).orElse("");
            if (!modId.isBlank()) result.add(modId);
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

    static String displayName(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("blueprints/")) path = path.substring("blueprints/".length());
        String[] pieces = path.split("/");
        int styleIndex = pieces.length > 1 && pieces[0].equals("minecolonies") ? 1 : 0;
        String styleId = pieces[styleIndex].toLowerCase(java.util.Locale.ROOT);
        String style = switch (styleId) {
            case "acacia" -> "Акация";
            case "ancientathens" -> "Древние Афины";
            case "birch", "medievalbirch" -> "Берёзовый стиль";
            case "caledonia" -> "Каледония";
            case "cavern" -> "Подземный мир";
            case "colonial" -> "Колониальный стиль";
            case "darkoak", "medievaldarkoak" -> "Тёмный дуб";
            case "fortress" -> "Крепость";
            case "incan" -> "Инкский стиль";
            case "jungle" -> "Стиль джунглей";
            case "lostcity" -> "Затерянный город";
            case "medievaloak", "defaultwooden", "wooden" -> "Деревянный стиль";
            case "medievalspruce", "spruce" -> "Еловый стиль";
            case "nordic" -> "Скандинавский стиль";
            case "original", "minecolonies" -> "Оригинальный стиль";
            case "pagoda" -> "Пагода";
            case "sandstone" -> "Песчаниковый стиль";
            case "shire" -> "Шир";
            case "spacewars" -> "Космические войны";
            case "stone", "medievalstone" -> "Каменный стиль";
            case "truedwarven" -> "Гномий стиль";
            case "warped" -> "Искажённый стиль";
            default -> "Стиль «" + styleId.replace("_", " ") + "»";
        };

        int categoryIndex = styleIndex + 1;
        String categoryId = categoryIndex < pieces.length - 1
                ? pieces[categoryIndex].toLowerCase(java.util.Locale.ROOT) : "";
        String category = switch (categoryId) {
            case "agriculture" -> "Сельское хозяйство";
            case "craftsmanship" -> "Ремесло";
            case "decorations" -> "Декорации";
            case "education" -> "Образование";
            case "fundamentals" -> "Основные постройки";
            case "infrastructure" -> "Инфраструктура";
            case "military" -> "Военные постройки";
            case "mystic" -> "Мистика";
            case "walls" -> "Стены и ограждения";
            case "buildings" -> "Постройки";
            case "roads" -> "Дороги";
            case "supplycamps" -> "Стартовые лагеря";
            case "supplyships" -> "Стартовые корабли";
            case "" -> "Чертежи";
            default -> "Раздел «" + categoryId.replace("_", " ") + "»";
        };

        String file = pieces[pieces.length - 1];
        if (file.endsWith(".blueprint")) file = file.substring(0, file.length() - ".blueprint".length());
        if (file.endsWith(".nbt")) file = file.substring(0, file.length() - ".nbt".length());
        return style + " — " + category + " — " + localizeBlueprintName(file);
    }

    private static String localizeBlueprintName(String rawName) {
        String compact = rawName.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        boolean alternative = compact.startsWith("alt") && compact.length() > 3;
        if (alternative) compact = compact.substring(3);

        int tier = 0;
        if (compact.length() > 1) {
            char last = compact.charAt(compact.length() - 1);
            String candidate = compact.substring(0, compact.length() - 1);
            if (last >= '1' && last <= '5' && !buildingName(candidate).isEmpty()) {
                compact = candidate;
                tier = last - '0';
            }
        }

        String translated = buildingName(compact);
        if (!translated.isEmpty()) {
            return (alternative ? "Альтернативный вариант: " : "")
                    + translated + (tier == 0 ? "" : ", уровень " + tier);
        }
        String readable = rawName.replace("_", " ").replace("-", " ");
        return alternative ? "Альтернативный вариант: " + readable : "Чертёж «" + readable + "»";
    }

    private static String buildingName(String stem) {
        return switch (stem) {
            case "alchemist" -> "Хижина алхимика";
            case "alchemisttower" -> "Башня алхимика";
            case "apiary", "beekeeper" -> "Пасека";
            case "archery" -> "Лучная мастерская";
            case "baker" -> "Хижина пекаря";
            case "bakery" -> "Пекарня";
            case "barracks" -> "Казармы";
            case "barrackstower" -> "Башня казарм";
            case "barrackstowerleft" -> "Левая башня казарм";
            case "barrackstowerright" -> "Правая башня казарм";
            case "barrackstowerlower" -> "Нижняя башня казарм";
            case "barrackstowerupper" -> "Верхняя башня казарм";
            case "blacksmith" -> "Кузница";
            case "builder" -> "Хижина строителя";
            case "chickenherder" -> "Птичник";
            case "citizen" -> "Дом жителя";
            case "combatacademy" -> "Боевая академия";
            case "cook" -> "Хижина повара";
            case "cowboy" -> "Коровник";
            case "deliveryman" -> "Хижина курьера";
            case "farmer" -> "Ферма";
            case "fisherman" -> "Рыбацкая хижина";
            case "guardtower" -> "Сторожевая башня";
            case "hospital" -> "Больница";
            case "house" -> "Дом";
            case "library" -> "Библиотека";
            case "lumberjack" -> "Хижина лесоруба";
            case "miner" -> "Шахта";
            case "mysticalsite" -> "Мистическое место";
            case "plantation" -> "Плантация";
            case "quarry" -> "Карьер";
            case "residence" -> "Жилой дом";
            case "restaurant" -> "Ресторан";
            case "sawmill" -> "Лесопилка";
            case "school" -> "Школа";
            case "stable" -> "Конюшня";
            case "swineherder" -> "Свинарник";
            case "tavern" -> "Таверна";
            case "townhall" -> "Ратуша";
            case "university" -> "Университет";
            case "warehouse" -> "Склад";
            default -> "";
        };
    }
}
