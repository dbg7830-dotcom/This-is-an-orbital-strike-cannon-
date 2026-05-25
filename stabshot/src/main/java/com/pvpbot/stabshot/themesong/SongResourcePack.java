package com.pvpbot.stabshot.themesong;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SongResourcePack — a lightweight ResourcePack that serves OGG files and
 * a dynamic sounds.json to Minecraft's SoundManager at reload time.
 *
 * Registered once at startup via StabShotClient. Songs are added at runtime
 * via registerSong() without needing another pack reload.
 */
@Environment(EnvType.CLIENT)
public class SongResourcePack implements ResourcePack {

    /** soundId.path → OGG file path */
    private static final Map<String, Path> SONGS = new ConcurrentHashMap<>();

    /** The single global instance registered with Minecraft. */
    public static final SongResourcePack INSTANCE = new SongResourcePack();

    private static final ResourcePackInfo INFO = new ResourcePackInfo(
            "stabshot_songs",
            false,
            ResourcePackSource.BUILTIN,
            java.util.Optional.empty()
    );

    private SongResourcePack() {}

    /** Call this before playing a song to make it available to the sound system. */
    public static void registerSong(Identifier soundId, Path oggFile) {
        SONGS.put(soundId.getPath(), oggFile);
    }

    // -------------------------------------------------------------------------
    // ResourcePack impl
    // -------------------------------------------------------------------------

    @Override
    public ResourcePackInfo getInfo() {
        return INFO;
    }

    @Override
    public @Nullable InputSupplier<InputStream> openRoot(String... segments) {
        return null;
    }

    @Override
    public @Nullable InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        if (type != ResourceType.CLIENT_RESOURCES) return null;
        if (!"stabshot".equals(id.getNamespace())) return null;

        String path = id.getPath();

        // sounds.json — serve dynamically
        if ("sounds.json".equals(path)) {
            return () -> buildSoundsJson();
        }

        // OGG files — path is "sounds/song/name.ogg"
        if (path.startsWith("sounds/") && path.endsWith(".ogg")) {
            String songPath = path.substring("sounds/".length(), path.length() - 4);
            Path oggFile = SONGS.get(songPath);
            if (oggFile != null) {
                return () -> new FileInputStream(oggFile.toFile());
            }
        }

        return null;
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix,
                               ResultConsumer consumer) {
        if (type != ResourceType.CLIENT_RESOURCES) return;
        if (!"stabshot".equals(namespace)) return;

        // Advertise sounds.json
        Identifier soundsJson = Identifier.of("stabshot", "sounds.json");
        consumer.accept(soundsJson, () -> buildSoundsJson());

        // Advertise each OGG file
        for (Map.Entry<String, Path> entry : SONGS.entrySet()) {
            String songPath  = entry.getKey(); // e.g. "song/mysong"
            Path   oggFile   = entry.getValue();
            Identifier resId = Identifier.of("stabshot", "sounds/" + songPath + ".ogg");
            consumer.accept(resId, () -> new FileInputStream(oggFile.toFile()));
        }
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return Set.of("stabshot");
    }

    @Override
    public <T> @Nullable T parseMetadata(ResourceMetadataReader<T> metaReader) {
        return null;
    }

    @Override public void close() {}

    // -------------------------------------------------------------------------
    // Dynamic sounds.json builder
    // -------------------------------------------------------------------------

    private static InputStream buildSoundsJson() {
        // e.g.: {"song/mysong": {"sounds": [{"name": "stabshot:song/mysong", "stream": true}]}}
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String songPath : SONGS.keySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(songPath).append("\":{\"sounds\":[{\"name\":\"stabshot:")
              .append(songPath).append("\",\"stream\":true}]}");
            first = false;
        }
        sb.append("}");
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
