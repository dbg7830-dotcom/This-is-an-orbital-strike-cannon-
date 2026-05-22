package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * StabLogic v4 â€” flat horizontal strike pattern.
 *
 * Previous design: vertical column from aim to bedrock.
 * Problem: each explosion launched the player upward into the next one above,
 * creating an unintended TNT cannon chain that one-shots everyone.
 *
 * New design: flat grid of explosions at a single Y level (aim + columnStartAbove).
 * The grid is a cross pattern expanding outward by `radius` blocks in X and Z.
 * Default radius 1 = 3x3 area (9 explosions). Radius 2 = 5x5 (25 explosions).
 *
 * All explosions fire in one tick at the same height = consistent flat damage,
 * no chain critting, no launching, no bedrock cratering.
 *
 * Config options in stabshot.properties:
 *   explosion_power    â€” power per blast (default 2.5)
 *   column_start_above â€” Y offset above aimed block (default 1)
 *   strike_radius      â€” grid half-width in blocks (default 1 = 3x3)
 *   destroy_terrain    â€” true/false block destruction (default false)
 */
public class StabLogic {

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int strikeY  = y + StabConfig.columnStartAbove;
        int radius   = StabConfig.strikeRadius;

        // Fire a flat grid centred on (x, strikeY, z)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                fireExplosion(world, x + dx + 0.5, strikeY, z + dz + 0.5);
            }
        }
    }

    static void fireExplosion(ServerWorld world, double x, double y, double z) {
        World.ExplosionSourceType type = StabConfig.destroyTerrain
                ? World.ExplosionSourceType.TNT
                : World.ExplosionSourceType.NONE; // NONE = entity damage only, no block break

        world.createExplosion(
                null,
                null,
                null,
                x, y, z,
                StabConfig.explosionPower,
                false,
                type
        );
    }
}
