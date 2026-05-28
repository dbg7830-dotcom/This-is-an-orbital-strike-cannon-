package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.entity.TntEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * StabLogic — two strike modes.
 *
 * ORBITAL (default, new):
 *   TNT spawns at spawnHeight above target with downward velocity.
 *   Fuse timed so TNT detonates on arrival. Smooth "incoming from above" visual.
 *   Identical feel to OrbitalStrike+ but no sky-limit lag.
 *
 * LEGACY:
 *   Instant flat-grid explosion. Switch with /stabshot mode legacy.
 */
public class StabLogic {

    private static final double FALL_VELOCITY = -1.5; // blocks/tick downward

    private static int getFuse() {
        return (int)(StabConfig.spawnHeight / Math.abs(FALL_VELOCITY)) + 1;
    }

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        if (StabConfig.useLegacyMode) {
            summonStabLegacy(world, x, y, z);
        } else {
            summonStabOrbital(world, x, y, z);
        }
    }

    // =========================================================================
    // ORBITAL — TNT falls from above
    // =========================================================================

    private static void summonStabOrbital(ServerWorld world, int x, int y, int z) {
        int strikeY = y + StabConfig.columnStartAbove;
        int radius  = StabConfig.strikeRadius;
        int spawnY  = strikeY + StabConfig.spawnHeight;

        spawnWarningParticles(world, x, strikeY, z, radius);

        // Launch sound — anticipation before impact
        world.playSound(null, x + 0.5, strikeY, z + 0.5,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.5f, 1.2f);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                spawnFallingTnt(world, x + dx + 0.5, spawnY, z + dz + 0.5);
            }
        }
    }

    private static void spawnFallingTnt(ServerWorld world, double x, double spawnY, double z) {
        TntEntity tnt = new TntEntity(world, x, spawnY, z, null);
        tnt.setVelocity(new Vec3d(0, FALL_VELOCITY, 0));
        tnt.setFuse(getFuse());
        world.spawnEntity(tnt);
    }

    // =========================================================================
    // LEGACY — instant explosion
    // =========================================================================

    private static void summonStabLegacy(ServerWorld world, int x, int y, int z) {
        int strikeY = y + StabConfig.columnStartAbove;
        int radius  = StabConfig.strikeRadius;

        spawnWarningParticles(world, x, strikeY, z, radius);

        for (int i = 0; i < 4; i++) {
            world.playSound(null, x + 0.5, strikeY, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                    4.0f, 0.8f + (i * 0.1f));
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                fireExplosion(world, x + dx + 0.5, strikeY, z + dz + 0.5);
            }
        }
    }

    // =========================================================================
    // Shared
    // =========================================================================

    private static void spawnWarningParticles(ServerWorld world,
                                               int cx, int cy, int cz, int radius) {
        for (int dx = -(radius + 1); dx <= (radius + 1); dx++) {
            for (int dz = -(radius + 1); dz <= (radius + 1); dz++) {
                if (Math.abs(dx) != radius + 1 && Math.abs(dz) != radius + 1) continue;
                world.spawnParticles(ParticleTypes.FLAME,
                        cx + dx + 0.5, cy + 0.1, cz + dz + 0.5, 3, 0.1, 0.3, 0.1, 0.0);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        cx + dx + 0.5, cy + 0.1, cz + dz + 0.5, 1, 0.1, 0.2, 0.1, 0.0);
            }
        }
    }

    static void fireExplosion(ServerWorld world, double x, double y, double z) {
        World.ExplosionSourceType type = StabConfig.destroyTerrain
                ? World.ExplosionSourceType.TNT
                : World.ExplosionSourceType.NONE;
        world.createExplosion(null, null, null,
                x, y, z, StabConfig.explosionPower, false, type);
    }
}
