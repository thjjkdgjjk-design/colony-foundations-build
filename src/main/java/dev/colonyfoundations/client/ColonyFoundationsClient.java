package dev.colonyfoundations.client;

import dev.colonyfoundations.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
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
    }

    public static void openTownHall(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new BlueprintCatalogScreen(pos));
    }

}
