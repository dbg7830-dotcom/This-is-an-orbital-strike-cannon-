package com.pvpbot.stabshot.logic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * PendingExplosionQueue — tick-accurate scheduler for staggered stab explosions.
 *
 * Registered once on mod init via ServerTickEvents.END_SERVER_TICK.
 * Each entry stores the ServerWorld, position, and the absolute server tick
 * on which it should fire. Each tick, the queue is drained of ready entries.
 *
 * This is the correct approach for true multi-tick delays on a Fabric server.
 * server.execute() queues work for the SAME tick, not a future one.
 */
public class PendingExplosionQueue {

    private static final List<Entry> QUEUE = new ArrayList<>();

    /** Called once from StabShotMod.onInitialize() to register the tick listener. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTick = server.getTicks();
            Iterator<Entry> it = QUEUE.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (currentTick >= e.targetTick) {
                    StabLogic.fireExplosion(e.world, e.x, e.y, e.z);
                    it.remove();
                }
            }
        });
    }

    /** Schedule one explosion at world position (x, y, z) to fire on targetTick. */
    public static void schedule(ServerWorld world, double x, double y, double z, long targetTick) {
        QUEUE.add(new Entry(world, x, y, z, targetTick));
    }

    private record Entry(ServerWorld world, double x, double y, double z, long targetTick) {}
}
