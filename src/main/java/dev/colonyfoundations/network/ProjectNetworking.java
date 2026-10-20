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
        PayloadTypeRegistry.playC2S().register(RequestProjectStatusPayload.ID, RequestProjectStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProjectStatusPayload.ID, ProjectStatusPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetProjectPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RequestProjectStatusPayload.ID, (payload, context) ->
                context.server().execute(() -> sendStatus(context.player(), payload.hallPos())));
    }

    private static void handle(ServerPlayerEntity player, SetProjectPayload payload) {
        BlockPos pos = payload.hallPos();
        if (!isNear(player, pos)) return;
        if (!(player.getWorld().getBlockEntity(pos) instanceof TownHallBlockEntity hall)) return;

        if (payload.action() == 2) {
            hall.togglePause(player);
            sendStatus(player, pos);
            return;
        }
        if ((payload.action() != 1 && payload.action() != 3) || payload.rotation() < 0 || payload.rotation() > 3) return;
        if (BlueprintRegistry.getDefinition(payload.blueprintId()).isEmpty()) {
            player.sendMessage(Text.literal("Чертёж не найден. Перезапустите мир или проверьте ресурсы мода."), true);
            ColonyFoundations.LOGGER.warn("Player {} requested missing blueprint {}",
                    player.getName().getString(), payload.blueprintId());
            sendStatus(player, pos);
            return;
        }
        if (payload.action() == 3) {
            hall.depositForSelectedProject(payload.blueprintId(), payload.rotation(), player);
        } else {
            hall.startProject(payload.blueprintId(), payload.rotation(), player);
        }
        sendStatus(player, pos);
    }

    private static void sendStatus(ServerPlayerEntity player, BlockPos pos) {
        if (!isNear(player, pos)) return;
        if (!(player.getWorld().getBlockEntity(pos) instanceof TownHallBlockEntity hall)) return;
        ServerPlayNetworking.send(player, new ProjectStatusPayload(pos, hall.statusForScreen()));
    }

    private static boolean isNear(ServerPlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
