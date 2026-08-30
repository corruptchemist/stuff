package dev.protomanly.pmweather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.Property;

public class TransformingBlock extends Block {
   private final Block turnsInto;
   private final int transformChance;

   public TransformingBlock(Block turnsInto, int transformChance, Properties properties) {
      super(properties);
      this.turnsInto = turnsInto;
      this.transformChance = transformChance;
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      if (random.nextInt(this.transformChance) == 0) {
         BlockState newState = this.turnsInto.defaultBlockState();

         for (Property<?> property : state.getProperties()) {
            newState = this.copyProperty(state, newState, property);
         }

         level.setBlockAndUpdate(pos, newState);
      }
   }

   private <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
      return (BlockState)to.trySetValue(property, from.getValue(property));
   }
}
