package dev.colonyfoundations.block;

import com.mojang.serialization.MapCodec;
import dev.colonyfoundations.item.ModItems;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Comparator;

/**
 * Registered colony work hut. Right-click with a Worker Contract to assign
 * a free villager; a normal right-click opens the persistent hut inventory.
 */
public final class ColonyHutBlock extends BlockWithEntity {
    public static final MapCodec<ColonyHutBlock> CODEC = createCodec(ColonyHutBlock::new);

    public ColonyHutBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends ColonyHutBlock> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyHutBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        boolean assignmentMode = player.getMainHandStack().isOf(ModItems.WORKER_CONTRACT);
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)
                || !(world.getBlockEntity(pos) instanceof ColonyHutBlockEntity hut)) {
            return ActionResult.PASS;
        }

        if (assignmentMode) {
            Box searchArea = new Box(pos).expand(12.0);
            VillagerEntity worker = serverWorld.getEntitiesByClass(VillagerEntity.class, searchArea,
                            candidate -> !candidate.isRemoved()
                                    && candidate.getCommandTags().stream().noneMatch(
                                    tag -> tag.startsWith(ColonyHutBlockEntity.HUT_ASSIGNED_TAG_PREFIX)))
                    .stream()
                    .min(Comparator.comparingDouble(candidate -> candidate.squaredDistanceTo(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                    .orElse(null);

            if (worker == null) {
                serverPlayer.sendMessage(Text.literal("[Colony] Рядом нет свободного жителя. Подведите жителя, которого ещё не назначали на здание."), true);
                return ActionResult.SUCCESS;
            }
            if (!hut.assignWorker(serverWorld, worker)) {
                serverPlayer.sendMessage(Text.literal("[Colony] Не удалось назначить жителя на эту работу."), true);
                return ActionResult.SUCCESS;
            }

            ItemStack contract = serverPlayer.getMainHandStack();
            if (!serverPlayer.getAbilities().creativeMode) contract.decrement(1);
            serverPlayer.sendMessage(Text.literal("[Colony] " + worker.getName().getString()
                    + " назначен в здание " + hut.hutId() + "."), false);
            return ActionResult.SUCCESS;
        }

        serverPlayer.openHandledScreen(hut);
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
