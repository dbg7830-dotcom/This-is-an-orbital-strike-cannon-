package com.pvpbot.stabshot.client;

import com.pvpbot.stabshot.themesong.ThemeSongCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * StabShotClient — client entrypoint.
 */
@Environment(EnvType.CLIENT)
public class StabShotClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ThemeSongCommand.register();
    }
}
