package dev.protomanly.pmweather.multiblock;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent.NeighborNotifyEvent;

@Mod.EventBusSubscriber(
   modid = "pmweather"
)
public class MultiBlockHandler {
   public static int searchRange = 6;
   public static boolean isDirty = false;

   public MultiBlockHandler() {
   }

   public static void update(BlockPos blockPos, LevelAccessor level) {
      if (!level.isClientSide()) {
         BlockState baseState = level.getBlockState(blockPos);
         if (baseState.is(ModBlocks.RADOME) || baseState.getBlock() instanceof MultiBlock) {
            for (int x = -searchRange; x <= searchRange; x++) {
               for (int y = -searchRange; y <= searchRange; y++) {
                  for (int z = -searchRange; z <= searchRange; z++) {
                     BlockState state = level.getBlockState(blockPos.offset(x, y, z));
                     if (state.getBlock() instanceof MultiBlock multiblock) {
                        level.scheduleTick(blockPos.offset(x, y, z), multiblock, 0);
                        PMWeather.LOGGER.debug("Scheduled a tick at {} for {}", blockPos.offset(x, y, z), state);
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onUpdate(NeighborNotifyEvent event) {
      update(event.getPos(), event.getLevel());
   }
}
