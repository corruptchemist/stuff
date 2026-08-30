package dev.protomanly.pmweather.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class LevelRendererMixin {
   private int rainSoundTimer = 0;

   public LevelRendererMixin() {
   }

   @Inject(
      method = {"tickRain"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editTickRain(Camera camera, CallbackInfo callbackInfo) {
      Player player = Minecraft.getInstance().player;
      if (GameBusClientEvents.weatherHandler != null && player != null) {
         ClientLevel level = Minecraft.getInstance().level;
         WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
         float rain = weatherHandlerClient.getPrecipitation();
         ThermodynamicEngine.Precipitation precip = ThermodynamicEngine.getPrecipitationType(weatherHandlerClient, player.position(), level, 0);
         if (rain > 0.0F && precip != ThermodynamicEngine.Precipitation.SNOW) {
            BlockPos blockPos = player.blockPosition();
            BlockPos blockPos1 = null;
            int i = (int)(100.0F * rain * rain);

            for (int j = 0; j < i; j++) {
               int k = PMWeather.RANDOM.nextInt(21) - 10;
               int l = PMWeather.RANDOM.nextInt(21) - 10;
               BlockPos blockPos2 = level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos.offset(k, 0, l));
               if (blockPos2.getY() > level.getMinBuildHeight() && blockPos2.getY() <= blockPos.getY() + 10 && blockPos2.getY() >= blockPos.getY() - 10) {
                  blockPos1 = blockPos2.below();
               }
            }

            if (blockPos1 != null && PMWeather.RANDOM.nextInt(3) < this.rainSoundTimer++) {
               this.rainSoundTimer = 0;
               if (precip == ThermodynamicEngine.Precipitation.RAIN
                  || precip == ThermodynamicEngine.Precipitation.FREEZING_RAIN
                  || precip == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  if (blockPos1.getY() > blockPos.getY() + 1
                     && level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY() > Mth.floor((float)blockPos.getY())) {
                     level.playLocalSound(
                        blockPos1, (SoundEvent)ModSounds.RAIN.value(), SoundSource.WEATHER, 0.15F * rain + 0.3F * rain, 1.0F / (rain + 1.0F), false
                     );
                  } else {
                     level.playLocalSound(
                        blockPos1, (SoundEvent)ModSounds.RAIN.value(), SoundSource.WEATHER, 0.3F * rain + 0.6F * rain, 1.5F / (rain + 1.0F), false
                     );
                  }
               }

               if (precip == ThermodynamicEngine.Precipitation.SLEET || precip == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  if (blockPos1.getY() > blockPos.getY() + 1
                     && level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY() > Mth.floor((float)blockPos.getY())) {
                     level.playLocalSound(
                        blockPos1, (SoundEvent)ModSounds.SLEET.value(), SoundSource.WEATHER, 0.3F * rain + 0.5F * rain, 1.0F / (rain + 1.0F), false
                     );
                  } else {
                     level.playLocalSound(blockPos1, (SoundEvent)ModSounds.SLEET.value(), SoundSource.WEATHER, 0.6F * rain + rain, 1.5F / (rain + 1.0F), false);
                  }
               }
            }
         }
      }

      callbackInfo.cancel();
   }

   @Inject(
      method = {"renderSnowAndRain"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void disableVanillaRainAndSnow(LightTexture lightmapIn, float partialTicks, double xIn, double yIn, double zIn, CallbackInfo callbackInfo) {
      callbackInfo.cancel();
   }

   @Inject(
      method = {"renderClouds"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void disableClouds(
      PoseStack poseStack,
      Matrix4f frustumMatrix,
      Matrix4f projectionMatrix,
      float partialTick,
      double camX,
      double camY,
      double camZ,
      CallbackInfo callbackInfo
   ) {
      callbackInfo.cancel();
   }
}
