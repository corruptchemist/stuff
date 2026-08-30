package dev.protomanly.pmweather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class ColoredFallingCharredBlock extends Block implements Fallable {
   private final ColorRGBA dustColor;

   public ColoredFallingCharredBlock(ColorRGBA dustColor, Properties properties) {
      super(properties);
      this.dustColor = dustColor;
   }

   public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
      return this.dustColor.rgba();
   }

   protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      level.scheduleTick(pos, this, 2);
   }

   protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
      level.scheduleTick(currentPos, this, 2);
      return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight()) {
         FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level, pos, state);
         fallingblockentity.disableDrop();
         this.falling(fallingblockentity);
      }
   }

   public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
      BlockState state = level.getBlockState(pos);
      BlockPos placePos = pos;
      if (!isFree(state)) {
         placePos = pos.above();
         state = level.getBlockState(placePos);
      }

      if (isFree(state)) {
         BlockState newState = fallingBlock.getBlockState();
         level.setBlockAndUpdate(placePos, newState);
      }
   }

   public static boolean isFree(BlockState state) {
      return state.isAir() || state.is(BlockTags.FIRE) || state.liquid() || state.canBeReplaced();
   }

   protected void falling(FallingBlockEntity entity) {
   }

   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      if (random.nextInt(16) == 0) {
         BlockPos blockpos = pos.below();
         if (isFree(level.getBlockState(blockpos))) {
            ParticleUtils.spawnParticleBelow(level, pos, random, new BlockParticleOption(ParticleTypes.FALLING_DUST, state));
         }
      }
   }
}
