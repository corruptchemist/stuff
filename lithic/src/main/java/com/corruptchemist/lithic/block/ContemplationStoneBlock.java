package com.corruptchemist.lithic.block;

import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.Research;
import com.corruptchemist.lithic.knowledge.ResearchManager;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where insight is turned into knowledge. Right-clicking spends insight on the
 * cheapest research the player has discovered and has the prerequisites for.
 */
public class ContemplationStoneBlock extends Block {

    public ContemplationStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        List<ResourceLocation> options = KnowledgeEvents.learnable(player);
        if (options.isEmpty()) {
            player.displayClientMessage(Component.translatable("lithic.contemplation.nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        // Cheapest first, so a player is never forced to save up before making progress.
        options.sort(Comparator.comparingInt(id -> {
            Research research = ResearchManager.get(id);
            return research == null ? Integer.MAX_VALUE : research.insightCost();
        }));

        int insight = KnowledgeEvents.of(player).getInsight();
        for (ResourceLocation id : options) {
            Research research = ResearchManager.get(id);
            if (research == null) continue;
            if (research.insightCost() > insight) continue;

            if (KnowledgeEvents.tryLearn(player, id) == KnowledgeEvents.LearnResult.LEARNED) {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.0F);
                return InteractionResult.CONSUME;
            }
        }

        // Something is available but nothing is affordable: say exactly how short they are.
        Research cheapest = ResearchManager.get(options.get(0));
        int needed = cheapest == null ? 0 : cheapest.insightCost();
        player.displayClientMessage(Component.translatable("lithic.contemplation.insufficient", insight, needed)
                .withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }
}
