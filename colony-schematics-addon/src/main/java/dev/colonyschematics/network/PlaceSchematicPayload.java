package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-authoritative placement request for a catalog blueprint. */
public record PlaceSchematicPayload(Identifier schematicId, int rotation, int mirror) implements CustomPayload {
    public static final Id<PlaceSchematicPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "place_schematic"));

    public static final PacketCodec<RegistryByteBuf, PlaceSchematicPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, PlaceSchematicPayload::schematicId,
            PacketCodecs.VAR_INT, PlaceSchematicPayload::rotation,
            PacketCodecs.VAR_INT, PlaceSchematicPayload::mirror,
            PlaceSchematicPayload::new
    );

    public PlaceSchematicPayload {
        rotation = Math.floorMod(rotation, 4);
        mirror = Math.floorMod(mirror, 3);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
