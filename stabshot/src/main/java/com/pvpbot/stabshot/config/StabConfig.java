package com.pvpbot.stabshot.config;

import com.pvpbot.stabshot.StabShotMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * StabConfig — auto-reloading config for stabshot.properties.
 * Hot-reload: checks last-modified every 40 ticks (2 seconds).
 * All values also editable in-game via /stabshot set <key> <value>.
 */
public class StabConfig {

    private static final String FILE_NAME = "stabshot.properties";

    // -------------------------------------------------------------------------
    // Config fields
    // -------------------------------------------------------------------------

    /** Explosion power per blast. Vanilla TNT = 4.0. Default 2.5. */
    public static float   explosionPower   = 2.5f;

    /** Blocks above aimed surface where strike fires. Default 1. */
    public static int     columnStartAbove = 1;

    /** Grid half-width. 0=single, 1=3x3, 2=5x5. Default 1. */
    public static int     strikeRadius     = 1;

    /** false = entity damage only (no block break). Default false. */
    public static boolean destroyTerrain   = false;

    /** ORBITAL: how many blocks above target TNT spawns. Default 30. */
    public static int     spawnHeight      = 30;

    /** true = use legacy instant-explosion mode. false = orbital falling TNT. */
    public static boolean useLegacyMode    = false;

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static Path configFile;
    private static long lastModified   = -1;
    private static int  reloadTick     = 0;
    private static final int RELOAD_INTERVAL = 40;

    // -------------------------------------------------------------------------
    // Init — called from StabShotMod.onInitialize()
    // -------------------------------------------------------------------------

    public static void init() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        configFile = configDir.resolve(FILE_NAME);

        if (!Files.exists(configFile)) {
            writeDefaults(configFile);
            StabShotMod.LOGGER.info("[StabShot] Config created at {}", configFile);
        } else {
            load();
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            reloadTick++;
            if (reloadTick >= RELOAD_INTERVAL) {
                reloadTick = 0;
                checkReload();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Hot-reload
    // -------------------------------------------------------------------------

    private static void checkReload() {
        if (configFile == null || !Files.exists(configFile)) return;
        try {
            long modified = Files.getLastModifiedTime(configFile).toMillis();
            if (modified != lastModified) {
                lastModified = modified;
                load();
                StabShotMod.LOGGER.info("[StabShot] Config hot-reloaded.");
            }
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Hot-reload check failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public static void load() {
        if (configFile == null) {
            configFile = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        }
        if (!Files.exists(configFile)) { writeDefaults(configFile); return; }

        Properties props = new Properties();
        try (Reader r = new FileReader(configFile.toFile())) {
            props.load(r);
            explosionPower   = parseFloat  (props, "explosion_power",    explosionPower);
            columnStartAbove = parseInt    (props, "column_start_above",  columnStartAbove);
            strikeRadius     = parseInt    (props, "strike_radius",       strikeRadius);
            destroyTerrain   = parseBoolean(props, "destroy_terrain",     destroyTerrain);
            spawnHeight      = parseInt    (props, "spawn_height",        spawnHeight);
            useLegacyMode    = parseBoolean(props, "use_legacy_mode",     useLegacyMode);
            lastModified = Files.getLastModifiedTime(configFile).toMillis();
            StabShotMod.LOGGER.info("[StabShot] Config — power={} radius={} mode={} spawnHeight={}",
                    explosionPower, strikeRadius,
                    useLegacyMode ? "LEGACY" : "ORBITAL", spawnHeight);
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to load config: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Save — called by /stabshot set commands to persist changes
    // -------------------------------------------------------------------------

    public static void save() {
        if (configFile == null) return;
        try (Writer w = new FileWriter(configFile.toFile())) {
            w.write("# StabShot Configuration\n");
            w.write("# Changes apply automatically every 2 seconds.\n");
            w.write("# Or use /stabshot set <key> <value> to change in-game.\n\n");
            w.write("# ORBITAL: TNT falls from above (smooth, like OrbitalStrike+)\n");
            w.write("# LEGACY:  Instant flat-grid explosion\n");
            w.write("use_legacy_mode="    + useLegacyMode    + "\n\n");
            w.write("# Explosion power per blast. Vanilla TNT = 4.0\n");
            w.write("explosion_power="    + explosionPower   + "\n\n");
            w.write("# Blocks above aimed surface where strike fires (1=body, 2=head)\n");
            w.write("column_start_above=" + columnStartAbove + "\n\n");
            w.write("# Grid half-width. 0=single point, 1=3x3, 2=5x5\n");
            w.write("strike_radius="      + strikeRadius     + "\n\n");
            w.write("# ORBITAL only: blocks above target where TNT spawns\n");
            w.write("spawn_height="       + spawnHeight      + "\n\n");
            w.write("# true=breaks blocks, false=entity damage only (recommended)\n");
            w.write("destroy_terrain="    + destroyTerrain   + "\n");
            // Update lastModified so hot-reload doesn't immediately re-trigger
            lastModified = Files.getLastModifiedTime(configFile).toMillis();
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to save config: {}", e.getMessage());
        }
    }

    private static void writeDefaults(Path path) {
        configFile = path;
        save(); // reuse save() to write defaults
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    private static float parseFloat(Properties p, String key, float def) {
        try { return Float.parseFloat(p.getProperty(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private static int parseInt(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private static boolean parseBoolean(Properties p, String key, boolean def) {
        try { return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }
}
