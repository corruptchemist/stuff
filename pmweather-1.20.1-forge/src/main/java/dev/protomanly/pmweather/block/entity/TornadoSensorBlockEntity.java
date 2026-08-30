package dev.protomanly.pmweather.block.entity;

import dev.protomanly.pmweather.block.TornadoSensorBlock;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.Storm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TornadoSensorBlockEntity extends BlockEntity {
   public TornadoSensorBlockEntity(BlockPos pos, BlockState blockState) {
      super(ModBlockEntities.TORNADO_SENSOR_BE.get(), pos, blockState);
   }

   public void tick(Level level, BlockPos blockPos, BlockState blockState) {
      if (level.getGameTime() % 20L == 0L && !level.isClientSide()) {
         boolean nearTornado = false;

         for (Storm storm : GameBusEvents.MANAGERS.get(level.dimension()).getStorms()) {
            double dist = blockPos.getCenter().multiply(1.0, 0.0, 1.0).distanceTo(storm.position.multiply(1.0, 0.0, 1.0));
            if (dist < ServerConfig.stormSize * 2.0 && storm.triggerTornadoSensorAndSiren()) {
               nearTornado = true;
               break;
            }
         }

         if ((Boolean)blockState.getValue(TornadoSensorBlock.POWERED) != nearTornado) {
            level.setBlockAndUpdate(blockPos, (BlockState)blockState.setValue(TornadoSensorBlock.POWERED, nearTornado));
         }
      }
   }
}
