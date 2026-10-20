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
import net.minecraft.registry.Registries;
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
    private static final int MAX_JOB_OPERATIONS_PER_TICK = 128;
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
        if (!schematic.compatibilityWarnings().isEmpty()) {
            int displayed = Math.min(4, schematic.compatibilityWarnings().size());
            String details = String.join("; ", schematic.compatibilityWarnings().subList(0, displayed));
            if (schematic.compatibilityWarnings().size() > displayed) {
                details += "; и ещё " + (schematic.compatibilityWarnings().size() - displayed);
            }
            player.sendMessage(Text.translatable(
                    "message.colony_schematics.compatibility_warnings", details), true);
        }
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
        private long lastBlockEntityNoticeTick = Long.MIN_VALUE / 2;

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
                        notifyBlockEntityRestoreIssue(server, target);
                    } else {
                        NbtCompound nbt = placement.nbt().copy();
                        String schematicType = nbt.getString("id").orElse("");
                        Identifier actualType = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                        if (!SchematicSafetyRules.blockEntityTypeMatches(
                                schematicType, actualType == null ? null : actualType.toString())) {
                            skippedPositions++;
                            ColonySchematics.LOGGER.warn(
                                    "Skipped schematic block entity at {}: expected type {}, found {}",
                                    target, schematicType, actualType);
                            notifyBlockEntityRestoreIssue(server, target);
                        } else {
                            nbt.putInt("x", target.getX());
                            nbt.putInt("y", target.getY());
                            nbt.putInt("z", target.getZ());
                            try {
                                blockEntity.read(nbt, world.getRegistryManager());
                                blockEntity.markDirty();
                                BlockState state = world.getBlockState(target);
                                world.updateListeners(target, state, state, net.minecraft.block.Block.NOTIFY_ALL);
                                blockEntitiesPlaced++;
                            } catch (RuntimeException exception) {
                                skippedPositions++;
                                ColonySchematics.LOGGER.warn(
                                        "Failed to restore schematic block entity {} at {}; skipping this entry",
                                        schematicType, target, exception);
                                notifyBlockEntityRestoreIssue(server, target);
                            }
                        }
                    }
                    blockEntityIndex++;
                    used++;
                    lastProgressTick = world.getTime();
                    continue;
                }

                if (entityIndex < schematic.entities().size()) {
                    NbtCompound data = schematic.entities().get(entityIndex).copy();
                    Identifier entityId = Identifier.tryParse(data.getString("id").orElse(""));
                    if (entityId == null || SchematicSafetyRules.containsUnsafeEntityTree(data)) {
                        skippedPositions++;
                        entityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }

                    transformEntityTree(data, origin, schematic.sizeX(), schematic.sizeZ(), turns, mirrorMode);
                    if (!entityTreeInsideWorld(world, data)) {
                        skippedPositions++;
                        entityIndex++;
                        used++;
                        lastProgressTick = world.getTime();
                        continue;
                    }
                    BlockPos unloadedPosition = firstUnloadedEntityPosition(world, data);
                    if (unloadedPosition != null) {
                        // Check root and passengers before instantiating the tree; retries do not recreate entities.
                        notifyWaitingForChunk(server, unloadedPosition);
                        return used;
                    }

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

        private void notifyBlockEntityRestoreIssue(MinecraftServer server, BlockPos target) {
            if (world.getTime() - lastBlockEntityNoticeTick < 100L) return;
            lastBlockEntityNoticeTick = world.getTime();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requester);
            if (player != null) {
                player.sendMessage(Text.translatable("message.colony_schematics.block_entity_restore_skipped",
                        target.getX(), target.getY(), target.getZ()), true);
            }
        }
    }

    private static void transformEntityTree(NbtCompound data, BlockPos origin, int sx, int sz, int turns, int mirrorMode) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position != null && position.size() >= 3) {
            double x = position.getDouble(0).orElse(0.0);
            double y = position.getDouble(1).orElse(0.0);
            double z = position.getDouble(2).orElse(0.0);
            net.minecraft.util.math.Vec3d transformed = SchematicTransform.transformEntityPosition(
                    new net.minecraft.util.math.Vec3d(x, y, z), sx, sz, turns, mirrorMode);
            NbtList result = new NbtList();
            result.add(NbtDouble.of(origin.getX() + transformed.x));
            result.add(NbtDouble.of(origin.getY() + transformed.y));
            result.add(NbtDouble.of(origin.getZ() + transformed.z));
            data.put("Pos", result);
        }

        NbtList rotation = data.getList("Rotation").orElse(null);
        if (rotation != null && rotation.size() >= 2 && (turns != 0 || mirrorMode != 0)) {
            float yaw = rotation.getFloat(0).orElse(0.0f);
            if (mirrorMode == 1) yaw = -yaw;
            if (mirrorMode == 2) yaw = 180.0f - yaw;
            yaw += 90.0f * turns;
            NbtList transformedRotation = new NbtList();
            transformedRotation.add(NbtFloat.of(yaw));
            transformedRotation.add(NbtFloat.of(rotation.getFloat(1).orElse(0.0f)));
            data.put("Rotation", transformedRotation);
        }

        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers != null) {
            for (int i = 0; i < passengers.size(); i++) {
                NbtCompound passenger = passengers.getCompound(i).orElse(null);
                if (passenger != null) transformEntityTree(passenger, origin, sx, sz, turns, mirrorMode);
            }
        }
    }

    private static boolean entityTreeInsideWorld(ServerWorld world, NbtCompound data) {
        BlockPos position = entityBlockPos(data);
        if (position == null || !insideWorld(world, position)) return false;
        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers == null) return true;
        for (int i = 0; i < passengers.size(); i++) {
            NbtCompound passenger = passengers.getCompound(i).orElse(null);
            if (passenger == null || !entityTreeInsideWorld(world, passenger)) return false;
        }
        return true;
    }

    private static BlockPos firstUnloadedEntityPosition(ServerWorld world, NbtCompound data) {
        BlockPos position = entityBlockPos(data);
        if (position != null && !world.isChunkLoaded(position)) return position;
        NbtList passengers = data.getList("Passengers").orElse(null);
        if (passengers == null) return null;
        for (int i = 0; i < passengers.size(); i++) {
            NbtCompound passenger = passengers.getCompound(i).orElse(null);
            if (passenger != null) {
                BlockPos unloaded = firstUnloadedEntityPosition(world, passenger);
                if (unloaded != null) return unloaded;
            }
        }
        return null;
    }

    private static BlockPos entityBlockPos(NbtCompound data) {
        NbtList position = data.getList("Pos").orElse(null);
        if (position == null || position.size() < 3) return null;
        double x = position.getDouble(0).orElse(Double.NaN);
        double y = position.getDouble(1).orElse(Double.NaN);
        double z = position.getDouble(2).orElse(Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static boolean insideWorld(ServerWorld world, BlockPos pos) {
        return pos.getY() >= world.getBottomY()
                && pos.getY() < world.getBottomY() + world.getDimension().height()
                && world.getWorldBorder().contains(pos);
    }
}
