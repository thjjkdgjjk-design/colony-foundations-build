package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client request for starting construction or toggling its pause state. */
public record SetProjectPayload(BlockPos hallPos, Identifier blueprintId, int rotation, int action)
        implements CustomPayload {
    public static final Id<SetProjectPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "set_project"));

    public static final PacketCodec<RegistryByteBuf, SetProjectPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SetProjectPayload::hallPos,
            Identifier.PACKET_CODEC, SetProjectPayload::blueprintId,
            PacketCodecs.VAR_INT, SetProjectPayload::rotation,
            PacketCodecs.VAR_INT, SetProjectPayload::action,
            SetProjectPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
