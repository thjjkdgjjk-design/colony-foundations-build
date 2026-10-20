package dev.colonyschematics.network;

import dev.colonyschematics.ColonySchematics;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record SelectSchematicCornerPayload(BlockPos position) implements CustomPayload {
    public static final Id<SelectSchematicCornerPayload> ID =
            new Id<>(Identifier.of(ColonySchematics.MOD_ID, "select_schematic_corner"));

    public static final PacketCodec<RegistryByteBuf, SelectSchematicCornerPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SelectSchematicCornerPayload::position,
            SelectSchematicCornerPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
