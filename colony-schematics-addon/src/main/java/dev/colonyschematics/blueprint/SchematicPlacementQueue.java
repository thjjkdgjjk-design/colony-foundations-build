package dev.colonyschematics.blueprint;

import dev.colonyschematics.ColonySchematics;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-authoritative, time-sliced schematic placement. Large blueprints no
 * longer attempt tens of thousands of world writes in a single network callback.
 */
public final class SchematicPlacementQueue {
    private static final int MAX_ACTIVE_JOBS = 8;
    private static final int MAX_SERVER_OPERATIONS_PER_TICK = 1024;
    private static final int MAX_JOB_OPERATIONS_PER_TICK = 256;
    private static final long UNLOADED_CHUNK_TIMEOUT_TICKS = 12_000L;

    private static final Map<UUID, PlacementJob> JOBS = new LinkedHashMap<>();
    private static boolean initialized;

    private SchematicPlacementQueue() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(SchematicPlacementQueue::tick);
    }

    public static void enqueue(ServerPlayerEntity player, SchematicLoader.Schematic schematic,
                               int requestedRotation, int requestedMirror, Identifier sourceId) {
        UUID requester = player.getUuid();
        if (JOBS.containsKey(requester) || JOBS.size() >= MAX_ACTIVE_JOBS) {
            player.sendMessage(Text.translatable("message.colony_schematics.placement_busy"), true);
            return;
        }

        int turns = Math.floorMod(requestedRotation, 4);
        int mirrorMode = Math.floorMod(requestedMirror, 3);
        Direction facing = player.getHorizontalFacing();
        BlockPos requestedAnchor = player.getBlockPos().offset(facing, 4);
        BlockPos anchorOffset = SchematicTransform.transformBlockPos(
                schematic.primaryOffset(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
        BlockPos origin = requestedAnchor.subtract(anchorOffset);

        PlacementJob job = new PlacementJob(requester, player.getServerWorld(),
                schematic, sourceId, origin, turns, mirrorMode);
        JOBS.put(requester, job);
        player.sendMessage(Text.translatable(
                "message.colony_schematics.placement_queued", schematic.blocks().size()), false);
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        int remainingBudget = MAX_SERVER_OPERATIONS_PER_TICK;
        Iterator<Map.Entry<UUID, PlacementJob>> iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlacementJob> entry = iterator.next();
            PlacementJob job = entry.getValue();
            if (remainingBudget > 0) {
                int used = job.advance(server, Math.min(MAX_JOB_OPERATIONS_PER_TICK, remainingBudget));
                remainingBudget -= used;
            }
            if (job.isComplete()) {
                iterator.remove();
                job.reportCompletion(server);
            } else if (job.hasTimedOut()) {
                iterator.remove();
                job.reportTimeout(server);
            }
        }
    }

    private static final class PlacementJob {
        private final UUID requester;
        private final ServerWorld world;
        private final SchematicLoader.Schematic schematic;
        private final Identifier sourceId;
        private final BlockPos origin;
        private final int turns;
        private final int mirrorMode;
        private final BlockMirror mirror;
        private final BlockRotation rotation;

        private int blockIndex;
        private int blockEntityIndex;
        private int entityIndex;
        private int blocksPlaced;
        private int blockEntitiesPlaced;
        private int entitiesPlaced;
        private int skippedPositions;
        private long lastProgressTick;
        private long lastChunkNoticeTick = Long.MIN_VALUE / 2;

        private PlacementJob(UUID requester, ServerWorld world, SchematicLoader.Schematic schematic,
                             Identifier sourceId, BlockPos origin, int turns, int mirrorMode) {
            this.requester = requester;
            this.world = world;
            this.schematic = schematic;
            this.sourceId = sourceId;
            this.origin = origin.toImmutable();
            this.turns = turns;
            this.mirrorMode = mirrorMode;
            this.mirror = switch (mirrorMode) {
                case 1 -> BlockMirror.FRONT_BACK;
                case 2 -> BlockMirror.LEFT_RIGHT;
                default -> BlockMirror.NONE;
            };
            this.rotation = switch (turns) {
                case 1 -> BlockRotation.CLOCKWISE_90;
                case 2 -> BlockRotation.CLOCKWISE_180;
                case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
                default -> BlockRotation.NONE;
            };
            this.lastProgressTick = world.getTime();
        }

        private int advance(MinecraftServer server, int maxOperations) {
            int used = 0;
            while (used < maxOperations) {
                if (blockIndex < schematic.blocks().size()) {
                    SchematicLoader.BlockPlacement placement = schematic.blocks().get(blockIndex);
                    BlockPos target = origin.add(SchematicTransform.transformBlockPos(
                            placement.position(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode));
                    if (!insideWorld(world, target)) {
                        skippedPositions++;
                        blockIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }
                    if (!world.isChunkLoaded(target)) {
                        notifyWaitingForChunk(server, target);
                        return used;
                    }
                    BlockState state = placement.state().mirror(mirror).rotate(rotation);
                    if (world.setBlockState(target, state, net.minecraft.block.Block.NOTIFY_ALL)) blocksPlaced++;
                    else skippedPositions++;
                    blockIndex++;
                    used++;
                    lastProgressTick = world.getTime();
                    continue;
                }

                if (blockEntityIndex < schematic.blockEntities().size()) {
                    SchematicLoader.BlockEntityPlacement placement = schematic.blockEntities().get(blockEntityIndex);
                    BlockPos target = origin.add(SchematicTransform.transformBlockPos(
                            placement.position(), schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode));
                    if (!insideWorld(world, target)) {
                        skippedPositions++;
                        blockEntityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }
                    if (!world.isChunkLoaded(target)) {
                        notifyWaitingForChunk(server, target);
                        return used;
                    }
                    BlockEntity blockEntity = world.getBlockEntity(target);
                    if (blockEntity == null) {
                        skippedPositions++;
                    } else {
                        NbtCompound nbt = placement.nbt().copy();
                        nbt.putInt("x", target.getX());
                        nbt.putInt("y", target.getY());
                        nbt.putInt("z", target.getZ());
                        blockEntity.read(nbt, world.getRegistryManager());
                        blockEntity.markDirty();
                        BlockState state = world.getBlockState(target);
                        world.updateListeners(target, state, state, net.minecraft.block.Block.NOTIFY_ALL);
                        blockEntitiesPlaced++;
                    }
                    blockEntityIndex++;
                    used++;
                    lastProgressTick = world.getTime();
                    continue;
                }

                if (entityIndex < schematic.entities().size()) {
                    NbtCompound data = schematic.entities().get(entityIndex).copy();
                    Identifier entityId = Identifier.tryParse(data.getString("id").orElse(""));
                    if (entityId == null || entityId.getPath().equals("player")
                            || entityId.getPath().equals("command_block_minecart")) {
                        skippedPositions++;
                        entityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }

                    transformEntityTree(data, origin, schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
                    Entity entity = EntityType.loadEntityWithPassengers(data, world, SpawnReason.LOAD, Function.identity());
                    if (entity == null || !entity.getType().isSaveable()) {
                        skippedPositions++;
                        entityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }
                    BlockPos target = entity.getBlockPos();
                    if (!insideWorld(world, target)) {
                        skippedPositions++;
                        entityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }
                    if (!world.isChunkLoaded(target)) {
                        notifyWaitingForChunk(server, target);
                        return used;
                    }
                    if (world.spawnEntity(entity)) entitiesPlaced++;
                    else skippedPositions++;
                    entityIndex++;
                    used++;
                    lastProgressTick = world.getTime();
                    continue;
                }
                break;
            }
            return used;
        }

        private boolean isComplete() {
            return blockIndex >= schematic.blocks().size()
                    && blockEntityIndex >= schematic.blockEntities().size()
                    && entityIndex >= schematic.entities().size();
        }

        private boolean hasTimedOut() {
            return world.getTime() - lastProgressTick >= UNLOADED_CHUNK_TIMEOUT_TICKS;
        }

        private void notifyWaitingForChunk(MinecraftServer server, BlockPos target) {
            if (world.getTime() - lastChunkNoticeTick < 100L) return;
            lastChunkNoticeTick = world.getTime();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requester);
            if (player != null) {
                player.sendMessage(Text.translatable("message.colony_schematics.waiting_for_chunk",
                        target.getX() >> 4, target.getZ() >> 4), true);
            }
        }

        private void reportCompletion(MinecraftServer server) {
            String result = "Schematic placement {} finished in {}: {} blocks, {} block entities, {} entities; skipped {}";
            ColonySchematics.LOGGER.info(result, sourceId, world.getRegistryKey().getValue(),
                    blocksPlaced, blockEntitiesPlaced, entitiesPlaced, skippedPositions);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requester);
            if (player != null) {
                player.sendMessage(Text.translatable("message.colony_schematics.placement_complete",
                        blocksPlaced, blockEntitiesPlaced, entitiesPlaced, skippedPositions), false);
                if (!schematic.requiredMods().isEmpty()) {
                    player.sendMessage(Text.translatable("message.colony_schematics.required_mods",
                            String.join(", ", schematic.requiredMods())), false);
                }
            }
        }

        private void reportTimeout(MinecraftServer server) {
            ColonySchematics.LOGGER.warn("Schematic placement {} timed out in {} after placing {} blocks.",
                    sourceId, world.getRegistryKey().getValue(), blocksPlaced);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requester);
            if (player != null) {
                player.sendMessage(Text.translatable("message.colony_schematics.placement_timeout"), true);
            }
        }
    }

    private static void transformEntityTree(NbtCompound data, BlockPos origin, int sx, int sz, int turns, int mirrorMode) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position != null && position.size() >= 3) {
            double x = position.getDouble(0).orElse(0.0);
            double y = position.getDouble(1).orElse(0.0);
            double z = position.getDouble(2).orElse(0.0);
            double mx = x;
            double mz = z;
            if (mirrorMode == 1) mz = sz - mz;
            if (mirrorMode == 2) mx = sx - mx;
            double rx;
            double rz;
            switch (turns) {
                case 1 -> { rx = sz - mz; rz = mx; }
                case 2 -> { rx = sx - mx; rz = sz - mz; }
                case 3 -> { rx = mz; rz = sx - mx; }
                default -> { rx = mx; rz = mz; }
            }
            NbtList transformed = new NbtList();
            transformed.add(NbtDouble.of(origin.getX() + rx));
            transformed.add(NbtDouble.of(origin.getY() + y));
            transformed.add(NbtDouble.of(origin.getZ() + rz));
            data.put("Pos", transformed);
        }

        NbtList rotation = data.getList("Rotation").orElse(null);
        if (rotation != null && rotation.size() >= 2 && (turns != 0 || mirrorMode != 0)) {
            float yaw = rotation.getFloat(0).orElse(0.0f);
            if (mirrorMode == 1) yaw = -yaw;
            if (mirrorMode == 2) yaw = 180.0f - yaw;
            yaw += 90.0f * turns;
            NbtList transformed = new NbtList();
            transformed.add(NbtFloat.of(yaw));
            transformed.add(NbtFloat.of(rotation.getFloat(1).orElse(0.0f)));
            data.put("Rotation", transformed);
        }

        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers != null) {
            for (int i = 0; i < passengers.size(); i++) {
                Optional<NbtCompound> passenger = passengers.getCompound(i);
                passenger.ifPresent(tag -> transformEntityTree(tag, origin, sx, sz, turns, mirrorMode));
            }
        }
    }

    private static boolean insideWorld(ServerWorld world, BlockPos pos) {
        return pos.getY() >= world.getBottomY()
                && pos.getY() < world.getBottomY() + world.getDimension().height()
                && world.getWorldBorder().contains(pos);
    }
}
