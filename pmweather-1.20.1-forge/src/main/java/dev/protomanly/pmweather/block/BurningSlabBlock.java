package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.block.interfaces.BurningBlockInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class BurningSlabBlock extends SlabBlock implements BurningBlockInterface {
   private final Block burnsInto;
   private final int burnChance;
   private final int spreadChance;

   public BurningSlabBlock(Block burnsInto, int burnChance, int spreadChance, Properties properties) {
      super(properties);
      this.burnsInto = burnsInto;
      this.burnChance = burnChance;
      this.spreadChance = spreadChance;
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      this.doRandomTick(state, level, pos, random);
   }

   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      super.animateTick(state, level, pos, random);
      this.animate(state, level, pos, random);
   }

   @Override
   public int getBurnChance() {
      return this.burnChance;
   }

   @Override
   public int getSpreadChance() {
      return this.spreadChance;
   }

   @Override
   public Block getBurnsInto() {
      return this.burnsInto;
   }
}
