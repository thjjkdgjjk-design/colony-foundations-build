package dev.colonyfoundations.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.colonyfoundations.block.ColonyPermission;
import dev.colonyfoundations.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("colony")
                        .then(literal("info")
                                .then(argument("hall", BlockPosArgumentType.blockPos())
                                        .executes(context -> showInfo(context.getSource(),
                                                BlockPosArgumentType.getBlockPos(context, "hall")))))
                        .then(literal("rename")
                                .then(argument("hall", BlockPosArgumentType.blockPos())
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(context -> rename(context.getSource(),
                                                        BlockPosArgumentType.getBlockPos(context, "hall"),
                                                        StringArgumentType.getString(context, "name"))))))
                        .then(literal("invite")
                                .then(argument("hall", BlockPosArgumentType.blockPos())
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(context -> invite(context.getSource(),
                                                        BlockPosArgumentType.getBlockPos(context, "hall"),
                                                        EntityArgumentType.getPlayer(context, "player"))))))
                        .then(literal("revoke")
                                .then(argument("hall", BlockPosArgumentType.blockPos())
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(context -> revoke(context.getSource(),
                                                        BlockPosArgumentType.getBlockPos(context, "hall"),
                                                        EntityArgumentType.getPlayer(context, "player"))))))
                        .then(literal("permission")
                                .then(argument("hall", BlockPosArgumentType.blockPos())
                                        .then(argument("player", EntityArgumentType.player())
                                                .then(argument("action", StringArgumentType.word())
                                                        .then(argument("enabled", BoolArgumentType.bool())
                                                                .executes(context -> setPermission(context.getSource(),
                                                                        BlockPosArgumentType.getBlockPos(context, "hall"),
                                                                        EntityArgumentType.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "action"),
                                                                        BoolArgumentType.getBool(context, "enabled")))))))));
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
        if (!hall.isOwner(player)) {
            player.sendMessage(Text.literal("Only the colony owner can change permission grants."), true);
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
        hall.setMemberPermission(player, target.getUuid(), permission, enabled);
        player.sendMessage(Text.literal((enabled ? "Granted " : "Revoked ") + permission.id()
                + " for " + target.getName().getString() + "."), false);
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
