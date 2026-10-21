package dev.colonyfoundations.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import dev.colonyfoundations.block.ColonyPermission;
import dev.colonyfoundations.block.ColonyClaimState;
import dev.colonyfoundations.block.ColonyRank;
import dev.colonyfoundations.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;

/**
 * Server-authoritative colony owner workflow. All mutations are checked against the
 * Town Hall's persisted owner UUID; coordinates are resolved in the caller's dimension.
 */
public final class ColonyManagementCommands {
    private ColonyManagementCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = literal("colony");

            root.then(literal("info")
                    .then(argument("hall", BlockPosArgumentType.blockPos())
                            .executes(context -> showInfo(context.getSource(),
                                    BlockPosArgumentType.getBlockPos(context, "hall")))));

            root.then(literal("rename")
                    .then(argument("hall", BlockPosArgumentType.blockPos())
                            .then(argument("name", StringArgumentType.greedyString())
                                    .executes(context -> rename(context.getSource(),
                                            BlockPosArgumentType.getBlockPos(context, "hall"),
                                            StringArgumentType.getString(context, "name"))))));

            root.then(literal("invite")
                    .then(argument("hall", BlockPosArgumentType.blockPos())
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(context -> invite(context.getSource(),
                                            BlockPosArgumentType.getBlockPos(context, "hall"),
                                            EntityArgumentType.getPlayer(context, "player"))))));

            root.then(literal("revoke")
                    .then(argument("hall", BlockPosArgumentType.blockPos())
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(context -> revoke(context.getSource(),
                                            BlockPosArgumentType.getBlockPos(context, "hall"),
                                            EntityArgumentType.getPlayer(context, "player"))))));

            var permissionCommand = literal("permission");
            var permissionHall = argument("hall", BlockPosArgumentType.blockPos());
            var permissionPlayer = argument("player", EntityArgumentType.player());
            var permissionAction = argument("action", StringArgumentType.word());
            var permissionEnabled = argument("enabled", BoolArgumentType.bool());
            permissionEnabled.executes(context -> setPermission(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    EntityArgumentType.getPlayer(context, "player"),
                    StringArgumentType.getString(context, "action"),
                    BoolArgumentType.getBool(context, "enabled")));
            permissionAction.then(permissionEnabled);
            permissionPlayer.then(permissionAction);
            permissionHall.then(permissionPlayer);
            permissionCommand.then(permissionHall);
            root.then(permissionCommand);

            var rankCommand = literal("rank");
            var rankHall = argument("hall", BlockPosArgumentType.blockPos());
            var rankPlayer = argument("player", EntityArgumentType.player());
            var rankName = argument("rank", StringArgumentType.word());
            rankName.executes(context -> setMemberRank(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    EntityArgumentType.getPlayer(context, "player"),
                    StringArgumentType.getString(context, "rank")));
            rankPlayer.then(rankName);
            rankHall.then(rankPlayer);
            rankCommand.then(rankHall);
            root.then(rankCommand);

            var cancelOrderCommand = literal("order-cancel");
            var cancelOrderHall = argument("hall", BlockPosArgumentType.blockPos());
            var cancelOrderSequence = argument("sequence", LongArgumentType.longArg(1L));
            cancelOrderSequence.executes(context -> cancelQueuedWorkOrder(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    LongArgumentType.getLong(context, "sequence")));
            cancelOrderHall.then(cancelOrderSequence);
            cancelOrderCommand.then(cancelOrderHall);
            root.then(cancelOrderCommand);

            var orderPriorityCommand = literal("order-priority");
            var orderPriorityHall = argument("hall", BlockPosArgumentType.blockPos());
            var orderPrioritySequence = argument("sequence", LongArgumentType.longArg(1L));
            var orderPriorityValue = argument("priority", IntegerArgumentType.integer(-10, 10));
            orderPriorityValue.executes(context -> reprioritizeQueuedWorkOrder(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    LongArgumentType.getLong(context, "sequence"),
                    IntegerArgumentType.getInteger(context, "priority")));
            orderPrioritySequence.then(orderPriorityValue);
            orderPriorityHall.then(orderPrioritySequence);
            orderPriorityCommand.then(orderPriorityHall);
            root.then(orderPriorityCommand);

            root.then(literal("work-orders")
                    .then(argument("hall", BlockPosArgumentType.blockPos())
                            .executes(context -> listWorkOrders(context.getSource(),
                                    BlockPosArgumentType.getBlockPos(context, "hall")))));

            var queueCommand = literal("queue");
            var queueHall = argument("hall", BlockPosArgumentType.blockPos());
            var queueBlueprint = argument("blueprint", StringArgumentType.word());
            var queueRotation = argument("rotation", IntegerArgumentType.integer(0, 3));
            var queueOffsetX = argument("offset_x", IntegerArgumentType.integer(-64, 64));
            var queueOffsetZ = argument("offset_z", IntegerArgumentType.integer(-64, 64));
            var queuePriority = argument("priority", IntegerArgumentType.integer(-10, 10));
            queuePriority.executes(context -> queueBuildOrder(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    StringArgumentType.getString(context, "blueprint"),
                    IntegerArgumentType.getInteger(context, "rotation"),
                    IntegerArgumentType.getInteger(context, "offset_x"),
                    IntegerArgumentType.getInteger(context, "offset_z"),
                    IntegerArgumentType.getInteger(context, "priority")));
            queueOffsetZ.then(queuePriority);
            queueOffsetX.then(queueOffsetZ);
            queueRotation.then(queueOffsetX);
            queueBlueprint.then(queueRotation);
            queueHall.then(queueBlueprint);
            queueCommand.then(queueHall);
            root.then(queueCommand);

            var rankPermissionCommand = literal("rank-permission");
            var rankPermissionHall = argument("hall", BlockPosArgumentType.blockPos());
            var rankPermissionRank = argument("rank", StringArgumentType.word());
            var rankPermissionAction = argument("action", StringArgumentType.word());
            var rankPermissionEnabled = argument("enabled", BoolArgumentType.bool());
            rankPermissionEnabled.executes(context -> setRankPermission(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "hall"),
                    StringArgumentType.getString(context, "rank"),
                    StringArgumentType.getString(context, "action"),
                    BoolArgumentType.getBool(context, "enabled")));
            rankPermissionAction.then(rankPermissionEnabled);
            rankPermissionRank.then(rankPermissionAction);
            rankPermissionHall.then(rankPermissionRank);
            rankPermissionCommand.then(rankPermissionHall);
            root.then(rankPermissionCommand);

            dispatcher.register(root);
        });
    }

    private static int cancelQueuedWorkOrder(ServerCommandSource source, BlockPos pos, long sequence)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null || !hall.canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You need build_projects permission to manage work orders."), true);
            return 0;
        }
        if (!hall.cancelQueuedWorkOrder(sequence, player)) {
            player.sendMessage(Text.literal("Only a queued/blocked zero-progress order belonging to this Town Hall can be cancelled; active or progressed orders are protected."), true);
            return 0;
        }
        player.sendMessage(Text.literal("Cancelled work order #" + sequence + "."), false);
        return 1;
    }

    private static int reprioritizeQueuedWorkOrder(ServerCommandSource source, BlockPos pos,
                                                   long sequence, int priority)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null || !hall.canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You need build_projects permission to manage work orders."), true);
            return 0;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld) || hall.colonyUuid() == null) return 0;
        var updated = ColonyClaimState.get(serverWorld)
                .reprioritizeQueuedWorkOrder(hall.colonyUuid(), sequence, priority);
        if (updated.isEmpty()) {
            player.sendMessage(Text.literal("Only a queued order with zero progress can be reprioritized."), true);
            return 0;
        }
        player.sendMessage(Text.literal("Work order #" + sequence + " priority is now " + priority + "."), false);
        return 1;
    }

    private static int queueBuildOrder(ServerCommandSource source, BlockPos pos, String blueprintId,
                                       int rotation, int offsetX, int offsetZ, int priority)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        Identifier identifier = Identifier.tryParse(blueprintId);
        if (identifier == null) {
            player.sendMessage(Text.literal("Invalid blueprint identifier."), true);
            return 0;
        }
        return hall.queueBuildOrder(identifier, rotation, offsetX, offsetZ, priority, player) ? 1 : 0;
    }

    private static int listWorkOrders(ServerCommandSource source, BlockPos pos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null || !hall.canPerformColonyAction(player, ColonyPermission.BUILD_PROJECTS)) {
            player.sendMessage(Text.literal("You need build_projects permission to view colony work orders."), true);
            return 0;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld) || hall.colonyUuid() == null) {
            player.sendMessage(Text.literal("This Town Hall has not founded a persistent colony."), true);
            return 0;
        }
        var orders = ColonyClaimState.get(serverWorld).workOrdersForColony(hall.colonyUuid());
        player.sendMessage(Text.literal("Colony work orders (" + orders.size() + "):"), false);
        for (var order : orders.stream().limit(10).toList()) {
            String line = "#" + order.createdSequence() + " " + order.type() + "/" + order.status()
                    + " " + order.blueprintId() + " " + order.progress() + "/" + order.totalPlacements()
                    + " at " + BlockPos.fromLong(order.sitePosition()).toShortString()
                    + (order.issue().isBlank() ? "" : " — " + order.issue());
            player.sendMessage(Text.literal(line), false);
        }
        if (orders.isEmpty()) {
            player.sendMessage(Text.literal("No work orders have been recorded for this colony."), false);
        }
        return 1;
    }

    private static int showInfo(ServerCommandSource source, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getManageableHall(source, player, pos);
        if (hall == null) return 0;
        player.sendMessage(Text.literal("Colony: " + hall.colonyName()
                + " | owner: " + hall.colonyOwnerName()
                + " | level: " + hall.colonyLevel()
                + " | members: " + hall.colonyMembers().size()), false);
        return 1;
    }

    private static int rename(ServerCommandSource source, BlockPos pos, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        if (hall.renameColony(player, name)) return 1;
        return 0;
    }

    private static int invite(ServerCommandSource source, BlockPos pos, ServerPlayerEntity target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        if (hall.inviteMember(player, target)) return 1;
        player.sendMessage(Text.literal("Only a player with manage_members permission can invite, and the target must not already be a member."), true);
        return 0;
    }

    private static int setPermission(ServerCommandSource source, BlockPos pos, ServerPlayerEntity target,
                                     String actionId, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        if (!hall.hasPermission(player.getUuid(), ColonyPermission.MANAGE_MEMBERS)) {
            player.sendMessage(Text.literal("You need manage_members permission to edit rank actions."), true);
            return 0;
        }
        ColonyPermission permission = ColonyPermission.parse(actionId).orElse(null);
        if (permission == null) {
            player.sendMessage(Text.literal("Unknown action. Use break_blocks, place_blocks, use_blocks, manage_members, build_projects, or assign_workers."), true);
            return 0;
        }
        if (!hall.colonyMembers().contains(target.getUuid())) {
            player.sendMessage(Text.literal("Invite that player before assigning permissions."), true);
            return 0;
        }
        ColonyRank targetRank = hall.colonyRank(target.getUuid());
        boolean changed = hall.setMemberPermission(player, target.getUuid(), permission, enabled);
        player.sendMessage(Text.literal(changed
                ? (enabled ? "Granted " : "Revoked ") + permission.id() + " for rank " + targetRank.id()
                    + " (all members of that rank)."
                : "That action already has the requested state for rank " + targetRank.id() + "."),
                false);
        return 1;
    }

    private static int setMemberRank(ServerCommandSource source, BlockPos pos, ServerPlayerEntity target,
                                     String rankId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        ColonyRank rank = ColonyRank.parse(rankId).orElse(null);
        if (rank == null || rank == ColonyRank.OWNER) {
            player.sendMessage(Text.literal("Rank must be officer, friend, neutral, or hostile; owner is reserved."), true);
            return 0;
        }
        if (!hall.setMemberRank(player, target.getUuid(), rank)) {
            player.sendMessage(Text.literal("You need manage_members permission; target must be a member and not the owner."), true);
            return 0;
        }
        player.sendMessage(Text.literal("Set " + target.getName().getString() + " to rank " + rank.id() + "."), false);
        return 1;
    }

    private static int setRankPermission(ServerCommandSource source, BlockPos pos, String rankId,
                                         String actionId, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        ColonyRank rank = ColonyRank.parse(rankId).orElse(null);
        ColonyPermission permission = ColonyPermission.parse(actionId).orElse(null);
        if (rank == null || rank == ColonyRank.OWNER || permission == null) {
            player.sendMessage(Text.literal("Use rank officer/friend/neutral/hostile and a supported action id."), true);
            return 0;
        }
        boolean changed = hall.setRankPermission(player, rank, permission, enabled);
        if (!changed) {
            player.sendMessage(Text.literal("You need manage_members permission, or the rank action already has that state."), true);
            return 0;
        }
        player.sendMessage(Text.literal((enabled ? "Granted " : "Revoked ") + permission.id()
                + " for rank " + rank.id() + " (all current and future members)."), false);
        return 1;
    }

    private static int revoke(ServerCommandSource source, BlockPos pos, ServerPlayerEntity target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return 0;
        if (hall.revokeMember(player, target.getUuid())) {
            player.sendMessage(Text.literal("Colony access revoked for " + target.getName().getString() + "."), false);
            return 1;
        }
        player.sendMessage(Text.literal("Only a player with manage_members permission can revoke an existing member."), true);
        return 0;
    }

    private static TownHallBlockEntity getManageableHall(ServerCommandSource source, ServerPlayerEntity player, BlockPos pos) {
        TownHallBlockEntity hall = getHall(source, player, pos);
        if (hall == null) return null;
        return hall.canManage(player) ? hall : null;
    }

    private static TownHallBlockEntity getHall(ServerCommandSource source, ServerPlayerEntity player, BlockPos pos) {
        if (!player.getWorld().isInBuildLimit(pos)
                || player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            player.sendMessage(Text.literal("The Town Hall must be within 8 blocks of you."), true);
            return null;
        }
        if (!(player.getWorld().getBlockEntity(pos) instanceof TownHallBlockEntity hall)) {
            player.sendMessage(Text.literal("No Colony Foundations Town Hall at that position."), true);
            return null;
        }
        return hall;
    }
}
