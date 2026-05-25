package com.pvpbot.stabshot.client;

import com.pvpbot.stabshot.themesong.SongResourcePack;
import com.pvpbot.stabshot.themesong.ThemeSongCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;

/**
 * StabShotClient — client entrypoint.
 * Registers /ts commands and injects the dynamic song resource pack.
 */
@Environment(EnvType.CLIENT)
public class StabShotClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register /ts play, /ts stop, /ts list
        ThemeSongCommand.register();

        // Register our dynamic song pack with Minecraft's resource pack manager.
        // Done here (before world load) so it's available during the first resource reload.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            injectSongPack(client);
        }
        // Also register for when client is available later (safety net)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
                .CLIENT_STARTED.register(this::injectSongPack);
    }

    private void injectSongPack(MinecraftClient client) {
        ResourcePackProfile profile = ResourcePackProfile.create(
                "stabshot_songs",
                Text.literal("StabShot Songs"),
                true,  // required = always active
                id -> SongResourcePack.INSTANCE,
                ResourceType.CLIENT_RESOURCES,
                ResourcePackProfile.InsertionPosition.TOP,
                ResourcePackSource.BUILTIN
        );
        if (profile != null) {
            client.getResourcePackManager().addPack(profile);
        }
    }
}
