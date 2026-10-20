package dev.colonyfoundations.block;

import dev.colonyfoundations.worker.FarmerJobRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Persisted crop selection, work radius, fertilizer setting, and daily work marker. */
public final class FarmFieldBlockEntity extends BlockEntity {
    private String cropBlockId = "";
    private int fieldRadius = 3;
    private long lastActionDay = Long.MIN_VALUE;

    public FarmFieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARM_FIELD, pos, state);
    }

    public boolean configureCropFromItem(String itemId) {
        var crop = FarmerJobRules.cropForItemId(itemId);
        if (crop.isEmpty()) return false;
        cropBlockId = crop.get().blockId();
        markDirty();
        return true;
    }

    public String cropBlockId() { return cropBlockId; }

    public boolean configured() {
        return FarmerJobRules.cropForBlockId(cropBlockId).isPresent();
    }

    public int fieldRadius() { return fieldRadius; }

    public int cycleRadius() {
        fieldRadius = fieldRadius >= 5 ? 1 : fieldRadius + 1;
        markDirty();
        return fieldRadius;
    }

    public boolean canActOnDay(long currentDay) {
        return FarmerJobRules.canActOnDay(lastActionDay, currentDay);
    }

    public void markActionDone(long currentDay) {
        if (lastActionDay == currentDay) return;
        lastActionDay = currentDay;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("CropBlockId", cropBlockId);
        nbt.putInt("FieldRadius", fieldRadius);
        nbt.putLong("LastActionDay", lastActionDay);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        String crop = nbt.getString("CropBlockId").orElse("");
        cropBlockId = FarmerJobRules.cropForBlockId(crop).isPresent() ? crop : "";
        fieldRadius = FarmerJobRules.clampFieldRadius(nbt.getInt("FieldRadius").orElse(3));
        lastActionDay = nbt.getLong("LastActionDay").orElse(Long.MIN_VALUE);
    }
}
