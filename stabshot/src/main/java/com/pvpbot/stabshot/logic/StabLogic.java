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
 * StabLogic â€” Vulgar's OSC strike logic.
 *
 * Delay is handled server-side via a tick queue (no off-thread scheduling).
 * Particles are spawned per-block so they stay exactly inside the strike radius.
 * Terrain carves a stepped funnel: each ledge ring the shaft narrows by 1,
 * matching the concentric-squares look visible in the reference screenshots.
 */
public class StabLogic {

    private static final int   WEMMBU_STOP_ABOVE_BOTTOM = 6;
    private static final float UNBREAKABLE_RESISTANCE   = 1_000.0f;

    // -------------------------------------------------------------------------
    // Server-tick delay queue â€” 100% on the server thread, no race conditions
    // -------------------------------------------------------------------------

    private record PendingStrike(ServerWorld world, int x, int y, int z, long fireAtTick) {}

    private static final List<PendingStrike> PENDING = new ArrayList<>();

    /**
     * Called every server tick from StabShotMod's tick event.
     * Drains any strikes whose fireAtTick has been reached.
     */
    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTicks();
        PENDING.removeIf(strike -> {
            if (now >= strike.fireAtTick()) {
                executeStrike(strike.world(), strike.x(), strike.y(), strike.z());
                return true;
            }
            return false;
        });
    }

    /** Entry point â€” schedules or fires immediately based on config delay. */
    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int delayTicks = Math.max(0, StabConfig.fireDelayTicks);
        if (delayTicks <= 0) {
            executeStrike(world, x, y, z);
        } else {
            long fireAt = world.getServer().getTicks() + delayTicks;
            PENDING.add(new PendingStrike(world, x, y, z, fireAt));
        }
    }

    private static void executeStrike(ServerWorld world, int x, int y, int z) {
        if (StabConfig.isWemmbuMode()) {
            summonWemmbu(world, x, z);
        } else {
            summonLegacy(world, x, y, z);
        }
    }

    // -------------------------------------------------------------------------
    // WEMMBU mode
    // -------------------------------------------------------------------------

    private static void summonWemmbu(ServerWorld world, int centerX, int centerZ) {
        int radius  = Math.max(0, StabConfig.strikeRadius);
        int topY    = findHighestSurfaceInFootprint(world, centerX, centerZ, radius);
        int bottomY = world.getBottomY() + WEMMBU_STOP_ABOVE_BOTTOM;

        // Sounds first â€” play before carving so they aren't blocked by chunk updates
        playSounds(world, centerX, topY, centerZ);

        // Particles â€” one EXPLOSION_EMITTER per block position in the footprint,
        // zero spread so they stay exactly where the strike lands
        spawnStrikeParticles(world, centerX, topY, centerZ, radius);

        if (StabConfig.destroyTerrain) {
            carveSteppedShaft(world, centerX, centerZ, radius, topY, bottomY);
        }

        damageEntities(world, centerX, bottomY, topY, centerZ, radius, 1.85f);
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
                int cx = x + dx;
                int cz = z + dz;
                int surfaceY = findColumnSurface(world, cx, y, cz);
                int colY = surfaceY + StabConfig.columnStartAbove;

                // One EXPLOSION_EMITTER exactly at this column's surface â€” no spread
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        cx + 0.5, colY + 0.5, cz + 0.5, 1, 0, 0, 0, 0);

                if (StabConfig.destroyTerrain) {
                    carveLegacyColumn(world, cx, surfaceY, cz);
                }
            }
        }

        damageEntities(world, x, y - 16, strikeY + 2, z, radius, 1.35f);
    }

    // -------------------------------------------------------------------------
    // Particles â€” zero spread, positioned exactly at each block in the footprint
    // -------------------------------------------------------------------------

    /**
     * Spawns one EXPLOSION_EMITTER at the exact center of every block position
     * inside the strike footprint. Speed=0 and all offsets=0 means particles
     * appear right where the blast is and do NOT fly outward.
     *
     * Also adds a dense layer of small EXPLOSION particles at ground level
     * to fill the visual and give the "everything explodes at once" look.
     */
    private static void spawnStrikeParticles(ServerWorld world,
                                              int centerX, int topY, int centerZ,
                                              int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Big emitter exactly on each block â€” stays put, no drift
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        centerX + dx + 0.5,
                        topY + 1.0,
                        centerZ + dz + 0.5,
                        1,   // count = 1 per position
                        0, 0, 0,   // zero XYZ offset
                        0);  // speed = 0

                // Small explosion at ground level per block for density
                world.spawnParticles(ParticleTypes.EXPLOSION,
                        centerX + dx + 0.5,
                        topY + 0.5,
                        centerZ + dz + 0.5,
                        3,
                        0, 0, 0,
                        0);
            }
        }

        // One extra central EXPLOSION_EMITTER at the very epicentre for emphasis
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                centerX + 0.5, topY + 1.5, centerZ + 0.5,
                1, 0, 0, 0, 0);
    }

    // -------------------------------------------------------------------------
    // Terrain â€” stepped funnel shaft
    // -------------------------------------------------------------------------

    /**
     * Instantly removes every block in the shaft from topY down to bottomY.
     * No stepping, no animation, no delays â€” the entire column is gone in one tick.
     */
    private static void carveSteppedShaft(ServerWorld world,
                                           int centerX, int centerZ,
                                           int radius, int topY, int bottomY) {
        for (int y = topY; y >= bottomY; y--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    breakIfPossible(world, centerX + dx, y, centerZ + dz);
                }
            }
        }
    }

    private static void breakIfPossible(ServerWorld world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (canAffect(state)) {
            world.breakBlock(pos, false);
        }
    }

    private static void carveLegacyColumn(ServerWorld world, int x, int surfaceY, int z) {
        int impactY  = surfaceY + StabConfig.columnStartAbove;
        int maxDepth = Math.max(1, StabConfig.blastDepth);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = impactY; y >= surfaceY - maxDepth; y--) {
            mutable.set(x, y, z);
            BlockState state = world.getBlockState(mutable);
            if (!canAffect(state)) continue;
            double progress = (impactY - y) / (double) (maxDepth + StabConfig.columnStartAbove + 1);
            if (state.getBlock().getBlastResistance() <= 60.0 * (1.0 - progress * 0.6)) {
                world.breakBlock(mutable, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sound â€” high volume, longer-lasting layered booms
    // -------------------------------------------------------------------------

    private static void playSounds(ServerWorld world, int x, int y, int z) {
        // Custom sounds (if .ogg files are provided)
        playCustomSound(world, x, y + 15, z, "stabshot:explosion2", 6.0f, 0.75f);
        playCustomSound(world, x, y,      z, "stabshot:explosion1", 6.0f, 0.55f);

        // Vanilla layered booms â€” always present so sound is never missing.
        // 6 pitches spread across a wide range = long cinematic rumble.
        // Volume 6.0 = audible from ~96 blocks away.
        float[] pitches = { 0.50f, 0.60f, 0.68f, 0.76f, 0.85f, 0.95f };
        for (float pitch : pitches) {
            world.playSound(null, x + 0.5, y, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.MASTER,
                    6.0f, pitch);
        }
    }

    private static void playCustomSound(ServerWorld world,
                                         int x, int y, int z,
                                         String soundId,
                                         float volume, float pitch) {
        try {
            SoundEvent event = Registries.SOUND_EVENT.get(Identifier.of(soundId));
            if (event != null) {
                world.playSound(null, x + 0.5, y, z + 0.5,
                        event, SoundCategory.MASTER, volume, pitch);
            }
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Surface finding
    // -------------------------------------------------------------------------

    private static int findHighestSurfaceInFootprint(ServerWorld world,
                                                      int centerX, int centerZ,
                                                      int radius) {
        int highest = world.getBottomY();
        int top = world.getTopY() - 1;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = top; y >= world.getBottomY(); y--) {
                    mutable.set(centerX + dx, y, centerZ + dz);
                    if (!world.getBlockState(mutable).isAir()) {
                        highest = Math.max(highest, y);
                        break;
                    }
                }
            }
        }
        return highest;
    }

    private static int findColumnSurface(ServerWorld world, int x, int targetY, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = targetY + 8; y >= targetY - 16; y--) {
            mutable.set(x, y, z);
            if (!world.getBlockState(mutable).isAir()) return y;
        }
        return targetY;
    }

    // -------------------------------------------------------------------------
    // Entity damage
    // -------------------------------------------------------------------------

    private static void damageEntities(ServerWorld world,
                                        int centerX, int minY, int maxY, int centerZ,
                                        int radius, float multiplier) {
        double reach = radius + 0.75;
        Box box = new Box(centerX + 0.5 - reach, minY, centerZ + 0.5 - reach,
                          centerX + 0.5 + reach, maxY + 1.0, centerZ + 0.5 + reach);

        for (Entity entity : world.getOtherEntities(null, box, Entity::isAlive)) {
            double dist = Math.max(
                    Math.abs(entity.getX() - (centerX + 0.5)),
                    Math.abs(entity.getZ() - (centerZ + 0.5)));
            if (dist > reach) continue;
            float dmg = (float) (StabConfig.explosionPower * 4.0 * multiplier
                    * (1.0 - (dist / (reach + 1.0)) * 0.55));
            if (dmg > 0) entity.damage(world.getDamageSources().explosion(null, null), dmg);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean canAffect(BlockState state) {
        return !state.isAir()
                && state.getBlock().getBlastResistance() < UNBREAKABLE_RESISTANCE;
    }
                              }
