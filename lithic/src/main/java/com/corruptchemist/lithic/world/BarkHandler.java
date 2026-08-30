package com.corruptchemist.lithic.world;

import com.corruptchemist.lithic.registry.LithicItems;
import com.corruptchemist.lithic.registry.LithicTags;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Stripping a log with a cutting tool also yields Bark.
 *
 * <p>The event is deliberately <em>not</em> cancelled: vanilla's own axe-stripping
 * still runs and changes the block. This handler only adds the drop, which keeps
 * it working for every wood type, modded ones included.
 */
public class BarkHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (!held.is(LithicTags.Items.CUTTING_TOOLS)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Player player = event.getEntity();

        // Simulate the strip to find out whether this block is strippable at all,
        // rather than guessing from tags and getting modded wood wrong.
        UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
        BlockState stripped = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, true);
        if (stripped == null) {
            return;
        }

        Block.popResource(level, pos.above(), new ItemStack(LithicItems.BARK.get()));
        level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 0.8F, 0.9F);
    }
}
