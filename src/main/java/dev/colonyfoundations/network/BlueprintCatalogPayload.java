package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;

/** Bounded page of server-authoritative buildable schematic metadata. */
public record BlueprintCatalogPayload(
        BlockPos hallPos,
        int page,
        int totalPages,
        int totalEntries,
        ArrayList<Entry> entries
) implements CustomPayload {
    public static final int PAGE_SIZE = 5;

    public record Entry(Identifier id, String title, String sizeSummary, int blockCount) {
        public static final PacketCodec<ByteBuf, Entry> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, Entry::id,
                PacketCodecs.string(512), Entry::title,
                PacketCodecs.string(96), Entry::sizeSummary,
                PacketCodecs.VAR_INT, Entry::blockCount,
                Entry::new
        );

        public Entry {
            title = title == null || title.isBlank()
                    ? (id == null ? "Неизвестная схема" : id.toString())
                    : title.substring(0, Math.min(512, title.length()));
            sizeSummary = sizeSummary == null ? "" : sizeSummary.substring(0, Math.min(96, sizeSummary.length()));
            blockCount = Math.max(0, blockCount);
        }
    }

    public static final Id<BlueprintCatalogPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "blueprint_catalog"));

    public static final PacketCodec<RegistryByteBuf, BlueprintCatalogPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlueprintCatalogPayload::hallPos,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::page,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::totalPages,
            PacketCodecs.VAR_INT, BlueprintCatalogPayload::totalEntries,
            PacketCodecs.collection(ArrayList::new, Entry.CODEC, PAGE_SIZE),
            BlueprintCatalogPayload::entries,
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
}
