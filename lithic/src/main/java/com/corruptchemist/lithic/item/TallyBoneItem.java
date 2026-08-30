package com.corruptchemist.lithic.item;

import com.corruptchemist.lithic.knowledge.Knowledge;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.Research;
import com.corruptchemist.lithic.knowledge.ResearchManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A notched bone the player reads their own progress off. Deliberately a chat
 * readout rather than a research GUI: the information is what matters, and a
 * scrollable tree screen is a lot of surface area for very little extra clarity.
 */
public class TallyBoneItem extends Item {

    public TallyBoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(held);
        }

        Knowledge knowledge = KnowledgeEvents.of(player);
        player.sendSystemMessage(Component.translatable("lithic.tally.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("lithic.tally.insight", knowledge.getInsight())
                .withStyle(ChatFormatting.AQUA));

        List<ResourceLocation> learnable = KnowledgeEvents.learnable(player);
        if (learnable.isEmpty()) {
            player.sendSystemMessage(Component.translatable("lithic.tally.none")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("lithic.tally.available")
                    .withStyle(ChatFormatting.YELLOW));
            for (ResourceLocation id : learnable) {
                Research research = ResearchManager.get(id);
                int cost = research == null ? 0 : research.insightCost();
                boolean affordable = cost <= knowledge.getInsight();
                player.sendSystemMessage(Component.literal(" - ")
                        .append(Component.translatable(Research.titleKey(id)))
                        .append(Component.literal(" (" + cost + ")"))
                        .withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }

        player.sendSystemMessage(Component.translatable("lithic.tally.learned", knowledge.learnedView().size())
                .withStyle(ChatFormatting.DARK_GRAY));
        return InteractionResultHolder.success(held);
    }
}
