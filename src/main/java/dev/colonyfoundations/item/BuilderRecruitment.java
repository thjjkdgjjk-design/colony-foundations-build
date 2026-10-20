package dev.colonyfoundations.item;

import dev.colonyfoundations.block.TownHallBlockEntity;
import dev.colonyfoundations.block.WorkerRoleRules;
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
                player.sendMessage(Text.literal("Этот житель уже назначен строителем."), true);
                return ActionResult.SUCCESS;
            }
            if (!WorkerRoleRules.canRecruitAsBuilder(villager.getCommandTags())) {
                player.sendMessage(Text.literal("Этот житель уже назначен на другую работу. Сначала освободите его от текущего назначения."), true);
                return ActionResult.SUCCESS;
            }

            if (!villager.addCommandTag(TownHallBlockEntity.BUILDER_TAG)) {
                player.sendMessage(Text.literal("Не удалось назначить роль строителя."), true);
                return ActionResult.FAIL;
            }

            villager.setCustomName(Text.literal("Строитель"));
            villager.setCustomNameVisible(true);
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.sendMessage(Text.literal("Житель назначен строителем. Поставьте ратушу и выберите чертёж в каталоге, чтобы начать строительство."), false);
            return ActionResult.SUCCESS;
        });
    }
}
