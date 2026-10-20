package dev.colonyfoundations.client;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.block.ModBlocks;
import dev.colonyfoundations.network.ProjectStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ColonyFoundationsClient implements ClientModInitializer {
    private static final Map<Identifier, BlueprintDefinition> PREVIEW_CACHE = new HashMap<>();

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
    }

    public static void openTownHall(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new BlueprintCatalogScreen(pos));
    }

    public static BlueprintDefinition preview(Identifier blueprintId) {
        if (blueprintId.getPath().equals("cottage")) {
            return CottageBlueprint.defaultDefinition();
        }
        BlueprintDefinition cached = PREVIEW_CACHE.get(blueprintId);
        if (cached != null) return cached;

        Identifier assetId = Identifier.of(ColonyFoundations.MOD_ID,
                "schematics/" + blueprintId.getPath() + ".nbt");
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(assetId);
        if (resource.isEmpty()) return null;

        try {
            BlueprintDefinition loaded = BlueprintRegistry.readNativeNbt(blueprintId, resource.get());
            PREVIEW_CACHE.put(blueprintId, loaded);
            return loaded;
        } catch (IOException | RuntimeException exception) {
            ColonyFoundations.LOGGER.warn("Could not create preview for {}: {}", blueprintId, exception.getMessage());
            return null;
        }
    }
}
