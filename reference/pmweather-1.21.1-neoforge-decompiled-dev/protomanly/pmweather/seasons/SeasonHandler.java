package dev.protomanly.pmweather.seasons;

import dev.protomanly.pmweather.config.ServerConfig;
import java.util.HashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public class SeasonHandler {
   private static HashMap<Integer, String> monthNames = new HashMap<Integer, String>() {
      {
         this.put(Integer.valueOf(1), "calendar.pmweather.january");
         this.put(Integer.valueOf(2), "calendar.pmweather.february");
         this.put(Integer.valueOf(3), "calendar.pmweather.march");
         this.put(Integer.valueOf(4), "calendar.pmweather.april");
         this.put(Integer.valueOf(5), "calendar.pmweather.may");
         this.put(Integer.valueOf(6), "calendar.pmweather.june");
         this.put(Integer.valueOf(7), "calendar.pmweather.july");
         this.put(Integer.valueOf(8), "calendar.pmweather.august");
         this.put(Integer.valueOf(9), "calendar.pmweather.september");
         this.put(Integer.valueOf(10), "calendar.pmweather.october");
         this.put(Integer.valueOf(11), "calendar.pmweather.november");
         this.put(Integer.valueOf(12), "calendar.pmweather.december");
      }
   };

   public SeasonHandler() {
   }

   public static String getMonthName(int month) {
      return Component.translatable(monthNames.get(month)).getString();
   }

   public static int getDay(Level level) {
      return (int)(level.getDayTime() / 24000L) + ServerConfig.monthLength * 4;
   }

   public static int getDayInMonth(Level level) {
      return getDay(level) % ServerConfig.monthLength + 1;
   }

   public static int getMonth(Level level) {
      int day = getDay(level);
      int month = day / ServerConfig.monthLength % 12;
      return month + 1;
   }

   public static float getSmoothedMonthTime(Level level) {
      int day = getDay(level) % ServerConfig.monthLength;
      int month = getMonth(level);
      return (float)month + (float)day / (float)ServerConfig.monthLength;
   }

   public static int getYear(Level level) {
      return getDay(level) / (ServerConfig.monthLength * 12);
   }

   public static float getSeasonEffectSine(Level level, float offset) {
      float month = getSmoothedMonthTime(level) - 1.0F;
      return Mth.sin((float) Math.PI * (month - (3.5F + offset)) / 6.0F);
   }
}
