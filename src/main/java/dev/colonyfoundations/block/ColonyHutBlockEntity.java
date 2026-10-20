package dev.colonyfoundations.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import dev.colonyfoundations.worker.CitizenJobController;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared persistence/inventory base for registered MineColonies-style hut blocks.
 *
 * Original profession-specific production AI and upgrade requirements remain
 * separate port tasks; the base already gives every hut a real, persistent
 * in-game inventory, building level, and citizen assignment field.
 */
public final class ColonyHutBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    public static final String HUT_ASSIGNED_TAG_PREFIX = "colony_foundations_hut_assigned_";
    public static final String HUT_WORKER_TAG = "colony_foundations_hut_worker";
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(18, ItemStack.EMPTY);
    private int buildingLevel = 1;
    private UUID assignedCitizen;
    private boolean enabled = true;

    public ColonyHutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_HUT, pos, state);
    }

    public int buildingLevel() {
        return buildingLevel;
    }

    public boolean setBuildingLevel(int level) {
        int safeLevel = Math.max(1, Math.min(5, level));
        if (safeLevel == buildingLevel) return false;
        buildingLevel = safeLevel;
        markDirty();
        return true;
    }

    public Optional<UUID> assignedCitizen() {
        return Optional.ofNullable(assignedCitizen);
    }

    public boolean isAssignedWorker(VillagerEntity villager) {
        return assignedCitizen != null && assignedCitizen.equals(villager.getUuid())
                && villager.getCommandTags().contains(HUT_WORKER_TAG)
                && villager.getCommandTags().contains(assignmentTag());
    }

    /** Server-side work tick. Individual job handlers use bounded searches and never load chunks. */
    public static void tick(World world, BlockPos pos, BlockState state, ColonyHutBlockEntity hut) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)
                || serverWorld.getTime() % 20L != 0L || !hut.enabled()) return;
        CitizenJobController.tick(serverWorld, pos, hut);
    }

    /**
     * Test capacity for a complete job output before mutating the world. The simulated
     * inventory prevents harvesting a crop whose drops would be lost to a full hut.
     */
    public boolean canStoreItems(List<ItemStack> incoming) {
        List<ItemStack> simulated = new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) simulated.add(stack.copy());
        for (ItemStack stack : incoming) {
            if (stack != null && !stack.isEmpty() && !insertInto(simulated, stack.copy())) return false;
        }
        return true;
    }

    public boolean storeItems(List<ItemStack> incoming) {
        if (!canStoreItems(incoming)) return false;
        for (ItemStack stack : incoming) {
            if (stack != null && !stack.isEmpty()) insertInto(inventory, stack.copy());
        }
        markDirty();
        return true;
    }

    public boolean hasItem(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return false;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(id)) return true;
        }
        return false;
    }

    public boolean hasItemPathSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty()) return false;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith(suffix)) return true;
        }
        return false;
    }

    /** Consume stock only after callers validate the associated world mutation. */
    public boolean consumeItem(String itemId, int amount) {
        if (amount <= 0 || !hasItem(itemId)) return false;
        int available = 0;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                available += stack.getCount();
            }
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) continue;
            int moved = Math.min(remaining, stack.getCount());
            stack.decrement(moved);
            remaining -= moved;
            if (stack.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
        }
        markDirty();
        return remaining == 0;
    }

    

    

    /** Consume stock only after callers validate the associated world mutation. */
    

    private static boolean insertInto(List<ItemStack> slots, ItemStack incoming) {
        for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
            ItemStack existing = slots.get(i);
            if (!existing.isEmpty() && existing.getItem() == incoming.getItem()
                    && existing.getCount() < existing.getMaxCount()) {
                int moved = Math.min(incoming.getCount(), existing.getMaxCount() - existing.getCount());
                existing.increment(moved);
                incoming.decrement(moved);
            }
        }
        for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                int moved = Math.min(incoming.getCount(), incoming.getMaxCount());
                ItemStack placed = incoming.copy();
                placed.setCount(moved);
                slots.set(i, placed);
                incoming.decrement(moved);
            }
        }
        return incoming.isEmpty();
    }


    public void assignCitizen(UUID citizenUuid) {
        if (citizenUuid != null && citizenUuid.equals(assignedCitizen)) return;
        assignedCitizen = citizenUuid;
        markDirty();
    }

    public boolean assignWorker(ServerWorld serverWorld, VillagerEntity villager) {
        if (world != serverWorld || villager.isRemoved()) return false;
        if (!WorkerRoleRules.canAssignToHut(villager.getCommandTags())) return false;
        String ownAssignmentTag = assignmentTag();

        if (assignedCitizen != null && !assignedCitizen.equals(villager.getUuid())) {
            Entity previous = serverWorld.getEntity(assignedCitizen);
            if (previous instanceof VillagerEntity oldVillager) {
                oldVillager.removeCommandTag(ownAssignmentTag);
                oldVillager.removeCommandTag(HUT_WORKER_TAG);
                removeJobTags(oldVillager);
            }
        }

        removeJobTags(villager);
        villager.addCommandTag(HUT_WORKER_TAG);
        villager.addCommandTag(ownAssignmentTag);
        villager.addCommandTag("colony_foundations_job_" + hutId());
        villager.setCustomName(Text.translatable(
                "entity.colony_foundations.worker_at",
                Text.translatable("block.colony_foundations." + hutId())));
        villager.setCustomNameVisible(true);

        assignedCitizen = villager.getUuid();
        markDirty();
        return true;
    }

    private String assignmentTag() {
        return HUT_ASSIGNED_TAG_PREFIX + Long.toUnsignedString(pos.asLong(), 36);
    }

    private static void removeJobTags(VillagerEntity villager) {
        villager.getCommandTags().stream()
                .filter(tag -> tag.startsWith("colony_foundations_job_"))
                .toList()
                .forEach(villager::removeCommandTag);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        markDirty();
    }

    public String hutId() {
        return Registries.BLOCK.getId(getCachedState().getBlock()).getPath();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X2, syncId, playerInventory, this, 2);
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot >= 0 && slot < inventory.size() ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot < 0 || slot >= inventory.size()) return ItemStack.EMPTY;
        ItemStack removed = Inventories.splitStack(inventory, slot, amount);
        if (!removed.isEmpty()) markDirty();
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= inventory.size()) return ItemStack.EMPTY;
        ItemStack removed = Inventories.removeStack(inventory, slot);
        if (!removed.isEmpty()) markDirty();
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.size()) return;
        ItemStack value = stack == null ? ItemStack.EMPTY : stack;
        if (!value.isEmpty() && value.getCount() > value.getMaxCount()) {
            value = value.copy();
            value.setCount(value.getMaxCount());
        }
        inventory.set(slot, value);
        markDirty();
    }

    @Override
    public void clear() {
        inventory.clear();
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null
                && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("BuildingLevel", buildingLevel);
        nbt.putBoolean("Enabled", enabled);
        if (assignedCitizen != null) nbt.putString("AssignedCitizen", assignedCitizen.toString());
        Inventories.writeNbt(nbt, inventory, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        buildingLevel = Math.max(1, Math.min(5, nbt.getInt("BuildingLevel").orElse(1)));
        enabled = nbt.getBoolean("Enabled").orElse(true);
        String rawUuid = nbt.getString("AssignedCitizen").orElse("");
        try {
            assignedCitizen = rawUuid.isBlank() ? null : UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
            assignedCitizen = null;
        }
        inventory.clear();
        Inventories.readNbt(nbt, inventory, registries);
    }
}
