package com.pvpbot.stabshot.themesong;

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
 * /ts play <song>       — play once
 * /ts play <song> on    — loop forever
 * /ts play <song> off   — play once (explicit)
 * /ts stop              — stop playback
 * /ts list              — list available songs
 */
@Environment(EnvType.CLIENT)
public class ThemeSongCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("ts")

                .then(literal("play")
                    // /ts play loop <song name with spaces>
                    .then(literal("loop")
                        .then(argument("song", StringArgumentType.greedyString())
                            .executes(ctx -> execPlay(ctx, true))))
                    // /ts play <song name with spaces>  (no loop)
                    .then(argument("song", StringArgumentType.greedyString())
                        .executes(ctx -> execPlay(ctx, false)))
                )

                .then(literal("stop").executes(ThemeSongCommand::execStop))
                .then(literal("list").executes(ThemeSongCommand::execList))
            )
        );
    }

    private static int execPlay(CommandContext<FabricClientCommandSource> ctx, boolean loop) {
        String song = StringArgumentType.getString(ctx, "song");
        String err  = ThemeSongPlayer.play(song, loop);
        if (err != null) {
            ctx.getSource().sendFeedback(Text.literal("§c✘ " + err));
        } else {
            String loopInfo = loop ? " §7(looping — §f/ts stop §7to end)" : " §7(once)";
            ctx.getSource().sendFeedback(
                Text.literal("§a♪ Now playing: §f" + song + loopInfo));
        }
        return 1;
    }

    private static int execStop(CommandContext<FabricClientCommandSource> ctx) {
        if (!ThemeSongPlayer.isPlaying()) {
            ctx.getSource().sendFeedback(Text.literal("§7No song is currently playing."));
            return 0;
        }
        String  was        = ThemeSongPlayer.getCurrentSong();
        boolean wasLooping = ThemeSongPlayer.isLooping();
        ThemeSongPlayer.stop();
        ctx.getSource().sendFeedback(Text.literal(
            "§7■ Stopped: §f" + was + (wasLooping ? " §7(was looping)" : "")));
        return 1;
    }

    private static int execList(CommandContext<FabricClientCommandSource> ctx) {
        var songs = ThemeSongPlayer.getSongNames();
        if (songs.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal(
                "§7No songs found. Add §f.ogg §7or §f.mp3 §7files to:\n§f"
                + ThemeSongPlayer.getSongsDir()));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§6§lSongs (" + songs.size() + "):"));
        songs.forEach(s -> ctx.getSource().sendFeedback(Text.literal("§7 • §f" + s)));
        ctx.getSource().sendFeedback(Text.literal("§7Usage: §f/ts play <name> §8[on/off]"));
        return 1;
    }
}
