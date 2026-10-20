package dev.colonyfoundations.block;

import com.mojang.serialization.MapCodec;
import dev.colonyfoundations.worker.FarmerJobRules;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Farmer field/scarecrow anchor with persistent crop and radius settings. */
public final class FarmFieldBlock extends BlockWithEntity {
    public static final MapCodec<FarmFieldBlock> CODEC = createCodec(FarmFieldBlock::new);

    public FarmFieldBlock(Settings settings) { super(settings); }

    @Override
    protected MapCodec<? extends FarmFieldBlock> getCodec() { return CODEC; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FarmFieldBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld)
                || !(world.getBlockEntity(pos) instanceof FarmFieldBlockEntity field)) {
            return ActionResult.PASS;
        }

        ItemStack held = serverPlayer.getMainHandStack();
        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        var crop = FarmerJobRules.cropForItemId(itemId);
        if (crop.isPresent()) {
            field.configureCropFromItem(itemId);
            Identifier cropId = Identifier.tryParse(crop.get().blockId());
            String cropName = cropId != null && Registries.BLOCK.containsId(cropId)
                    ? Text.translatable(Registries.BLOCK.get(cropId).getTranslationKey()).getString()
                    : crop.get().blockId();
            serverPlayer.sendMessage(Text.translatable(
                    "message.colony_foundations.field_crop_set", cropName), true);
            return ActionResult.SUCCESS;
        }

        if (serverPlayer.isSneaking() && held.isEmpty()) {
            int radius = field.cycleRadius();
            serverPlayer.sendMessage(Text.translatable(
                    "message.colony_foundations.field_radius_set", radius, radius * 2 + 1), true);
            return ActionResult.SUCCESS;
        }

        String cropName = "не выбрана";
        Identifier cropId = Identifier.tryParse(field.cropBlockId());
        if (field.configured() && cropId != null && Registries.BLOCK.containsId(cropId)) {
            cropName = Text.translatable(Registries.BLOCK.get(cropId).getTranslationKey()).getString();
        }
        serverPlayer.sendMessage(Text.translatable(
                "message.colony_foundations.field_status", cropName, field.fieldRadius()), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
}
