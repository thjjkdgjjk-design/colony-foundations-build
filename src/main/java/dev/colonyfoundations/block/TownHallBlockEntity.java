package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.blueprint.CompatibilityWarningSummary;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

public final class TownHallBlockEntity extends BlockEntity {
    public static final String BUILDER_TAG = WorkerRoleRules.BUILDER_TAG;
    private static final String ASSIGNED_TAG_PREFIX = WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX;

    private boolean projectActive;
    private boolean projectComplete;
    private int progress;
    private int skippedObstructions;
    private long lastMissingResourceNotification = -1000L;
    private Identifier blueprintId = Identifier.of(ColonyFoundations.MOD_ID, "cottage");
    private int rotation;
    private int siteOffsetX = 5;
    private int siteOffsetZ = -3;
    private final Map<Identifier, Integer> resourceStock = new HashMap<>();
    private UUID ownerUuid;
    /** Stable colony identity, also indexed in world PersistentState while this chunk is unloaded. */
    private UUID colonyUuid;
    private String ownerName = "";
    private String colonyName = "";
    private int colonyLevel = 1;
    /** Last durable reason construction paused; empty when no known blocker exists. */
    private String projectIssue = "";
    private UUID assignedBuilderUuid;
    /** Durable identifier for the current Town Hall BUILD work order. */
    private UUID activeWorkOrderUuid;
    private String projectRequestedByUuid = "";
    private long lastWorkOrderSyncWarning = -1000L;
    private BlockPos lastBuilderPosition;
    private long lastBuilderUnavailableNotification = -1000L;
    // Transient navigation diagnostics; these reset after a world reload.
    private double lastBuilderDistance = -1.0;
    private int noProgressPathChecks;
    private int navigationFailures;
    private final Set<UUID> colonyMembers = new HashSet<>();
    private final Map<UUID, ColonyRank> colonyMemberRanks = new HashMap<>();
    private final Map<ColonyRank, Set<ColonyPermission>> colonyRankPermissions = new java.util.EnumMap<>(ColonyRank.class);
    /** Reads the briefly-used per-member permission format so existing worlds retain explicit grants. */
    private final Map<UUID, Set<ColonyPermission>> legacyMemberPermissionOverrides = new HashMap<>();

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    /**
     * Establish ownership only when the hall has not already been claimed.
     * Normal player placement assigns the owner in TownHallBlock.onPlaced; this fallback
     * remains for halls created by automation or restored without an owner field.
     */
    public void ensureFounded(PlayerEntity player) {
        if (ownerUuid != null) return;
        ownerUuid = player.getUuid();
        colonyUuid = UUID.randomUUID();
        ownerName = player.getName().getString();
        colonyName = "Колония " + ownerName;
        colonyLevel = 1;
        markDirty();
        syncTerritoryClaim();
        player.sendMessage(Text.literal("[Колония] Колония «" + colonyName + "» основана. Ратуша теперь принадлежит вам."), false);
    }

    public boolean canManage(PlayerEntity player) {
        if (ownerUuid == null) {
            player.sendMessage(Text.literal("[Колония] Сначала нажмите ПКМ по ратуше, чтобы основать колонию."), true);
            return false;
        }
        if (ColonyTerritoryRules.canModify(ownerUuid, colonyMembers, player.getUuid())) return true;
        player.sendMessage(Text.literal("[Колония] У вас нет прав на управление этой колонией."), true);
        return false;
    }

    /** Permission queries are pure and must never claim an unowned Town Hall. */
    public boolean isOwner(PlayerEntity player) {
        return ownerUuid != null && ownerUuid.equals(player.getUuid());
    }

    /** True only after a player has explicitly founded this Town Hall. */
    public boolean isFounded() {
        return ownerUuid != null;
    }

    public UUID colonyOwnerUuid() {
        return ownerUuid;
    }

    public UUID colonyUuid() {
        return colonyUuid;
    }

    /** Apply the UUID resolved by the persistent colony directory during legacy migration. */
    public void setColonyUuid(UUID colonyUuid) {
        if (java.util.Objects.equals(this.colonyUuid, colonyUuid)) return;
        this.colonyUuid = colonyUuid;
        markDirty();
    }

    private void syncTerritoryClaim() {
        if (world instanceof ServerWorld serverWorld) {
            ColonyProtection.registerTownHall(serverWorld, pos);
        }
    }

    /** Permission check with no side effect; protection must never auto-found a colony. */
    public boolean canModifyTerritory(PlayerEntity player) {
        return ownerUuid != null && ColonyTerritoryRules.canModify(ownerUuid, colonyMembers, player.getUuid());
    }

    public String colonyName() {
        return colonyName.isBlank() ? "Колония без названия" : colonyName;
    }

    public int colonyLevel() {
        return colonyLevel;
    }

    public String colonyOwnerName() {
        return ownerName;
    }

    public Set<UUID> colonyMembers() {
        return Set.copyOf(colonyMembers);
    }

    /** Snapshot of effective rank grants, mirrored into world PersistentState for unloaded claims. */
    public Map<String, List<String>> colonyMemberPermissions() {
        Map<String, List<String>> snapshot = new HashMap<>();
        for (UUID member : colonyMembers) {
            Set<ColonyPermission> actions;
            if (legacyMemberPermissionOverrides.containsKey(member)) {
                actions = legacyMemberPermissionOverrides.get(member);
            } else {
                actions = colonyRankPermissions.getOrDefault(colonyRank(member), Set.of());
            }
            List<String> ids = actions.stream().map(ColonyPermission::id).sorted().toList();
            if (!ids.isEmpty()) snapshot.put(member.toString(), ids);
        }
        return Map.copyOf(snapshot);
    }

    public ColonyRank colonyRank(UUID memberUuid) {
        if (memberUuid != null && memberUuid.equals(ownerUuid)) return ColonyRank.OWNER;
        return colonyMemberRanks.getOrDefault(memberUuid, ColonyRank.NEUTRAL);
    }

    public boolean hasPermission(UUID playerUuid, ColonyPermission permission) {
        if (playerUuid == null || permission == null) return false;
        if (playerUuid.equals(ownerUuid)) return true;
        if (!colonyMembers.contains(playerUuid)) return false;
        if (legacyMemberPermissionOverrides.containsKey(playerUuid)) {
            return legacyMemberPermissionOverrides.get(playerUuid).contains(permission);
        }
        return colonyRankPermissions.getOrDefault(colonyRank(playerUuid), Set.of()).contains(permission);
    }

    public boolean setMemberRank(ServerPlayerEntity actor, UUID memberUuid, ColonyRank rank) {
        if (!hasPermission(actor == null ? null : actor.getUuid(), ColonyPermission.MANAGE_MEMBERS)
                || memberUuid == null || memberUuid.equals(ownerUuid) || rank == null
                || rank == ColonyRank.OWNER || !colonyMembers.contains(memberUuid)) return false;
        ColonyRank previous = colonyRank(memberUuid);
        if (previous == rank && !legacyMemberPermissionOverrides.containsKey(memberUuid)) return false;
        legacyMemberPermissionOverrides.remove(memberUuid);
        colonyMemberRanks.put(memberUuid, rank);
        markDirty();
        syncTerritoryClaim();
        return true;
    }

    /** Edit a rank-wide permission profile; all members of that rank are affected. */
    public boolean setRankPermission(ServerPlayerEntity actor, ColonyRank rank,
                                     ColonyPermission permission, boolean enabled) {
        if (!hasPermission(actor == null ? null : actor.getUuid(), ColonyPermission.MANAGE_MEMBERS)
                || rank == null || rank == ColonyRank.OWNER || permission == null) return false;
        Set<ColonyPermission> actions = colonyRankPermissions.computeIfAbsent(
                rank, ignored -> java.util.EnumSet.noneOf(ColonyPermission.class));
        boolean changed = enabled ? actions.add(permission) : actions.remove(permission);
        boolean overridesChanged = legacyMemberPermissionOverrides.keySet()
                .removeIf(member -> colonyRank(member) == rank);
        if (changed || overridesChanged) {
            markDirty();
            syncTerritoryClaim();
        }
        return changed || overridesChanged;
    }

    /** Compatibility endpoint: changes the target member's rank profile, not a private grant. */
    public boolean setMemberPermission(ServerPlayerEntity actor, UUID memberUuid,
                                       ColonyPermission permission, boolean enabled) {
        if (memberUuid == null || !colonyMembers.contains(memberUuid)) return false;
        return setRankPermission(actor, colonyRank(memberUuid), permission, enabled);
    }

    public boolean renameColony(ServerPlayerEntity owner, String requestedName) {
        if (!isOwner(owner)) {
            owner.sendMessage(Text.literal("[Колония] Только владелец может переименовать колонию."), true);
            return false;
        }
        String safe = requestedName == null ? "" : requestedName.replaceAll("[^\\p{L}\\p{N} _'-]", "").trim();
        if (safe.isBlank() || safe.length() > 32) {
            owner.sendMessage(Text.literal("[Колония] Имя должно содержать 1–32 допустимых символа."), true);
            return false;
        }
        colonyName = safe;
        markDirty();
        syncTerritoryClaim();
        return true;
    }

    public boolean inviteMember(ServerPlayerEntity owner, ServerPlayerEntity target) {
        if (!hasPermission(owner.getUuid(), ColonyPermission.MANAGE_MEMBERS)) return false;
        if (target.getUuid().equals(ownerUuid)) return true;
        boolean added = colonyMembers.add(target.getUuid());
        if (added) {
            legacyMemberPermissionOverrides.remove(target.getUuid());
            colonyMemberRanks.put(target.getUuid(), ColonyRank.NEUTRAL);
            markDirty();
            syncTerritoryClaim();
            target.sendMessage(Text.literal("[Колония] Вас добавили в колонию «" + colonyName() + "»."), false);
            owner.sendMessage(Text.literal("[Колония] Игрок " + target.getName().getString() + " получил доступ к колонии."), false);
        }
        return added;
    }

    public boolean revokeMember(ServerPlayerEntity owner, UUID memberUuid) {
        if (!hasPermission(owner.getUuid(), ColonyPermission.MANAGE_MEMBERS)) return false;
        boolean removed = colonyMembers.remove(memberUuid);
        if (removed) {
            colonyMemberRanks.remove(memberUuid);
            legacyMemberPermissionOverrides.remove(memberUuid);
            markDirty();
            syncTerritoryClaim();
        }
        return removed;
    }

    public int siteOffsetX() {
        return siteOffsetX;
    }

    public int siteOffsetZ() {
        return siteOffsetZ;
    }

    public boolean canPerformColonyAction(PlayerEntity player, ColonyPermission permission) {
        if (player == null || permission == null
                || !(player.getWorld() instanceof ServerWorld serverWorld) || colonyUuid == null) return false;
        return ColonyClaimState.get(serverWorld).findColony(colonyUuid)
                .filter(claim -> claim.canPerform(player.getUuid(), permission)).isPresent();
    }

    /**
     * Add another BUILD operation to the persistent queue without mutating the order currently
     * executing in this Town Hall. The inventory remains shared; deposits account for every
     * queued BUILD order, while the single executor runs one order at a time.
     */
    public boolean queueBuildOrder(Identifier requestedBlueprint, int quarterTurns,
                                   int requestedOffsetX, int requestedOffsetZ, int priority,
                                   ServerPlayerEntity player) {
        if (!canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You do not have build_projects permission for this colony."), true);
            return false;
        }
        if (colonyUuid == null) {
            player.sendMessage(Text.literal("Found the colony at this Town Hall before queuing work."), true);
            return false;
        }
        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(requestedBlueprint).orElse(null);
        if (definition == null || definition.placements().isEmpty()) {
            player.sendMessage(Text.literal("That blueprint is unavailable or contains no placements."), true);
            return false;
        }

        int safeRotation = Math.floorMod(quarterTurns, 4);
        int safeX = clampSiteOffset(requestedOffsetX);
        int safeZ = clampSiteOffset(requestedOffsetZ);
        BlockPos site = pos.add(safeX, -1, safeZ);
        ColonyClaimState.WorkOrderRecord requested = new ColonyClaimState.WorkOrderRecord(
                UUID.randomUUID().toString(), colonyUuid.toString(),
                player.getServerWorld().getRegistryKey().getValue().toString(), pos.asLong(), site.asLong(),
                requestedBlueprint.toString(), ColonyWorkOrderType.BUILD.id(), 0, 1,
                Math.max(1, Math.min(5, colonyLevel)), safeRotation, safeX, safeZ,
                Math.max(-10, Math.min(10, priority)), 0L, ColonyWorkOrderStatus.QUEUED.id(),
                0, definition.placements().size(), "", player.getUuid().toString());
        var stored = ColonyClaimState.get(player.getServerWorld()).upsertWorkOrder(requested);
        if (stored.isEmpty()) {
            player.sendMessage(Text.literal("The order was rejected: verify the site is inside this colony and no active order uses it."), true);
            return false;
        }
        player.sendMessage(Text.literal("Queued BUILD #" + stored.get().createdSequence() + " for "
                + requestedBlueprint + " at " + site.toShortString()
                + " with priority " + stored.get().priority() + "."), false);
        return true;
    }

    public void startProject(Identifier newBlueprintId, int quarterTurns, int requestedOffsetX, int requestedOffsetZ, PlayerEntity player) {
        if (!canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You do not have build_projects permission for this colony."), true);
            return;
        }
        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(newBlueprintId).orElse(null);
        if (definition == null || definition.placements().isEmpty()) {
            player.sendMessage(Text.literal("[Колония] Этот чертёж недоступен. Проверьте ресурсы сборки."), true);
            return;
        }
        if ((projectActive || (progress > 0 && !projectComplete)) && !blueprintId.equals(newBlueprintId)) {
            player.sendMessage(Text.literal("[Колония] Сначала заверши или приостанови текущий проект."), true);
            return;
        }

        boolean resumeQueuedSelection = activeWorkOrderUuid != null
                && blueprintId.equals(newBlueprintId) && !projectActive && !projectComplete
                && progress == 0 && projectIssue.isBlank();
        boolean resumeSame = resumeQueuedSelection || BuildResumeRules.shouldResume(
                blueprintId, newBlueprintId, projectActive, progress, projectComplete, projectIssue);
        if (resumeSame) {
            if (activeWorkOrderUuid == null) activeWorkOrderUuid = UUID.randomUUID();
            if (projectRequestedByUuid.isBlank()) projectRequestedByUuid = player.getUuid().toString();
            // Resuming a partially built structure must keep its saved orientation and site.
            projectActive = true;
            projectIssue = "";
        } else {
            activeWorkOrderUuid = UUID.randomUUID();
            projectRequestedByUuid = player.getUuid().toString();
            blueprintId = newBlueprintId;
            rotation = Math.floorMod(quarterTurns, 4);
            siteOffsetX = clampSiteOffset(requestedOffsetX);
            siteOffsetZ = clampSiteOffset(requestedOffsetZ);
            progress = 0;
            skippedObstructions = 0;
            projectIssue = "";
            projectComplete = false;
            projectActive = true;
        }
        markDirty();
        if (player.getWorld() instanceof ServerWorld serverWorld) syncWorkOrder(serverWorld, pos);
        int moved = depositMaterials(player);
        player.sendMessage(Text.literal("[Колония] Заказано: " + definition.title() + ". Блоков: "
                + definition.placements().size() + ". Поворот: " + (rotation * 90)
                + "°. В склад ратуши добавлено предметов: " + moved + "."), false);
        notifyCompatibilityWarnings(definition, player);
    }

    public void depositForSelectedProject(Identifier requestedBlueprint, int requestedRotation,
                                          int requestedOffsetX, int requestedOffsetZ, ServerPlayerEntity player) {
        if (!canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You do not have build_projects permission for this colony."), true);
            return;
        }
        if (blueprintId.equals(requestedBlueprint)) {
            BlueprintDefinition selectedDefinition = BlueprintRegistry.getBuildDefinition(requestedBlueprint).orElse(null);
            if (selectedDefinition == null) {
                player.sendMessage(Text.literal("[Колония] Сохранённый чертёж недоступен. Восстановите его datapack или выберите другую схему."), true);
                return;
            }
            if (!projectActive && progress == 0 && !projectComplete) {
                rotation = Math.floorMod(requestedRotation, 4);
                siteOffsetX = clampSiteOffset(requestedOffsetX);
                siteOffsetZ = clampSiteOffset(requestedOffsetZ);
                if (activeWorkOrderUuid == null) activeWorkOrderUuid = UUID.randomUUID();
                if (projectRequestedByUuid.isBlank()) projectRequestedByUuid = player.getUuid().toString();
            }
            syncWorkOrder(player.getServerWorld(), pos);
            int moved = depositMaterials(player);
            player.sendMessage(Text.literal("[Колония] В ратушу перенесено предметов: " + moved
                    + ". Запас стройки обновлён."), true);
            notifyCompatibilityWarnings(selectedDefinition, player);
            markDirty();
            return;
        }
        if (projectActive || (progress > 0 && !projectComplete)) {
            player.sendMessage(Text.literal("[Колония] Нельзя сменить чертёж, пока текущий проект не завершён."), true);
            return;
        }
        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(requestedBlueprint).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.literal("[Колония] Чертёж не найден."), true);
            return;
        }
        activeWorkOrderUuid = UUID.randomUUID();
        projectRequestedByUuid = player.getUuid().toString();
        blueprintId = requestedBlueprint;
        rotation = Math.floorMod(requestedRotation, 4);
        siteOffsetX = clampSiteOffset(requestedOffsetX);
        siteOffsetZ = clampSiteOffset(requestedOffsetZ);
        progress = 0;
        projectComplete = false;
        markDirty();
        syncWorkOrder(player.getServerWorld(), pos);
        int moved = depositMaterials(player);
        player.sendMessage(Text.literal("[Колония] Выбрана схема «" + definition.title()
                + "». Перенесено предметов: " + moved + ". Чтобы начать стройку, нажми соответствующую кнопку."), true);
        notifyCompatibilityWarnings(definition, player);
    }

    private static void notifyCompatibilityWarnings(BlueprintDefinition definition, PlayerEntity player) {
        String summary = CompatibilityWarningSummary.summarize(definition.compatibilityWarnings(), 3);
        if (!summary.isBlank()) {
            player.sendMessage(Text.literal("[Колония] Предупреждения схемы: " + summary), true);
        }
    }

    private int depositMaterials(PlayerEntity player) {
        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(blueprintId).orElse(null);
        if (definition == null) return 0;
        Map<Identifier, Integer> required = remainingRequirements(definition);
        int movedTotal = 0;

        for (Map.Entry<Identifier, Integer> entry : required.entrySet()) {
            Identifier itemId = entry.getKey();
            int missing = entry.getValue() - resourceStock.getOrDefault(itemId, 0);
            if (missing <= 0 || !Registries.ITEM.containsId(itemId)) continue;
            Item item = Registries.ITEM.get(itemId);

            for (int slot = 0; slot < 36 && missing > 0; slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (missing <= 0) break;
                if (stack.isEmpty() || stack.getItem() != item) continue;
                int amount = Math.min(missing, stack.getCount());
                stack.decrement(amount);
                resourceStock.merge(itemId, amount, Integer::sum);
                missing -= amount;
                movedTotal += amount;
            }
        }
        if (movedTotal > 0) markDirty();
        return movedTotal;
    }

    private Map<Identifier, Integer> remainingRequirements(BlueprintDefinition fallbackDefinition) {
        Map<Identifier, Integer> required = new HashMap<>();
        boolean countedOrder = false;
        if (world instanceof ServerWorld serverWorld && colonyUuid != null) {
            for (ColonyClaimState.WorkOrderRecord order
                    : ColonyClaimState.get(serverWorld).workOrdersForColony(colonyUuid)) {
                if (order.isTerminal() || !ColonyWorkOrderType.BUILD.id().equals(order.type())) continue;
                Identifier orderBlueprintId = Identifier.tryParse(order.blueprintId());
                BlueprintDefinition definition = orderBlueprintId == null ? null
                        : BlueprintRegistry.getBuildDefinition(orderBlueprintId).orElse(null);
                if (definition == null) continue;
                List<CottageBlueprint.Placement> plan = definition.placements();
                int completed = order.progress();
                if (activeWorkOrderUuid != null && activeWorkOrderUuid.toString().equals(order.orderId())
                        && blueprintId.equals(definition.id())) {
                    completed = Math.max(completed, progress);
                }
                int start = Math.min(completed, plan.size());
                for (int i = start; i < plan.size(); i++) {
                    Item item = plan.get(i).state().getBlock().asItem();
                    if (item == Items.AIR) continue;
                    required.merge(Registries.ITEM.getId(item), 1, Integer::sum);
                }
                countedOrder = true;
            }
        }
        if (countedOrder) return required;

        List<CottageBlueprint.Placement> fallbackPlan = fallbackDefinition.placements();
        int start = blueprintId.equals(fallbackDefinition.id())
                ? Math.min(progress, fallbackPlan.size()) : 0;
        for (int i = start; i < fallbackPlan.size(); i++) {
            Item item = fallbackPlan.get(i).state().getBlock().asItem();
            if (item == Items.AIR) continue;
            required.merge(Registries.ITEM.getId(item), 1, Integer::sum);
        }
        return required;
    }

    public String statusForScreen() {
        String state = projectComplete ? "Завершено" : projectActive ? "Строится" : progress > 0 ? "Пауза" : "Не начато";
        String stock = resourceStock.entrySet().stream()
                .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed())
                .limit(2)
                .map(entry -> Registries.ITEM.get(entry.getKey()).getName().getString() + " ×" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        if (stock.isBlank()) stock = "пусто";
        if (stock.length() > 70) stock = stock.substring(0, 67) + "...";

        Optional<BlueprintDefinition> definition = BlueprintRegistry.getBuildDefinition(blueprintId);
        String heading = "Колония: " + colonyName() + " (ур. " + colonyLevel + ")\nВладелец: "
                + (ownerName.isBlank() ? "не назначен" : ownerName);
        if (definition.isEmpty()) {
            String reason = projectIssue.isBlank()
                    ? "Восстановите datapack или файл схемы для продолжения."
                    : projectIssue;
            return heading + "\nСхема недоступна\nID: " + blueprintId
                    + "\nПрогресс: " + progress + "\nСклад: " + stock + "\nПричина: " + reason;
        }

        int total = definition.get().placements().size();
        return heading + "\n" + state + "\nПрогресс: " + Math.min(progress, total) + "/" + total
                + "\nСклад: " + stock
                + (projectIssue.isBlank() ? "" : "\nПричина: " + projectIssue);
    }

    public void togglePause(PlayerEntity player) {
        if (!canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You do not have build_projects permission for this colony."), true);
            return;
        }
        if (projectComplete) {
            player.sendMessage(Text.literal("[Колония] Проект завершён. Выбери чертёж и начни новый заказ."), true);
            return;
        }
        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(blueprintId).orElse(null);
        if (definition == null) {
            projectActive = false;
            projectIssue = "Сохранённый чертёж недоступен: " + blueprintId;
            markDirty();
            if (player.getWorld() instanceof ServerWorld serverWorld) syncWorkOrder(serverWorld, pos);
            player.sendMessage(Text.literal("[Колония] Нельзя возобновить стройку: сохранённая схема недоступна. Восстановите datapack или файл схемы."), true);
            return;
        }
        if (!projectActive && progress == 0 && projectIssue.isBlank()) {
            player.sendMessage(Text.literal("[Колония] Сначала выбери чертёж и нажми «Начать строительство»."), true);
            return;
        }
        projectActive = !projectActive;
        projectIssue = projectActive ? "" : "Приостановлено пользователем";
        markDirty();
        if (player.getWorld() instanceof ServerWorld serverWorld) syncWorkOrder(serverWorld, pos);
        player.sendMessage(Text.literal("[Колония] Строительство " + (projectActive ? "возобновлено" : "приостановлено")
                + ": " + progress + "/" + definition.placements().size()), true);
    }

    public static void tick(World world, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) return;
        ColonyProtection.registerTownHall(serverWorld, pos);
        if (serverWorld.getTime() % 10L != 0L) return;
        hall.syncWorkOrder(serverWorld, pos);
        boolean idleWithoutSelection = hall.activeWorkOrderUuid == null
                && !hall.projectComplete && !hall.projectActive && hall.progress == 0
                && hall.projectIssue.isBlank();
        if (!hall.projectActive && (hall.projectComplete || idleWithoutSelection)) {
            hall.activateNextQueuedWorkOrder(serverWorld, pos);
        }
        if (!hall.projectActive) return;

        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(hall.blueprintId).orElse(null);
        if (definition == null) {
            hall.pauseForPlacementIssue(serverWorld, pos, pos,
                    "Сохранённый чертёж " + hall.blueprintId
                            + " недоступен. Восстановите datapack или повторно установите схему");
            return;
        }
        List<CottageBlueprint.Placement> plan = definition.placements();
        if (plan.isEmpty()) {
            hall.pauseForPlacementIssue(serverWorld, pos, pos, "Сохранённый чертёж не содержит блоков");
            return;
        }
        if (hall.progress > plan.size()) {
            hall.pauseForPlacementIssue(serverWorld, pos, pos,
                    "Сохранённый прогресс превышает размер текущей версии схемы; данные не будут перезаписаны автоматически");
            return;
        }
        if (hall.progress >= plan.size()) {
            hall.finish(serverWorld, hall.findAssignedBuilder(serverWorld, pos));
            return;
        }

        Box searchArea = new Box(pos).expand(32.0);
        String assignmentTag = assignmentTag(serverWorld, pos);
        VillagerEntity builder = null;

        // Resolve the saved worker by UUID first, not only by a nearby entity query.
        if (hall.assignedBuilderUuid != null) {
            var assigned = serverWorld.getEntity(hall.assignedBuilderUuid);
            if (assigned instanceof VillagerEntity villager && !villager.isRemoved()) {
                if (WorkerRoleRules.hasForeignAssignment(villager.getCommandTags(), assignmentTag)) {
                    hall.pauseForPlacementIssue(serverWorld, pos, villager.getBlockPos(),
                            "Назначенный строитель также назначен на другую работу");
                    return;
                }
                builder = villager;
                if (!builder.getCommandTags().contains(BUILDER_TAG)) builder.addCommandTag(BUILDER_TAG);
                hall.recordBuilderPosition(villager);
                if (!builder.getCommandTags().contains(assignmentTag)) builder.addCommandTag(assignmentTag);
            } else if (hall.lastBuilderPosition == null || serverWorld.isChunkLoaded(hall.lastBuilderPosition)) {
                // Its last known chunk is loaded but the UUID is gone: release the stale assignment.
                ColonyClaimState.get(serverWorld).releaseCitizenAssignment(
                        hall.assignedBuilderUuid, serverWorld.getRegistryKey(), pos);
                hall.assignedBuilderUuid = null;
                hall.lastBuilderPosition = null;
                hall.resetNavigationDiagnostics();
                hall.markDirty();
            } else {
                hall.notifyAssignedBuilderUnavailable(serverWorld, pos);
                return;
            }
        }

        if (builder == null) {
            // Recover assignments saved by older versions before UUID tracking existed.
            builder = serverWorld.getEntitiesByClass(VillagerEntity.class, searchArea,
                            villager -> villager.getCommandTags().contains(assignmentTag)
                                    && !WorkerRoleRules.hasForeignAssignment(villager.getCommandTags(), assignmentTag))
                    .stream().findFirst().orElse(null);
            if (builder == null) {
                builder = serverWorld.getEntitiesByClass(VillagerEntity.class, searchArea,
                                villager -> WorkerRoleRules.canContinueBuilderAssignment(
                                        villager.getCommandTags(), assignmentTag))
                        .stream()
                        .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                        .orElse(null);
            }
            if (builder == null) {
                hall.notifyNoBuilder(serverWorld, pos);
                return;
            }
            builder.addCommandTag(assignmentTag);
            hall.assignedBuilderUuid = builder.getUuid();
            hall.recordBuilderPosition(builder);
            hall.resetNavigationDiagnostics();
            hall.markDirty();
        }

        // Reconcile/migrate the saved builder into the durable directory before performing work.
        if (hall.colonyUuid == null || !ColonyClaimState.get(serverWorld).registerCitizenAssignment(
                builder.getUuid(), hall.colonyUuid, serverWorld.getRegistryKey(), pos,
                "colony_foundations:builder", "colony_foundations:builder", builder.getBlockPos())) {
            hall.pauseForPlacementIssue(serverWorld, pos, builder.getBlockPos(),
                    "Строитель уже назначен на другую работу или не связан с единственной колонией");
            return;
        }

        BlockPos site = pos.add(hall.siteOffsetX, -1, hall.siteOffsetZ);
        int rotatedWidth = (hall.rotation & 1) == 0 ? definition.sizeX() : definition.sizeZ();
        int rotatedDepth = (hall.rotation & 1) == 0 ? definition.sizeZ() : definition.sizeX();
        BlockPos workSpot = site.add(rotatedWidth + 1, 1, rotatedDepth / 2);
        double distance = builder.squaredDistanceTo(
                workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5);
        if (distance > 9.0) {
            boolean madeProgress = hall.lastBuilderDistance < 0.0 || distance < hall.lastBuilderDistance - 0.25;
            hall.noProgressPathChecks = madeProgress ? 0 : hall.noProgressPathChecks + 1;
            hall.lastBuilderDistance = distance;
            boolean repeatedlyStuck = hall.noProgressPathChecks >= 6;
            if (builder.getNavigation().isIdle() || repeatedlyStuck) {
                if (repeatedlyStuck) builder.getNavigation().stop();
                boolean started = builder.getNavigation().startMovingTo(
                        workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5, 1.0);
                if (!started || repeatedlyStuck) hall.navigationFailures++;
                else hall.navigationFailures = 0;
                hall.noProgressPathChecks = 0;
                if (hall.navigationFailures >= 4) {
                    builder.getNavigation().stop();
                    hall.pauseForPlacementIssue(serverWorld, pos, workSpot,
                            "Строитель не может найти путь к рабочей площадке после нескольких попыток");
                    return;
                }
            }
            return;
        }
        hall.resetNavigationDiagnostics();
        hall.recordBuilderPosition(builder);

        CottageBlueprint.Placement placement = plan.get(hall.progress);
        BlockPos target = site.add(rotate(placement.offset(), definition, hall.rotation)
                .subtract(rotate(definition.primaryOffset(), definition, hall.rotation)));

        // Entity-driven construction bypasses player block events, so enforce claim ownership here too.
        if (!ColonyProtection.isConstructionAllowed(serverWorld, pos, target)) {
            hall.pauseForPlacementIssue(serverWorld, pos, target,
                    "Целевая позиция находится вне территории этой колонии или в чужом поселении");
            return;
        }

        // Never force-load or silently write outside the active world bounds.
        if (target.getY() < serverWorld.getBottomY() || target.getY() >= serverWorld.getBottomY() + serverWorld.getDimension().height()) {
            hall.pauseForPlacementIssue(serverWorld, pos, target, "Целевая позиция вне высоты измерения");
            return;
        }
        if (!serverWorld.getWorldBorder().contains(target)) {
            hall.pauseForPlacementIssue(serverWorld, pos, target, "Целевая позиция за границей мира");
            return;
        }
        if (!serverWorld.isChunkLoaded(target)) {
            hall.pauseForPlacementIssue(serverWorld, pos, target,
                    "Чанк для строительства не загружен; подойдите к площадке и возобновите заказ");
            return;
        }

        BlockState oldState = serverWorld.getBlockState(target);
        BlockState desiredState = rotateState(placement.state(), hall.rotation);

        // If a saved progress counter lagged behind a successful world write, recover safely.
        if (oldState.equals(desiredState)) {
            hall.progress++;
            hall.projectIssue = "";
            hall.markDirty();
            hall.syncWorkOrder(serverWorld, pos);
            if (hall.progress >= plan.size()) hall.finish(serverWorld, builder);
            return;
        }

        if (!(oldState.isAir() || oldState.isReplaceable()) || !oldState.getFluidState().isEmpty()) {
            hall.pauseForPlacementIssue(serverWorld, pos, target,
                    "Позиция занята блоком " + Registries.BLOCK.getId(oldState.getBlock()));
            return;
        }

        Item item = desiredState.getBlock().asItem();
        Identifier itemId = item == Items.AIR ? null : Registries.ITEM.getId(item);
        if (itemId != null && hall.resourceStock.getOrDefault(itemId, 0) <= 0) {
            hall.notifyMissingResource(serverWorld, pos, item);
            return;
        }

        // Consume material only after Minecraft confirms placement.
        if (!serverWorld.setBlockState(target, desiredState, Block.NOTIFY_ALL)) {
            hall.pauseForPlacementIssue(serverWorld, pos, target,
                    "Мир отклонил размещение блока " + Registries.BLOCK.getId(desiredState.getBlock()));
            return;
        }
        if (itemId != null) {
            int stock = hall.resourceStock.getOrDefault(itemId, 0);
            if (stock <= 1) hall.resourceStock.remove(itemId);
            else hall.resourceStock.put(itemId, stock - 1);
        }

        hall.progress++;
        hall.projectIssue = "";
        hall.markDirty();
        hall.syncWorkOrder(serverWorld, pos);
        if (hall.progress >= plan.size()) hall.finish(serverWorld, builder);
    }

    private void recordBuilderPosition(VillagerEntity builder) {
        BlockPos current = builder.getBlockPos().toImmutable();
        boolean changedChunk = lastBuilderPosition == null
                || (lastBuilderPosition.getX() >> 4) != (current.getX() >> 4)
                || (lastBuilderPosition.getZ() >> 4) != (current.getZ() >> 4);
        lastBuilderPosition = current;
        if (changedChunk) markDirty();
    }

    private void resetNavigationDiagnostics() {
        lastBuilderDistance = -1.0;
        noProgressPathChecks = 0;
        navigationFailures = 0;
    }

    private void notifyNoBuilder(ServerWorld world, BlockPos hallPos) {
        if (world.getTime() - lastBuilderUnavailableNotification < 100L) return;
        lastBuilderUnavailableNotification = world.getTime();
        Text message = Text.literal("[Колония] Стройка ожидает исполнителя: наймите жителя контрактом строителя и подведите его к ратуше.");
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5, hallPos.getZ() + 0.5) <= 64.0) {
                player.sendMessage(message, false);
            }
        }
    }

    private void notifyAssignedBuilderUnavailable(ServerWorld world, BlockPos hallPos) {
        if (world.getTime() - lastBuilderUnavailableNotification < 100L) return;
        lastBuilderUnavailableNotification = world.getTime();
        String location = lastBuilderPosition == null ? "" : " Последнее известное место: "
                + lastBuilderPosition.getX() + ", " + lastBuilderPosition.getY() + ", " + lastBuilderPosition.getZ() + ".";
        Text message = Text.literal("[Колония] Назначенный строитель сейчас не загружен; новый рабочий не назначен, чтобы избежать дублирования задания."
                + location + " Загрузите этот участок мира, чтобы продолжить работу.");
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5, hallPos.getZ() + 0.5) <= 64.0) {
                player.sendMessage(message, false);
            }
        }
    }

    private void pauseForPlacementIssue(ServerWorld world, BlockPos hallPos, BlockPos target, String reason) {
        projectActive = false;
        projectIssue = reason;
        markDirty();
        syncWorkOrder(world, hallPos);
        Text message = Text.literal("[Колония] Строительство приостановлено: " + reason
                + ". Координаты: " + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + ". Устраните причину и возобновите заказ.");
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5, hallPos.getZ() + 0.5) <= 64.0) {
                player.sendMessage(message, false);
            }
        }
        ColonyFoundations.LOGGER.warn("Construction paused at hall {}: {} (target {})", hallPos, reason, target);
    }

    private void notifyMissingResource(ServerWorld world, BlockPos hallPos, Item item) {
        if (world.getTime() - lastMissingResourceNotification < 100L) return;
        lastMissingResourceNotification = world.getTime();
        Text message = Text.literal("[Колония] На стройке закончился ресурс: ").append(item.getName())
                .append(Text.literal(". Пополни ратушу через интерфейс."));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5, hallPos.getZ() + 0.5) <= 64.0) {
                player.sendMessage(message, false);
            }
        }
    }

    private static int clampSiteOffset(int value) {
        return Math.max(-64, Math.min(64, value));
    }

    private static BlockPos rotate(BlockPos source, BlueprintDefinition definition, int turns) {
        int x = source.getX(), y = source.getY(), z = source.getZ();
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new BlockPos(definition.sizeZ() - 1 - z, y, x);
            case 2 -> new BlockPos(definition.sizeX() - 1 - x, y, definition.sizeZ() - 1 - z);
            case 3 -> new BlockPos(z, y, definition.sizeX() - 1 - x);
            default -> source;
        };
    }

    private static BlockState rotateState(BlockState state, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> state.rotate(BlockRotation.CLOCKWISE_90);
            case 2 -> state.rotate(BlockRotation.CLOCKWISE_180);
            case 3 -> state.rotate(BlockRotation.COUNTERCLOCKWISE_90);
            default -> state;
        };
    }

    private VillagerEntity findAssignedBuilder(ServerWorld world, BlockPos pos) {
        if (assignedBuilderUuid != null) {
            var entity = world.getEntity(assignedBuilderUuid);
            if (entity instanceof VillagerEntity villager && !villager.isRemoved()) return villager;
        }
        String tag = assignmentTag(world, pos);
        return world.getEntitiesByClass(VillagerEntity.class, new Box(pos).expand(32.0),
                        villager -> villager.getCommandTags().contains(tag))
                .stream().findFirst().orElse(null);
    }

    private void finish(ServerWorld world, VillagerEntity builder) {
        projectActive = false;
        projectComplete = true;
        UUID completedBuilderUuid = builder != null ? builder.getUuid() : assignedBuilderUuid;
        if (completedBuilderUuid != null) {
            ColonyClaimState directory = ColonyClaimState.get(world);
            directory.releaseCitizenAssignment(completedBuilderUuid, world.getRegistryKey(), pos);
            if (builder != null) {
                builder.removeCommandTag(assignmentTag(world, pos));
                builder.removeCommandTag(BUILDER_TAG);
                // The loaded villager's tags are reconciled now, so no tombstone needs to remain.
                directory.forgetReleasedCitizenAssignment(completedBuilderUuid);
            }
        }
        assignedBuilderUuid = null;
        lastBuilderPosition = null;
        resetNavigationDiagnostics();
        markDirty();
        syncWorkOrder(world, pos);
        ColonyFoundations.LOGGER.info("Blueprint {} completed at {} in dimension {} ({} obstructed cells skipped).",
                blueprintId, pos, world.getRegistryKey().getValue(), skippedObstructions);
    }

    /** Activate the highest-priority queued BUILD after the current order has finished. */
    private boolean activateNextQueuedWorkOrder(ServerWorld serverWorld, BlockPos hallPos) {
        if (colonyUuid == null) return false;
        ColonyClaimState directory = ColonyClaimState.get(serverWorld);
        for (ColonyClaimState.WorkOrderRecord order : directory.workOrdersForColony(colonyUuid)) {
            if (!ColonyWorkOrderStatus.QUEUED.id().equals(order.status())
                    || !ColonyWorkOrderType.BUILD.id().equals(order.type())
                    || order.townHallPosition() != hallPos.asLong()) continue;

            Identifier nextBlueprintId = Identifier.tryParse(order.blueprintId());
            BlueprintDefinition definition = nextBlueprintId == null ? null
                    : BlueprintRegistry.getBuildDefinition(nextBlueprintId).orElse(null);
            if (definition == null || definition.placements().isEmpty()
                    || order.progress() > definition.placements().size()
                    || hallPos.add(order.offsetX(), -1, order.offsetZ()).asLong() != order.sitePosition()) {
                directory.upsertWorkOrder(order.withStatus(ColonyWorkOrderStatus.BLOCKED.id(),
                        "Blueprint missing/empty or persisted site no longer matches its offsets"));
                continue;
            }

            activeWorkOrderUuid = UUID.fromString(order.orderId());
            projectRequestedByUuid = order.requestedBy();
            blueprintId = nextBlueprintId;
            rotation = order.rotation();
            siteOffsetX = clampSiteOffset(order.offsetX());
            siteOffsetZ = clampSiteOffset(order.offsetZ());
            progress = order.progress();
            projectActive = true;
            projectComplete = false;
            projectIssue = "";
            skippedObstructions = 0;
            assignedBuilderUuid = null;
            lastBuilderPosition = null;
            resetNavigationDiagnostics();
            markDirty();
            syncWorkOrder(serverWorld, hallPos);
            Text message = Text.literal("[Колония] Начат следующий заказ BUILD #"
                    + order.createdSequence() + ": " + order.blueprintId() + ".");
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5,
                        hallPos.getZ() + 0.5) <= 64.0) player.sendMessage(message, false);
            }
            return true;
        }
        return false;
    }

    /**
     * Mirror the currently executable Town Hall build order into persistent colony history.
     * The Town Hall remains the executor; this durable record is the first integration step
     * toward the upstream WorkManager and multi-order scheduler.
     */
    private void syncWorkOrder(ServerWorld serverWorld, BlockPos hallPos) {
        boolean hasOrder = activeWorkOrderUuid != null || projectActive || projectComplete
                || progress > 0 || !projectIssue.isBlank();
        if (!hasOrder || colonyUuid == null) return;
        if (activeWorkOrderUuid == null) {
            activeWorkOrderUuid = UUID.randomUUID();
            markDirty();
        }

        BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(blueprintId).orElse(null);
        int total = Math.max(progress, definition == null ? 0 : definition.placements().size());
        String status = projectComplete ? ColonyWorkOrderStatus.COMPLETED.id()
                : projectActive ? ColonyWorkOrderStatus.ACTIVE.id()
                : "Приостановлено пользователем".equals(projectIssue) ? ColonyWorkOrderStatus.PAUSED.id()
                : !projectIssue.isBlank() ? ColonyWorkOrderStatus.BLOCKED.id()
                : progress > 0 ? ColonyWorkOrderStatus.PAUSED.id()
                : ColonyWorkOrderStatus.QUEUED.id();
        BlockPos site = hallPos.add(siteOffsetX, -1, siteOffsetZ);
        ColonyClaimState.WorkOrderRecord draft = new ColonyClaimState.WorkOrderRecord(
                activeWorkOrderUuid.toString(), colonyUuid.toString(),
                serverWorld.getRegistryKey().getValue().toString(), hallPos.asLong(), site.asLong(),
                blueprintId.toString(), ColonyWorkOrderType.BUILD.id(), 0, 1,
                Math.max(1, Math.min(5, colonyLevel)), rotation, siteOffsetX, siteOffsetZ,
                0, 0L, status, progress, total, projectIssue, projectRequestedByUuid);
        var stored = ColonyClaimState.get(serverWorld).upsertWorkOrder(draft);
        if (stored.isEmpty() && serverWorld.getTime() - lastWorkOrderSyncWarning >= 100L) {
            lastWorkOrderSyncWarning = serverWorld.getTime();
            ColonyFoundations.LOGGER.warn("Could not persist BUILD work order {} for colony {} at {}",
                    activeWorkOrderUuid, colonyUuid, hallPos);
        } else if (stored.isPresent()) {
            activeWorkOrderUuid = UUID.fromString(stored.get().orderId());
        }
    }

    private static String assignmentTag(ServerWorld world, BlockPos pos) {
        String dimension = world.getRegistryKey().getValue().toString().replace(':', '_').replace('/', '_');
        return ASSIGNED_TAG_PREFIX + Long.toUnsignedString(pos.asLong(), 36) + "_" + dimension;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putBoolean("ProjectActive", projectActive);
        nbt.putBoolean("ProjectComplete", projectComplete);
        if (activeWorkOrderUuid != null) nbt.putString("ActiveWorkOrderUuid", activeWorkOrderUuid.toString());
        nbt.putString("ProjectRequestedByUuid", projectRequestedByUuid);
        nbt.putInt("Progress", progress);
        nbt.putInt("SkippedObstructions", skippedObstructions);
        nbt.putString("BlueprintId", blueprintId.toString());
        nbt.putInt("Rotation", rotation);
        nbt.putInt("SiteOffsetX", siteOffsetX);
        nbt.putInt("SiteOffsetZ", siteOffsetZ);
        if (ownerUuid != null) nbt.putString("ColonyOwnerUuid", ownerUuid.toString());
        if (colonyUuid != null) nbt.putString("ColonyUuid", colonyUuid.toString());
        nbt.putString("ColonyOwnerName", ownerName);
        nbt.putString("ColonyName", colonyName);
        nbt.putInt("ColonyLevel", colonyLevel);
        nbt.putString("ProjectIssue", projectIssue);
        if (assignedBuilderUuid != null) nbt.putString("AssignedBuilderUuid", assignedBuilderUuid.toString());
        if (lastBuilderPosition != null) nbt.putLong("AssignedBuilderLastPos", lastBuilderPosition.asLong());
        NbtList membersTag = new NbtList();
        colonyMembers.stream().map(UUID::toString).sorted().forEach(member -> membersTag.add(NbtString.of(member)));
        nbt.put("ColonyMembers", membersTag);
        nbt.putInt("ColonyPermissionVersion", 2);
        NbtList memberRankEntries = new NbtList();
        colonyMembers.stream().sorted().forEach(member -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("PlayerUuid", member.toString());
            entry.putString("RankId", colonyRank(member).id());
            memberRankEntries.add(entry);
        });
        nbt.put("ColonyMemberRanks", memberRankEntries);

        NbtList rankProfileEntries = new NbtList();
        colonyRankPermissions.entrySet().stream()
                .filter(entry -> entry.getKey() != ColonyRank.OWNER)
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    NbtCompound profile = new NbtCompound();
                    profile.putString("RankId", entry.getKey().id());
                    NbtList actions = new NbtList();
                    entry.getValue().stream().map(ColonyPermission::id).sorted()
                            .forEach(action -> actions.add(NbtString.of(action)));
                    profile.put("Actions", actions);
                    rankProfileEntries.add(profile);
                });
        nbt.put("ColonyRankPermissions", rankProfileEntries);

        // Keep effective grants so older builds can still read per-member permissions.
        NbtList memberPermissionEntries = new NbtList();
        colonyMemberPermissions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NbtCompound permissionEntry = new NbtCompound();
            permissionEntry.putString("PlayerUuid", entry.getKey());
            NbtList actions = new NbtList();
            entry.getValue().forEach(action -> actions.add(NbtString.of(action)));
            permissionEntry.put("Actions", actions);
            memberPermissionEntries.add(permissionEntry);
        });
        nbt.put("ColonyMemberPermissions", memberPermissionEntries);
        NbtCompound stockTag = new NbtCompound();
        resourceStock.forEach((id, amount) -> stockTag.putInt(id.toString(), amount));
        nbt.put("ResourceStock", stockTag);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        projectActive = nbt.getBoolean("ProjectActive").orElse(false);
        projectComplete = nbt.getBoolean("ProjectComplete").orElse(false);
        activeWorkOrderUuid = ColonyIdentityRules.parseUuid(
                nbt.getString("ActiveWorkOrderUuid").orElse("")).orElse(null);
        projectRequestedByUuid = ColonyIdentityRules.parseUuid(
                nbt.getString("ProjectRequestedByUuid").orElse("")).map(UUID::toString).orElse("");
        progress = Math.max(0, nbt.getInt("Progress").orElse(0));
        skippedObstructions = Math.max(0, nbt.getInt("SkippedObstructions").orElse(0));
        Identifier parsed = Identifier.tryParse(nbt.getString("BlueprintId").orElse("colony_foundations:cottage"));
        blueprintId = parsed == null ? Identifier.of("colony_foundations", "cottage") : parsed;
        rotation = Math.floorMod(nbt.getInt("Rotation").orElse(0), 4);
        siteOffsetX = clampSiteOffset(nbt.getInt("SiteOffsetX").orElse(5));
        siteOffsetZ = clampSiteOffset(nbt.getInt("SiteOffsetZ").orElse(-3));
        String ownerRaw = nbt.getString("ColonyOwnerUuid").orElse("");
        try {
            ownerUuid = ownerRaw.isBlank() ? null : UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ignored) {
            ownerUuid = null;
        }
        ownerName = nbt.getString("ColonyOwnerName").orElse("");
        colonyName = nbt.getString("ColonyName").orElse("");
        String colonyIdRaw = nbt.getString("ColonyUuid").orElse("");
        colonyUuid = ColonyIdentityRules.parseUuid(colonyIdRaw).orElse(null);
        colonyLevel = Math.max(1, nbt.getInt("ColonyLevel").orElse(1));
        projectIssue = nbt.getString("ProjectIssue").orElse("");
        String builderRaw = nbt.getString("AssignedBuilderUuid").orElse("");
        try {
            assignedBuilderUuid = builderRaw.isBlank() ? null : UUID.fromString(builderRaw);
        } catch (IllegalArgumentException ignored) {
            assignedBuilderUuid = null;
        }
        lastBuilderPosition = nbt.getLong("AssignedBuilderLastPos").map(BlockPos::fromLong).orElse(null);
        resetNavigationDiagnostics();
        colonyMembers.clear();
        nbt.getList("ColonyMembers").ifPresent(members -> {
            for (int i = 0; i < members.size(); i++) {
                members.getString(i).ifPresent(raw -> {
                    try { colonyMembers.add(UUID.fromString(raw)); }
                    catch (IllegalArgumentException ignored) { }
                });
            }
        });
        colonyMemberRanks.clear();
        legacyMemberPermissionOverrides.clear();
        colonyRankPermissions.clear();
        colonyRankPermissions.putAll(ColonyRank.defaultProfiles());
        boolean hasRankVersion = nbt.contains("ColonyPermissionVersion");
        boolean hasLegacyPermissionList = nbt.contains("ColonyMemberPermissions");

        if (hasRankVersion) {
            nbt.getList("ColonyRankPermissions").ifPresent(entries -> {
                for (int i = 0; i < entries.size(); i++) {
                    entries.getCompound(i).ifPresent(entry -> {
                        ColonyRank rank = ColonyRank.parse(entry.getString("RankId").orElse(""))
                                .orElse(null);
                        if (rank == null || rank == ColonyRank.OWNER) return;
                        Set<ColonyPermission> actions = java.util.EnumSet.noneOf(ColonyPermission.class);
                        entry.getList("Actions").ifPresent(actionList -> {
                            for (int j = 0; j < actionList.size(); j++) {
                                actionList.getString(j).ifPresent(raw ->
                                        ColonyPermission.parse(raw).ifPresent(actions::add));
                            }
                        });
                        colonyRankPermissions.put(rank, actions);
                    });
                }
            });
            nbt.getList("ColonyMemberRanks").ifPresent(entries -> {
                for (int i = 0; i < entries.size(); i++) {
                    entries.getCompound(i).ifPresent(entry -> {
                        UUID memberUuid = ColonyIdentityRules.parseUuid(
                                entry.getString("PlayerUuid").orElse("")).orElse(null);
                        ColonyRank rank = ColonyRank.parse(entry.getString("RankId").orElse(""))
                                .orElse(null);
                        if (memberUuid != null && colonyMembers.contains(memberUuid)
                                && rank != null && rank != ColonyRank.OWNER) {
                            colonyMemberRanks.put(memberUuid, rank);
                        }
                    });
                }
            });
            colonyMembers.forEach(member -> colonyMemberRanks.putIfAbsent(member, ColonyRank.NEUTRAL));
        } else if (hasLegacyPermissionList) {
            // Preserve grants from the interim per-member format exactly.
            colonyMembers.forEach(member -> colonyMemberRanks.put(member, ColonyRank.NEUTRAL));
            nbt.getList("ColonyMemberPermissions").ifPresent(entries -> {
                for (int i = 0; i < entries.size(); i++) {
                    entries.getCompound(i).ifPresent(entry -> {
                        UUID memberUuid = ColonyIdentityRules.parseUuid(
                                entry.getString("PlayerUuid").orElse("")).orElse(null);
                        if (memberUuid == null || !colonyMembers.contains(memberUuid)) return;
                        Set<ColonyPermission> actions = java.util.EnumSet.noneOf(ColonyPermission.class);
                        entry.getList("Actions").ifPresent(actionList -> {
                            for (int j = 0; j < actionList.size(); j++) {
                                actionList.getString(j).ifPresent(raw ->
                                        ColonyPermission.parse(raw).ifPresent(actions::add));
                            }
                        });
                        legacyMemberPermissionOverrides.put(memberUuid, actions);
                    });
                }
            });
        } else {
            // Worlds older than the first permission implementation preserve the old member access.
            colonyMembers.forEach(member -> colonyMemberRanks.put(member, ColonyRank.OFFICER));
        }
        resourceStock.clear();
        nbt.getCompound("ResourceStock").ifPresent(stockTag -> stockTag.getKeys().forEach(key -> {
            Identifier itemId = Identifier.tryParse(key);
            int amount = Math.max(0, stockTag.getInt(key).orElse(0));
            if (itemId != null && amount > 0 && Registries.ITEM.containsId(itemId)) resourceStock.put(itemId, amount);
        }));
        // Bound corrupt NBT even when a datapack is temporarily unavailable.
        progress = Math.min(progress, 131_072);
        if (!projectComplete) {
            BlueprintDefinition definition = BlueprintRegistry.getBuildDefinition(blueprintId).orElse(null);
            if (definition == null) {
                projectActive = false;
                if (projectIssue.isBlank()) {
                    projectIssue = "Сохранённый чертёж недоступен: " + blueprintId;
                }
            } else {
                int max = definition.placements().size();
                if (progress > max) {
                    projectActive = false;
                    projectIssue = "Сохранённый прогресс превышает размер текущей версии схемы";
                } else if (progress >= max) {
                    projectComplete = true;
                    projectActive = false;
                    projectIssue = "";
                }
            }
        }
    }
}
