package com.corruptchemist.lithic.knowledge;

import com.corruptchemist.lithic.LithicConfig;
import com.corruptchemist.lithic.registry.LithicAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The bridge between "the player did a thing" and "the player now knows a thing",
 * plus the public API the rest of the mod uses to ask what a player knows.
 */
public class KnowledgeEvents {

    // ---------------------------------------------------------------- API ----

    public static Knowledge of(Player player) {
        return player.getData(LithicAttachments.KNOWLEDGE);
    }

    /** Re-sets the attachment so NeoForge marks it dirty and syncs it. */
    public static void commit(Player player, Knowledge knowledge) {
        player.setData(LithicAttachments.KNOWLEDGE, knowledge);
    }

    /** Whether {@code player} may use a recipe requiring {@code required}. */
    public static boolean knows(Player player, ResourceLocation required) {
        if (!LithicConfig.RESEARCH_GATING.get()) return true;
        if (player.isCreative()) return true;
        return of(player).hasLearned(required);
    }

    /** Research the player has discovered, has the parents for, and can afford. */
    public static List<ResourceLocation> learnable(Player player) {
        Knowledge knowledge = of(player);
        List<ResourceLocation> out = new ArrayList<>();
        ResearchManager.all().forEach((id, research) -> {
            if (knowledge.hasLearned(id) || !knowledge.hasDiscovered(id)) return;
            if (!research.parents().stream().allMatch(knowledge::hasLearned)) return;
            out.add(id);
        });
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    public enum LearnResult {
        LEARNED, ALREADY_KNOWN, NOT_DISCOVERED, MISSING_PARENT, NOT_ENOUGH_INSIGHT, UNKNOWN
    }

    public static LearnResult tryLearn(Player player, ResourceLocation id) {
        Research research = ResearchManager.get(id);
        if (research == null) return LearnResult.UNKNOWN;

        Knowledge knowledge = of(player);
        if (knowledge.hasLearned(id)) return LearnResult.ALREADY_KNOWN;
        if (!knowledge.hasDiscovered(id)) return LearnResult.NOT_DISCOVERED;
        for (ResourceLocation parent : research.parents()) {
            if (!knowledge.hasLearned(parent)) return LearnResult.MISSING_PARENT;
        }
        if (!knowledge.spendInsight(research.insightCost())) return LearnResult.NOT_ENOUGH_INSIGHT;

        knowledge.learn(id);
        commit(player, knowledge);
        announce(player, Component.translatable("lithic.research.learned",
                Component.translatable(Research.titleKey(id))).withStyle(ChatFormatting.GOLD));
        return LearnResult.LEARNED;
    }

    // ----------------------------------------------------------- dispatch ----

    public static void fireItem(Player player, ResearchTrigger.TriggerType type, ItemStack stack) {
        dispatch(player, type, trigger -> trigger.matchesItem(stack));
    }

    public static void fireBlock(Player player, BlockState state) {
        dispatch(player, ResearchTrigger.TriggerType.BREAK_BLOCK, trigger -> trigger.matchesBlock(state));
    }

    public static void fireEntity(Player player, EntityType<?> entityType) {
        dispatch(player, ResearchTrigger.TriggerType.KILL_ENTITY, trigger -> trigger.matchesEntity(entityType));
    }

    public static void fireStation(Player player, ResourceLocation stationId) {
        dispatch(player, ResearchTrigger.TriggerType.USE_STATION, trigger -> trigger.matchesId(stationId));
    }

    private static void dispatch(Player player, ResearchTrigger.TriggerType type, Predicate<ResearchTrigger> test) {
        if (player.level().isClientSide()) return;
        if (ResearchManager.all().isEmpty()) return;

        Knowledge knowledge = of(player);
        boolean changed = false;

        for (var entry : ResearchManager.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (knowledge.hasDiscovered(id)) continue;

            for (ResearchTrigger trigger : entry.getValue().triggers()) {
                if (trigger.type() != type || !test.test(trigger)) continue;
                if (knowledge.discover(id)) {
                    knowledge.addInsight(trigger.insight());
                    changed = true;
                    announce(player, Component.translatable("lithic.research.discovered",
                            Component.translatable(Research.titleKey(id))).withStyle(ChatFormatting.AQUA));
                }
                break;
            }
        }

        if (changed) {
            commit(player, knowledge);
            // Free nodes are pure signposting, so learn them immediately rather than
            // making the player walk to a stone to click a zero-cost button. Learning
            // one can expose another, so keep going until the set stops growing.
            boolean learnedAny = true;
            while (learnedAny) {
                learnedAny = false;
                for (ResourceLocation id : learnable(player)) {
                    Research research = ResearchManager.get(id);
                    if (research != null && research.insightCost() == 0
                            && tryLearn(player, id) == LearnResult.LEARNED) {
                        learnedAny = true;
                    }
                }
            }
        }
    }

    private static void announce(Player player, Component message) {
        if (!LithicConfig.ANNOUNCE_DISCOVERIES.get()) return;
        player.displayClientMessage(message, false);
    }

    // ------------------------------------------------------------- events ----

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        fireItem(event.getPlayer(), ResearchTrigger.TriggerType.OBTAIN_ITEM, event.getOriginalStack());
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        fireItem(event.getEntity(), ResearchTrigger.TriggerType.CRAFT_ITEM, event.getCrafting());
        // Crafting a thing is also the most reliable way to end up holding it.
        fireItem(event.getEntity(), ResearchTrigger.TriggerType.OBTAIN_ITEM, event.getCrafting());
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        fireBlock(event.getPlayer(), event.getState());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            fireEntity(player, event.getEntity().getType());
        }
    }
}
