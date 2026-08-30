package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class CharredLogBlock extends RotatedPillarBlock {
   public CharredLogBlock(Properties properties) {
      super(properties);
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      if (random.nextInt(40) == 0 && ServerConfig.doRotting) {
         BlockState newstate = (BlockState)((Block)ModBlocks.ROTTED_LOG.get()).defaultBlockState().setValue(AXIS, (Axis)state.getValue(AXIS));
         level.setBlockAndUpdate(pos, newstate);
      }
   }
}
