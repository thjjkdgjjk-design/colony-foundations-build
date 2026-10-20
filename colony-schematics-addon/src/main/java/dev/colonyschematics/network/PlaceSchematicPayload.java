package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlaceSchematicPayload(Identifier schematicId, int rotation) implements CustomPayload {
    public static final Id<PlaceSchematicPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "place_schematic"));

    public static final PacketCodec<RegistryByteBuf, PlaceSchematicPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, PlaceSchematicPayload::schematicId,
            PacketCodecs.VAR_INT, PlaceSchematicPayload::rotation,
            PlaceSchematicPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
