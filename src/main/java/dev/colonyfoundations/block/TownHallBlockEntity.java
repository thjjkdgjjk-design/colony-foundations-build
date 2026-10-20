package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
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
    private String ownerName = "";
    private String colonyName = "";
    private int colonyLevel = 1;
    /** Last durable reason construction paused; empty when no known blocker exists. */
    private String projectIssue = "";
    private UUID assignedBuilderUuid;
    private BlockPos lastBuilderPosition;
    private long lastBuilderUnavailableNotification = -1000L;
    // Transient navigation diagnostics; these reset after a world reload.
    private double lastBuilderDistance = -1.0;
    private int noProgressPathChecks;
    private int navigationFailures;
    private final Set<UUID> colonyMembers = new HashSet<>();

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    /**
     * Claim a newly-placed hall for the first player who opens it. Ownership is persisted
     * with the hall so other players cannot silently overwrite colony projects.
     */
    public void ensureFounded(PlayerEntity player) {
        if (ownerUuid != null) return;
        ownerUuid = player.getUuid();
        ownerName = player.getName().getString();
        colonyName = "Колония " + ownerName;
        colonyLevel = 1;
        markDirty();
        player.sendMessage(Text.literal("[Колония] Колония «" + colonyName + "» основана. Ратуша теперь принадлежит вам."), false);
    }

    public boolean canManage(PlayerEntity player) {
        ensureFounded(player);
        if (ownerUuid != null && (ownerUuid.equals(player.getUuid()) || colonyMembers.contains(player.getUuid()))) {
            return true;
        }
        player.sendMessage(Text.literal("[Колония] У вас нет прав на управление этой колонией."), true);
        return false;
    }

    public boolean isOwner(PlayerEntity player) {
        ensureFounded(player);
        return ownerUuid != null && ownerUuid.equals(player.getUuid());
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
        return true;
    }

    public boolean inviteMember(ServerPlayerEntity owner, ServerPlayerEntity target) {
        if (!isOwner(owner)) return false;
        if (target.getUuid().equals(ownerUuid)) return true;
        boolean added = colonyMembers.add(target.getUuid());
        if (added) {
            markDirty();
            target.sendMessage(Text.literal("[Колония] Вас добавили в колонию «" + colonyName() + "»."), false);
            owner.sendMessage(Text.literal("[Колония] Игрок " + target.getName().getString() + " получил доступ к колонии."), false);
        }
        return added;
    }

    public boolean revokeMember(ServerPlayerEntity owner, UUID memberUuid) {
        if (!isOwner(owner)) return false;
        boolean removed = colonyMembers.remove(memberUuid);
        if (removed) markDirty();
        return removed;
    }

    public int siteOffsetX() {
        return siteOffsetX;
    }

    public int siteOffsetZ() {
        return siteOffsetZ;
    }

    public void startProject(Identifier newBlueprintId, int quarterTurns, int requestedOffsetX, int requestedOffsetZ, PlayerEntity player) {
        BlueprintDefinition definition = BlueprintRegistry.getDefinition(newBlueprintId).orElse(null);
        if (definition == null || definition.placements().isEmpty()) {
            player.sendMessage(Text.literal("[Колония] Этот чертёж недоступен. Проверьте ресурсы сборки."), true);
            return;
        }
        if ((projectActive || (progress > 0 && !projectComplete)) && !blueprintId.equals(newBlueprintId)) {
            player.sendMessage(Text.literal("[Колония] Сначала заверши или приостанови текущий проект."), true);
            return;
        }

        boolean resumeSame = blueprintId.equals(newBlueprintId) && progress > 0 && !projectComplete;
        if (resumeSame) {
            // Resuming a partially built structure must keep its saved orientation and site.
            projectActive = true;
            projectIssue = "";
        } else {
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
        int moved = depositMaterials(player);
        player.sendMessage(Text.literal("[Колония] Заказано: " + definition.title() + ". Блоков: "
                + definition.placements().size() + ". Поворот: " + (rotation * 90)
                + "°. В склад ратуши добавлено предметов: " + moved + "."), false);
    }

    public void depositForSelectedProject(Identifier requestedBlueprint, int requestedRotation,
                                          int requestedOffsetX, int requestedOffsetZ, ServerPlayerEntity player) {
        if (blueprintId.equals(requestedBlueprint)) {
            if (!projectActive && progress == 0 && !projectComplete) {
                rotation = Math.floorMod(requestedRotation, 4);
                siteOffsetX = clampSiteOffset(requestedOffsetX);
                siteOffsetZ = clampSiteOffset(requestedOffsetZ);
            }
            int moved = depositMaterials(player);
            player.sendMessage(Text.literal("[Колония] В ратушу перенесено предметов: " + moved
                    + ". Запас стройки обновлён."), true);
            markDirty();
            return;
        }
        if (projectActive || (progress > 0 && !projectComplete)) {
            player.sendMessage(Text.literal("[Колония] Нельзя сменить чертёж, пока текущий проект не завершён."), true);
            return;
        }
        BlueprintDefinition definition = BlueprintRegistry.getDefinition(requestedBlueprint).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.literal("[Колония] Чертёж не найден."), true);
            return;
        }
        blueprintId = requestedBlueprint;
        rotation = Math.floorMod(requestedRotation, 4);
        siteOffsetX = clampSiteOffset(requestedOffsetX);
        siteOffsetZ = clampSiteOffset(requestedOffsetZ);
        progress = 0;
        projectComplete = false;
        int moved = depositMaterials(player);
        markDirty();
        player.sendMessage(Text.literal("[Колония] Выбрана схема «" + definition.title()
                + "». Перенесено предметов: " + moved + ". Чтобы начать стройку, нажми соответствующую кнопку."), true);
    }

    private int depositMaterials(PlayerEntity player) {
        BlueprintDefinition definition = currentDefinition();
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

    private Map<Identifier, Integer> remainingRequirements(BlueprintDefinition definition) {
        Map<Identifier, Integer> required = new HashMap<>();
        List<CottageBlueprint.Placement> plan = definition.placements();
        int start = blueprintId.equals(definition.id()) ? Math.min(progress, plan.size()) : 0;
        for (int i = start; i < plan.size(); i++) {
            Item item = plan.get(i).state().getBlock().asItem();
            if (item == Items.AIR) continue;
            Identifier itemId = Registries.ITEM.getId(item);
            required.merge(itemId, 1, Integer::sum);
        }
        return required;
    }

    public String statusForScreen() {
        String state = projectComplete ? "Завершено" : projectActive ? "Строится" : progress > 0 ? "Пауза" : "Не начато";
        BlueprintDefinition definition = currentDefinition();
        int total = definition.placements().size();
        String stock = resourceStock.entrySet().stream()
                .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed())
                .limit(2)
                .map(entry -> Registries.ITEM.get(entry.getKey()).getName().getString() + " ×" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        if (stock.isBlank()) stock = "пусто";
        if (stock.length() > 70) stock = stock.substring(0, 67) + "...";
        return "Колония: " + colonyName() + " (ур. " + colonyLevel + ")\nВладелец: "
                + (ownerName.isBlank() ? "не назначен" : ownerName)
                + "\n" + state + "\nПрогресс: " + Math.min(progress, total) + "/" + total
                + "\nСклад: " + stock
                + (projectIssue.isBlank() ? "" : "\nПричина: " + projectIssue);
    }

    public void togglePause(PlayerEntity player) {
        if (projectComplete) {
            player.sendMessage(Text.literal("[Колония] Проект завершён. Выбери чертёж и начни новый заказ."), true);
            return;
        }
        if (!projectActive && progress == 0 && projectIssue.isBlank()) {
            player.sendMessage(Text.literal("[Колония] Сначала выбери чертёж и нажми «Начать строительство»."), true);
            return;
        }
        projectActive = !projectActive;
        projectIssue = projectActive ? "" : "Приостановлено пользователем";
        markDirty();
        player.sendMessage(Text.literal("[Колония] Строительство " + (projectActive ? "возобновлено" : "приостановлено")
                + ": " + progress + "/" + currentDefinition().placements().size()), true);
    }

    public static void tick(World world, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld) || !hall.projectActive) return;
        if (serverWorld.getTime() % 10L != 0L) return;

        BlueprintDefinition definition = hall.currentDefinition();
        List<CottageBlueprint.Placement> plan = definition.placements();
        if (plan.isEmpty()) return;
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

    private BlueprintDefinition currentDefinition() {
        return BlueprintRegistry.getDefinition(blueprintId).orElseGet(CottageBlueprint::defaultDefinition);
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
        if (builder != null) builder.removeCommandTag(assignmentTag(world, pos));
        assignedBuilderUuid = null;
        lastBuilderPosition = null;
        resetNavigationDiagnostics();
        markDirty();
        ColonyFoundations.LOGGER.info("Blueprint {} completed at {} in dimension {} ({} obstructed cells skipped).",
                blueprintId, pos, world.getRegistryKey().getValue(), skippedObstructions);
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
        nbt.putInt("Progress", progress);
        nbt.putInt("SkippedObstructions", skippedObstructions);
        nbt.putString("BlueprintId", blueprintId.toString());
        nbt.putInt("Rotation", rotation);
        nbt.putInt("SiteOffsetX", siteOffsetX);
        nbt.putInt("SiteOffsetZ", siteOffsetZ);
        if (ownerUuid != null) nbt.putString("ColonyOwnerUuid", ownerUuid.toString());
        nbt.putString("ColonyOwnerName", ownerName);
        nbt.putString("ColonyName", colonyName);
        nbt.putInt("ColonyLevel", colonyLevel);
        nbt.putString("ProjectIssue", projectIssue);
        if (assignedBuilderUuid != null) nbt.putString("AssignedBuilderUuid", assignedBuilderUuid.toString());
        if (lastBuilderPosition != null) nbt.putLong("AssignedBuilderLastPos", lastBuilderPosition.asLong());
        NbtList membersTag = new NbtList();
        colonyMembers.stream().map(UUID::toString).sorted().forEach(member -> membersTag.add(NbtString.of(member)));
        nbt.put("ColonyMembers", membersTag);
        NbtCompound stockTag = new NbtCompound();
        resourceStock.forEach((id, amount) -> stockTag.putInt(id.toString(), amount));
        nbt.put("ResourceStock", stockTag);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        projectActive = nbt.getBoolean("ProjectActive").orElse(false);
        projectComplete = nbt.getBoolean("ProjectComplete").orElse(false);
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
        resourceStock.clear();
        nbt.getCompound("ResourceStock").ifPresent(stockTag -> stockTag.getKeys().forEach(key -> {
            Identifier itemId = Identifier.tryParse(key);
            int amount = Math.max(0, stockTag.getInt(key).orElse(0));
            if (itemId != null && amount > 0 && Registries.ITEM.containsId(itemId)) resourceStock.put(itemId, amount);
        }));
        int max = Math.max(1, currentDefinition().placements().size());
        progress = Math.min(progress, max);
        if (progress >= max) {
            projectComplete = true;
            projectActive = false;
            projectIssue = "";
        }
    }
}
