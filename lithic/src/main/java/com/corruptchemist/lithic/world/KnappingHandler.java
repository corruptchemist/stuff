package com.corruptchemist.lithic.world;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.LithicConfig;
import com.corruptchemist.lithic.compat.ToughAsNailsCompat;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.registry.LithicItems;
import com.corruptchemist.lithic.registry.LithicTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The very first thing a new player can do: strike flint against stone and hope.
 *
 * <p>It fails more often than it succeeds, which is the point. Flint is abundant;
 * patience is the actual resource being spent.
 */
public class KnappingHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (!held.is(Items.FLINT)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(LithicTags.Blocks.KNAPPING_SURFACE)) {
            return;
        }

        event.setCanceled(true);

        Player player = event.getEntity();
        held.shrink(1);
        ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get());
        KnowledgeEvents.fireStation(player, Lithic.id("knapping"));

        boolean success = level.getRandom().nextInt(100) < LithicConfig.KNAPPING_SUCCESS_PERCENT.get();
        if (success) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8F, 1.4F);
            Block.popResource(level, pos.above(), new ItemStack(LithicItems.FLINT_SHARD.get()));
        } else {
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
            player.displayClientMessage(Component.translatable("lithic.knapping.shattered")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
