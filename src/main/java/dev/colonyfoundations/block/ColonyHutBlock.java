package dev.colonyfoundations.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Generic, data-backed work-hut block used as the first Fabric registration layer
 * for MineColonies building families. Every hut has its own registry ID, while
 * shared inventory/level/worker state is handled by ColonyHutBlockEntity.
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
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity serverPlayer
                && world.getBlockEntity(pos) instanceof ColonyHutBlockEntity hut) {
            serverPlayer.openHandledScreen(hut);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
