package com.corruptchemist.lithic.compat;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.block.FirePitBlock;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.ResearchManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import toughasnails.api.temperature.IProximityBlockModifier;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

/**
 * All Tough As Nails coupling lives here.
 *
 * <p>TAN is a hard dependency, so there is no "is it loaded" guard: if it is absent
 * the mod does not load at all. What <em>is</em> guarded is TAN's own feature
 * toggles, since a pack can switch thirst or temperature off independently.
 *
 * <p>The interesting integration is {@link #nightChillModifier()}: until the player
 * has learned how to make fire, nights are a full temperature step colder. That is
 * what turns "I have not researched firecraft yet" from a crafting inconvenience
 * into something the survival systems will actually kill you over.
 */
public final class ToughAsNailsCompat {

    /** Research that lifts the early-game night penalty. Optional in the datapack. */
    public static final ResourceLocation FIRECRAFT = Lithic.id("firecraft");

    public static void register() {
        TemperatureHelper.registerProximityBlockModifier(proximityModifier());
        TemperatureHelper.registerPlayerTemperatureModifier(nightChillModifier());
        Lithic.LOGGER.info("Registered Lithic modifiers into Tough As Nails");
    }

    /** A lit fire pit reads as a heat source to TAN, an unlit one as nothing. */
    private static IProximityBlockModifier proximityModifier() {
        return (level, pos, state) -> {
            if (state.getBlock() instanceof FirePitBlock && state.getValue(FirePitBlock.LIT)) {
                return IProximityBlockModifier.Type.HEATING;
            }
            return IProximityBlockModifier.Type.NONE;
        };
    }

    private static toughasnails.api.temperature.IPlayerTemperatureModifier nightChillModifier() {
        return (player, current) -> {
            // No-op unless the pack actually ships the firecraft node.
            if (!ResearchManager.exists(FIRECRAFT)) {
                return current;
            }
            if (KnowledgeEvents.knows(player, FIRECRAFT)) {
                return current;
            }
            return isNight(player) ? current.decrement(1) : current;
        };
    }

    private static boolean isNight(Player player) {
        long timeOfDay = player.level().getDayTime() % 24000L;
        return timeOfDay >= 13000L && timeOfDay < 23000L;
    }

    /**
     * Adds thirst exhaustion for heavy physical work. Exhaustion is TAN's own
     * accumulator, so this drains the thirst bar at TAN's configured rate rather
     * than yanking the value directly.
     */
    public static void addLabourExhaustion(Player player, double amount) {
        if (amount <= 0.0D || player.level().isClientSide()) {
            return;
        }
        if (!ThirstHelper.isThirstEnabled()) {
            return;
        }
        IThirst thirst = ThirstHelper.getThirst(player);
        if (thirst != null) {
            thirst.addExhaustion((float) amount);
        }
    }

    /** Current temperature band for the player, for use in gameplay rules. */
    public static TemperatureLevel temperatureOf(Player player) {
        if (!TemperatureHelper.isTemperatureEnabled()) {
            return TemperatureLevel.NEUTRAL;
        }
        return TemperatureHelper.getTemperatureForPlayer(player);
    }

    private ToughAsNailsCompat() {}
}
