package com.pvpbot.stabshot.themesong;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ThemeSongPlayer — client-side WAV audio player with loop support.
 *
 * Songs folder: .minecraft/config/stabshot/songs/
 * Format: WAV files (16-bit PCM recommended for best compatibility).
 * Convert MP3s with: ffmpeg -i song.mp3 song.wav
 *                or: any online MP3→WAV converter.
 *
 * Commands: /ts play <name>   (no .wav extension needed)
 *           /ts stop
 */
@Environment(EnvType.CLIENT)
public class ThemeSongPlayer {

    private static final String SONGS_FOLDER = "stabshot/songs";

    private static Clip     currentClip   = null;
    private static String   currentSong   = null;
    private static boolean  playing       = false;

    // Volume: 0.0f = silent, 1.0f = max. 0.85f feels natural in-game
    private static final float VOLUME = 0.85f;

    // -------------------------------------------------------------------------
    // Play
    // -------------------------------------------------------------------------

    /**
     * Starts playing the named song on loop.
     * @param name Song name without extension (e.g. "mytheme" for mytheme.wav)
     * @return Error message string if failed, null if success.
     */
    public static String play(String name) {
        stop(); // stop any currently playing song first

        Path songsDir = getSongsDir();
        if (!Files.exists(songsDir)) {
            try { Files.createDirectories(songsDir); } catch (Exception ignored) {}
        }

        // Try exact name first, then with .wav appended
        File file = songsDir.resolve(name + ".wav").toFile();
        if (!file.exists()) file = songsDir.resolve(name).toFile();
        if (!file.exists()) {
            return "Song not found: §f" + name + ".wav §cin §f" + songsDir;
        }

        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);

            // Convert to PCM_SIGNED if needed (required for Clip playback)
            AudioFormat baseFormat = audioStream.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            if (!baseFormat.equals(targetFormat)) {
                audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            }

            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);

            // Set volume
            setVolume(currentClip, VOLUME);

            // Loop indefinitely
            currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            currentClip.start();

            currentSong = name;
            playing     = true;
            return null; // success

        } catch (UnsupportedAudioFileException e) {
            return "Unsupported format. Use WAV (PCM). Convert with ffmpeg: ffmpeg -i song.mp3 song.wav";
        } catch (Exception e) {
            return "Playback error: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    public static void stop() {
        if (currentClip != null) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
        currentSong = null;
        playing     = false;
    }

    // -------------------------------------------------------------------------
    // Song list — for tab completion
    // -------------------------------------------------------------------------

    /**
     * Returns a list of song names (without .wav extension) from the songs folder.
     * Used for tab-completion in /ts play.
     */
    public static List<String> getSongNames() {
        List<String> names = new ArrayList<>();
        Path songsDir = getSongsDir();
        if (!Files.exists(songsDir)) return names;
        File[] files = songsDir.toFile().listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".wav"));
        if (files == null) return names;
        for (File f : files) {
            String name = f.getName();
            // Strip .wav extension for display
            names.add(name.substring(0, name.length() - 4));
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static boolean isPlaying()       { return playing; }
    public static String  getCurrentSong()  { return currentSong; }

    private static Path getSongsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SONGS_FOLDER);
    }

    private static void setVolume(Clip clip, float volume) {
        try {
            FloatControl gainControl =
                    (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            // Convert linear 0.0-1.0 to decibels
            float dB = (float) (Math.log10(Math.max(volume, 0.0001f)) * 20.0);
            // Clamp to valid range
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(dB);
        } catch (Exception ignored) {
            // Some audio lines don't support gain control — play at default volume
        }
    }
                                            }
