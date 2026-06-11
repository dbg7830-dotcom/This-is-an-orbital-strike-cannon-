package com.pvpbot.stabshot.themesong;

import javazoom.jl.decoder.*;
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

import javax.sound.sampled.AudioFormat;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class ThemeSongPlayer {

    public static final String SONGS_FOLDER = "stabshot/songs";

    private static final Identifier DUMMY_ID          = Identifier.of("stabshot", "dummy");
    private static final SoundEvent DUMMY_SOUND_EVENT = SoundEvent.of(DUMMY_ID);

    private static DiskSoundInstance currentInstance = null;
    private static String            currentSong     = null;
    private static boolean           playing         = false;
    private static boolean           looping         = false;

    private static Thread        loopThread = null;
    private static AtomicBoolean loopActive = new AtomicBoolean(false);

    public static String play(String name, boolean loop) {
        stop();

        Path songsDir = getSongsDir();
        if (!Files.exists(songsDir)) {
            try { Files.createDirectories(songsDir); }
            catch (Exception e) { return "Could not create songs folder: " + e.getMessage(); }
        }

        Path   file = null;
        String ext  = null;
        for (String e : new String[]{"ogg", "mp3"}) {
            Path candidate = songsDir.resolve(name + "." + e);
            if (Files.exists(candidate)) { file = candidate; ext = e; break; }
        }

        if (file == null) {
            return "§cSong not found: §f" + name + ".ogg §7or §f" + name + ".mp3\n"
                 + "§7Put audio files in: §f" + songsDir + "\n"
                 + "§7Available: §f" + String.join(", ", getSongNames());
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return "Client not ready.";

        final Path   fFile = file;
        final String fExt  = ext;

        currentSong = name;
        playing     = true;
        looping     = loop;

        if (loop) {
            loopActive.set(true);
            loopThread = new Thread(() -> {
                while (loopActive.get()) {
                    long durationMs = estimateDurationMs(fFile, fExt);
                    if (durationMs <= 0) durationMs = 3000;

                    DiskSoundInstance inst = new DiskSoundInstance(fFile, fExt);
                    client.execute(() -> {
                        synchronized (ThemeSongPlayer.class) {
                            currentInstance = inst;
                        }
                        client.getSoundManager().play(inst);
                    });

                    try {
                        Thread.sleep(durationMs);
                    } catch (InterruptedException e) {
                        break;
                    }

                    client.execute(() -> {
                        synchronized (ThemeSongPlayer.class) {
                            if (currentInstance != null) {
                                client.getSoundManager().stop(currentInstance);
                            }
                        }
                    });
                }
            }, "StabShot-LoopThread");
            loopThread.setDaemon(true);
            loopThread.start();
        } else {
            DiskSoundInstance inst = new DiskSoundInstance(fFile, fExt);
            client.execute(() -> {
                try {
                    synchronized (ThemeSongPlayer.class) {
                        currentInstance = inst;
                    }
                    client.getSoundManager().play(inst);
                } catch (Exception e) {
                    playing = false;
                    if (client.player != null) {
                        client.player.sendMessage(
                            net.minecraft.text.Text.literal(
                                "§c[StabShot] Play error: " + e.getMessage()), false);
                    }
                }
            });
        }

        return null;
    }

    private static long estimateDurationMs(Path file, String ext) {
        try {
            if (ext.equals("mp3")) {
                long totalMs = 0;
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    Bitstream bs = new Bitstream(in);
                    Header h;
                    while ((h = bs.readFrame()) != null) {
                        totalMs += (long) h.msPerFrame();
                        bs.closeFrame();
                    }
                    bs.close();
                }
                return totalMs;
            } else {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    OggAudioStream ogg = new OggAudioStream(in);
                    AudioFormat fmt = ogg.getFormat();
                    int sr = (int) fmt.getSampleRate();
                    int ch = fmt.getChannels();
                    long totalBytes = 0;
                    ByteBuffer buf;
                    while ((buf = ogg.read(8192)) != null && buf.remaining() > 0) {
                        totalBytes += buf.remaining();
                    }
                    ogg.close();
                    if (sr <= 0 || ch <= 0) return 0;
                    return (totalBytes * 1000L) / (sr * ch * 2L);
                }
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static void stop() {
        loopActive.set(false);
        if (loopThread != null) {
            loopThread.interrupt();
            loopThread = null;
        }
        if (currentInstance != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                final DiskSoundInstance inst = currentInstance;
                client.execute(() -> client.getSoundManager().stop(inst));
            }
            currentInstance = null;
        }
        currentSong = null;
        playing     = false;
        looping     = false;
    }

    public static List<String> getSongNames() {
        List<String> names = new ArrayList<>();
        Path dir = getSongsDir();
        if (!Files.exists(dir)) return names;
        File[] files = dir.toFile().listFiles(f -> {
            if (!f.isFile()) return false;
            String n = f.getName().toLowerCase();
            return n.endsWith(".ogg") || n.endsWith(".mp3");
        });
        if (files == null) return names;
        for (File f : files) {
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            names.add(dot > 0 ? n.substring(0, dot) : n);
        }
        Collections.sort(names);
        return names;
    }

    public static boolean isPlaying()      { return playing; }
    public static boolean isLooping()      { return looping; }
    public static String  getCurrentSong() { return currentSong; }

    public static Path getSongsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SONGS_FOLDER);
    }

    @Environment(EnvType.CLIENT)
    static class DiskSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

        private final Path   filePath;
        private final String ext;

        DiskSoundInstance(Path filePath, String ext) {
            super(DUMMY_SOUND_EVENT, SoundCategory.MASTER, Random.create());
            this.filePath        = filePath;
            this.ext             = ext;
            this.volume          = 1.0f;
            this.pitch           = 1.0f;
            this.repeat          = false;
            this.repeatDelay     = 0;
            this.relative        = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader,
                                                              Identifier id,
                                                              boolean repeatInstantly) {
            try {
                InputStream in = new BufferedInputStream(Files.newInputStream(filePath));
                AudioStream stream = ext.equals("mp3")
                        ? new Mp3AudioStream(in)
                        : new OggAudioStream(in);
                return CompletableFuture.completedFuture(stream);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(
                    new RuntimeException(
                        "StabShot: can't open audio: " + filePath + " — " + e.getMessage(), e));
            }
        }
    }

    @Environment(EnvType.CLIENT)
    static class Mp3AudioStream implements AudioStream {

        private final Bitstream bitstream;
        private final Decoder   decoder;

        private byte[] overflowBytes = new byte[0];
        private int    overflowPos   = 0;

        private int     sampleRate = 44100;
        private int     channels   = 2;
        private boolean headerRead = false;

        Mp3AudioStream(InputStream in) {
            this.bitstream = new Bitstream(in);
            this.decoder   = new Decoder();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            while ((overflowBytes.length - overflowPos) < size) {
                if (!decodeNextFrame()) break;
            }
            int available = overflowBytes.length - overflowPos;
            if (available <= 0) return ByteBuffer.allocateDirect(0);
            int toReturn = Math.min(size, available);
            ByteBuffer buf = ByteBuffer.allocateDirect(toReturn);
            buf.put(overflowBytes, overflowPos, toReturn);
            overflowPos += toReturn;
            buf.flip();
            return buf;
        }

        private boolean decodeNextFrame() throws IOException {
            try {
                Header header = bitstream.readFrame();
                if (header == null) return false;
                if (!headerRead) {
                    sampleRate = header.frequency();
                    channels   = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                    headerRead = true;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                short[] samples = output.getBuffer();
                int     count   = output.getBufferLength();
                int remaining = overflowBytes.length - overflowPos;
                byte[] newBuf = new byte[remaining + count * 2];
                if (remaining > 0) System.arraycopy(overflowBytes, overflowPos, newBuf, 0, remaining);
                int off = remaining;
                for (int i = 0; i < count; i++) {
                    short s = samples[i];
                    newBuf[off++] = (byte)(s & 0xFF);
                    newBuf[off++] = (byte)((s >> 8) & 0xFF);
                }
                overflowBytes = newBuf;
                overflowPos   = 0;
                return true;
            } catch (BitstreamException e) {
                if (e.getErrorCode() == BitstreamErrors.STREAM_EOF) return false;
                throw new IOException("MP3 bitstream error: " + e.getMessage(), e);
            } catch (DecoderException e) {
                throw new IOException("MP3 decoder error: " + e.getMessage(), e);
            }
        }

        @Override
        public AudioFormat getFormat() {
            int frameSize = channels * 2;
            return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, channels, frameSize, sampleRate, false
            );
        }

        @Override
        public void close() throws IOException {
            try { bitstream.close(); } catch (BitstreamException ignored) {}
        }
    }
}
