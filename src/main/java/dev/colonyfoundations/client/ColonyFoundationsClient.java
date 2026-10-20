package dev.colonyfoundations.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class ColonyFoundationsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() { }

    public static void openTownHall(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new BlueprintCatalogScreen(pos));
    }
}
