package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class RottedLogBlock extends ModLogBlock {
   public RottedLogBlock(Properties properties, RotatedPillarBlock stripInto) {
      super(properties, stripInto);
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (reinforcementManager != null) {
         if (!reinforcementManager.isPlayerPlaced(pos)) {
            Util.checkLogs(state, level, pos);
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isAir() || random.nextInt(3) == 0) {
               if (belowState.is(BlockTags.DIRT) && state.getValue(AXIS) == Axis.Y) {
                  Block sapling = reinforcementManager.getSaplingType(pos);
                  level.setBlockAndUpdate(pos, (sapling == null ? Blocks.OAK_SAPLING : sapling).defaultBlockState());
               } else {
                  level.removeBlock(pos, false);
               }
            }
         }
      }
   }
}
