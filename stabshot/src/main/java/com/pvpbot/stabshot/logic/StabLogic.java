package com.pvpbot.stabshot.logic;

import com.pvpbot.stabshot.config.StabConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public class StabLogic {

    /**
     * Fires a stab column at block (x, y, z).
     * All values (power, height, timing) come from StabConfig / stabshot.properties.
     */
    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int startY  = y + StabConfig.columnStartAbove;
        MinecraftServer server = world.getServer();

        for (int i = 0; i < StabConfig.columnHeight; i++) {
            final double ex = x + 0.5;
            final double ey = startY - i;
            final double ez = z + 0.5;
            final long targetTick = server.getTicks() + (long)(i * StabConfig.tickDelayBetween);
            PendingExplosionQueue.schedule(world, ex, ey, ez, targetTick);
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
