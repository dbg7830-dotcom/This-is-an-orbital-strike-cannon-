package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * StabLogic v3 — instant single-tick stab column from aimed block to bedrock.
 *
 * Changes:
 *  - ALL explosions fire in ONE tick (no staggering, no PendingExplosionQueue needed).
 *  - Column runs from aimed block DOWNWARD to bedrock (y=0), not upward.
 *  - COLUMN_START_ABOVE blocks above aim = covers head + body first.
 *  - Bedrock (y <= 0) is never targeted — loop stops there.
 *  - Power lowered to 4.0f (matches OrbitalStrike+ feel — ~4 hearts on full netherite).
 *  - Still invisible: no TntEntity, direct world.createExplosion().
 */
public class StabLogic {

    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int startY = y + StabConfig.columnStartAbove;

        // Fire from startY down to bedrock (y = 1, never break bedrock at y = 0)
        for (int ey = startY; ey >= 1; ey--) {
            fireExplosion(world, x + 0.5, ey, z + 0.5);
        }
    }

    static void fireExplosion(ServerWorld world, double x, double y, double z) {
        world.createExplosion(
                null,
                null,
                null,
                x, y, z,
                StabConfig.explosionPower,
                false,
                World.ExplosionSourceType.TNT
        );
    }
}
