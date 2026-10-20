package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Bounded server-authoritative catalogue page and an exact bounded subset of preview geometry. */
public record BlueprintCatalogPayload(
        BlockPos hallPos, int page, int totalPages, int totalEntries, ArrayList<Entry> entries
) implements CustomPayload {
    public static final int PAGE_SIZE = 5;
    public static final int MAX_PREVIEW_CELLS = 1800;
    public static final int MAX_MATERIAL_LINES = 4;

    public record PreviewCell(BlockPos offset, int color) {
        public static final PacketCodec<ByteBuf, PreviewCell> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, PreviewCell::offset,
                PacketCodecs.INTEGER, PreviewCell::color,
                PreviewCell::new
        );
        public PreviewCell {
            offset = offset == null ? BlockPos.ORIGIN : offset.toImmutable();
        }
    }

    public record Layout(int sizeX, int sizeY, int sizeZ, BlockPos primaryOffset) {
        public static final PacketCodec<ByteBuf, Layout> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Layout::sizeX,
                PacketCodecs.VAR_INT, Layout::sizeY,
                PacketCodecs.VAR_INT, Layout::sizeZ,
                BlockPos.PACKET_CODEC, Layout::primaryOffset,
                Layout::new
        );
        public Layout {
            sizeX = Math.max(1, Math.min(128, sizeX));
            sizeY = Math.max(1, Math.min(128, sizeY));
            sizeZ = Math.max(1, Math.min(128, sizeZ));
            primaryOffset = primaryOffset == null ? BlockPos.ORIGIN : primaryOffset.toImmutable();
        }
    }

    public record MaterialLine(Identifier blockId, int count) {
        public static final PacketCodec<ByteBuf, MaterialLine> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, MaterialLine::blockId,
                PacketCodecs.VAR_INT, MaterialLine::count,
                MaterialLine::new
        );
        public MaterialLine {
            count = Math.max(0, Math.min(131_072, count));
        }
    }

    public record PreviewData(Layout layout, int blockCount,
                              ArrayList<PreviewCell> cells, ArrayList<MaterialLine> materials) {
        public static final PacketCodec<ByteBuf, PreviewData> CODEC = PacketCodec.tuple(
                Layout.CODEC, PreviewData::layout,
                PacketCodecs.VAR_INT, PreviewData::blockCount,
                PacketCodecs.collection(ArrayList::new, PreviewCell.CODEC, MAX_PREVIEW_CELLS), PreviewData::cells,
                PacketCodecs.collection(ArrayList::new, MaterialLine.CODEC, MAX_MATERIAL_LINES), PreviewData::materials,
                PreviewData::new
        );
        public PreviewData {
            layout = layout == null ? new Layout(1, 1, 1, BlockPos.ORIGIN) : layout;
            blockCount = Math.max(0, Math.min(131_072, blockCount));
            cells = cells == null ? new ArrayList<>() : new ArrayList<>(cells);
            materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
            if (cells.size() > MAX_PREVIEW_CELLS) cells.subList(MAX_PREVIEW_CELLS, cells.size()).clear();
            if (materials.size() > MAX_MATERIAL_LINES) materials.subList(MAX_MATERIAL_LINES, materials.size()).clear();
        }
    }

    public record Entry(Identifier id, String title, PreviewData previewData) {
        public static final PacketCodec<ByteBuf, Entry> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, Entry::id,
                PacketCodecs.string(512), Entry::title,
                PreviewData.CODEC, Entry::previewData,
                Entry::new
        );
        public Entry {
            title = title == null || title.isBlank()
                    ? (id == null ? "Неизвестная схема" : id.toString())
                    : title.substring(0, Math.min(512, title.length()));
            previewData = previewData == null
                    ? new PreviewData(new Layout(1, 1, 1, BlockPos.ORIGIN), 0, new ArrayList<>(), new ArrayList<>())
                    : previewData;
        }

        public int blockCount() {
            return previewData.blockCount();
        }

        public String sizeSummary() {
            Layout layout = previewData.layout();
            return layout.sizeX() + " × " + layout.sizeY() + " × " + layout.sizeZ();
        }

        public static Entry fromDefinition(BlueprintDefinition definition) {
            ArrayList<PreviewCell> cells = new ArrayList<>();
            Map<Identifier, Integer> counts = new HashMap<>();
            for (CottageBlueprint.Placement placement : definition.placements()) {
                BlockState state = placement.state();
                Identifier blockId = Registries.BLOCK.getId(state.getBlock());
                counts.merge(blockId, 1, Integer::sum);
                if (cells.size() < MAX_PREVIEW_CELLS) {
                    cells.add(new PreviewCell(placement.offset(), colorForBlockId(blockId)));
                }
            }
            ArrayList<MaterialLine> materials = counts.entrySet().stream()
                    .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed()
                            .thenComparing(entry -> entry.getKey().toString()))
                    .limit(MAX_MATERIAL_LINES)
                    .map(entry -> new MaterialLine(entry.getKey(), entry.getValue()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Layout layout = new Layout(definition.sizeX(), definition.sizeY(), definition.sizeZ(),
                    definition.primaryOffset());
            return new Entry(definition.id(), definition.title(),
                    new PreviewData(layout, definition.placements().size(), cells, materials));
        }
    }

    public static final Id<BlueprintCatalogPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "blueprint_catalog"));
    public static final PacketCodec<RegistryByteBuf, BlueprintCatalogPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlueprintCatalogPayload::hallPos,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::page,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::totalPages,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::totalEntries,
            PacketCodecs.collection(ArrayList::new, Entry.CODEC, PAGE_SIZE), BlueprintCatalogPayload::entries,
            (pos, page, pages, total, entries) ->
                    new BlueprintCatalogPayload(pos, page, pages, total, new ArrayList<>(entries))
    );

    public BlueprintCatalogPayload {
        page = Math.max(0, page);
        totalEntries = Math.max(0, totalEntries);
        totalPages = Math.max(1, totalPages);
        entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        if (entries.size() > PAGE_SIZE) entries.subList(PAGE_SIZE, entries.size()).clear();
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static int colorForBlockId(Identifier id) {
        String path = id.getPath();
        if (path.contains("glass")) return 0xFF79BCD5;
        if (path.contains("leaves")) return 0xFF588D4B;
        if (path.contains("log") || path.contains("stem")) return 0xFF795235;
        if (path.contains("planks") || path.contains("wood")) return 0xFFB58C55;
        if (path.contains("stone") || path.contains("cobble") || path.contains("brick")) return 0xFF828B94;
        if (path.contains("roof") || path.contains("stair")) return 0xFF9A7154;
        return 0xFFCCD3D8;
    }
}
