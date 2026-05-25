package com.pvpbot.stabshot.client;

import com.pvpbot.stabshot.themesong.SongResourcePack;
import com.pvpbot.stabshot.themesong.ThemeSongCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackPosition;
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

        // Inject song pack once the client is ready
        ClientLifecycleEvents.CLIENT_STARTED.register(this::injectSongPack);
    }

    private void injectSongPack(MinecraftClient client) {
        ResourcePackManager manager = client.getResourcePackManager();

        // Register our provider so the pack shows up on every resource reload
        manager.registerProvider(profileAdder -> {
            ResourcePackProfile profile = ResourcePackProfile.create(
                    SongResourcePack.INSTANCE.getInfo(),
                    info -> SongResourcePack.INSTANCE,
                    ResourceType.CLIENT_RESOURCES,
                    new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false)
            );
            if (profile != null) {
                profileAdder.accept(profile);
            }
        });

        // Force an immediate reload so the pack is live without restarting
        client.reloadResourcesConcurrently();
    }
}
