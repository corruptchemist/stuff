package dev.protomanly.pmweather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import org.jetbrains.annotations.Nullable;

public class BurningBlockNoSpread extends BurningBlock {
   public BurningBlockNoSpread(Block burnsInto, int burnChance, int spreadChance, Properties properties) {
      super(burnsInto, burnChance, spreadChance, properties);
   }

   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      this.doRandomTick(state, level, pos, random);
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      super.animateTick(state, level, pos, random);
      this.animate(state, level, pos, random);
   }

   @Override
   public int getKeepChance() {
      return 1;
   }

   @Override
   public int getFireIntensity() {
      return 0;
   }

   @Override
   public void spread(@Nullable BlockState actor, @Nullable BlockPos actorPos, BlockState to, BlockPos toPos, Level level) {
   }

   @Override
   public void trySpreadFireBlock(BlockState actor, BlockPos actorPos, Level level) {
   }
}
