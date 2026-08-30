package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CropBlock.class})
public class CropBlockMixin {
   public CropBlockMixin() {
   }

   @Inject(
      method = {"isRandomlyTicking"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editRandomlyTicking(BlockState state, CallbackInfoReturnable<Boolean> callbackInfo) {
      if (ServerConfig.doSeasons && ServerConfig.seasonalCrops) {
         callbackInfo.setReturnValue(true);
      }
   }

   @Inject(
      method = {"randomTick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callbackInfo) {
      if (ServerConfig.doSeasons && ServerConfig.seasonalCrops) {
         WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, pos.above().getCenter(), level, null, 0);
         if (dataPoint.temperature() < random.nextFloat() * 3.5F) {
            if (dataPoint.temperature() < -4.0F && random.nextInt(0, 3) == 0) {
               level.setBlockAndUpdate(pos, Blocks.SHORT_GRASS.defaultBlockState());
            }

            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"isBonemealSuccess"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editIsBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> callbackInfo) {
      if (ServerConfig.doSeasons && ServerConfig.seasonalCrops) {
         WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, pos.above().getCenter(), level, null, 0);
         if (dataPoint.temperature() <= 0.0F) {
            callbackInfo.setReturnValue(false);
         }
      }
   }
}
