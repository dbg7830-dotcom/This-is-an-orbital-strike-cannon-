package com.pvpbot.stabshot.themesong;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * ThemeSongCommand — registers /ts play <name> and /ts stop.
 *
 * CLIENT-SIDE ONLY. Uses Fabric's ClientCommandRegistrationCallback.
 * Works in singleplayer and on any server without the mod installed server-side.
 * Tab-completes song names from .minecraft/config/stabshot/songs/*.wav
 */
@Environment(EnvType.CLIENT)
public class ThemeSongCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("ts")
                .then(literal("play")
                    .then(argument("song", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            ThemeSongPlayer.getSongNames().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ThemeSongCommand::execPlay)))
                .then(literal("stop")
                    .executes(ThemeSongCommand::execStop))
                .then(literal("list")
                    .executes(ThemeSongCommand::execList))
            );
        });
    }

    private static int execPlay(CommandContext<FabricClientCommandSource> ctx) {
        String song = StringArgumentType.getString(ctx, "song");
        String err  = ThemeSongPlayer.play(song);
        if (err != null) {
            ctx.getSource().sendFeedback(Text.literal("§c✘ " + err));
        } else {
            ctx.getSource().sendFeedback(
                    Text.literal("§a♪ Now playing: §f" + song + " §7(looping)"));
        }
        return 1;
    }

    private static int execStop(CommandContext<FabricClientCommandSource> ctx) {
        if (!ThemeSongPlayer.isPlaying()) {
            ctx.getSource().sendFeedback(Text.literal("§7No song is currently playing."));
            return 0;
        }
        String was = ThemeSongPlayer.getCurrentSong();
        ThemeSongPlayer.stop();
        ctx.getSource().sendFeedback(Text.literal("§7■ Stopped: §f" + was));
        return 1;
    }

    private static int execList(CommandContext<FabricClientCommandSource> ctx) {
        var songs = ThemeSongPlayer.getSongNames();
        if (songs.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal(
                    "§7No songs found. Add .wav files to §f.minecraft/config/stabshot/songs/"));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§6§lSongs (" + songs.size() + "):"));
        songs.forEach(s -> ctx.getSource().sendFeedback(Text.literal("§7 • §f" + s)));
        ctx.getSource().sendFeedback(Text.literal(
                "§7Usage: §f/ts play <name>"));
        return 1;
    }
}
