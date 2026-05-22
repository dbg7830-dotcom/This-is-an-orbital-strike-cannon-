package com.pvpbot.stabshot;

import com.pvpbot.stabshot.command.StabShotCommand;
import com.pvpbot.stabshot.config.StabConfig;
import com.pvpbot.stabshot.item.StabRodItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StabShotMod implements ModInitializer {

    public static final String MOD_ID = "stabshot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static StabRodItem STAB_ROD;

    @Override
    public void onInitialize() {
        LOGGER.info("[StabShot] Initializing...");

        // Load config immediately â€” SERVER_STARTING doesn't fire reliably in singleplayer
        StabConfig.load();

        STAB_ROD = Registry.register(

        STAB_ROD = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "stab_rod"),
                // maxDamage(1) = 1 durability point â€” finite rods break after 1 use
                // maxCount(1) = non-stackable
                new StabRodItem(new Item.Settings().maxCount(1).maxDamage(1))
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StabShotCommand.register(dispatcher));

        LOGGER.info("[StabShot] Ready. /giveob <amount|infinite> stab [player]");
    }
}
