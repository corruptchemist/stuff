package com.corruptchemist.lithic.block;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.menu.CrudeWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The only place Lithic's gated recipes resolve. Deliberately not a crafting table:
 * the vanilla table keeps working for whatever vanilla recipes a pack leaves intact,
 * while everything on the Lithic progression path has to come through here, where a
 * player reference is available and the research check can actually be enforced.
 */
public class CrudeWorkbenchBlock extends Block {
    public static final Component TITLE = Component.translatable("container.lithic.crude_workbench");

    public CrudeWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        KnowledgeEvents.fireStation(player, Lithic.id("crude_workbench"));
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> new CrudeWorkbenchMenu(
                        containerId, inventory, ContainerLevelAccess.create(level, pos)),
                TITLE), pos);
        return InteractionResult.CONSUME;
    }
}
