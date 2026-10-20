package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlaceStoredBlueprintPayload(int handOrdinal, int rotation) implements CustomPayload {
    public static final Id<PlaceStoredBlueprintPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "place_stored_blueprint"));

    public static final PacketCodec<RegistryByteBuf, PlaceStoredBlueprintPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PlaceStoredBlueprintPayload::handOrdinal,
            PacketCodecs.VAR_INT, PlaceStoredBlueprintPayload::rotation,
            PlaceStoredBlueprintPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
