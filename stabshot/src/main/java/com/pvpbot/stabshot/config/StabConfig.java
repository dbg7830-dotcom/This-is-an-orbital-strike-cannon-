package com.pvpbot.stabshot.config;

import com.pvpbot.stabshot.StabShotMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * StabConfig — reads/writes stabshot.properties in the server config folder.
 *
 * File location: <server_root>/config/stabshot.properties
 *
 * Available settings:
 *   explosion_power    — power of each blast (default 9.5, vanilla TNT = 4.0)
 *   column_height      — how many TNT blasts in the column (default 8)
 *   column_start_above — blocks above aimed surface where column starts (default 2)
 *   tick_delay_between — server ticks between each blast (default 2)
 *
 * Edit the file while the server is stopped, then restart.
 * Invalid values fall back to defaults.
 */
public class StabConfig {

    private static final String FILE_NAME = "stabshot.properties";

    // Defaults — tuned to match OrbitalStrike+ feel (~4 hearts on full netherite)
    public static float explosionPower     = 4.0f;
    public static int   columnStartAbove   = 2;
    // columnHeight removed — column now runs from startAbove all the way to bedrock

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
            explosionPower   = parseFloat(props, "explosion_power",    explosionPower);
            columnStartAbove = parseInt  (props, "column_start_above",  columnStartAbove);
            StabShotMod.LOGGER.info("[StabShot] Config loaded — power={} startAbove={}",
                    explosionPower, columnStartAbove);
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to load config, using defaults: {}", e.getMessage());
        }
    }

    private static void writeDefaults(Path path) {
        try (Writer w = new FileWriter(path.toFile())) {
            w.write("# StabShot Configuration\n");
            w.write("# Edit these values and restart the server.\n");
            w.write("\n");
            w.write("# Power of each explosion blast. Vanilla TNT = 4.0\n");
            w.write("# 4.0 = ~4 hearts on full netherite prot 4 (matches OrbitalStrike+ feel)\n");
            w.write("# Recommended range: 3.0 - 6.0\n");
            w.write("explosion_power=" + explosionPower + "\n");
            w.write("\n");
            w.write("# How many blocks above the aimed block the column starts.\n");
            w.write("# 2 = hits head and body of a player standing at that position.\n");
            w.write("# Recommended range: 1 - 4\n");
            w.write("column_start_above=" + columnStartAbove + "\n");
            w.write("# Column runs from start_above all the way down to bedrock (y=1).\n");
            w.write("# This is automatic and cannot be configured.\n");
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
}
