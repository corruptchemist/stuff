package dev.protomanly.pmweather.compat.powergrid;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PowerGridMod {
   private static boolean initialized = false;
   private static boolean powerGridPresent = false;
   private static PowerGridHandler handler = null;

   public PowerGridMod() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;

         try {
            Class.forName("org.patryk3211.powergrid.PowerGrid");
            handler = new PowerGridHandler();
            handler.initialize();
            powerGridPresent = true;
            PMWeather.LOGGER.info("Create Power Grid compatibility initialized");
         } catch (NoClassDefFoundError | ClassNotFoundException var1) {
            PMWeather.LOGGER.info("Create Power Grid not found, skipping integration");
            powerGridPresent = false;
            handler = null;
         } catch (Exception var2) {
            PMWeather.LOGGER.error("Failed to initialize Create Power Grid compatibility", var2);
            powerGridPresent = false;
            handler = null;
         }
      }
   }

   public static void lightning(Level level, Vec3 pos) {
      if (exists()) {
         handler.lightning(level, pos);
      }
   }

   public static boolean exists() {
      return powerGridPresent;
   }

   public static PowerGridHandler getHandler() {
      return handler;
   }
}
