package com.pvpbot.stabshot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pvpbot.stabshot.item.StabRodItem;
import com.pvpbot.stabshot.StabShotMod;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * StabShotCommand — registers /giveob command.
 *
 * Usage:
 *   /giveob <amount> stab           — gives <amount> uses to command executor
 *   /giveob <amount> stab <player>  — gives to a specific player
 *   /giveob infinite stab           — gives an infinite-use rod
 *
 * Requires op level 2.
 * The rod has custom NBT (StabShotRod=true) so renaming via anvil doesn't work.
 */
public class StabShotCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("giveob")
            .requires(src -> src.hasPermissionLevel(2))

            // /giveob <amount> stab [player]
            .then(argument("amount", IntegerArgumentType.integer(1, 64))
                .then(literal("stab")
                    // give to self
                    .executes(ctx -> execGive(ctx,
                            IntegerArgumentType.getInteger(ctx, "amount"),
                            null))
                    // give to specific player
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> execGive(ctx,
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

    private static int execGive(CommandContext<ServerCommandSource> ctx,
                                 int amount,
                                 ServerPlayerEntity targetArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, amount);
        giveItem(target, rod);

        String who = target.getName().getString();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §f" + amount + "x §6Stab Shot Rod§a to §f" + who + "§a."), false);
        return 1;
    }

    private static int execGiveInfinite(CommandContext<ServerCommandSource> ctx,
                                         ServerPlayerEntity targetArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayerEntity target = resolveTarget(ctx, targetArg);
        if (target == null) return 0;

        ItemStack rod = StabRodItem.createStack(StabShotMod.STAB_ROD, -1);
        giveItem(target, rod);

        String who = target.getName().getString();
        ctx.getSource().sendFeedback(() ->
                Text.literal("§a✔ Gave §a§l∞ §6Stab Shot Rod §a(infinite) to §f" + who + "§a."), false);
        return 1;
    }

    private static ServerPlayerEntity resolveTarget(CommandContext<ServerCommandSource> ctx,
                                                     ServerPlayerEntity explicit)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (explicit != null) return explicit;
        try {
            return ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            ctx.getSource().sendFeedback(() ->
                    Text.literal("§c✘ Run this as a player or specify a target: /giveob <amount> stab <player>"),
                    false);
            return null;
        }
    }

    private static void giveItem(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            // Inventory full — drop at feet
            player.dropItem(stack, false);
        }
    }
}
