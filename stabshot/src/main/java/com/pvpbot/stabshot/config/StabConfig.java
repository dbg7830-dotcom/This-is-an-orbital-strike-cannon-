package com.pvpbot.stabshot.config;

import com.pvpbot.stabshot.StabShotMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * StabConfig â€” reads/writes stabshot.properties in the server config folder.
 *
 * File: <world>/config/stabshot.properties (auto-created with defaults on first start)
 *
 * Settings:
 *   explosion_power    â€” power per blast. Default 2.5. Vanilla TNT = 4.0.
 *   column_start_above â€” Y blocks above aimed surface. Default 1.
 *   strike_radius      â€” grid half-width. 1 = 3x3, 2 = 5x5. Default 1.
 *   destroy_terrain    â€” true = breaks blocks, false = entity damage only. Default false.
 */
public class StabConfig {

    private static final String FILE_NAME = "stabshot.properties";

    public static float   explosionPower   = 2.5f;
    public static int     columnStartAbove = 1;
    public static int     strikeRadius     = 1;
    public static boolean destroyTerrain   = false;

    public static void load() {
        Path configDir  = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);

        if (!Files.exists(configFile)) {
            writeDefaults(configFile);
            StabShotMod.LOGGER.info("[StabShot] Config created at {}", configFile);
            return;
        }

        Properties props = new Properties();
        try (Reader r = new FileReader(configFile.toFile())) {
            props.load(r);
            explosionPower   = parseFloat  (props, "explosion_power",    explosionPower);
            columnStartAbove = parseInt    (props, "column_start_above",  columnStartAbove);
            strikeRadius     = parseInt    (props, "strike_radius",       strikeRadius);
            destroyTerrain   = parseBoolean(props, "destroy_terrain",     destroyTerrain);

            StabShotMod.LOGGER.info(
                    "[StabShot] Config loaded â€” power={} startAbove={} radius={} destroyTerrain={}",
                    explosionPower, columnStartAbove, strikeRadius, destroyTerrain);
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to load config, using defaults: {}", e.getMessage());
        }
    }

    private static void writeDefaults(Path path) {
        try (Writer w = new FileWriter(path.toFile())) {
            w.write("# StabShot Configuration\n");
            w.write("# Restart the server after editing.\n");
            w.write("\n");
            w.write("# Explosion power per blast.\n");
            w.write("# Vanilla TNT = 4.0. Recommended: 2.0 - 3.5 for balanced PvP.\n");
            w.write("explosion_power=" + explosionPower + "\n");
            w.write("\n");
            w.write("# Blocks above the aimed surface where the strike fires.\n");
            w.write("# 1 = hits body level. 2 = hits head level.\n");
            w.write("column_start_above=" + columnStartAbove + "\n");
            w.write("\n");
            w.write("# Flat grid half-width in blocks.\n");
            w.write("# 1 = 3x3 strike zone (9 blasts). 2 = 5x5 (25 blasts). 0 = single point.\n");
            w.write("# Larger radius = wider area but more server load per strike.\n");
            w.write("strike_radius=" + strikeRadius + "\n");
            w.write("\n");
            w.write("# Whether explosions break blocks.\n");
            w.write("# false = entity damage only, terrain stays clean (recommended).\n");
            w.write("# true  = breaks blocks like TNT.\n");
            w.write("destroy_terrain=" + destroyTerrain + "\n");
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to write default config: {}", e.getMessage());
        }
    }

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
