package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client request to refresh the colony construction status panel. */
public record RequestProjectStatusPayload(BlockPos hallPos) implements CustomPayload {
    public static final Id<RequestProjectStatusPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "request_project_status"));

    public static final PacketCodec<RegistryByteBuf, RequestProjectStatusPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, RequestProjectStatusPayload::hallPos,
            RequestProjectStatusPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
