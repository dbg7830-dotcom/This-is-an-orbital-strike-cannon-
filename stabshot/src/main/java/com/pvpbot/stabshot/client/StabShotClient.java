package com.pvpbot.stabshot.client;

import com.pvpbot.stabshot.themesong.SongResourcePack;
import com.pvpbot.stabshot.themesong.ThemeSongCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * StabShotClient — client entrypoint.
 * Registers /ts commands and injects the dynamic song resource pack.
 */
@Environment(EnvType.CLIENT)
public class StabShotClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ThemeSongCommand.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(this::injectSongPack);
    }

    private void injectSongPack(MinecraftClient client) {
        ResourcePackManager manager = client.getResourcePackManager();

        // Build our provider — PackFactory has two abstract methods so must be
        // an anonymous class, not a lambda.
        ResourcePackProvider provider = profileAdder -> {
            ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
                @Override
                public ResourcePack open(ResourcePackInfo info) {
                    return SongResourcePack.INSTANCE;
                }
                @Override
                public ResourcePack openWithOverlays(ResourcePackInfo info,
                                                     ResourcePackProfile.Metadata metadata) {
                    return SongResourcePack.INSTANCE;
                }
            };

            ResourcePackProfile profile = ResourcePackProfile.create(
                    SongResourcePack.INSTANCE.getInfo(),
                    factory,
                    ResourceType.CLIENT_RESOURCES,
                    new ResourcePackPosition(
                            true,
                            ResourcePackProfile.InsertionPosition.TOP,
                            false
                    )
            );
            if (profile != null) {
                profileAdder.accept(profile);
            }
        };

        // ResourcePackManager.providers is private final — inject via reflection.
        try {
            Field providersField = ResourcePackManager.class.getDeclaredField("providers");
            providersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<ResourcePackProvider> providers =
                    (Set<ResourcePackProvider>) providersField.get(manager);
            providers.add(provider);
        } catch (Exception e) {
            throw new RuntimeException("StabShot: failed to inject resource pack provider", e);
        }

        // Rescan so the profile is picked up immediately.
        manager.scanPacks();
        manager.enable("stabshot_songs");
    }
}
