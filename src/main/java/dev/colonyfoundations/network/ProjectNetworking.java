package dev.colonyfoundations.network;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.block.TownHallBlockEntity;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class ProjectNetworking {
    private ProjectNetworking() { }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(SetProjectPayload.ID, SetProjectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestProjectStatusPayload.ID, RequestProjectStatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBlueprintCatalogPayload.ID, RequestBlueprintCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProjectStatusPayload.ID, ProjectStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintCatalogPayload.ID, BlueprintCatalogPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetProjectPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RequestProjectStatusPayload.ID, (payload, context) ->
                context.server().execute(() -> sendStatus(context.player(), payload.hallPos())));
        ServerPlayNetworking.registerGlobalReceiver(RequestBlueprintCatalogPayload.ID, (payload, context) ->
                context.server().execute(() -> sendCatalog(context.player(), payload)));
    }

    private static void sendCatalog(ServerPlayerEntity player, RequestBlueprintCatalogPayload request) {
        BlockPos hallPos = request.hallPos();
        if (!isNear(player, hallPos)
                || !(player.getWorld().getBlockEntity(hallPos) instanceof TownHallBlockEntity)) return;

        java.util.List<dev.colonyfoundations.blueprint.BlueprintDefinition> definitions =
                BlueprintRegistry.allBuildDefinitions();
        int total = definitions.size();
        int totalPages = Math.max(1, (total + BlueprintCatalogPayload.PAGE_SIZE - 1)
                / BlueprintCatalogPayload.PAGE_SIZE);
        int page = Math.max(0, Math.min(request.page(), totalPages - 1));
        int start = page * BlueprintCatalogPayload.PAGE_SIZE;
        int end = Math.min(total, start + BlueprintCatalogPayload.PAGE_SIZE);
        java.util.ArrayList<BlueprintCatalogPayload.Entry> entries = new java.util.ArrayList<>(end - start);
        for (var definition : definitions.subList(start, end)) {
            entries.add(BlueprintCatalogPayload.Entry.fromDefinition(definition));
        }
        ServerPlayNetworking.send(player,
                new BlueprintCatalogPayload(hallPos, page, totalPages, total, entries));
    }

    private static void handle(ServerPlayerEntity player, SetProjectPayload payload) {
        BlockPos pos = payload.hallPos();
        if (!isNear(player, pos)) return;
        if (!(player.getWorld().getBlockEntity(pos) instanceof TownHallBlockEntity hall)) return;
        if (!hall.canManage(player)) {
            sendStatus(player, pos);
            return;
        }

        if (payload.action() == 2) {
            hall.togglePause(player);
            sendStatus(player, pos);
            return;
        }
        if ((payload.action() != 1 && payload.action() != 3)
                || payload.rotation() < 0 || payload.rotation() > 3
                || payload.siteOffsetX() < -64 || payload.siteOffsetX() > 64
                || payload.siteOffsetZ() < -64 || payload.siteOffsetZ() > 64) return;
        if (BlueprintRegistry.getBuildDefinition(payload.blueprintId()).isEmpty()) {
            player.sendMessage(Text.literal("Чертёж не найден. Перезапустите мир или проверьте ресурсы мода."), true);
            ColonyFoundations.LOGGER.warn("Player {} requested missing blueprint {}",
                    player.getName().getString(), payload.blueprintId());
            sendStatus(player, pos);
            return;
        }
        if (payload.action() == 3) {
            hall.depositForSelectedProject(payload.blueprintId(), payload.rotation(),
                    payload.siteOffsetX(), payload.siteOffsetZ(), player);
        } else {
            hall.startProject(payload.blueprintId(), payload.rotation(),
                    payload.siteOffsetX(), payload.siteOffsetZ(), player);
        }
        sendStatus(player, pos);
    }

    private static void sendStatus(ServerPlayerEntity player, BlockPos pos) {
        if (!isNear(player, pos)) return;
        if (!(player.getWorld().getBlockEntity(pos) instanceof TownHallBlockEntity hall)) return;
        ServerPlayNetworking.send(player, new ProjectStatusPayload(
                pos, hall.statusForScreen(), hall.siteOffsetX(), hall.siteOffsetZ()));
    }

    private static boolean isNear(ServerPlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
