package com.pvpbot.stabshot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pvpbot.stabshot.StabShotMod;
import com.pvpbot.stabshot.item.StabRodItem;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * StabShotCommand — /giveob command.
 *
 * /giveob <amount> stab           — gives <amount> separate rods (1 use each) to self
 * /giveob <amount> stab <player>  — same but to a specific player
 * /giveob infinite stab           — gives 1 infinite-use rod to self
 * /giveob infinite stab <player>  — same but to a specific player
 *
 * Each finite rod fires ONCE then disappears from inventory.
 * The infinite rod never disappears.
 */
public class StabShotCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("giveob")
            .requires(src -> src.hasPermissionLevel(2))

            // /giveob <amount> stab [player]
            .then(argument("amount", IntegerArgumentType.integer(1, 64))
                .then(literal("stab")
                    .executes(ctx -> execGiveFinite(ctx,
                            IntegerArgumentType.getInteger(ctx, "amount"), null))
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> execGiveFinite(ctx,
                                IntegerArgumentType.getInteger(ctx, "amount"),
                                EntityArgumentType.getPlayer(ctx, "player"))))))

            // /giveob infinite stab [player]
            .then(literal("infinite")
                .then(literal("stab")
                    .executes(ctx -> execGiveInfinite(ctx, null))
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> execGiveInfinite(ctx,
                                EntityArgumentType.getPlayer(ctx, "player"))))))
        );
    }

    /**
     * Gives `amount` separate stab rods, each with 1 use.
     * /giveob 3 stab → 3 rods in inventory, each fires once then disappears.
     */
    private static int execGiveFinite(CommandContext<ServerCommandSource> ctx,
                                       int amount,
                                       ServerPlayerEntity targetArg)
            throws CommandSyntaxException {

        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        // Give `amount` separate stacks, each with UsesLeft = -1... 
        // wait — we want each rod to have 1 use, not `amount` uses on one rod.
        // So we create `amount` individual ItemStacks with UsesLeft = 1.
        int given = 0;
        for (int i = 0; i < amount; i++) {
            ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, 1);
            if (!target.getInventory().insertStack(rod)) {
                target.dropItem(rod, false); // inventory full — drop at feet
            }
            given++;
        }

        final int finalGiven = given;
        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §f" + finalGiven + "x §6Stab Shot Rod §7(1 use each) to §f"
                        + target.getName().getString() + "§a."), false);
        return 1;
    }

    /**
     * Gives 1 infinite-use stab rod.
     */
    private static int execGiveInfinite(CommandContext<ServerCommandSource> ctx,
                                          ServerPlayerEntity targetArg)
            throws CommandSyntaxException {

        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, -1);
        if (!target.getInventory().insertStack(rod)) {
            target.dropItem(rod, false);
        }

        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §a§l∞ §6Stab Shot Rod §a(infinite) to §f"
                        + target.getName().getString() + "§a."), false);
        return 1;
    }

    private static ServerPlayerEntity resolveTarget(CommandContext<ServerCommandSource> ctx,
                                                     ServerPlayerEntity explicit)
            throws CommandSyntaxException {
        if (explicit != null) return explicit;
        try {
            return ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§c✘ Run as a player or specify target: /giveob <amount> stab <player>"), false);
            return null;
        }
    }
}
