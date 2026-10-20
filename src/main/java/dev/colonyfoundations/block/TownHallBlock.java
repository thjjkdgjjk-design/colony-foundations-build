package dev.colonyfoundations.block;

import com.mojang.serialization.MapCodec;
import dev.colonyfoundations.block.TownHallBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class TownHallBlock extends BlockWithEntity {
    public static final MapCodec<TownHallBlock> CODEC = createCodec(TownHallBlock::new);

    public TownHallBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends TownHallBlock> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Founding is an explicit server-side block interaction, never a side effect of a status packet.
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer
                && world.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            hall.ensureFounded(serverPlayer);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return BlockWithEntity.validateTicker(type, ModBlockEntities.TOWN_HALL, TownHallBlockEntity::tick);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
