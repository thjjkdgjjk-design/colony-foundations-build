package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.block.TownHallBlockEntity;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ProjectNetworking {
    private ProjectNetworking() { }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(SetProjectPayload.ID, SetProjectPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetProjectPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    private static void handle(ServerPlayerEntity player, SetProjectPayload payload) {
        World world = player.getWorld();
        BlockPos pos = payload.hallPos();
        if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
        if (!(world.getBlockEntity(pos) instanceof TownHallBlockEntity hall)) return;

        if (payload.action() == 2) {
            hall.togglePause(player);
            return;
        }
        if (payload.action() != 1 || payload.rotation() < 0 || payload.rotation() > 3) return;
        if (BlueprintRegistry.getDefinition(payload.blueprintId()).isEmpty()) {
            player.sendMessage(Text.literal("Чертёж не найден. Перезапустите мир или проверьте ресурсы мода."), true);
            ColonyFoundations.LOGGER.warn("Player {} requested missing blueprint {}",
                    player.getName().getString(), payload.blueprintId());
            return;
        }
        hall.startProject(payload.blueprintId(), payload.rotation(), player);
    }
}
