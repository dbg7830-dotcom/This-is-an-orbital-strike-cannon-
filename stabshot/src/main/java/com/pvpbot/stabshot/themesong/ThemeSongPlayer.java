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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class ThemeSongPlayer {

    public static final String SONGS_FOLDER = "stabshot/songs";

    // Must match the entry in assets/stabshot/sounds.json
    private static final Identifier  DUMMY_ID          = Identifier.of("stabshot", "dummy");
    private static final SoundEvent  DUMMY_SOUND_EVENT = SoundEvent.of(DUMMY_ID);

    private static DiskSoundInstance currentInstance = null;
    private static String            currentSong     = null;
    private static boolean           playing         = false;

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
                DiskSoundInstance inst = new DiskSoundInstance(capturedFile);
                client.getSoundManager().play(inst);
                currentInstance = inst;
                currentSong     = capturedName;
                playing         = true;
            } catch (Exception e) {
                playing = false;
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal(
                            "§c[StabShot] Play error: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage()),
                        false);
                }
                e.printStackTrace();
            }
        });

        return null;
    }

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

    @Environment(EnvType.CLIENT)
    static class DiskSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

        private final Path oggPath;

        DiskSoundInstance(Path oggPath) {
            super(DUMMY_SOUND_EVENT, SoundCategory.MASTER, Random.create());
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
            try {
                InputStream in = Files.newInputStream(oggPath);
                return CompletableFuture.completedFuture(new OggAudioStream(in));
            } catch (IOException e) {
                return CompletableFuture.failedFuture(
                    new RuntimeException(
                        "StabShot: failed to open OGG: " + oggPath
                        + " — " + e.getMessage(), e));
            }
        }
    }
}
