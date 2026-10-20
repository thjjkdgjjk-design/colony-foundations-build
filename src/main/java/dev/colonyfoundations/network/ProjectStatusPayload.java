package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server response for the construction status panel in the catalog. */
public record ProjectStatusPayload(
        BlockPos hallPos, String statusText, int siteOffsetX, int siteOffsetZ
) implements CustomPayload {
    public static final Id<ProjectStatusPayload> ID =
            new Id<>(Identifier.of(ColonyFoundations.MOD_ID, "project_status"));

    public static final PacketCodec<RegistryByteBuf, ProjectStatusPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ProjectStatusPayload::hallPos,
            PacketCodecs.STRING, ProjectStatusPayload::statusText,
            PacketCodecs.VAR_INT, ProjectStatusPayload::siteOffsetX,
            PacketCodecs.VAR_INT, ProjectStatusPayload::siteOffsetZ,
            ProjectStatusPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
