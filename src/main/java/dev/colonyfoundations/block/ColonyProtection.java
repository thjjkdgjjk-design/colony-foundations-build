package dev.colonyfoundations.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.RegistryKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative initial Town Hall territory protection.
 * Claim membership is rebuilt from loaded chunks and lazily refreshed by Town Hall ticks.
 */
public final class ColonyProtection {
    private static final Map<RegistryKey<World>, Map<Long, Set<BlockPos>>> TOWN_HALLS_BY_DIMENSION = new HashMap<>();
    private static final Map<UUID, DenialNotice> LAST_DENIAL_NOTICE = new HashMap<>();
    private static boolean initialized;

    private record DenialNotice(RegistryKey<World> dimension, long worldTime) {
    }

    private ColonyProtection() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        ServerChunkEvents.CHUNK_LOAD.register(ColonyProtection::indexChunk);
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> removeChunk(world, chunk.getPos()));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TOWN_HALLS_BY_DIMENSION.clear();
            LAST_DENIAL_NOTICE.clear();
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (!isDenied(serverWorld, pos, serverPlayer)) return true;
            notifyDenied(serverWorld, serverPlayer, false);
            return false;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld && blockEntity instanceof TownHallBlockEntity) {
                unregisterTownHall(serverWorld, pos);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld serverWorld)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            BlockPos clicked = hit.getBlockPos();
            BlockPos adjacent = clicked.offset(hit.getSide());
            if (isDenied(serverWorld, clicked, serverPlayer) || isDenied(serverWorld, adjacent, serverPlayer)) {
                notifyDenied(serverWorld, serverPlayer, true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    /** May be called by both chunk-load scanning and the Town Hall server ticker. */
    public static void registerTownHall(ServerWorld world, BlockPos pos) {
        BlockPos stablePos = pos.toImmutable();
        TOWN_HALLS_BY_DIMENSION
                .computeIfAbsent(world.getRegistryKey(), ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey(stablePos.getX() >> 4, stablePos.getZ() >> 4), ignored -> new HashSet<>())
                .add(stablePos);
    }

    public static void unregisterTownHall(ServerWorld world, BlockPos pos) {
        Map<Long, Set<BlockPos>> byChunk = TOWN_HALLS_BY_DIMENSION.get(world.getRegistryKey());
        if (byChunk == null) return;
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> halls = byChunk.get(key);
        if (halls != null) {
            halls.remove(pos);
            if (halls.isEmpty()) byChunk.remove(key);
        }
        if (byChunk.isEmpty()) TOWN_HALLS_BY_DIMENSION.remove(world.getRegistryKey());
    }

    private static void indexChunk(ServerWorld world, WorldChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof TownHallBlockEntity) registerTownHall(world, blockEntity.getPos());
        }
    }

    private static void removeChunk(ServerWorld world, ChunkPos pos) {
        Map<Long, Set<BlockPos>> byChunk = TOWN_HALLS_BY_DIMENSION.get(world.getRegistryKey());
        if (byChunk == null) return;
        byChunk.remove(chunkKey(pos.x, pos.z));
        if (byChunk.isEmpty()) TOWN_HALLS_BY_DIMENSION.remove(world.getRegistryKey());
    }

    private static boolean isDenied(ServerWorld world, BlockPos target, ServerPlayerEntity player) {
        Map<Long, Set<BlockPos>> byChunk = TOWN_HALLS_BY_DIMENSION.get(world.getRegistryKey());
        if (byChunk == null) return false;

        int targetChunkX = target.getX() >> 4;
        int targetChunkZ = target.getZ() >> 4;
        for (int dx = -ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS;
             dx <= ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS; dx++) {
            for (int dz = -ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS;
                 dz <= ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS; dz++) {
                Set<BlockPos> halls = byChunk.get(chunkKey(targetChunkX + dx, targetChunkZ + dz));
                if (halls == null || halls.isEmpty()) continue;

                Iterator<BlockPos> iterator = halls.iterator();
                while (iterator.hasNext()) {
                    BlockPos hallPos = iterator.next();
                    if (!world.isChunkLoaded(hallPos)) continue;
                    BlockEntity blockEntity = world.getBlockEntity(hallPos);
                    if (!(blockEntity instanceof TownHallBlockEntity hall)) {
                        iterator.remove();
                        continue;
                    }
                    if (!hall.isFounded()) continue;
                    if (!ColonyTerritoryRules.containsInitialClaim(
                            hallPos.getX(), hallPos.getZ(), target.getX(), target.getZ())) continue;
                    if (!hall.canModifyTerritory(player)) return true;
                }
                if (halls.isEmpty()) byChunk.remove(chunkKey(targetChunkX + dx, targetChunkZ + dz));
            }
        }
        if (byChunk.isEmpty()) TOWN_HALLS_BY_DIMENSION.remove(world.getRegistryKey());
        return false;
    }

    private static void notifyDenied(ServerWorld world, ServerPlayerEntity player, boolean interaction) {
        long now = world.getTime();
        DenialNotice previous = LAST_DENIAL_NOTICE.get(player.getUuid());
        if (previous != null && previous.dimension().equals(world.getRegistryKey())
                && now >= previous.worldTime() && now - previous.worldTime() < 20L) return;
        LAST_DENIAL_NOTICE.put(player.getUuid(), new DenialNotice(world.getRegistryKey(), now));
        player.sendMessage(Text.translatable(interaction
                ? "message.colony_foundations.territory_interaction_protected"
                : "message.colony_foundations.territory_protected"), true);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFF_FFFFL) << 32 | ((long) z & 0xFFFF_FFFFL);
    }
}
