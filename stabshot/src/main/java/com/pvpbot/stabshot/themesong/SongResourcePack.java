package com.pvpbot.stabshot.themesong;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
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
 */
@Environment(EnvType.CLIENT)
public class SongResourcePack implements ResourcePack {

    /** soundId.path → OGG file path */
    private static final Map<String, Path> SONGS = new ConcurrentHashMap<>();

    /** The single global instance registered with Minecraft. */
    public static final SongResourcePack INSTANCE = new SongResourcePack();

    private static final ResourcePackInfo INFO = new ResourcePackInfo(
            "stabshot_songs",
            Text.literal("StabShot Songs"),
            ResourcePackSource.BUILTIN,
            Optional.empty()
    );

    private SongResourcePack() {}

    /** Call this before playing a song to make it available to the sound system. */
    public static void registerSong(Identifier soundId, Path oggFile) {
        SONGS.put(soundId.getPath(), oggFile);
    }

    public static ResourcePackInfo getPackInfo() {
        return INFO;
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

        if ("sounds.json".equals(path)) {
            return SongResourcePack::buildSoundsJson;
        }

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

        Identifier soundsJson = Identifier.of("stabshot", "sounds.json");
        consumer.accept(soundsJson, SongResourcePack::buildSoundsJson);

        for (Map.Entry<String, Path> entry : SONGS.entrySet()) {
            String songPath = entry.getKey();
            Path   oggFile  = entry.getValue();
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

    @Override
    public void close() {}

    // -------------------------------------------------------------------------
    // Dynamic sounds.json builder
    // -------------------------------------------------------------------------

    private static InputStream buildSoundsJson() {
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
