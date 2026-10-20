package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Requests one page of server-loaded schematics near the specified Town Hall. */
public record RequestBlueprintCatalogPayload(BlockPos hallPos, int page) implements CustomPayload {
    public static final Id<RequestBlueprintCatalogPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "request_blueprint_catalog"));

    public static final PacketCodec<RegistryByteBuf, RequestBlueprintCatalogPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, RequestBlueprintCatalogPayload::hallPos,
            PacketCodecs.VAR_INT, RequestBlueprintCatalogPayload::page,
            RequestBlueprintCatalogPayload::new
    );

    public RequestBlueprintCatalogPayload {
        page = Math.max(0, page);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
