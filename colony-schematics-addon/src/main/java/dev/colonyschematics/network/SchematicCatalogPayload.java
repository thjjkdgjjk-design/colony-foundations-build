package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

public record SchematicCatalogPayload(
        int page,
        String query,
        int totalPages,
        int totalEntries,
        ArrayList<Entry> entries
) implements CustomPayload {
    public static final int PAGE_SIZE = 20;
    private static final int MAX_PACKET_ENTRIES = PAGE_SIZE;

    public record Entry(Identifier id, String title) {
        public static final PacketCodec<ByteBuf, Entry> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, Entry::id,
                PacketCodecs.string(512), Entry::title,
                Entry::new
        );
    }

    public static final Id<SchematicCatalogPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "schematic_catalog"));

    public static final PacketCodec<ByteBuf, SchematicCatalogPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SchematicCatalogPayload::page,
            PacketCodecs.string(128), SchematicCatalogPayload::query,
            PacketCodecs.VAR_INT, SchematicCatalogPayload::totalPages,
            PacketCodecs.VAR_INT, SchematicCatalogPayload::totalEntries,
            PacketCodecs.collection(ArrayList::new, Entry.CODEC, MAX_PACKET_ENTRIES),
            SchematicCatalogPayload::entries,
            (page, query, totalPages, totalEntries, entries) ->
                    new SchematicCatalogPayload(page, query, totalPages, totalEntries, new ArrayList<>(entries))
    );

    public SchematicCatalogPayload {
        query = query == null ? "" : query.substring(0, Math.min(query.length(), 128));
        entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        if (entries.size() > MAX_PACKET_ENTRIES) {
            entries.subList(MAX_PACKET_ENTRIES, entries.size()).clear();
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
