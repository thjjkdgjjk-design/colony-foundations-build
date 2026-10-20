package dev.colonyfoundations.block;

import dev.colonyfoundations.ColonyFoundations;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

/** Stores a persistent build order and advances it while a recruited vanilla villager works on site. */
public final class TownHallBlockEntity extends BlockEntity {
    public static final String BUILDER_TAG = "colony_foundations_builder";
    private static final String ASSIGNED_TAG_PREFIX = "colony_foundations_assigned_";

    private boolean projectActive;
    private boolean projectComplete;
    private int progress;
    private int skippedObstructions;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    public void startOrReport(PlayerEntity player) {
        if (projectComplete) {
            player.sendMessage(Text.literal("[Colony] Этот коттедж уже построен. Поставьте ещё одну ратушу для отдельного проекта."), true);
            return;
        }
        if (projectActive) {
            player.sendMessage(Text.literal("[Colony] Строительство: " + progress + "/" + CottageBlueprint.cottage().size()
                    + ". Подкрадитесь и нажмите ПКМ, чтобы приостановить."), true);
            return;
        }
        projectActive = true;
        markDirty();
        player.sendMessage(Text.literal("[Colony] Заказ коттеджа активирован. Используйте контракт на жителе, затем дождитесь, пока он дойдёт до стройки."), false);
    }

    public void pauseProject(PlayerEntity player) {
        if (projectActive) {
            projectActive = false;
            markDirty();
            player.sendMessage(Text.literal("[Colony] Строительство приостановлено на этапе " + progress + ". ПКМ возобновит его."), true);
        } else if (!projectComplete && progress > 0) {
            projectActive = true;
            markDirty();
            player.sendMessage(Text.literal("[Colony] Строительство возобновлено с этапа " + progress + "."), true);
        } else {
            player.sendMessage(Text.literal("[Colony] Активируйте проект обычным ПКМ; приседание ставит его на паузу."), true);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld) || !hall.projectActive) return;
        if (serverWorld.getTime() % 10L != 0L) return;

        List<CottageBlueprint.Placement> plan = CottageBlueprint.cottage();
        if (hall.progress >= plan.size()) {
            hall.finish(serverWorld, hall.findAssignedBuilder(serverWorld, pos));
            return;
        }

        Box searchArea = new Box(pos).expand(32.0);
        String assignmentTag = assignmentTag(serverWorld, pos);
        VillagerEntity builder = serverWorld.getEntitiesByClass(
                        VillagerEntity.class,
                        searchArea,
                        villager -> villager.getCommandTags().contains(assignmentTag))
                .stream().findFirst().orElse(null);

        if (builder == null) {
            builder = serverWorld.getEntitiesByClass(
                            VillagerEntity.class,
                            searchArea,
                            villager -> villager.getCommandTags().contains(BUILDER_TAG)
                                    && villager.getCommandTags().stream().noneMatch(tag -> tag.startsWith(ASSIGNED_TAG_PREFIX)))
                    .stream()
                    .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                    .orElse(null);
            if (builder == null) return;
            builder.addCommandTag(assignmentTag);
        }

        BlockPos site = pos.add(5, -1, -3);
        BlockPos workSpot = site.add(8, 1, 3);
        double distance = builder.squaredDistanceTo(workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5);
        if (distance > 9.0) {
            builder.getNavigation().startMovingTo(workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5, 1.0);
            return;
        }

        CottageBlueprint.Placement placement = plan.get(hall.progress);
        BlockPos target = site.add(placement.offset());
        BlockState oldState = serverWorld.getBlockState(target);
        if ((oldState.isAir() || oldState.isReplaceable()) && oldState.getFluidState().isEmpty()) {
            serverWorld.setBlockState(target, placement.state(), Block.NOTIFY_ALL);
        } else {
            hall.skippedObstructions++;
        }
        hall.progress++;
        hall.markDirty();
        if (hall.progress >= plan.size()) hall.finish(serverWorld, builder);
    }

    private VillagerEntity findAssignedBuilder(ServerWorld world, BlockPos pos) {
        String tag = assignmentTag(world, pos);
        return world.getEntitiesByClass(VillagerEntity.class, new Box(pos).expand(32.0), v -> v.getCommandTags().contains(tag))
                .stream().findFirst().orElse(null);
    }

    private void finish(ServerWorld world, VillagerEntity builder) {
        projectActive = false;
        projectComplete = true;
        if (builder != null) builder.removeCommandTag(assignmentTag(world, pos));
        markDirty();
        ColonyFoundations.LOGGER.info("Cottage blueprint completed at {} in dimension {} ({} obstructed cells skipped).",
                pos, world.getRegistryKey().getValue(), skippedObstructions);
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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        projectActive = nbt.getBoolean("ProjectActive");
        projectComplete = nbt.getBoolean("ProjectComplete");
        progress = Math.max(0, nbt.getInt("Progress"));
        skippedObstructions = Math.max(0, nbt.getInt("SkippedObstructions"));
        int max = CottageBlueprint.cottage().size();
        progress = Math.min(progress, max);
        if (progress >= max) {
            projectComplete = true;
            projectActive = false;
        }
    }
}
