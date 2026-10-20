package dev.colonyfoundations.item;

import dev.colonyfoundations.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class BuilderRecruitment {
    private BuilderRecruitment() { }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack held = player.getStackInHand(hand);
            if (!(entity instanceof VillagerEntity villager) || !held.isOf(ModItems.BUILDER_CONTRACT)) {
                return ActionResult.PASS;
            }

            if (world.isClient) return ActionResult.SUCCESS;
            if (villager.getCommandTags().contains(TownHallBlockEntity.BUILDER_TAG)) {
                player.sendMessage(Text.literal("[Колония] Этот житель уже нанят строителем."), true);
                return ActionResult.SUCCESS;
            }

            if (!villager.addCommandTag(TownHallBlockEntity.BUILDER_TAG)) {
                player.sendMessage(Text.literal("[Колония] Не удалось назначить роль строителя."), true);
                return ActionResult.FAIL;
            }

            villager.setCustomName(Text.literal("Строитель"));
            villager.setCustomNameVisible(true);
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.sendMessage(Text.literal("[Колония] Житель нанят строителем. Поставьте Town Hall и нажмите ПКМ, чтобы заказать коттедж."), false);
            return ActionResult.SUCCESS;
        });
    }
}
