package com.pvpbot.stabshot.themesong;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ThemeSongPlayer v4 — registers the OGG into the resource pack and
 * performs a full resource reload before playing.
 *
 * Android-safe: waits for the reload CompletableFuture to complete before
 * calling play(), so the SoundManager actually has the sound definition loaded.
 *
 * Song format: OGG Vorbis (.ogg)
 * Songs folder: .minecraft/config/stabshot/songs/
 */
@Environment(EnvType.CLIENT)
public class ThemeSongPlayer {

    public static final String SONGS_FOLDER = "stabshot/songs";

    private static LoopingSoundInstance currentInstance = null;
    private static String               currentSong     = null;
    private static boolean              playing         = false;

    // -------------------------------------------------------------------------
    // Play
    // -------------------------------------------------------------------------

    public static String play(String name) {
        stop();

        Path songsDir = getSongsDir();
        if (!Files.exists(songsDir)) {
            try { Files.createDirectories(songsDir); }
            catch (Exception e) { return "Could not create songs folder: " + e.getMessage(); }
        }

        Path oggFile = songsDir.resolve(name + ".ogg");
        if (!Files.exists(oggFile)) {
            return "§cSong not found: §f" + name + ".ogg\n"
                 + "§7Put .ogg files in: §f" + songsDir + "\n"
                 + "§7Available: §f" + String.join(", ", getSongNames());
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return "Client not ready.";

        String safeName = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        Identifier soundId = Identifier.of("stabshot", "song/" + safeName);

        // Register the OGG with our dynamic resource pack
        SongResourcePack.registerSong(soundId, oggFile);

        // Capture for lambda
        final String capturedName = name;

        client.execute(() -> {
            // reloadResources returns a CompletableFuture — chain play() onto it
            // so we only play once the reload is fully done and the sound is registered.
            client.reloadResources().thenRunAsync(() -> {
                client.execute(() -> {
                    try {
                        currentInstance = new LoopingSoundInstance(soundId);
                        client.getSoundManager().play(currentInstance);
                        currentSong = capturedName;
                        playing     = true;
                    } catch (Exception e) {
                        if (client.player != null) {
                            client.player.sendMessage(
                                net.minecraft.text.Text.literal("§c[StabShot] Play error: " + e.getMessage()),
                                false);
                        }
                    }
                });
            }).exceptionally(ex -> {
                if (client.player != null) {
                    client.execute(() -> client.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[StabShot] Reload error: " + ex.getMessage()),
                        false));
                }
                return null;
            });
        });

        return null; // success (async)
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    public static void stop() {
        if (currentInstance == null) { playing = false; currentSong = null; return; }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            final LoopingSoundInstance inst = currentInstance;
            client.execute(() -> client.getSoundManager().stop(inst));
        }
        currentInstance = null;
        currentSong     = null;
        playing         = false;
    }

    // -------------------------------------------------------------------------
    // Song list (for tab-complete)
    // -------------------------------------------------------------------------

    public static List<String> getSongNames() {
        List<String> names = new ArrayList<>();
        Path dir = getSongsDir();
        if (!Files.exists(dir)) return names;
        File[] files = dir.toFile().listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".ogg"));
        if (files == null) return names;
        for (File f : files) {
            String n = f.getName();
            names.add(n.substring(0, n.length() - 4));
        }
        Collections.sort(names);
        return names;
    }

    public static boolean isPlaying()      { return playing; }
    public static String  getCurrentSong() { return currentSong; }

    public static Path getSongsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SONGS_FOLDER);
    }

    // -------------------------------------------------------------------------
    // Looping sound instance — NONE attenuation = full volume everywhere
    // -------------------------------------------------------------------------

    @Environment(EnvType.CLIENT)
    static class LoopingSoundInstance extends AbstractSoundInstance {
        LoopingSoundInstance(Identifier id) {
            super(SoundEvent.of(id), SoundCategory.MASTER, Random.create());
            this.volume          = 1.0f;
            this.pitch           = 1.0f;
            this.repeat          = true;
            this.repeatDelay     = 0;
            this.relative        = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }
    }
}
