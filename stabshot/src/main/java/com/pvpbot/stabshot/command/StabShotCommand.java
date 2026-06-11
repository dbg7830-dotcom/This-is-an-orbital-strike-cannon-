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

public class StabShotCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(buildConfigRoot("stabshot"));
        dispatcher.register(buildConfigRoot("pb"));

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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildConfigRoot(String name) {
        return literal(name)
            .requires(src -> src.hasPermissionLevel(2))
            .then(literal("mode")
                .then(literal("wemmbu").executes(ctx -> execSetMode(ctx, StabConfig.MODE_WEMMBU)))
                .then(literal("legacy").executes(ctx -> execSetMode(ctx, StabConfig.MODE_LEGACY))))
            .then(literal("set")
                .then(literal("power")
                    .then(argument("value", FloatArgumentType.floatArg(0.0f))
                        .executes(ctx -> execSetPower(ctx,
                                FloatArgumentType.getFloat(ctx, "value")))))
                .then(literal("radius")
                    .then(argument("value", FloatArgumentType.floatArg(0.0f))
                        .executes(ctx -> execSetRadius(ctx,
                                FloatArgumentType.getFloat(ctx, "value")))))
                .then(literal("wemmburadius")
                    .then(argument("value", FloatArgumentType.floatArg(0.0f))
                        .executes(ctx -> execSetWemmbuRadius(ctx,
                                FloatArgumentType.getFloat(ctx, "value")))))
                .then(literal("startabove")
                    .then(argument("value", IntegerArgumentType.integer(0))
                        .executes(ctx -> execSetStartAbove(ctx,
                                IntegerArgumentType.getInteger(ctx, "value")))))
                .then(literal("depth")
                    .then(argument("value", IntegerArgumentType.integer(1))
                        .executes(ctx -> execSetDepth(ctx,
                                IntegerArgumentType.getInteger(ctx, "value")))))
                .then(literal("terrain")
                    .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> execSetTerrain(ctx,
                                BoolArgumentType.getBool(ctx, "value")))))
                .then(literal("ledgechance")
                    .then(argument("value", FloatArgumentType.floatArg(0.0f))
                        .executes(ctx -> execSetLedgeChance(ctx,
                                FloatArgumentType.getFloat(ctx, "value")))))
                .then(literal("delay")
                    .then(argument("value", IntegerArgumentType.integer(0))
                        .executes(ctx -> execSetDelay(ctx,
                                IntegerArgumentType.getInteger(ctx, "value"))))))
            .then(literal("reload").executes(StabShotCommand::execReload))
            .then(literal("info").executes(StabShotCommand::execInfo));
    }

    private static int execSetMode(CommandContext<ServerCommandSource> ctx, String mode) {
        StabConfig.mode = StabConfig.normalizeMode(mode);
        StabConfig.save();
        ctx.getSource().sendFeedback(() -> Text.literal("§6[StabShot] §7mode → §f" + StabConfig.mode), false);
        return 1;
    }

    private static int execSetPower(CommandContext<ServerCommandSource> ctx, float value) {
        StabConfig.explosionPower = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7custom_damage_power → §f" + value), false);
        return 1;
    }

    private static int execSetWemmbuRadius(CommandContext<ServerCommandSource> ctx, float value) {
        StabConfig.wemmbuRadius = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7wemmbu_radius → §f" + value), false);
        return 1;
    }

    private static int execSetRadius(CommandContext<ServerCommandSource> ctx, float value) {
        StabConfig.strikeRadius = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7strike_radius → §f" + value), false);
        return 1;
    }

    private static int execSetStartAbove(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.columnStartAbove = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7column_start_above → §f" + value
                        + " §7(legacy mode)"), false);
        return 1;
    }

    private static int execSetDepth(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.blastDepth = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7blast_depth → §f" + value
                        + " §7(legacy mode only)"), false);
        return 1;
    }

    private static int execSetTerrain(CommandContext<ServerCommandSource> ctx, boolean value) {
        StabConfig.destroyTerrain = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7destroy_terrain → §f" + value
                        + (value ? " §c(custom terrain carving enabled)" : " §a(entity damage only)")),
                false);
        return 1;
    }

    private static int execSetLedgeChance(CommandContext<ServerCommandSource> ctx, float value) {
        StabConfig.ledgeBlockChance = value;
        StabConfig.save();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7ledge_block_chance → §f" + value
                        + " §7(" + (int)(value * 100) + "% wall blocks kept as protrusions)"),
                false);
        return 1;
    }

    private static int execSetDelay(CommandContext<ServerCommandSource> ctx, int value) {
        StabConfig.fireDelayTicks = value;
        StabConfig.save();
        double seconds = value / 20.0;
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §7fire_delay_ticks → §f" + value
                        + " §7(" + String.format("%.2f", seconds) + "s)"), false);
        return 1;
    }

    private static int execReload(CommandContext<ServerCommandSource> ctx) {
        StabConfig.load();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§6[StabShot] §aConfig reloaded from disk."), false);
        execInfo(ctx);
        return 1;
    }

    private static int execInfo(CommandContext<ServerCommandSource> ctx) {
        double delaySeconds = StabConfig.fireDelayTicks / 20.0;
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§6§l── Vulgar's OSC Config ──\n"
                + "§7mode:                §f" + StabConfig.mode + "\n"
                + "§7custom_damage_power: §f" + StabConfig.explosionPower + "\n"
                + "§7strike_radius:       §f" + StabConfig.strikeRadius + "\n"
                + "§7wemmbu_radius:       §f" + StabConfig.wemmbuRadius + "\n"
                + "§7column_start_above:  §f" + StabConfig.columnStartAbove + " §7(legacy)\n"
                + "§7blast_depth:         §f" + StabConfig.blastDepth + " §7(legacy only)\n"
                + "§7destroy_terrain:     §f" + StabConfig.destroyTerrain + "\n"
                + "§7ledge_block_chance:  §f" + StabConfig.ledgeBlockChance + " §7(" + (int)(StabConfig.ledgeBlockChance * 100) + "% wall protrusions)\n"
                + "§7fire_delay_ticks:    §f" + StabConfig.fireDelayTicks
                        + " §7(" + String.format("%.2f", delaySeconds) + "s)"
        ), false);
        return 1;
    }

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
