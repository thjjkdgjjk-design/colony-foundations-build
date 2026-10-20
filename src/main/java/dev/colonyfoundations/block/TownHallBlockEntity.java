package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.BlueprintRegistry;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

/** Stores a selected construction schematic and advances it as a villager builder works. */
public final class TownHallBlockEntity extends BlockEntity {
    public static final String BUILDER_TAG = "colony_foundations_builder";
    private static final String ASSIGNED_TAG_PREFIX = "colony_foundations_assigned_";

    private boolean projectActive;
    private boolean projectComplete;
    private int progress;
    private int skippedObstructions;
    private Identifier blueprintId = Identifier.of(ColonyFoundations.MOD_ID, "cottage");
    private int rotation;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    public void startProject(Identifier newBlueprintId, int quarterTurns, PlayerEntity player) {
        BlueprintDefinition definition = BlueprintRegistry.getDefinition(newBlueprintId).orElse(null);
        if (definition == null || definition.placements().isEmpty()) {
            player.sendMessage(Text.literal("[Colony] Этот чертёж недоступен. Проверьте ресурсы сборки."), true);
            return;
        }
        blueprintId = newBlueprintId;
        rotation = Math.floorMod(quarterTurns, 4);
        progress = 0;
        skippedObstructions = 0;
        projectComplete = false;
        projectActive = true;
        markDirty();
        player.sendMessage(Text.literal("[Colony] Заказано: " + definition.title()
                + ". Блоков: " + definition.placements().size()
                + ". Поворот: " + (rotation * 90) + "°."), false);
    }

    public void togglePause(PlayerEntity player) {
        if (projectComplete) {
            player.sendMessage(Text.literal("[Colony] Проект завершён. Выберите чертёж и начните новый заказ."), true);
            return;
        }
        if (!projectActive && progress == 0) {
            player.sendMessage(Text.literal("[Colony] Сначала выберите чертёж и нажмите «Начать строительство»."), true);
            return;
        }
        projectActive = !projectActive;
        markDirty();
        String status = projectActive ? "возобновлено" : "приостановлено";
        player.sendMessage(Text.literal("[Colony] Строительство " + status + ": " + progress
                + "/" + currentDefinition().placements().size()), true);
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
        BlockPos target = site.add(rotate(placement.offset(), definition, hall.rotation));
        BlockState oldState = serverWorld.getBlockState(target);
        BlockState rotatedState = rotateState(placement.state(), hall.rotation);
        if ((oldState.isAir() || oldState.isReplaceable()) && oldState.getFluidState().isEmpty()) {
            serverWorld.setBlockState(target, rotatedState, Block.NOTIFY_ALL);
        } else {
            hall.skippedObstructions++;
        }
        hall.progress++;
        hall.markDirty();
        if (hall.progress >= plan.size()) hall.finish(serverWorld, builder);
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
        int max = Math.max(1, currentDefinition().placements().size());
        progress = Math.min(progress, max);
        if (progress >= max) {
            projectComplete = true;
            projectActive = false;
        }
    }
}
