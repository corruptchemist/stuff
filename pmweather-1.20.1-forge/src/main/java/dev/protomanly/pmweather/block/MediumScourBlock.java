package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class MediumScourBlock extends Block {
   public MediumScourBlock(Properties properties) {
      super(properties);
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);

      for (int i = 0; i < 4; i++) {
         BlockPos randomSample = pos.offset(PMWeather.RANDOM.nextInt(-2, 3), PMWeather.RANDOM.nextInt(-2, 3), PMWeather.RANDOM.nextInt(-2, 3));
         BlockState state1 = level.getBlockState(randomSample);
         if (state1.is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
         }
      }
   }
}
