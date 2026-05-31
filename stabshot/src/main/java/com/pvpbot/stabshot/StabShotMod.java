package com.pvpbot.stabshot;

import com.pvpbot.stabshot.command.StabShotCommand;
import com.pvpbot.stabshot.config.StabConfig;
import com.pvpbot.stabshot.item.StabRodItem;
import com.pvpbot.stabshot.logic.StabLogic;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        LOGGER.info("[VulgarLog] Initializing Vulgar's OSC...");

        StabConfig.init();

        STAB_ROD = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "stab_rod"),
                new StabRodItem(new Item.Settings().maxCount(1).maxDamage(1))
        );

        // Tick-based delay queue â€” fires strikes on the server thread, exact tick timing
        ServerTickEvents.END_SERVER_TICK.register(StabLogic::onServerTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StabShotCommand.register(dispatcher));

        LOGGER.info("[StabShot] Ready. /giveob <amount|infinite> stab [player]");
    }
}
