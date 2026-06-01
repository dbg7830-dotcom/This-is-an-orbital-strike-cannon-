package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * StabLogic — Vulgar's OSC
 *
 * Particle system:
 *   Phase 0 (instant with strike): EXPLOSION_EMITTER grid at surface
 *           + dense WHITE_SMOKE filling the entire shaft column top-to-bottom
 *   Phase 1 (+20 ticks / 1s):  medium-density column — fade begins
 *   Phase 2 (+35 ticks / 1.75s): sparse column — nearly gone
 *
 * WHITE_SMOKE is a tall white drifting particle (added in 1.20.4) — the closest
 * vanilla equivalent to the swirling white column seen in the reference screenshots.
 *
 * Terrain: instant clean shaft, ~8% wall blocks kept as random protrusions.
 * Delay: server-tick queue, exact timing, no off-thread scheduling.
 */
public class StabLogic {

    private static final int   WEMMBU_STOP_ABOVE_BOTTOM = 6;
    private static final float UNBREAKABLE_RESISTANCE   = 1_000.0f;

    // -------------------------------------------------------------------------
    // Tick queues — everything runs on the server thread
    // -------------------------------------------------------------------------

    private record PendingStrike(ServerWorld world, int x, int y, int z, long fireAtTick) {}

    private record PendingParticles(
            ServerWorld world, int cx, int topY, int bottomY, int cz,
            int radius, int phase, long fireAtTick) {}

    private static final List<PendingStrike>    PENDING         = new ArrayList<>();
    private static final List<PendingParticles> PARTICLE_PHASES = new ArrayList<>();

    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty() && PARTICLE_PHASES.isEmpty()) return;
        long now = server.getTicks();

        PENDING.removeIf(s -> {
            if (now >= s.fireAtTick()) {
                executeStrike(s.world(), s.x(), s.y(), s.z());
                return true;
            }
            return false;
        });

        PARTICLE_PHASES.removeIf(pp -> {
            if (now >= pp.fireAtTick()) {
                spawnColumnPhase(pp.world(), pp.cx(), pp.topY(), pp.bottomY(),
                        pp.cz(), pp.radius(), pp.phase());
                return true;
            }
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int delay = Math.max(0, StabConfig.fireDelayTicks);
        if (delay <= 0) {
            executeStrike(world, x, y, z);
        } else {
            PENDING.add(new PendingStrike(world, x, y, z,
                    world.getServer().getTicks() + delay));
        }
    }

    private static void executeStrike(ServerWorld world, int x, int y, int z) {
        if (StabConfig.isWemmbuMode()) summonWemmbu(world, x, z);
        else                           summonLegacy(world, x, y, z);
    }

    // -------------------------------------------------------------------------
    // WEMMBU mode
    // -------------------------------------------------------------------------

    private static void summonWemmbu(ServerWorld world, int cx, int cz) {
        int radius  = Math.max(0, StabConfig.strikeRadius);
        int topY    = findHighestSurfaceInFootprint(world, cx, cz, radius);
        int bottomY = world.getBottomY() + WEMMBU_STOP_ABOVE_BOTTOM;

        playSounds(world, cx, topY, cz);

        // Phase 0 — fires with the strike
        spawnColumnPhase(world, cx, topY, bottomY, cz, radius, 0);

        // Schedule fading phases
        long now = world.getServer().getTicks();
        PARTICLE_PHASES.add(new PendingParticles(
                world, cx, topY, bottomY, cz, radius, 1, now + 20));
        PARTICLE_PHASES.add(new PendingParticles(
                world, cx, topY, bottomY, cz, radius, 2, now + 35));

        if (StabConfig.destroyTerrain) carveShaft(world, cx, cz, radius, topY, bottomY);
        damageEntities(world, cx, bottomY, topY, cz, radius, 1.85f);
    }

    // -------------------------------------------------------------------------
    // LEGACY mode
    // -------------------------------------------------------------------------

    private static void summonLegacy(ServerWorld world, int x, int y, int z) {
        int radius  = Math.max(0, StabConfig.strikeRadius);
        int strikeY = y + StabConfig.columnStartAbove;

        playSounds(world, x, strikeY, z);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int colX = x + dx, colZ = z + dz;
                int surfaceY = findColumnSurface(world, colX, y, colZ);
                int colY = surfaceY + StabConfig.columnStartAbove;
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        colX + 0.5, colY + 0.5, colZ + 0.5, 1, 0, 0, 0, 0);
                if (StabConfig.destroyTerrain) carveLegacyColumn(world, colX, surfaceY, colZ);
            }
        }
        damageEntities(world, x, y - 16, strikeY + 2, z, radius, 1.35f);
    }

    // -------------------------------------------------------------------------
    // Particle phases — white column that fills the shaft and fades
    // -------------------------------------------------------------------------

    /**
     * Phase 0 (instant with strike):
     *   - 1 central EXPLOSION_EMITTER at impact point
     *   - EXPLOSION particles spread across surface
     *   - Dense WHITE_SMOKE every 2 Y-levels filling full shaft depth
     *
     * Phase 1 (+20 ticks / 1s):  medium density — fade begins
     * Phase 2 (+35 ticks / 1.75s): sparse — nearly gone
     *
     * CRITICAL: speed = 0.0 makes deltaX/Y/Z act as POSITION spread, not velocity.
     * With speed > 0, all particles spawn at the center and fly outward (invisible).
     * With speed = 0, particles spawn scattered randomly within ±xzSpread of center.
     *
     * xzSpread = radius + 0.5 so particles fill wall-to-wall across the shaft.
     * WHITE_SMOKE has natural upward drift built into the particle itself, so
     * speed = 0 still produces a gently rising column effect.
     */
    private static void spawnColumnPhase(ServerWorld world,
                                          int cx, int topY, int bottomY,
                                          int cz, int radius, int phase) {
        if (topY < bottomY) return;

        // Fill wall-to-wall: shaft goes from cx-radius to cx+radius, half-width = radius+0.5
        double xzSpread = radius + 0.5;

        int yStep, count;
        switch (phase) {
            case 0  -> { yStep = 2; count = Math.max(8,  (radius * 2 + 1) * 2); }
            case 1  -> { yStep = 3; count = Math.max(4,   radius * 2 + 1);      }
            default -> { yStep = 6; count = Math.max(2,   radius + 1);          }
        }

        // Phase 0 only: surface impact effects
        if (phase == 0) {
            // Single central EXPLOSION_EMITTER — not per-block, that made a big blob
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    cx + 0.5, topY + 1.5, cz + 0.5,
                    1, 0, 0, 0, 0);
            // EXPLOSION spread across surface at speed=0 (position spread, not velocity)
            world.spawnParticles(ParticleTypes.EXPLOSION,
                    cx + 0.5, topY + 0.3, cz + 0.5,
                    Math.max(4, (radius * 2 + 1) * (radius * 2 + 1)),
                    xzSpread, 0.2, xzSpread, 0.0);
        }

        // WHITE_SMOKE column — speed=0.0 so deltaXYZ = POSITION spread (not velocity)
        // Particles appear scattered across the full shaft cross-section at each y level
        for (int y = topY; y >= bottomY; y -= yStep) {
            world.spawnParticles(ParticleTypes.WHITE_SMOKE,
                    cx + 0.5, y + 0.5, cz + 0.5,
                    count,
                    xzSpread, 0.5, xzSpread,
                    0.0);  // <-- 0.0 is the critical fix: position spread not velocity
        }
    }

    // -------------------------------------------------------------------------
    // Terrain carving
    // -------------------------------------------------------------------------

    private static void carveShaft(ServerWorld world, int cx, int cz,
                                    int radius, int topY, int bottomY) {
        for (int y = topY; y >= bottomY; y--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Wall-edge blocks have a small chance to stay as protrusions
                    int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                    if (cheb == radius
                            && stableChance(cx + dx, y, cz + dz,
                                            StabConfig.ledgeBlockChance)) continue;
                    breakIfPossible(world, cx + dx, y, cz + dz);
                }
            }
        }
    }

    private static void breakIfPossible(ServerWorld world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (canAffect(state)) world.breakBlock(pos, false);
    }

    private static void carveLegacyColumn(ServerWorld world, int x, int surfaceY, int z) {
        int impactY  = surfaceY + StabConfig.columnStartAbove;
        int maxDepth = Math.max(1, StabConfig.blastDepth);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = impactY; y >= surfaceY - maxDepth; y--) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!canAffect(state)) continue;
            double progress = (impactY - y) / (double)(maxDepth + StabConfig.columnStartAbove + 1);
            if (state.getBlock().getBlastResistance() <= 60.0 * (1.0 - progress * 0.6))
                world.breakBlock(pos, false);
        }
    }

    // -------------------------------------------------------------------------
    // Sounds
    // -------------------------------------------------------------------------

    private static void playSounds(ServerWorld world, int x, int y, int z) {
        playCustomSound(world, x, y + 15, z, "stabshot:explosion2", 6.0f, 0.75f);
        playCustomSound(world, x, y,      z, "stabshot:explosion1", 6.0f, 0.55f);
        float[] pitches = { 0.50f, 0.60f, 0.68f, 0.76f, 0.85f, 0.95f };
        for (float p : pitches)
            world.playSound(null, x + 0.5, y, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 6.0f, p);
    }

    private static void playCustomSound(ServerWorld world, int x, int y, int z,
                                         String id, float vol, float pitch) {
        try {
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of(id));
            if (ev != null)
                world.playSound(null, x + 0.5, y, z + 0.5, ev, SoundCategory.MASTER, vol, pitch);
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Surface finding
    // -------------------------------------------------------------------------

    private static int findHighestSurfaceInFootprint(ServerWorld world,
                                                      int cx, int cz, int radius) {
        int highest = world.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = world.getTopY() - 1; y >= world.getBottomY(); y--) {
                    pos.set(cx + dx, y, cz + dz);
                    if (!world.getBlockState(pos).isAir()) {
                        highest = Math.max(highest, y);
                        break;
                    }
                }
            }
        }
        return highest;
    }

    private static int findColumnSurface(ServerWorld world, int x, int targetY, int z) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = targetY + 8; y >= targetY - 16; y--) {
            pos.set(x, y, z);
            if (!world.getBlockState(pos).isAir()) return y;
        }
        return targetY;
    }

    // -------------------------------------------------------------------------
    // Entity damage
    // -------------------------------------------------------------------------

    private static void damageEntities(ServerWorld world, int cx, int minY, int maxY,
                                        int cz, int radius, float mult) {
        double reach = radius + 0.75;
        Box box = new Box(cx + 0.5 - reach, minY, cz + 0.5 - reach,
                          cx + 0.5 + reach, maxY + 1.0, cz + 0.5 + reach);
        for (Entity e : world.getOtherEntities(null, box, Entity::isAlive)) {
            double dist = Math.max(Math.abs(e.getX() - (cx + 0.5)),
                                   Math.abs(e.getZ() - (cz + 0.5)));
            if (dist > reach) continue;
            float dmg = (float)(StabConfig.explosionPower * 4.0 * mult
                    * (1.0 - (dist / (reach + 1.0)) * 0.55));
            if (dmg > 0) e.damage(world.getDamageSources().explosion(null, null), dmg);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean stableChance(int x, int y, int z, double chance) {
        if (chance <= 0) return false;
        if (chance >= 1) return true;
        long seed = x * 3129871L ^ z * 116129781L ^ y * 42317861L;
        seed = seed * seed * 42317861L + seed * 11L;
        return (((seed >> 16) & 1023L) / 1023.0) < chance;
    }

    private static boolean canAffect(BlockState state) {
        return !state.isAir()
                && state.getBlock().getBlastResistance() < UNBREAKABLE_RESISTANCE;
    }
}
