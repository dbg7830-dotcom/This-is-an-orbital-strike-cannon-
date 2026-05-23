package com.pvpbot.stabshot.config;

import com.pvpbot.stabshot.StabShotMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * StabConfig — auto-reloading config for stabshot.properties.
 *
 * Hot-reload: checks file's last-modified timestamp every 40 ticks (2 seconds).
 * If the file changed, reloads automatically — no restart needed.
 */
public class StabConfig {

    private static final String FILE_NAME = "stabshot.properties";

    public static float   explosionPower   = 2.5f;
    public static int     columnStartAbove = 1;
    public static int     strikeRadius     = 1;
    public static boolean destroyTerrain   = false;

    private static Path        configFile;
    private static long        lastModified   = -1;
    private static int         reloadTick     = 0;
    private static final int   RELOAD_INTERVAL = 40; // check every 2 seconds

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

        // Register tick-based hot-reload check
        // Works in both singleplayer and dedicated server
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            reloadTick++;
            if (reloadTick >= RELOAD_INTERVAL) {
                reloadTick = 0;
                checkReload();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Hot-reload check
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
            Path configDir = FabricLoader.getInstance().getConfigDir();
            configFile = configDir.resolve(FILE_NAME);
        }
        if (!Files.exists(configFile)) {
            writeDefaults(configFile);
            return;
        }

        Properties props = new Properties();
        try (Reader r = new FileReader(configFile.toFile())) {
            props.load(r);
            explosionPower   = parseFloat  (props, "explosion_power",    explosionPower);
            columnStartAbove = parseInt    (props, "column_start_above",  columnStartAbove);
            strikeRadius     = parseInt    (props, "strike_radius",       strikeRadius);
            destroyTerrain   = parseBoolean(props, "destroy_terrain",     destroyTerrain);

            // Update lastModified so we don't immediately re-trigger
            lastModified = Files.getLastModifiedTime(configFile).toMillis();

            StabShotMod.LOGGER.info(
                "[StabShot] Config — power={} startAbove={} radius={} destroyTerrain={}",
                explosionPower, columnStartAbove, strikeRadius, destroyTerrain);
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to load config: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Write defaults
    // -------------------------------------------------------------------------

    private static void writeDefaults(Path path) {
        try (Writer w = new FileWriter(path.toFile())) {
            w.write("# StabShot Configuration\n");
            w.write("# Changes apply automatically — no restart needed.\n");
            w.write("\n");
            w.write("# Explosion power per blast. Vanilla TNT = 4.0\n");
            w.write("# Recommended: 2.0 - 3.5 for balanced PvP.\n");
            w.write("explosion_power=" + explosionPower + "\n");
            w.write("\n");
            w.write("# Blocks above the aimed surface where the strike fires.\n");
            w.write("# 1 = body level. 2 = head level.\n");
            w.write("column_start_above=" + columnStartAbove + "\n");
            w.write("\n");
            w.write("# Flat grid half-width. 0 = single point, 1 = 3x3, 2 = 5x5.\n");
            w.write("strike_radius=" + strikeRadius + "\n");
            w.write("\n");
            w.write("# Block destruction. false = entity damage only (recommended).\n");
            w.write("destroy_terrain=" + destroyTerrain + "\n");
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to write defaults: {}", e.getMessage());
        }
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
