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
 * The first thing a new player can do, and for a while the only thing.
 *
 * <p>Two interactions, both performed against bare stone with no crafting grid
 * involved, because at this point the player has no crafting grid worth the name:
 * <ul>
 *   <li>flint against stone produces a shard, or destroys the flint</li>
 *   <li>two shards worked together produce a blade</li>
 * </ul>
 */
public class KnappingHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(LithicTags.Blocks.KNAPPING_SURFACE)) {
            return;
        }

        ItemStack held = event.getItemStack();
        Player player = event.getEntity();

        if (held.is(Items.FLINT)) {
            event.setCanceled(true);
            this.knapFlint(level, pos, player, held);
        } else if (held.is(LithicItems.FLINT_SHARD.get()) && held.getCount() >= 2) {
            event.setCanceled(true);
            this.workShards(level, pos, player, held);
        }
    }

    /** Flint against stone: a shard, or nothing at all. */
    private void knapFlint(Level level, BlockPos pos, Player player, ItemStack held) {
        held.shrink(1);
        ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get());
        KnowledgeEvents.fireStation(player, Lithic.id("knapping"));

        if (level.getRandom().nextInt(100) < LithicConfig.KNAPPING_SUCCESS_PERCENT.get()) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8F, 1.4F);
            Block.popResource(level, pos.above(), new ItemStack(LithicItems.FLINT_SHARD.get()));
        } else {
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
            player.displayClientMessage(
                    Component.translatable("lithic.knapping.shattered").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Two shards worked against each other: a blade. This one does not fail. */
    private void workShards(Level level, BlockPos pos, Player player, ItemStack held) {
        held.shrink(2);
        ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get());
        KnowledgeEvents.fireStation(player, Lithic.id("knapping"));

        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.9F, 1.1F);
        Block.popResource(level, pos.above(), new ItemStack(LithicItems.FLINT_BLADE.get()));
        player.displayClientMessage(
                Component.translatable("lithic.knapping.blade").withStyle(ChatFormatting.GREEN), true);
    }
}
