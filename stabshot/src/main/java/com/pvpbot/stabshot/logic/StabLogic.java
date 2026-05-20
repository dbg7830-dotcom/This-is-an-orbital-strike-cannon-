package com.pvpbot.stabshot.logic;

import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * StabLogic — spawns a compressed vertical TNT column.
 *
 * Design goals (fixing OrbitalStrike+ complaints):
 *  - Starts only COLUMN_START_ABOVE blocks above the aimed block, NOT sky limit.
 *    Zero lag, instant visual, covers head + body of a player at that block.
 *  - Column of COLUMN_HEIGHT TNT entities placed downward.
 *  - Staggered fuse: bottom TNT has shortest fuse = explodes first = upward push.
 *  - Safe to call from any server-side context: fake player, scheduler, command.
 */
public class StabLogic {

    /** Blocks above the aimed surface where the column starts. */
    private static final int COLUMN_START_ABOVE = 2;

    /** Total TNT entities in the column. */
    private static final int COLUMN_HEIGHT = 8;

    /** Base fuse for the topmost TNT (ticks). Each lower TNT gets 1 fewer tick. */
    private static final int BASE_FUSE = 10;

    /**
     * Spawns the stab column at block coordinates (x, y, z).
     * y is the surface of the aimed block — column starts COLUMN_START_ABOVE above it.
     */
    public static void summonStab(ServerWorld world, int x, int y, int z) {
        int startY = y + COLUMN_START_ABOVE;
        for (int i = 0; i < COLUMN_HEIGHT; i++) {
            int tntY = startY - i;
            // Bottom TNT explodes first (shortest fuse) → upward push
            int fuse = Math.max(1, BASE_FUSE - i);
            spawnTnt(world, x, tntY, z, fuse);
        }
    }

    private static void spawnTnt(ServerWorld world, int x, int y, int z, int fuse) {
        TntEntity tnt = new TntEntity(world,
                x + 0.5, // center of block X
                y,
                z + 0.5, // center of block Z
                null     // no causing entity
        );
        tnt.setFuse(fuse);
        world.spawnEntity(tnt);
    }
}
