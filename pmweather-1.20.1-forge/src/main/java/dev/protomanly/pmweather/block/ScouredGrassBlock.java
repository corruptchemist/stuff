package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class ScouredGrassBlock extends Block {
   public ScouredGrassBlock(Properties properties) {
      super(properties);
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);

      for (int i = 0; i < 4; i++) {
         BlockPos randomSample = pos.offset(PMWeather.RANDOM.nextInt(-2, 3), PMWeather.RANDOM.nextInt(-2, 3), PMWeather.RANDOM.nextInt(-2, 3));
         BlockState state1 = level.getBlockState(randomSample);
         if (state1.is(Blocks.DIRT) && level.getBlockState(randomSample.above()).isAir()) {
            level.setBlockAndUpdate(randomSample, Blocks.GRASS_BLOCK.defaultBlockState());
         }
      }

      if (!level.getBlockState(pos.above()).isAir()) {
         level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
      }
   }
}
