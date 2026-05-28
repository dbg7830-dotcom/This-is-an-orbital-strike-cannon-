package com.pvpbot.stabshot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pvpbot.stabshot.StabShotMod;
import com.pvpbot.stabshot.config.StabConfig;
import com.pvpbot.stabshot.item.StabRodItem;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * StabShotCommand — all /stabshot and /giveob commands.
 *
 * /stabshot mode orbital|legacy    — switch strike mode
 * /stabshot set power <float>      — explosion power
 * /stabshot set radius <int>       — grid radius
 * /stabshot set startabove <int>   — blocks above aimed surface
 * /stabshot set spawnheight <int>  — ORBITAL: TNT spawn height
 * /stabshot set terrain <bool>     — block destruction on/off
 * /stabshot reload                 — reload config from file
 * /stabshot info                   — show current config
 *
 * /giveob <amount> stab [player]   — give finite stab rods (1 use each)
 * /giveob infinite stab [player]   — give infinite stab rod
 */
public class StabShotCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // /stabshot — config and mode commands
        dispatcher.register(literal("stabshot")
            .requires(src -> src.hasPermissionLevel(2))

            // Mode switch
            .then(literal("mode")
                .then(literal("orbital").executes(ctx -> execMode(ctx, false)))
                .then(literal("legacy") .executes(ctx -> execMode(ctx, true))))

            // Set individual config values
            .then(literal("set")
                .then(literal("power")
                    .then(argument("value", FloatArgumentType.floatArg(0.1f, 20.0f))
                        .executes(ctx -> execSetPower(ctx,
                                FloatArgumentType.getFloat(ctx, "value")))))
                .then(literal("radius")
                    .then(argument("value", IntegerArgumentType.integer(0, 5))
                        .executes(ctx -> execSetRadius(ctx,
                                IntegerArgumentType.getInteger(ctx, "value")))))
                .then(literal("startabove")
                    .then(argument("value", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> execSetStartAbove(ctx,
                                IntegerArgumentType.getInteger(ctx, "value")))))
                .then(literal("spawnheight")
                    .then(argument("value", IntegerArgumentType.integer(5, 100))
                        .executes(ctx -> execSetSpawnHeight(ctx,
                                IntegerArgumentType.getInteger(ctx, "value")))))
                .then(literal("terrain")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> execSetTerrain(ctx,
                                BoolArgumentType.getBool(ctx, "value"))))))

            // Reload and info
            .then(literal("reload").executes(StabShotCommand::execReload))
            .then(literal("info")  .executes(StabShotCommand::execInfo))
        );

        // /giveob
        dispatcher.register(literal("giveob")
            .requires(src -> src.hasPermissionLevel(2))
            .then(argument("amount", IntegerArgumentType.integer(1, 64))
                .then(literal("stab")
                    .executes(ctx -> execGiveFinite(ctx,
                            IntegerArgumentType.getInteger(ctx, "amount"), null))
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> execGiveFinite(ctx,
                                IntegerArgumentType.getInteger(ctx, "amount"),
                                EntityArgumentType.getPlayer(ctx, "player"))))))
            .then(literal("infinite")
                .then(literal("stab")
                    .executes(ctx -> execGiveInfinite(ctx, null))
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> execGiveInfinite(ctx,
                                EntityArgumentType.getPlayer(ctx, "player"))))))
        );
    }

    // =========================================================================
    // Mode
    // =========================================================================

    private static int execMode(CommandContext<ServerCommandSource> ctx, boolean legacy) {
        StabConfig.useLegacyMode = legacy;
        StabConfig.save();
        String mode = legacy ? "§eLEGACY §7(instant explosion)" : "§aORBITAL §7(falling TNT)";
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7Mode → " + mode), false);
        return 1;
    }

    // =========================================================================
    // Set commands — each updates the field and saves to disk immediately
    // =========================================================================

    private static int execSetPower(CommandContext<ServerCommandSource> ctx, float value) {
        StabConfig.explosionPower = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7explosion_power → §f" + value), false);
        return 1;
    }

    private static int execSetRadius(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.strikeRadius = value;
        StabConfig.save();
        int total = (value * 2 + 1) * (value * 2 + 1);
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7strike_radius → §f" + value
                        + " §7(" + total + " blasts)"), false);
        return 1;
    }

    private static int execSetStartAbove(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.columnStartAbove = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7column_start_above → §f" + value), false);
        return 1;
    }

    private static int execSetSpawnHeight(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.spawnHeight = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7spawn_height → §f" + value
                        + " §7(ORBITAL mode only)"), false);
        return 1;
    }

    private static int execSetTerrain(CommandContext<ServerCommandSource> ctx, boolean value) {
        StabConfig.destroyTerrain = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7destroy_terrain → §f" + value
                        + (value ? " §c(blocks will be destroyed)" : " §a(entity damage only)")),
                false);
        return 1;
    }

    // =========================================================================
    // Reload + Info
    // =========================================================================

    private static int execReload(CommandContext<ServerCommandSource> ctx) {
        StabConfig.load();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §aConfig reloaded from disk."), false);
        execInfo(ctx);
        return 1;
    }

    private static int execInfo(CommandContext<ServerCommandSource> ctx) {
        String mode = StabConfig.useLegacyMode ? "§eLEGACY" : "§aORBITAL";
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§6§l── StabShot Config ──\n"
                + "§7mode:           " + mode + "\n"
                + "§7explosion_power:    §f" + StabConfig.explosionPower + "\n"
                + "§7strike_radius:      §f" + StabConfig.strikeRadius
                        + " §7(" + (StabConfig.strikeRadius*2+1)*(StabConfig.strikeRadius*2+1) + " blasts)\n"
                + "§7column_start_above: §f" + StabConfig.columnStartAbove + "\n"
                + "§7spawn_height:       §f" + StabConfig.spawnHeight + " §7(orbital only)\n"
                + "§7destroy_terrain:    §f" + StabConfig.destroyTerrain
        ), false);
        return 1;
    }

    // =========================================================================
    // /giveob
    // =========================================================================

    private static int execGiveFinite(CommandContext<ServerCommandSource> ctx,
                                       int amount, ServerPlayerEntity targetArg)
            throws CommandSyntaxException {
        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        int given = 0;
        for (int i = 0; i < amount; i++) {
            ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, 1);
            if (!target.getInventory().insertStack(rod)) target.dropItem(rod, false);
            given++;
        }
        final int finalGiven = given;
        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §f" + finalGiven
                        + "x §6Stab Shot Rod §7(1 use each) to §f"
                        + target.getName().getString()), false);
        return 1;
    }

    private static int execGiveInfinite(CommandContext<ServerCommandSource> ctx,
                                          ServerPlayerEntity targetArg)
            throws CommandSyntaxException {
        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, -1);
        if (!target.getInventory().insertStack(rod)) target.dropItem(rod, false);

        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §a§l∞ §6Stab Shot Rod §7to §f"
                        + target.getName().getString()), false);
        return 1;
    }

    private static ServerPlayerEntity resolveTarget(CommandContext<ServerCommandSource> ctx,
                                                     ServerPlayerEntity explicit)
            throws CommandSyntaxException {
        if (explicit != null) return explicit;
        try { return ctx.getSource().getPlayerOrThrow(); }
        catch (Exception e) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§c✘ Run as a player or specify: /giveob <amount> stab <player>"), false);
            return null;
        }
    }
}
