package com.pvpbot.stabshot.logic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * StabLogic — fires a vertical stab column using direct world explosions.
 *
 * v2 fixes:
 *  - No TntEntity — fully invisible, no floating TNT model shown.
 *  - Direct world.createExplosion() at power 6.5f (vanilla TNT = 4.0f).
 *  - True tick-staggered scheduling via a counter checked on server tick.
 *    Each blast fires 2 ticks after the previous one.
 *  - Column starts 2 blocks above aimed surface = hits head AND body.
 */
public class StabLogic {

    private static final int   COLUMN_START_ABOVE = 2;
    private static final int   COLUMN_HEIGHT      = 8;
    private static final float EXPLOSION_POWER    = 9.5f;
    private static final int   TICK_DELAY_BETWEEN = 2;

    /**
     * Fires a stab column at block (x, y, z).
     * Explosions are staggered using the pending queue — each fires 2 ticks apart.
     */
    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int startY = y + COLUMN_START_ABOVE;
        MinecraftServer server = world.getServer();

        for (int i = 0; i < COLUMN_HEIGHT; i++) {
            final double ex = x + 0.5;
            final double ey = startY - i;
            final double ez = z + 0.5;
            final long targetTick = server.getTicks() + (long)(i * TICK_DELAY_BETWEEN);

            // Register a one-shot listener that fires at targetTick
            PendingExplosionQueue.schedule(world, ex, ey, ez, targetTick);
        }
    }

    static void fireExplosion(ServerWorld world, double x, double y, double z) {
        world.createExplosion(
                null,
                null,
                null,
                x, y, z,
                EXPLOSION_POWER,
                false,
                World.ExplosionSourceType.TNT
        );
    }
}
