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

public final class TownHallBlockEntity extends BlockEntity {
    public static final String BUILDER_TAG = "colony_foundations_builder";
    private static final String ASSIGNED_TAG_PREFIX = "colony_foundations_assigned_";

    private boolean projectActive;
    private boolean projectComplete;
    private int progress;
    private int skippedObstructions;
    private long lastMissingResourceNotification = -1000L;
    private Identifier blueprintId = Identifier.of(ColonyFoundations.MOD_ID, "cottage");
    private int rotation;
    private final Map<Identifier, Integer> resourceStock = new HashMap<>();

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    public void startProject(Identifier newBlueprintId, int quarterTurns, PlayerEntity player) {
        BlueprintDefinition definition = BlueprintRegistry.getDefinition(newBlueprintId).orElse(null);
        if (definition == null || definition.placements().isEmpty()) {
            player.sendMessage(Text.literal("[Colony] Этот чертёж недоступен. Проверьте ресурсы сборки."), true);
            return;
        }
        if ((projectActive || (progress > 0 && !projectComplete)) && !blueprintId.equals(newBlueprintId)) {
            player.sendMessage(Text.literal("[Colony] Сначала заверши или приостанови текущий проект."), true);
            return;
        }

        boolean resumeSame = blueprintId.equals(newBlueprintId) && progress > 0 && !projectComplete;
        if (resumeSame) {
            rotation = Math.floorMod(quarterTurns, 4);
            projectActive = true;
        } else {
            blueprintId = newBlueprintId;
            rotation = Math.floorMod(quarterTurns, 4);
            progress = 0;
            skippedObstructions = 0;
            projectComplete = false;
            projectActive = true;
        }
        markDirty();
        int moved = depositMaterials(player);
        player.sendMessage(Text.literal("[Colony] Заказано: " + definition.title() + ". Блоков: "
                + definition.placements().size() + ". Поворот: " + (rotation * 90)
                + "°. В склад ратуши добавлено предметов: " + moved + "."), false);
    }

    public void depositForSelectedProject(Identifier requestedBlueprint, int requestedRotation, ServerPlayerEntity player) {
        if (blueprintId.equals(requestedBlueprint)) {
            rotation = Math.floorMod(requestedRotation, 4);
            int moved = depositMaterials(player);
            player.sendMessage(Text.literal("[Colony] В ратушу перенесено предметов: " + moved
                    + ". Запас стройки обновлён."), true);
            markDirty();
            return;
        }
        if (projectActive || (progress > 0 && !projectComplete)) {
            player.sendMessage(Text.literal("[Colony] Нельзя сменить чертёж, пока текущий проект не завершён."), true);
            return;
        }
        BlueprintDefinition definition = BlueprintRegistry.getDefinition(requestedBlueprint).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.literal("[Colony] Чертёж не найден."), true);
            return;
        }
        blueprintId = requestedBlueprint;
        rotation = Math.floorMod(requestedRotation, 4);
        progress = 0;
        projectComplete = false;
        int moved = depositMaterials(player);
        markDirty();
        player.sendMessage(Text.literal("[Colony] Выбрана схема «" + definition.title()
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
        return state + "\nПрогресс: " + Math.min(progress, total) + "/" + total + "\nСклад: " + stock;
    }

    public void togglePause(PlayerEntity player) {
        if (projectComplete) {
            player.sendMessage(Text.literal("[Colony] Проект завершён. Выбери чертёж и начни новый заказ."), true);
            return;
        }
        if (!projectActive && progress == 0) {
            player.sendMessage(Text.literal("[Colony] Сначала выбери чертёж и нажми «Начать строительство»."), true);
            return;
        }
        projectActive = !projectActive;
        markDirty();
        player.sendMessage(Text.literal("[Colony] Строительство " + (projectActive ? "возобновлено" : "приостановлено")
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
        VillagerEntity builder = serverWorld.getEntitiesByClass(VillagerEntity.class, searchArea,
                        villager -> villager.getCommandTags().contains(assignmentTag))
                .stream().findFirst().orElse(null);
        if (builder == null) {
            builder = serverWorld.getEntitiesByClass(VillagerEntity.class, searchArea,
                            villager -> villager.getCommandTags().contains(BUILDER_TAG)
                                    && villager.getCommandTags().stream().noneMatch(tag -> tag.startsWith(ASSIGNED_TAG_PREFIX)))
                    .stream()
                    .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                    .orElse(null);
            if (builder == null) return;
            builder.addCommandTag(assignmentTag);
        }

        BlockPos site = pos.add(5, -1, -3);
        int rotatedWidth = (hall.rotation & 1) == 0 ? definition.sizeX() : definition.sizeZ();
        int rotatedDepth = (hall.rotation & 1) == 0 ? definition.sizeZ() : definition.sizeX();
        BlockPos workSpot = site.add(rotatedWidth + 1, 1, rotatedDepth / 2);
        double distance = builder.squaredDistanceTo(
                workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5);
        if (distance > 9.0) {
            builder.getNavigation().startMovingTo(
                    workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5, 1.0);
            return;
        }

        CottageBlueprint.Placement placement = plan.get(hall.progress);
        BlockPos target = site.add(rotate(placement.offset(), definition, hall.rotation)
                .subtract(rotate(definition.primaryOffset(), definition, hall.rotation)));
        BlockState oldState = serverWorld.getBlockState(target);
        if (!(oldState.isAir() || oldState.isReplaceable()) || !oldState.getFluidState().isEmpty()) {
            hall.skippedObstructions++;
        } else {
            Item item = placement.state().getBlock().asItem();
            if (item != Items.AIR) {
                Identifier itemId = Registries.ITEM.getId(item);
                int stock = hall.resourceStock.getOrDefault(itemId, 0);
                if (stock <= 0) {
                    hall.notifyMissingResource(serverWorld, pos, item);
                    return;
                }
                if (stock == 1) hall.resourceStock.remove(itemId);
                else hall.resourceStock.put(itemId, stock - 1);
            }
            serverWorld.setBlockState(target, rotateState(placement.state(), hall.rotation), Block.NOTIFY_ALL);
        }

        hall.progress++;
        hall.markDirty();
        if (hall.progress >= plan.size()) hall.finish(serverWorld, builder);
    }

    private void notifyMissingResource(ServerWorld world, BlockPos hallPos, Item item) {
        if (world.getTime() - lastMissingResourceNotification < 100L) return;
        lastMissingResourceNotification = world.getTime();
        Text message = Text.literal("[Colony] На стройке закончился ресурс: ").append(item.getName())
                .append(Text.literal(". Пополни ратушу через интерфейс."));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(hallPos.getX() + 0.5, hallPos.getY() + 0.5, hallPos.getZ() + 0.5) <= 64.0) {
                player.sendMessage(message, false);
            }
        }
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
        String tag = assignmentTag(world, pos);
        return world.getEntitiesByClass(VillagerEntity.class, new Box(pos).expand(32.0),
                        villager -> villager.getCommandTags().contains(tag))
                .stream().findFirst().orElse(null);
    }

    private void finish(ServerWorld world, VillagerEntity builder) {
        projectActive = false;
        projectComplete = true;
        if (builder != null) builder.removeCommandTag(assignmentTag(world, pos));
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
        }
    }
}
