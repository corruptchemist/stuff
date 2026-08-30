package com.corruptchemist.lithic.world;

import com.corruptchemist.lithic.LithicConfig;
import com.corruptchemist.lithic.compat.ToughAsNailsCompat;
import com.corruptchemist.lithic.registry.LithicTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * "You cannot punch a tree."
 *
 * <p>Cancelling {@link BlockEvent.BreakEvent} outright is deliberate: it stops the
 * block breaking at all rather than merely removing the drop, so the player gets
 * immediate, unambiguous feedback instead of quietly wasting swings.
 */
public class ToolGatingHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) {
            return;
        }

        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();

        boolean needsCutting = LithicConfig.REQUIRE_CUTTING_TOOL.get()
                && state.is(LithicTags.Blocks.REQUIRES_CUTTING_TOOL);
        boolean needsStriking = LithicConfig.REQUIRE_STRIKING_TOOL.get()
                && state.is(LithicTags.Blocks.REQUIRES_STRIKING_TOOL);

        if (needsCutting && !tool.is(LithicTags.Items.CUTTING_TOOLS)) {
            this.refuse(event, player, tool, "lithic.gate.cutting");
            return;
        }
        if (needsStriking && !tool.is(LithicTags.Items.STRIKING_TOOLS)) {
            this.refuse(event, player, tool, "lithic.gate.striking");
            return;
        }

        // Getting through it counts as real work.
        if (needsCutting || needsStriking) {
            ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get());
        }
    }

    private void refuse(BlockEvent.BreakEvent event, Player player, ItemStack tool, String messageKey) {
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);

        if (LithicConfig.BARE_HAND_HURTS.get() && tool.isEmpty()) {
            float damage = LithicConfig.BARE_HAND_DAMAGE.get().floatValue();
            if (damage > 0.0F) {
                player.hurt(player.damageSources().generic(), damage);
            }
        }
    }
}
