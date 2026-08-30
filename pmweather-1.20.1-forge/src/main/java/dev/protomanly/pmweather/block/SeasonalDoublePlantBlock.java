package dev.protomanly.pmweather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class SeasonalDoublePlantBlock extends SeasonalPlantBlock {
   public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

   public SeasonalDoublePlantBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{HALF});
   }

   protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
      DoubleBlockHalf doubleblockhalf = (DoubleBlockHalf)state.getValue(HALF);
      if (facing.getAxis() != Axis.Y
         || doubleblockhalf == DoubleBlockHalf.LOWER != (facing == Direction.UP)
         || facingState.is(this) && facingState.getValue(HALF) != doubleblockhalf) {
         return doubleblockhalf == DoubleBlockHalf.LOWER && facing == Direction.DOWN && !state.canSurvive(level, currentPos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
      } else {
         return Blocks.AIR.defaultBlockState();
      }
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockPos blockpos = context.getClickedPos();
      Level level = context.getLevel();
      return blockpos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(blockpos.above()).canBeReplaced(context)
         ? super.getStateForPlacement(context)
         : null;
   }

   public static void placeAt(LevelAccessor level, BlockState state, BlockPos pos, int flags) {
      BlockPos blockpos = pos.above();
      level.setBlock(pos, (BlockState)state.setValue(HALF, DoubleBlockHalf.LOWER), flags);
      level.setBlock(blockpos, (BlockState)state.setValue(HALF, DoubleBlockHalf.UPPER), flags);
   }

   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide) {
         if (player.isCreative()) {
            preventDropFromBottomPart(level, pos, state, player);
         } else {
            dropResources(state, level, pos, null, player, player.getMainHandItem());
         }
      }

      return super.playerWillDestroy(level, pos, state, player);
   }

   public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
      super.playerDestroy(level, player, pos, Blocks.AIR.defaultBlockState(), blockEntity, tool);
   }

   protected static void preventDropFromBottomPart(Level level, BlockPos pos, BlockState state, Player player) {
      DoubleBlockHalf doubleblockhalf = (DoubleBlockHalf)state.getValue(HALF);
      if (doubleblockhalf == DoubleBlockHalf.UPPER) {
         BlockPos blockpos = pos.below();
         BlockState blockstate = level.getBlockState(blockpos);
         if (blockstate.is(state.getBlock()) && blockstate.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockState blockstate1 = blockstate.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(blockpos, blockstate1, 35);
            level.levelEvent(player, 2001, blockpos, Block.getId(blockstate));
         }
      }
   }

   protected long getSeed(BlockState state, BlockPos pos) {
      return Mth.getSeed(pos.getX(), pos.below(state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
   }

   @Override
}
