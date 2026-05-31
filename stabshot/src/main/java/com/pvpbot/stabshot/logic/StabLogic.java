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
 * StabLogic — two custom no-visible-TNT strike modes.
 *
 * WEMMBU (default): finds the highest real block in the configured footprint,
 * starts the blast there, then cuts a square shaft down to one safe layer above
 * bedrock. It uses custom carving and explosion particles only; it never spawns
 * visible TNT entities and never calls vanilla createExplosion.
 *
 * LEGACY: enhanced configurable crater mode. blast_depth controls how deep the
 * strike can cut, while explosion_power only controls custom entity damage.
 */
public class StabLogic {

    private static final int SURFACE_SCAN_UP = 8;
    private static final int SURFACE_SCAN_DOWN = 16;
    private static final int WEMMBU_STOP_ABOVE_BOTTOM = 6;
    private static final float UNBREAKABLE_RESISTANCE = 1_000.0f;

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        if (StabConfig.isWemmbuMode()) {
            summonWemmbu(world, x, z);
        } else {
            summonLegacy(world, x, y, z);
        }
    }

    private static void summonWemmbu(ServerWorld world, int centerX, int centerZ) {
        int radius = Math.max(0, StabConfig.strikeRadius);
        int topY = findHighestSurfaceInFootprint(world, centerX, centerZ, radius);
        int bottomY = Math.max(world.getBottomY() + WEMMBU_STOP_ABOVE_BOTTOM, world.getBottomY() + 1);

        playCompressedImpactSound(world, centerX, topY, centerZ, 4.0f, 0.65f);
        spawnShaftBlastParticles(world, centerX, topY, centerZ, radius);

        if (StabConfig.destroyTerrain) {
            carveWemmbuShaft(world, centerX, centerZ, radius, topY, bottomY);
        }

        damageEntitiesInFootprint(world, centerX, bottomY, topY, centerZ, radius, 1.85f);
    }

    private static void summonLegacy(ServerWorld world, int x, int y, int z) {
        int strikeY = y + StabConfig.columnStartAbove;
        int radius = Math.max(0, StabConfig.strikeRadius);

        playCompressedImpactSound(world, x, strikeY, z, 3.2f, 0.78f);
        performLegacyImpact(world, x, y, z, radius);
    }

    private static void performLegacyImpact(ServerWorld world,
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
                    carveLegacyColumn(world, columnX, surfaceY, columnZ, edgeFalloff);
                }
            }
        }

        damageEntitiesInFootprint(world, centerX, targetY - SURFACE_SCAN_DOWN,
                targetY + SURFACE_SCAN_UP + StabConfig.columnStartAbove + 2, centerZ, radius, 1.35f);
    }

    private static void carveWemmbuShaft(ServerWorld world,
                                         int centerX, int centerZ,
                                         int radius, int topY, int bottomY) {
        for (int y = topY; y >= bottomY; y--) {
            double verticalProgress = (topY == bottomY) ? 0.0 : (topY - y) / (double) (topY - bottomY);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!canAffect(state)) continue;

                    double edgeFalloff = getEdgeFalloff(dx, dz, radius);
                    double ledgeNoise = getStableNoise(x, y, z);
                    boolean keepGripBlock = shouldKeepWemmbuGripBlock(radius, edgeFalloff, verticalProgress, ledgeNoise);
                    if (!keepGripBlock && canWemmbuBreak(state, edgeFalloff, verticalProgress, ledgeNoise)) {
                        world.breakBlock(pos, false);
                    }
                }
            }
        }
    }

    private static boolean shouldKeepWemmbuGripBlock(int radius,
                                                     double edgeFalloff,
                                                     double verticalProgress,
                                                     double noise) {
        if (radius <= 0) return false;
        boolean outerWall = edgeFalloff < 0.86;
        boolean ledgeBand = verticalProgress > 0.08 && verticalProgress < 0.92;
        return outerWall && ledgeBand && noise > 0.66;
    }

    private static boolean canWemmbuBreak(BlockState state,
                                          double edgeFalloff,
                                          double verticalProgress,
                                          double noise) {
        float resistance = state.getBlock().getBlastResistance();
        if (resistance >= UNBREAKABLE_RESISTANCE) return false;

        double compressedForce = 85.0 * edgeFalloff;
        compressedForce *= 1.0 - (verticalProgress * 0.18);
        compressedForce *= 0.92 + (noise * 0.16);
        return resistance <= compressedForce;
    }

    private static void carveLegacyColumn(ServerWorld world,
                                          int x, int surfaceY, int z,
                                          double edgeFalloff) {
        int impactY = surfaceY + StabConfig.columnStartAbove;
        int maxDepth = Math.max(1, StabConfig.blastDepth);
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, impactY, z);

        for (int y = impactY; y >= surfaceY - maxDepth; y--) {
            mutable.set(x, y, z);
            BlockState state = world.getBlockState(mutable);
            if (!canAffect(state)) continue;

            double progress = (impactY - y) / (double) (maxDepth + StabConfig.columnStartAbove + 1);
            double noise = getStableNoise(x, y, z);
            double ledgeChance = 0.20 + (progress * 0.18);
            boolean keepGripBlock = edgeFalloff < 0.88 && noise < ledgeChance;
            if (keepGripBlock) continue;

            double force = 28.0 * edgeFalloff * (1.0 - progress * 0.55);
            force *= 0.90 + (noise * 0.20);
            if (state.getBlock().getBlastResistance() <= force) {
                world.breakBlock(mutable, false);
            }
        }
    }

    private static int findHighestSurfaceInFootprint(ServerWorld world, int centerX, int centerZ, int radius) {
        int highest = world.getBottomY();
        int top = world.getTopY() - 1;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                for (int y = top; y >= world.getBottomY(); y--) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (!state.isAir()) {
                        highest = Math.max(highest, y);
                        break;
                    }
                }
            }
        }

        return highest;
    }

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

    private static void damageEntitiesInFootprint(ServerWorld world,
                                                  int centerX, int minY, int maxY, int centerZ,
                                                  int radius, float multiplier) {
        double reach = radius + 0.75;
        Box box = new Box(
                centerX + 0.5 - reach, minY, centerZ + 0.5 - reach,
                centerX + 0.5 + reach, maxY + 1.0, centerZ + 0.5 + reach
        );

        for (Entity entity : world.getOtherEntities(null, box, Entity::isAlive)) {
            double dx = Math.abs(entity.getX() - (centerX + 0.5));
            double dz = Math.abs(entity.getZ() - (centerZ + 0.5));
            double distance = Math.max(dx, dz);
            if (distance > reach) continue;

            float damage = (float) (StabConfig.explosionPower * 4.0 * multiplier
                    * (1.0 - (distance / (reach + 1.0)) * 0.55));
            if (damage > 0.0f) {
                entity.damage(world.getDamageSources().explosion(null, null), damage);
            }
        }
    }

    private static double getEdgeFalloff(int dx, int dz, int radius) {
        if (radius <= 0) return 1.0;
        double edgeDistance = Math.max(Math.abs(dx), Math.abs(dz)) / (double) radius;
        return 1.0 - (edgeDistance * 0.30);
    }

    private static boolean canAffect(BlockState state) {
        return !state.isAir() && state.getBlock().getBlastResistance() < UNBREAKABLE_RESISTANCE;
    }

    private static double getStableNoise(int x, int y, int z) {
        long seed = x * 3129871L ^ z * 116129781L ^ y * 42317861L;
        seed = seed * seed * 42317861L + seed * 11L;
        return ((seed >> 16) & 1023L) / 1023.0;
    }

    private static void playCompressedImpactSound(ServerWorld world,
                                                  int x, int y, int z,
                                                  float volume, float basePitch) {
        for (int i = 0; i < 4; i++) {
            world.playSound(null, x + 0.5, y, z + 0.5,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                    volume, basePitch + (i * 0.07f));
        }
    }

    private static void spawnShaftBlastParticles(ServerWorld world,
                                                 int centerX, int topY, int centerZ,
                                                 int radius) {
        int count = Math.max(6, (radius * 2 + 1) * 3);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                centerX + 0.5, topY + 0.5, centerZ + 0.5,
                count, radius + 0.35, 0.45, radius + 0.35, 0.0);
    }

    private static void spawnImpactParticles(ServerWorld world,
                                             int x, int y, int z,
                                             double edgeFalloff) {
        int count = Math.max(1, (int) Math.round(4 * edgeFalloff));
        world.spawnParticles(ParticleTypes.EXPLOSION,
                x + 0.5, y + 0.25, z + 0.5, count, 0.18, 0.10, 0.18, 0.0);
    }
}

