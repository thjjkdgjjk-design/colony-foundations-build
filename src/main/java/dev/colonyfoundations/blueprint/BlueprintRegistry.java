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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Original data-pack blueprint loader for Colony Foundations.
 * Files live at data/<namespace>/colony_blueprints/<path>.json.
 * This deliberately does not claim compatibility with Structurize's native format.
 */
public final class BlueprintRegistry implements SimpleSynchronousResourceReloadListener {
    private static final String RESOURCE_FOLDER = "colony_blueprints";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BLOCKS = 8192;
    private static final int MAX_COORDINATE = 128;
    private static volatile Map<Identifier, List<CottageBlueprint.Placement>> BLUEPRINTS = Map.of();

    private BlueprintRegistry() { }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BlueprintRegistry());
    }

    public static Optional<List<CottageBlueprint.Placement>> get(Identifier id) {
        return Optional.ofNullable(BLUEPRINTS.get(id));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(ColonyFoundations.MOD_ID, "blueprint_resources");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, List<CottageBlueprint.Placement>> loaded = new HashMap<>();
        manager.findResources(RESOURCE_FOLDER, id -> id.getPath().endsWith(".json")).forEach((fileId, resource) -> {
            String prefix = RESOURCE_FOLDER + "/";
            String resourcePath = fileId.getPath();
            if (!resourcePath.startsWith(prefix) || !resourcePath.endsWith(".json")) {
                return;
            }

            String blueprintPath = resourcePath.substring(prefix.length(), resourcePath.length() - ".json".length());
            if (blueprintPath.isBlank()) return;
            Identifier blueprintId = Identifier.of(fileId.getNamespace(), blueprintPath);

            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) throw new IllegalArgumentException("root JSON value must be an object");
                List<CottageBlueprint.Placement> placements = parseBlueprint(parsed.getAsJsonObject());
                loaded.put(blueprintId, List.copyOf(placements));
                ColonyFoundations.LOGGER.info("Loaded blueprint {} ({} blocks)", blueprintId, placements.size());
            } catch (IOException | RuntimeException exception) {
                ColonyFoundations.LOGGER.warn("Ignoring invalid Colony Foundations blueprint {}: {}",
                        blueprintId, exception.getMessage());
            }
        });

        BLUEPRINTS = Map.copyOf(loaded);
        ColonyFoundations.LOGGER.info("Loaded {} data-pack blueprint(s)", BLUEPRINTS.size());
    }

    private static List<CottageBlueprint.Placement> parseBlueprint(JsonObject root) {
        if (!root.has("format") || root.get("format").getAsInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported or missing format version (expected " + FORMAT_VERSION + ")");
        }
        JsonArray blocks = root.getAsJsonArray("blocks");
        if (blocks == null || blocks.isEmpty()) throw new IllegalArgumentException("blocks must be a non-empty array");
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("blueprint exceeds the " + MAX_BLOCKS + " block safety limit");
        }

        List<CottageBlueprint.Placement> placements = new ArrayList<>(blocks.size());
        for (JsonElement element : blocks) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("each blocks entry must be an object");
            JsonObject entry = element.getAsJsonObject();
            JsonArray coordinates = entry.getAsJsonArray("pos");
            if (coordinates == null || coordinates.size() != 3) {
                throw new IllegalArgumentException("each block needs pos: [x, y, z]");
            }

            int x = coordinates.get(0).getAsInt();
            int y = coordinates.get(1).getAsInt();
            int z = coordinates.get(2).getAsInt();
            if (Math.abs((long) x) > MAX_COORDINATE
                    || Math.abs((long) y) > MAX_COORDINATE
                    || Math.abs((long) z) > MAX_COORDINATE) {
                throw new IllegalArgumentException("block position exceeds +/-" + MAX_COORDINATE + " blocks");
            }

            if (!entry.has("block")) throw new IllegalArgumentException("each block needs a namespaced block identifier");
            Identifier blockId = Identifier.tryParse(entry.get("block").getAsString());
            if (blockId == null) throw new IllegalArgumentException("invalid block identifier: " + entry.get("block"));
            Block block = Registries.BLOCK.getOrEmpty(blockId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown block: " + blockId));
            BlockState state = block.getDefaultState();

            if (entry.has("properties")) {
                JsonElement propertiesElement = entry.get("properties");
                if (!propertiesElement.isJsonObject()) throw new IllegalArgumentException("properties must be an object");
                state = applyProperties(state, propertiesElement.getAsJsonObject());
            }

            placements.add(new CottageBlueprint.Placement(new BlockPos(x, y, z), state));
        }
        return placements;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperties(BlockState state, JsonObject properties) {
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            Property property = state.getBlock().getStateManager().getProperty(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException("block " + Registries.BLOCK.getId(state.getBlock())
                        + " has no property '" + entry.getKey() + "'");
            }
            Optional<?> value = property.parse(entry.getValue().getAsString());
            if (value.isEmpty()) {
                throw new IllegalArgumentException("invalid value '" + entry.getValue().getAsString()
                        + "' for property '" + entry.getKey() + "'");
            }
            state = state.with(property, (Comparable) value.get());
        }
        return state;
    }
}
