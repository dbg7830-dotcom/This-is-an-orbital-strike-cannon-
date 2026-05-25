package com.pvpbot.stabshot.themesong;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ThemeSongPlayer v6 — streams OGG directly from disk via FabricSoundInstance.
 * Anchored to stabshot:dummy which is declared in sounds.json with stream=true.
 * No resource reload needed. Works on PC + Android (Zalith/Pojav).
 *
 * Song format: OGG Vorbis (.ogg), mono recommended.
 * Songs folder: .minecraft/config/stabshot/songs/
 */
@Environment(EnvType.CLIENT)
public class ThemeSongPlayer {

    public static final String SONGS_FOLDER = "stabshot/songs";

    // Matches the entry in src/main/resources/assets/stabshot/sounds.json
    private static final Identifier DUMMY_ID = Identifier.of("stabshot", "dummy");

    private static DiskSoundInstance currentInstance = null;
    private static String            currentSong     = null;
    private static boolean           playing         = false;

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

        final String capturedName = name;
        final Path   capturedFile = oggFile;

        client.execute(() -> {
            try {
                currentInstance = new DiskSoundInstance(capturedFile);
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

        return null;
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    public static void stop() {
        if (currentInstance == null) { playing = false; currentSong = null; return; }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            final DiskSoundInstance inst = currentInstance;
            client.execute(() -> client.getSoundManager().stop(inst));
        }
        currentInstance = null;
        currentSong     = null;
        playing         = false;
    }

    // -------------------------------------------------------------------------
    // Song list
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
    // Sound instance — reads OGG directly from disk, bypasses resource pack
    // -------------------------------------------------------------------------

    @Environment(EnvType.CLIENT)
    static class DiskSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

        private final Path oggPath;

        DiskSoundInstance(Path oggPath) {
            super(SoundEvent.of(DUMMY_ID), SoundCategory.MASTER, Random.create());
            this.oggPath         = oggPath;
            this.volume          = 1.0f;
            this.pitch           = 1.0f;
            this.repeat          = true;
            this.repeatDelay     = 0;
            this.relative        = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader,
                                                              Identifier id,
                                                              boolean repeatInstantly) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new RepeatingAudioStream(
                            OggAudioStream::new,
                            Files.newInputStream(oggPath)
                    );
                } catch (IOException e) {
                    throw new RuntimeException(
                        "StabShot: failed to open OGG: " + oggPath.getFileName(), e);
                }
            });
        }
    }
}
