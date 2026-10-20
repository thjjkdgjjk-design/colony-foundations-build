package dev.colonyschematics.client;

import dev.colonyschematics.ColonySchematics;
import dev.colonyschematics.network.PlaceSchematicPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

public final class ColonySchematicsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(ColonySchematics.SCHEMATIC_TOOL)) return ActionResult.PASS;
            if (world.isClient) {
                MinecraftClient.getInstance().setScreen(new SchematicCatalogScreen());
            }
            return ActionResult.SUCCESS;
        });
    }

    public static void requestPlacement(PlaceSchematicPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
