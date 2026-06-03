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
 * All values also editable in-game via /stabshot or /pb commands.
 */
public class StabConfig {

    private static final String FILE_NAME = "stabshot.properties";

    public static final String MODE_WEMMBU = "wemmbu";
    public static final String MODE_LEGACY = "legacy";

    /** Default mode: vertical WEMMBU shaft from highest local block downward. */
    public static String mode = MODE_WEMMBU;

    /** Entity damage strength for the mod's custom strike damage only. */
    public static float explosionPower = 2.5f;

    /** Blocks above each found surface where legacy visuals begin. */
    public static int columnStartAbove = 1;

    /** Exact X/Z half-width for LEGACY mode. 0=single, 1=3x3, 2=5x5. */
    public static int strikeRadius = 1;

    /** Exact X/Z half-width for WEMMBU mode — separate from legacy so each can be tuned. */
    public static int wemmbuRadius = 1;

    /** Legacy only: max vertical terrain carve depth in blocks. */
    public static int blastDepth = 18;

    /** false = entity damage + visuals only; true = custom terrain carving. */
    public static boolean destroyTerrain = true;

    /**
     * Chance (0.0–1.0) for wall blocks to be kept as random protrusions.
     * 0.04 = ~4% of wall blocks kept — matches the sparse single blocks
     * visible in the reference screenshots. Tunable via /stabshot set ledgechance.
     */
    public static float ledgeBlockChance = 0.04f;

    /**
     * Delay in ticks before the strike detonates after being fired.
     * 1 tick = 50 ms at 20 TPS. Default 20 = 1 second. 0 = instant.
     */
    public static int fireDelayTicks = 20;

    private static Path configFile;
    private static long lastModified = -1;
    private static int  reloadTick   = 0;
    private static final int RELOAD_INTERVAL = 40;

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

    public static void load() {
        if (configFile == null) {
            configFile = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        }
        if (!Files.exists(configFile)) { writeDefaults(configFile); return; }

        Properties props = new Properties();
        try (Reader r = new FileReader(configFile.toFile())) {
            props.load(r);
            mode            = normalizeMode(props.getProperty("mode", mode));
            explosionPower  = parseFloat(props, "explosion_power", explosionPower);
            columnStartAbove = parseInt(props, "column_start_above", columnStartAbove);
            strikeRadius    = parseInt(props, "strike_radius", strikeRadius);
            wemmbuRadius    = parseInt(props, "wemmbu_radius", wemmbuRadius);
            blastDepth      = parseInt(props, "blast_depth", blastDepth);
            destroyTerrain  = parseBoolean(props, "destroy_terrain", destroyTerrain);
            ledgeBlockChance = parseFloat(props, "ledge_block_chance", ledgeBlockChance);
            fireDelayTicks  = parseInt(props, "fire_delay_ticks", fireDelayTicks);
            lastModified    = Files.getLastModifiedTime(configFile).toMillis();
            StabShotMod.LOGGER.info(
                    "[StabShot] Config — mode={} damage={} radius={} blastDepth={} destroyTerrain={} fireDelayTicks={}",
                    mode, explosionPower, strikeRadius, blastDepth, destroyTerrain, fireDelayTicks);
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to load config: {}", e.getMessage());
        }
    }

    public static void save() {
        if (configFile == null) return;
        try (Writer w = new FileWriter(configFile.toFile())) {
            w.write("# StabShot (Vulgar's OSC) Configuration\n");
            w.write("# Changes apply automatically every 2 seconds.\n");
            w.write("# Or use /stabshot and /pb commands to change in-game.\n\n");
            w.write("# wemmbu = default vertical shaft from highest block down near bedrock.\n");
            w.write("# legacy = configurable enhanced legacy crater using blast_depth.\n");
            w.write("mode=" + mode + "\n\n");
            w.write("# Custom entity damage strength for this mod only. Does not alter vanilla TNT.\n");
            w.write("explosion_power=" + explosionPower + "\n\n");
            w.write("# Legacy: blocks above each found surface where blast visuals begin (1=body, 2=head).\n");
            w.write("column_start_above=" + columnStartAbove + "\n\n");
            w.write("# Exact X/Z half-width for LEGACY mode. 0=single, 1=3x3, 2=5x5.\n");
            w.write("strike_radius=" + strikeRadius + "\n\n");
            w.write("# Exact X/Z half-width for WEMMBU mode (independent from legacy).\n");
            w.write("wemmbu_radius=" + wemmbuRadius + "\n\n");
            w.write("# Legacy only: max vertical terrain carve depth in blocks.\n");
            w.write("blast_depth=" + blastDepth + "\n\n");
            w.write("# true=custom terrain carving, false=entity damage + explosion particles only.\n");
            w.write("destroy_terrain=" + destroyTerrain + "\n\n");
            w.write("# Chance (0.0-1.0) for wall blocks to stay as random protrusions. 0=clean shaft, 0.08=default ~8%.\n");
            w.write("ledge_block_chance=" + ledgeBlockChance + "\n\n");
            w.write("# Delay in ticks before the strike detonates after firing (20 ticks = 1 second, 0 = instant).\n");
            w.write("fire_delay_ticks=" + fireDelayTicks + "\n");
            lastModified = Files.getLastModifiedTime(configFile).toMillis();
        } catch (Exception e) {
            StabShotMod.LOGGER.error("[StabShot] Failed to save config: {}", e.getMessage());
        }
    }

    public static String normalizeMode(String value) {
        if (value == null) return MODE_WEMMBU;
        String normalized = value.trim().toLowerCase();
        if (MODE_LEGACY.equals(normalized)) return MODE_LEGACY;
        return MODE_WEMMBU;
    }

    public static boolean isWemmbuMode() {
        return MODE_WEMMBU.equals(mode);
    }

    private static void writeDefaults(Path path) {
        configFile = path;
        save();
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
