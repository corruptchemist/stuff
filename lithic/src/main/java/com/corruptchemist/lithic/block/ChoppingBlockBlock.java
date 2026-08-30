package com.corruptchemist.lithic.block;

import com.corruptchemist.lithic.LithicConfig;
import com.corruptchemist.lithic.compat.ToughAsNailsCompat;
import com.corruptchemist.lithic.registry.LithicItems;
import com.corruptchemist.lithic.registry.LithicTags;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a Rough Log becomes Split Wood.
 *
 * <p>Two beats rather than one: load the log, then split it. The second beat is
 * what makes the axe a required tool at this step rather than an optional one,
 * and it costs the axe durability every time.
 */
public class ChoppingBlockBlock extends Block {
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public ChoppingBlockBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOADED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        boolean loaded = state.getValue(LOADED);

        // Beat one: set a log on the block.
        if (!loaded && stack.is(LithicItems.ROUGH_LOG.get())) {
            if (!level.isClientSide()) {
                stack.shrink(1);
                level.setBlock(pos, state.setValue(LOADED, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9F, 0.8F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        // Beat two: split it.
        if (loaded && stack.is(LithicTags.Items.CUTTING_TOOLS)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(LOADED, false), Block.UPDATE_ALL);
                Block.popResource(level, pos.above(), new ItemStack(LithicItems.SPLIT_WOOD.get(), 2));
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);

                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(LOADED)) {
                Block.popResource(level, pos, new ItemStack(LithicItems.ROUGH_LOG.get()));
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
