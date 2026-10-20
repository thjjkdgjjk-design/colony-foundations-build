package dev.colonyfoundations.client;

import dev.colonyfoundations.block.ModBlocks;
import dev.colonyfoundations.network.BlueprintCatalogPayload;
import dev.colonyfoundations.network.ProjectStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ColonyFoundationsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.TOWN_HALL)) {
                openTownHall(hit.getBlockPos());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        BlueprintPreviewRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(ProjectStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof BlueprintCatalogScreen screen
                            && screen.previewHallPos().equals(payload.hallPos())) {
                        screen.updateStatus(payload.statusText(), payload.siteOffsetX(), payload.siteOffsetZ());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(BlueprintCatalogPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof BlueprintCatalogScreen screen
                            && screen.previewHallPos().equals(payload.hallPos())) {
                        screen.updateCatalog(payload);
                    }
                }));
    }

    public static void openTownHall(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new BlueprintCatalogScreen(pos));
    }

    /**
     * Only return preview geometry for the known built-in cottage.
     * Client resource packs are not proof that their schematic matches the server datapack.
     */
    public static BlueprintDefinition preview(Identifier blueprintId) {
        return Identifier.of("colony_foundations", "cottage").equals(blueprintId)
                ? CottageBlueprint.defaultDefinition() : null;
    }
}
