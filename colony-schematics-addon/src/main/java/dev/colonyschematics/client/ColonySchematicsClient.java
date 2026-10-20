package dev.colonyschematics.client;

import dev.colonyschematics.ColonySchematics;
import dev.colonyschematics.network.PlaceSchematicPayload;
import dev.colonyschematics.network.PlaceStoredBlueprintPayload;
import dev.colonyschematics.network.SelectSchematicCornerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class ColonySchematicsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(ColonySchematics.SCHEMATIC_TOOL)) {
                if (world.isClient) {
                    ClientPlayNetworking.send(new SelectSchematicCornerPayload(hit.getBlockPos()));
                }
                return ActionResult.SUCCESS;
            }
            if (stack.isOf(ColonySchematics.BLUEPRINT_SCROLL)) {
                if (world.isClient) {
                    ClientPlayNetworking.send(new PlaceStoredBlueprintPayload(hand.ordinal(), 0));
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(ColonySchematics.SCHEMATIC_TOOL)) {
                if (world.isClient) {
                    MinecraftClient.getInstance().setScreen(new SchematicCatalogScreen());
                }
                return ActionResult.SUCCESS;
            }
            if (stack.isOf(ColonySchematics.BLUEPRINT_SCROLL)) {
                if (world.isClient) {
                    ClientPlayNetworking.send(new PlaceStoredBlueprintPayload(hand.ordinal(), 0));
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static void requestPlacement(PlaceSchematicPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
