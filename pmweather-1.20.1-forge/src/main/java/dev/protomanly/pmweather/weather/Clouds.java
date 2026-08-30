package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.util.ShaderCompatibleNoise;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class Clouds {
   public Clouds() {
   }

   public static float getCloudDensity(WeatherHandler weatherHandler, Vector2f location, float partialTicks) {
      return getCloudDensity(weatherHandler, location, partialTicks, false);
   }

   public static float getCloudDensity(WeatherHandler weatherHandler, Vector2f location, float partialTicks, int advance) {
      return getCloudDensity(weatherHandler, location, partialTicks, false, advance);
   }

   public static float getCloudDensity(WeatherHandler weatherHandler, Vector2f location, float partialTicks, boolean getThunder) {
      return getCloudDensity(weatherHandler, location, partialTicks, getThunder, 0);
   }

   public static float getCloudDensity(WeatherHandler weatherHandler, Vector2f location, float partialTicks, boolean getThunder, int advance) {
      float c = 0.0F;
      if (!ServerConfig.validDimensions.contains(weatherHandler.getWorld().dimension())) {
         return c;
      } else {
         float worldTime = Util.getWorldTime(weatherHandler.getWorld(), partialTicks + (float)advance);
         Vector3f noisePos = new Vector3f(location.x + worldTime, (float)ServerConfig.layer0Height, location.y + worldTime);
         Vector3f cloudNoisePos = new Vector3f(location.x + worldTime * 0.5F, (float)ServerConfig.layer0Height, location.y + worldTime * 0.5F);
         Vector3f overrideNoisePos = new Vector3f(location.x + worldTime * 0.25F, (float)ServerConfig.layer0Height, location.y + worldTime * 0.25F);
         float overrideNoise = Mth.clamp(ShaderCompatibleNoise.noise2D(new Vector2f(overrideNoisePos.x, overrideNoisePos.z).div(200.0F)) + 1.0F, 0.0F, 2.0F)
            / 2.0F;
         float densityNoise = Math.min(ShaderCompatibleNoise.noise2D(new Vector2f(cloudNoisePos.x, cloudNoisePos.z).div(400.0F)), 1.0F);
         float cloudNoise = Math.min(ShaderCompatibleNoise.noise2D(new Vector2f(noisePos.x, noisePos.z).div(30.0F)), 1.0F);
         float heightNoise = ShaderCompatibleNoise.noise2D(new Vector2f(noisePos.x, noisePos.z).div(90.0F));
         float bgCloudHeight = Mth.lerp(Mth.clamp((heightNoise + 1.0F) * 0.5F, 0.0F, 1.0F), 300.0F, 850.0F);
         float seasonEffect = SeasonHandler.getSeasonEffectSine(weatherHandler.getWorld(), 3.5F) + 1.0F;
         double overcastDampen = (double)seasonEffect * 0.15;
         float overcastP = (float)Math.max(ServerConfig.overcastPercent - overcastDampen, 0.0);
         float v = Mth.clamp(densityNoise - (1.0F - overcastP), 0.0F, 1.0F);
         c += (float)Math.max(Math.sqrt(Math.sqrt((double)v)), 0.0);
         c *= Mth.lerp(v, Mth.clamp(cloudNoise - 0.1F + v, 0.0F, 1.0F), 1.0F);
         c = (float)Math.sqrt((double)c) * 0.5F;
         c *= Mth.lerp((float)Math.sqrt((double)v), bgCloudHeight / 850.0F, 1.0F);
         double stormSize = ServerConfig.stormSize;

         for (Storm storm : weatherHandler.getStorms()) {
            Vector2f stormPos = new Vector2f((float)storm.position.x, (float)storm.position.z);
            float dist = stormPos.distance(location);
            float smoothStage = (float)storm.stage + (float)storm.energy / 100.0F;
            if (storm.is(StormTypes.CYCLONE)) {
               c *= Mth.lerp(
                  Mth.clamp((float)(storm.windspeed - 65) / 60.0F, 0.0F, 1.0F),
                  1.0F,
                  (float)Math.pow(Mth.clamp((double)dist / ((double)storm.maxWidth * 0.1), 0.0, 1.0), 2.0)
               );
            }

            if (storm.is(StormTypes.SUPERCELL)) {
               float p = Mth.clamp(smoothStage / 1.75F, 0.0F, 1.0F);
               float clearing = (float)Mth.clamp((double)dist / (stormSize * (double)Mth.lerp(p, 1.0F, 4.0F)), 0.0, 1.0);
               clearing = Mth.lerp(p, 1.0F, clearing);
               clearing = Mth.square(clearing) * 0.6F;
               clearing += 0.4F;
               c *= clearing;
            }
         }

         float m = (float)Math.max(ServerConfig.overcastPercent - overcastDampen, 0.0);
         if (!getThunder) {
            c *= overrideNoise;
         } else {
            c -= 0.4F;
            c /= 0.6F;
         }

         return Mth.clamp(c * m, 0.0F, 1.0F);
      }
   }
}
