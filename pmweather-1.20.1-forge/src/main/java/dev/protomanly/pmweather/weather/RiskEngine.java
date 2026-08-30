package dev.protomanly.pmweather.weather;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class RiskEngine {
   public static final int DAY_TICK_OFFSET = 1000;
   private static final int tickDelta = 500;

   public RiskEngine() {
   }

   public static RiskEngine.Risk getRisk(WeatherHandler weatherHandler, Vec3 pos, int dayOffset) {
      long startTick = (long)dayOffset * 24000L;
      startTick -= 1000L;
      long daytime = weatherHandler.getWorld().getDayTime() + 1000L;
      int currentDay = (int)(daytime / 24000L);
      startTick += (long)currentDay * 24000L;
      long off = startTick - weatherHandler.getWorld().getDayTime();
      int peakTick = 0;
      float rawRiskPeak = 0.0F;
      float risk = 0.0F;
      float deltaPerc = 0.020833334F;

      for (int i = 0; i < 24000; i += 500) {
         int advance = (int)(off + (long)i);
         Sounding sounding = new Sounding(weatherHandler, pos, 250, 16000, advance);
         float r = sounding.getRisk(advance);
         if (r > rawRiskPeak) {
            rawRiskPeak = r;
            peakTick = i;
         }

         risk += Math.max(r * deltaPerc * 2.0F, 0.0F);
      }

      risk /= 1.5F;
      rawRiskPeak /= 1.5F;
      risk = Mth.sqrt(risk);
      risk = Mth.clamp(risk, rawRiskPeak / 1.5F, rawRiskPeak) * 1.5F;
      return new RiskEngine.Risk(risk, peakTick, startTick + 24000L);
   }

   public static record Risk(float risk, int peakRiskTick, long validUntil) {
   }
}
