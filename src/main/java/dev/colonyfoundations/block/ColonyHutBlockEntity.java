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
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private boolean requestFertilizer;
    private String courierRequestId = "";
    private String courierStage = "";
    private String courierTransferId = "";
    private long courierWarehousePosition;
    private long courierTargetPosition;
    private String courierCargoItemId = "";
    private int courierCargoCount;
    private static final int MAX_TRANSFER_RECEIPTS = 4096;
    private final LinkedHashMap<String, TransferReceipt> outboundTransferReceipts = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> appliedTransferReceipts = new LinkedHashMap<>();

    private record TransferReceipt(String itemId, int amount) { }

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

    /**
     * Insert a single stack and persist its receipt in the same block-entity NBT.
     * Replayed transfer IDs return success without inserting a second copy.
     */
    public boolean storeItemsOnce(String transferId, List<ItemStack> incoming) {
        if (!isValidTransferId(transferId) || incoming == null) return false;
        List<ItemStack> nonEmpty = incoming.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        if (nonEmpty.size() != 1) return false;

        ItemStack stack = nonEmpty.get(0);
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String fingerprint = itemId + "#" + stack.getCount();
        String applied = appliedTransferReceipts.get(transferId);
        if (applied != null) return applied.equals(fingerprint);
        if (!canStoreItems(nonEmpty) || !insertInto(inventory, stack.copy())) return false;

        rememberReceipt(appliedTransferReceipts, transferId, fingerprint);
        markDirty();
        return true;
    }

    public boolean hasDeliveryReceipt(String transferId) {
        return isValidTransferId(transferId) && appliedTransferReceipts.containsKey(transferId);
    }

    /** Only trust a durable receipt if its item and amount match the cargo being reconciled. */
    public boolean hasDeliveryReceipt(String transferId, String itemId, int amount) {
        if (!isValidTransferId(transferId) || amount < 1 || amount > 64) return false;
        Identifier item = Identifier.tryParse(itemId);
        return item != null && (item + "#" + amount).equals(appliedTransferReceipts.get(transferId));
    }

    public OptionalInt deliveryReceiptCount(String transferId, String itemId) {
        if (!isValidTransferId(transferId)) return OptionalInt.empty();
        Identifier item = Identifier.tryParse(itemId);
        if (item == null) return OptionalInt.empty();
        String fingerprint = appliedTransferReceipts.get(transferId);
        if (fingerprint == null) return OptionalInt.empty();
        int delimiter = fingerprint.lastIndexOf('#');
        if (delimiter <= 0 || !fingerprint.substring(0, delimiter).equals(item.toString())) {
            return OptionalInt.empty();
        }
        try {
            int count = Integer.parseInt(fingerprint.substring(delimiter + 1));
            return count > 0 && count <= 64 ? OptionalInt.of(count) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    public OptionalInt outboundTransferCount(String transferId, String itemId) {
        if (!isValidTransferId(transferId)) return OptionalInt.empty();
        Identifier item = Identifier.tryParse(itemId);
        if (item == null) return OptionalInt.empty();
        TransferReceipt receipt = outboundTransferReceipts.get(transferId);
        return receipt != null && receipt.itemId().equals(item.toString())
                && receipt.amount() > 0 && receipt.amount() <= 64
                ? OptionalInt.of(receipt.amount()) : OptionalInt.empty();
    }

    public boolean hasOutboundTransferReceipt(String transferId, String itemId) {
        return outboundTransferCount(transferId, itemId).isPresent();
    }

    private static boolean isValidTransferId(String transferId) {
        return transferId != null && !transferId.isBlank() && transferId.length() <= 128
                && transferId.indexOf('\n') < 0 && transferId.indexOf('\t') < 0;
    }

    private static <V> void rememberReceipt(LinkedHashMap<String, V> receipts, String transferId, V value) {
        receipts.putIfAbsent(transferId, value);
        while (receipts.size() > MAX_TRANSFER_RECEIPTS) {
            java.util.Iterator<String> iterator = receipts.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private void readTransferReceipts(NbtCompound nbt) {
        appliedTransferReceipts.clear();
        outboundTransferReceipts.clear();

        NbtCompound applied = nbt.getCompound("AppliedTransferReceipts").orElse(new NbtCompound());
        for (String transferId : applied.getKeys()) {
            if (appliedTransferReceipts.size() >= MAX_TRANSFER_RECEIPTS) break;
            if (!isValidTransferId(transferId)) continue;
            String fingerprint = applied.getString(transferId).orElse("");
            int delimiter = fingerprint.lastIndexOf('#');
            if (delimiter <= 0 || delimiter == fingerprint.length() - 1) continue;
            Identifier item = Identifier.tryParse(fingerprint.substring(0, delimiter));
            try {
                int amount = Integer.parseInt(fingerprint.substring(delimiter + 1));
                if (item != null && amount > 0 && amount <= 64) {
                    appliedTransferReceipts.putIfAbsent(transferId, item + "#" + amount);
                }
            } catch (NumberFormatException ignored) {
                // A damaged receipt is ignored; never synthesize inventory from it.
            }
        }

        NbtCompound outbound = nbt.getCompound("OutboundTransferReceipts").orElse(new NbtCompound());
        for (String transferId : outbound.getKeys()) {
            if (outboundTransferReceipts.size() >= MAX_TRANSFER_RECEIPTS) break;
            if (!isValidTransferId(transferId)) continue;
            NbtCompound record = outbound.getCompound(transferId).orElse(new NbtCompound());
            Identifier item = Identifier.tryParse(record.getString("ItemId").orElse(""));
            int amount = record.getInt("Amount").orElse(0);
            if (item != null && amount > 0 && amount <= 64) {
                outboundTransferReceipts.putIfAbsent(transferId, new TransferReceipt(item.toString(), amount));
            }
        }
    }

    public int countItem(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return 0;
        int count = 0;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(id)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Withdraw one stack of the requested item without touching other inventory slots. */
    public ItemStack withdrawItem(String itemId, int maximumCount) {
        if (maximumCount < 1) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).equals(id)) continue;
            return removeStack(slot, Math.min(maximumCount, stack.getCount()));
        }
        return ItemStack.EMPTY;
    }

    /** Idempotent warehouse withdrawal: a replay returns the original cargo without another debit. */
    public ItemStack withdrawItemForTransfer(String transferId, String itemId, int maximumCount) {
        if (!isValidTransferId(transferId) || maximumCount < 1) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        TransferReceipt previous = outboundTransferReceipts.get(transferId);
        if (previous != null) {
            if (!previous.itemId().equals(id.toString()) || previous.amount() > maximumCount) return ItemStack.EMPTY;
            ItemStack replay = new ItemStack(Registries.ITEM.get(id), previous.amount());
            return replay;
        }

        ItemStack removed = withdrawItem(id.toString(), maximumCount);
        if (removed.isEmpty()) return ItemStack.EMPTY;
        rememberReceipt(outboundTransferReceipts, transferId,
                new TransferReceipt(id.toString(), removed.getCount()));
        markDirty();
        return removed;
    }

    public boolean hasCourierTask() {
        return !courierRequestId.isBlank() && !courierStage.isBlank();
    }

    public String courierRequestId() { return courierRequestId; }
    public String courierStage() { return courierStage; }
    public String courierTransferId() { return courierTransferId; }
    public BlockPos courierWarehousePosition() { return BlockPos.fromLong(courierWarehousePosition); }
    public BlockPos courierTargetPosition() { return BlockPos.fromLong(courierTargetPosition); }
    public String courierCargoItemId() { return courierCargoItemId; }
    public int courierCargoCount() { return courierCargoCount; }

    public boolean beginCourierTask(String requestId, String transferId, BlockPos warehouse, BlockPos target) {
        if (requestId == null || requestId.isBlank() || !isValidTransferId(transferId)
                || warehouse == null || target == null || hasCourierTask() || courierCargoCount > 0) return false;
        courierRequestId = requestId;
        courierTransferId = transferId;
        courierWarehousePosition = warehouse.asLong();
        courierTargetPosition = target.asLong();
        courierStage = "to_warehouse";
        markDirty();
        return true;
    }

    public void setCourierStage(String stage) {
        if (!"to_warehouse".equals(stage) && !"to_target".equals(stage)) return;
        courierStage = stage;
        markDirty();
    }

    public boolean setCourierCargo(String itemId, int amount) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id) || amount < 1
                || amount > Registries.ITEM.get(id).getDefaultStack().getMaxCount()
                || countItem(itemId) < amount) return false;
        courierCargoItemId = id.toString();
        courierCargoCount = amount;
        markDirty();
        return true;
    }

    /**
     * Store the picked stack and the courier metadata in one block-entity update.
     * This prevents a restart from saving the inventory addition but losing the cargo marker.
     */
    public boolean receiveCourierCargo(String transferId, ItemStack cargo) {
        if (!isValidTransferId(transferId) || !transferId.equals(courierTransferId)
                || cargo == null || cargo.isEmpty() || !hasCourierTask()) return false;
        String itemId = Registries.ITEM.getId(cargo.getItem()).toString();
        if (courierCargoCount > 0) {
            return courierCargoCount == cargo.getCount() && courierCargoItemId.equals(itemId)
                    && countItem(itemId) >= courierCargoCount;
        }
        if (!courierStage.equals("to_warehouse") || !canStoreItems(List.of(cargo.copy()))
                || !insertInto(inventory, cargo.copy())) return false;
        courierCargoItemId = itemId;
        courierCargoCount = cargo.getCount();
        courierStage = "to_target";
        markDirty();
        return true;
    }

    public void clearCourierCargo() {
        courierCargoItemId = "";
        courierCargoCount = 0;
        markDirty();
    }

    public void clearCourierTask() {
        courierRequestId = "";
        courierStage = "";
        courierTransferId = "";
        courierWarehousePosition = 0L;
        courierTargetPosition = 0L;
        markDirty();
    }

    public void ensureCourierTransferId() {
        if (!hasCourierTask() || isValidTransferId(courierTransferId)) return;
        courierTransferId = UUID.randomUUID().toString();
        markDirty();
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

        ColonyClaimState directory = ColonyClaimState.get(serverWorld);
        String dimensionId = serverWorld.getRegistryKey().getValue().toString();
        ColonyClaimState.ClaimRecord colony = directory.findColonyAt(dimensionId, pos).orElse(null);
        UUID colonyUuid = colony == null ? null
                : ColonyIdentityRules.parseUuid(colony.colonyId()).orElse(null);
        if (colonyUuid == null || !directory.registerCitizenAssignment(
                villager.getUuid(), colonyUuid, serverWorld.getRegistryKey(), pos,
                "colony_foundations:worker", "colony_foundations:" + hutId(), villager.getBlockPos())) {
            return false;
        }

        if (assignedCitizen != null && !assignedCitizen.equals(villager.getUuid())) {
            directory.releaseCitizenAssignment(assignedCitizen, serverWorld.getRegistryKey(), pos);
            Entity previous = serverWorld.getEntity(assignedCitizen);
            if (previous instanceof VillagerEntity oldVillager) {
                oldVillager.removeCommandTag(ownAssignmentTag);
                oldVillager.removeCommandTag(HUT_WORKER_TAG);
                removeJobTags(oldVillager);
                directory.forgetReleasedCitizenAssignment(assignedCitizen);
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

    /**
     * Release a worker when this hut is removed. If its entity is unloaded, the released
     * tombstone is retained so CitizenAssignmentLifecycle can clear stale tags on load.
     */
    public void releaseAssignedCitizen(ServerWorld serverWorld) {
        if (world != serverWorld || assignedCitizen == null) return;
        UUID releasedUuid = assignedCitizen;
        ColonyClaimState directory = ColonyClaimState.get(serverWorld);
        directory.releaseCitizenAssignment(releasedUuid, serverWorld.getRegistryKey(), pos);
        Entity entity = serverWorld.getEntity(releasedUuid);
        if (entity instanceof VillagerEntity villager) {
            villager.removeCommandTag(HUT_WORKER_TAG);
            villager.removeCommandTag(assignmentTag());
            removeJobTags(villager);
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            directory.forgetReleasedCitizenAssignment(releasedUuid);
        }
        assignedCitizen = null;
        markDirty();
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

    public boolean requestFertilizer() {
        return requestFertilizer;
    }

    public boolean toggleRequestFertilizer() {
        requestFertilizer = !requestFertilizer;
        markDirty();
        return requestFertilizer;
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
        nbt.putBoolean("RequestFertilizer", requestFertilizer);
        if (assignedCitizen != null) nbt.putString("AssignedCitizen", assignedCitizen.toString());
        nbt.putString("CourierRequestId", courierRequestId);
        nbt.putString("CourierStage", courierStage);
        nbt.putString("CourierTransferId", courierTransferId);
        nbt.putLong("CourierWarehousePosition", courierWarehousePosition);
        nbt.putLong("CourierTargetPosition", courierTargetPosition);
        nbt.putString("CourierCargoItemId", courierCargoItemId);
        nbt.putInt("CourierCargoCount", courierCargoCount);
        NbtCompound applied = new NbtCompound();
        appliedTransferReceipts.forEach(applied::putString);
        nbt.put("AppliedTransferReceipts", applied);
        NbtCompound outbound = new NbtCompound();
        outboundTransferReceipts.forEach((transferId, receipt) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("ItemId", receipt.itemId());
            entry.putInt("Amount", receipt.amount());
            outbound.put(transferId, entry);
        });
        nbt.put("OutboundTransferReceipts", outbound);
        Inventories.writeNbt(nbt, inventory, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        buildingLevel = Math.max(1, Math.min(5, nbt.getInt("BuildingLevel").orElse(1)));
        enabled = nbt.getBoolean("Enabled").orElse(true);
        requestFertilizer = nbt.getBoolean("RequestFertilizer").orElse(false);
        String rawUuid = nbt.getString("AssignedCitizen").orElse("");
        try {
            assignedCitizen = rawUuid.isBlank() ? null : UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
            assignedCitizen = null;
        }
        courierRequestId = nbt.getString("CourierRequestId").orElse("");
        courierStage = nbt.getString("CourierStage").orElse("");
        courierTransferId = nbt.getString("CourierTransferId").orElse("");
        courierWarehousePosition = nbt.getLong("CourierWarehousePosition").orElse(0L);
        courierTargetPosition = nbt.getLong("CourierTargetPosition").orElse(0L);
        courierCargoItemId = nbt.getString("CourierCargoItemId").orElse("");
        courierCargoCount = Math.max(0, Math.min(64, nbt.getInt("CourierCargoCount").orElse(0)));
        if (courierCargoCount == 0 || Identifier.tryParse(courierCargoItemId) == null) {
            courierCargoItemId = "";
            courierCargoCount = 0;
        }
        if (courierRequestId.isBlank()
                || (!courierStage.equals("to_warehouse") && !courierStage.equals("to_target"))) {
            courierRequestId = "";
            courierStage = "";
            courierTransferId = "";
            courierWarehousePosition = 0L;
            courierTargetPosition = 0L;
        } else if (!isValidTransferId(courierTransferId)) {
            courierTransferId = UUID.randomUUID().toString();
        }
        readTransferReceipts(nbt);
        inventory.clear();
        Inventories.readNbt(nbt, inventory, registries);
    }
}
