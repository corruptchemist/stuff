package com.corruptchemist.lithic.block;

import com.corruptchemist.lithic.LithicConfig;
import com.corruptchemist.lithic.compat.ToughAsNailsCompat;
import com.corruptchemist.lithic.registry.LithicItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hearth you have to work for. Lighting it needs a Fire Drill in hand and a
 * Tinder in the pack, and it fails more often than not.
 *
 * <p>Lit, it is the earliest warmth Tough As Nails will recognise, which is what
 * makes Age 4 the point where nights stop being lethal.
 */
public class FirePitBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Matches the 4px-tall model; a full cube here would be an invisible wall. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    private static final int LIGHT_CHANCE_PERCENT = 40;

    public FirePitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(LIT) || !stack.is(LithicItems.FIRE_DRILL.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!consumeTinder(player)) {
            player.displayClientMessage(
                    Component.translatable("lithic.firepit.no_tinder").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }

        // Working a drill is hard labour whether or not it catches.
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        ToughAsNailsCompat.addLabourExhaustion(player, LithicConfig.LABOUR_THIRST_EXHAUSTION.get() * 2.0D);

        if (level.getRandom().nextInt(100) < LIGHT_CHANCE_PERCENT) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.7F, 0.8F);
            player.displayClientMessage(
                    Component.translatable("lithic.firepit.failed").withStyle(ChatFormatting.GRAY), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /** Bare-handed you can smother it, but never light it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(LIT)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean consumeTinder(Player player) {
        if (player.isCreative()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.is(LithicItems.TINDER.get())) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }
}
