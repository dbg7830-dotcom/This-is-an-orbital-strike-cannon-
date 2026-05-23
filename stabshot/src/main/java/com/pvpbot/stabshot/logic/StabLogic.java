package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * StabLogic v5 — flat grid strike with warning particles + 4x explosion sound.
 *
 * Improvements:
 *  - Warning particle ring (FLAME + SMOKE) spawned at strike zone before explosions.
 *    Gives a very brief visual cue — fair warning without ruining the mechanic.
 *  - 4 layered explosion sounds at varying pitch = sounds like multiple TNT.
 *  - All explosions still fire in one tick = instant damage.
 */
public class StabLogic {

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int strikeY = y + StabConfig.columnStartAbove;
        int radius  = StabConfig.strikeRadius;

        // Warning particles — FLAME ring at the strike perimeter
        spawnWarningParticles(world, x, strikeY, z, radius);

        // 4 layered explosion sounds at slightly different pitches
        // Sounds like 4 TNT blocks detonating together
        for (int i = 0; i < 4; i++) {
            world.playSound(
                    null,
                    x + 0.5, strikeY, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.BLOCKS,
                    4.0f,
                    0.8f + (i * 0.1f) // 0.8, 0.9, 1.0, 1.1
            );
        }

        // Flat grid explosions — all in one tick
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                fireExplosion(world, x + dx + 0.5, strikeY, z + dz + 0.5);
            }
        }
    }

    /**
     * Spawns FLAME and SMOKE particles around the perimeter of the strike zone.
     * These appear the same tick as the explosion so it's a simultaneous visual cue.
     */
    private static void spawnWarningParticles(ServerWorld world,
                                               int cx, int cy, int cz, int radius) {
        // Ring around the perimeter
        for (int dx = -(radius + 1); dx <= (radius + 1); dx++) {
            for (int dz = -(radius + 1); dz <= (radius + 1); dz++) {
                // Only spawn on the edge ring, not inside
                if (Math.abs(dx) != radius + 1 && Math.abs(dz) != radius + 1) continue;

                double px = cx + dx + 0.5;
                double pz = cz + dz + 0.5;

                // FLAME particles — bright, visible strike indicator
                world.spawnParticles(ParticleTypes.FLAME,
                        px, cy + 0.1, pz,
                        3,        // count
                        0.1, 0.3, 0.1, // spread X, Y, Z
                        0.0       // speed
                );
                // SMOKE for drama
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        px, cy + 0.1, pz,
                        1, 0.1, 0.2, 0.1, 0.0);
            }
        }
    }

    static void fireExplosion(ServerWorld world, double x, double y, double z) {
        World.ExplosionSourceType type = StabConfig.destroyTerrain
                ? World.ExplosionSourceType.TNT
                : World.ExplosionSourceType.NONE;

        world.createExplosion(
                null, null, null,
                x, y, z,
                StabConfig.explosionPower,
                false,
                type
        );
    }
}
