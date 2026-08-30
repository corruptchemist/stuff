package dev.protomanly.pmweather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CharredDoorBlock extends DoorBlock {
   public CharredDoorBlock(BlockSetType type, Properties properties) {
      super(type, properties);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      return !level.isClientSide() && this.tryBreak(player, level, pos)
         ? InteractionResult.sidedSuccess(level.isClientSide())
         : super.useWithoutItem(state, level, pos, player, hitResult);
   }

   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
      boolean flag = level.hasNeighborSignal(pos)
         || level.hasNeighborSignal(pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
      if (this.defaultBlockState().is(block) || flag == (Boolean)state.getValue(POWERED) || !this.tryBreak(null, level, pos)) {
         super.neighborChanged(state, level, pos, block, fromPos, isMoving);
      }
   }

   public boolean tryBreak(@Nullable Entity entity, LevelAccessor level, BlockPos pos) {
      return level.getRandom().nextInt(2) == 0 ? level.destroyBlock(pos, true, entity) : false;
   }
}
