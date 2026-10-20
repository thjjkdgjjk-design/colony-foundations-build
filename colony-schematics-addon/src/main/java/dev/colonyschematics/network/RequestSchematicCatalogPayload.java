package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestSchematicCatalogPayload(int page, String query) implements CustomPayload {
    public static final Id<RequestSchematicCatalogPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "request_schematic_catalog"));

    public static final PacketCodec<RegistryByteBuf, RequestSchematicCatalogPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RequestSchematicCatalogPayload::page,
            PacketCodecs.string(128), RequestSchematicCatalogPayload::query,
            RequestSchematicCatalogPayload::new
    );

    public RequestSchematicCatalogPayload {
        query = query == null ? "" : query.substring(0, Math.min(query.length(), 128));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
