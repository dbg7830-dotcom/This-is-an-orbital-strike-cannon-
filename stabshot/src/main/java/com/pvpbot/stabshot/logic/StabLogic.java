package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * StabLogic — enhanced legacy-only stab shot.
 *
 * There is no v2/orbital mode anymore. The rod always fires the legacy-style
 * instant stab burst, but with tighter radius control:
 *   - no visible TNT entities;
 *   - no falling-TNT delay or spawn-height config;
 *   - all block edits are clamped to the configured strike_radius footprint;
 *   - each X/Z column finds its own nearby surface so ledges scale up/down;
 *   - blast resistance controls how deep each column is carved;
 *   - entity damage is clamped to the same configured footprint.
 */
public class StabLogic {

    private static final int SURFACE_SCAN_UP = 8;
    private static final int SURFACE_SCAN_DOWN = 16;
    private static final float UNBREAKABLE_RESISTANCE = 1_000.0f;

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        summonEnhancedLegacy(world, x, y, z);
    }

    private static void summonEnhancedLegacy(ServerWorld world, int x, int y, int z) {
        int strikeY = y + StabConfig.columnStartAbove;
        int radius = Math.max(0, StabConfig.strikeRadius);

        spawnWarningParticles(world, x, strikeY, z, radius);
        playLegacyImpactSound(world, x, strikeY, z);
        performPrecisionImpact(world, x, y, z, radius);
    }

    private static void playLegacyImpactSound(ServerWorld world, int x, int strikeY, int z) {
        for (int i = 0; i < 3; i++) {
            world.playSound(null, x + 0.5, strikeY, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                    3.2f, 0.75f + (i * 0.08f));
        }
    }

    private static void performPrecisionImpact(ServerWorld world,
                                               int centerX, int targetY, int centerZ,
                                               int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int columnX = centerX + dx;
                int columnZ = centerZ + dz;
                int surfaceY = findColumnSurface(world, columnX, targetY, columnZ);
                double edgeFalloff = getEdgeFalloff(dx, dz, radius);

                spawnImpactParticles(world, columnX, surfaceY + StabConfig.columnStartAbove, columnZ, edgeFalloff);

                if (StabConfig.destroyTerrain) {
                    carveBoundedColumn(world, columnX, surfaceY, columnZ, edgeFalloff);
                }
            }
        }

        damageEntitiesInFootprint(world, centerX, targetY, centerZ, radius);
    }

    private static void damageEntitiesInFootprint(ServerWorld world,
                                                  int centerX, int targetY, int centerZ,
                                                  int radius) {
        double reach = radius + 0.75;
        Box box = new Box(
                centerX + 0.5 - reach, targetY - SURFACE_SCAN_DOWN, centerZ + 0.5 - reach,
                centerX + 0.5 + reach, targetY + SURFACE_SCAN_UP + StabConfig.columnStartAbove + 2, centerZ + 0.5 + reach
        );

        for (Entity entity : world.getOtherEntities(null, box, Entity::isAlive)) {
            double dx = Math.abs(entity.getX() - (centerX + 0.5));
            double dz = Math.abs(entity.getZ() - (centerZ + 0.5));
            double distance = Math.max(dx, dz);
            if (distance > reach) continue;

            float damage = (float) (StabConfig.explosionPower * 4.0 * (1.0 - (distance / (reach + 1.0)) * 0.65));
            if (damage > 0.0f) {
                entity.damage(world.getDamageSources().explosion(null, null), damage);
            }
        }
    }

    /**
     * Searches near the aimed height so nearby ledges inside the configured
     * radius scale up/down to their own exposed surface.
     */
    private static int findColumnSurface(ServerWorld world, int x, int targetY, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, targetY + SURFACE_SCAN_UP, z);
        int minY = targetY - SURFACE_SCAN_DOWN;

        for (int y = targetY + SURFACE_SCAN_UP; y >= minY; y--) {
            mutable.set(x, y, z);
            BlockState state = world.getBlockState(mutable);
            if (!state.isAir()) {
                return y;
            }
        }

        return targetY;
    }

    private static double getEdgeFalloff(int dx, int dz, int radius) {
        if (radius <= 0) return 1.0;
        double edgeDistance = Math.max(Math.abs(dx), Math.abs(dz)) / (double) radius;
        return 1.0 - (edgeDistance * 0.30);
    }

    private static void carveBoundedColumn(ServerWorld world,
                                           int x, int surfaceY, int z,
                                           double edgeFalloff) {
        int impactY = surfaceY + StabConfig.columnStartAbove;
        float surfaceResistance = getResistance(world, x, surfaceY, z);
        double surfaceScale = getResistanceScale(surfaceResistance);
        int depth = Math.max(1, (int) Math.ceil(StabConfig.explosionPower * surfaceScale * edgeFalloff * 1.75));
        double baseBudget = StabConfig.explosionPower * 4.0 * surfaceScale * edgeFalloff;

        BlockPos.Mutable mutable = new BlockPos.Mutable(x, impactY, z);
        for (int y = impactY; y >= surfaceY - depth; y--) {
            mutable.set(x, y, z);
            BlockState state = world.getBlockState(mutable);
            if (!canAffect(state)) continue;

            double depthFalloff = 1.0 - ((impactY - y) / (double) (depth + StabConfig.columnStartAbove + 1));
            double resistanceBudget = baseBudget * Math.max(0.15, depthFalloff);
            if (state.getBlock().getBlastResistance() <= resistanceBudget) {
                world.breakBlock(mutable, false);
            }
        }
    }

    private static float getResistance(ServerWorld world, int x, int y, int z) {
        BlockState state = world.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) return 0.0f;
        return state.getBlock().getBlastResistance();
    }

    private static double getResistanceScale(float resistance) {
        if (resistance <= 0.0f) return 1.35;
        if (resistance <= 1.0f) return 1.25;
        if (resistance <= 6.0f) return 1.0;
        if (resistance <= 30.0f) return 0.65;
        return 0.25;
    }

    private static boolean canAffect(BlockState state) {
        return !state.isAir() && state.getBlock().getBlastResistance() < UNBREAKABLE_RESISTANCE;
    }

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

    private static void spawnImpactParticles(ServerWorld world,
                                             int x, int y, int z,
                                             double edgeFalloff) {
        int smokeCount = Math.max(2, (int) Math.round(7 * edgeFalloff));
        int cloudCount = Math.max(1, (int) Math.round(5 * edgeFalloff));

        world.spawnParticles(ParticleTypes.EXPLOSION,
                x + 0.5, y + 0.25, z + 0.5, 1, 0.05, 0.05, 0.05, 0.0);
        world.spawnParticles(ParticleTypes.CLOUD,
                x + 0.5, y + 0.35, z + 0.5, cloudCount, 0.35, 0.18, 0.35, 0.02);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                x + 0.5, y + 0.45, z + 0.5, smokeCount, 0.40, 0.35, 0.40, 0.01);
    }
}
