package com.corruptchemist.lithic.world;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.LithicConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * World-scoped rules Lithic imposes on load.
 *
 * <p>Turning off natural regeneration is the single highest-impact change in the
 * whole mod: it converts food from "heals you" into "stops you starving", and makes
 * every unnecessary hit matter for the rest of that life.
 */
public class WorldRuleHandler {

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!LithicConfig.DISABLE_NATURAL_REGEN.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        GameRules rules = server.getGameRules();
        if (rules.getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
            Lithic.LOGGER.info("Lithic disabled naturalRegeneration; bandage yourself or die tired");
        }
    }
}
