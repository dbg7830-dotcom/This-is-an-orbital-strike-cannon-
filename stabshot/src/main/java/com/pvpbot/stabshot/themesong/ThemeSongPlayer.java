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
 * ThemeSongPlayer v3 — uses Minecraft's SoundManager (OpenAL/LWJGL).
 *
 * Works on ALL launchers including Android-based (Zalith, Pojav) because
 * it uses Minecraft's own audio stack, not javax.sound.sampled.
 *
 * Song format: OGG Vorbis (.ogg)
 * Convert:     ffmpeg -i song.mp3 -c:a libvorbis -q:a 4 song.ogg
 *              or any online MP3→OGG converter.
 *
 * Songs folder: .minecraft/config/stabshot/songs/  (auto-created on first /ts command)
 *
 * Flow:
 *  1. /ts play <name> → registers OGG file path in SongResourcePack
 *  2. Triggers SoundManager.reloadSounds() so it picks up the new sounds.json entry
 *  3. Plays a looping AbstractSoundInstance with NONE attenuation (full volume everywhere)
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

        // Auto-create folder
        Path songsDir = getSongsDir();
        if (!Files.exists(songsDir)) {
            try { Files.createDirectories(songsDir); }
            catch (Exception e) { return "Could not create songs folder: " + e.getMessage(); }
        }

        // Find .ogg file
        Path oggFile = songsDir.resolve(name + ".ogg");
        if (!Files.exists(oggFile)) {
            return "§cSong not found: §f" + name + ".ogg\n"
                 + "§7Put .ogg files in: §f" + songsDir + "\n"
                 + "§7Available: §f" + String.join(", ", getSongNames());
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return "Client not ready.";

        // Build a stable sound ID from the song name
        String safeName = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        Identifier soundId = Identifier.of("stabshot", "song/" + safeName);

        // Register the OGG file in our resource pack so SoundManager can find it
        SongResourcePack.registerSong(soundId, oggFile);

        // Reload sound definitions only (lightweight — doesn't freeze game)
        client.execute(() -> {
            try {
                client.getSoundManager().reloadSounds();

                // Play after a 1-tick delay to let the reload complete
                client.execute(() -> {
                    currentInstance = new LoopingSoundInstance(soundId);
                    client.getSoundManager().play(currentInstance);
                    currentSong = name;
                    playing     = true;
                });
            } catch (Exception e) {
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[StabShot] Audio error: " + e.getMessage()),
                        false);
                }
            }
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
            this.relative        = true; // relative to listener = no distance fade
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }
    }
}
