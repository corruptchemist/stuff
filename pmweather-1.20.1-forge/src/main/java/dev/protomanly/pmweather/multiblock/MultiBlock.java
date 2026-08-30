package dev.protomanly.pmweather.multiblock;

import dev.protomanly.pmweather.PMWeather;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class MultiBlock extends Block {
   public static BooleanProperty COMPLETED = BooleanProperty.create("completed");

   public MultiBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(COMPLETED, false));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{COMPLETED});
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.tick(state, level, pos, random);
      boolean completed = true;

      for (BlockPos blockPos : this.getStructure().keySet()) {
         Block goal = this.getStructure().get(blockPos);
         if (!level.getBlockState(pos.offset(blockPos)).is(goal)) {
            completed = false;
         }
      }

      if (completed != this.isComplete(state)) {
         level.setBlockAndUpdate(pos, (BlockState)state.setValue(COMPLETED, completed));
         MultiBlockHandler.isDirty = true;
         if (completed) {
            PMWeather.LOGGER.debug("MultiBlock structure {} marked complete", state);
         } else {
            PMWeather.LOGGER.debug("MultiBlock structure {} marked dismantled", state);
         }

         this.completionChanged(completed, level, state, pos);
      }
   }

   public void completionChanged(boolean newValue, Level level, BlockState blockState, BlockPos pos) {
   }

   public boolean isComplete(BlockState blockState) {
      return (Boolean)blockState.getValue(COMPLETED);
   }

   public Map<BlockPos, Block> getStructure() {
      return new HashMap<>();
   }
}
