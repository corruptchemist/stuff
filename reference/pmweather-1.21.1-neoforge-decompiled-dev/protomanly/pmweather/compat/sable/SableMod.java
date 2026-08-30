package dev.protomanly.pmweather.compat.sable;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SableMod {
   private static boolean initialized = false;
   private static boolean sablePresent = false;
   private static SableHandler handler = null;

   public SableMod() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;

         try {
            Class.forName("dev.ryanhcode.sable.Sable");
            handler = new SableHandler();
            handler.initialize();
            sablePresent = true;
            PMWeather.LOGGER.info("Sable compatibility initialized");
         } catch (NoClassDefFoundError | ClassNotFoundException var1) {
            PMWeather.LOGGER.info("Sable not found, skipping integration");
            sablePresent = false;
            handler = null;
         } catch (Exception var2) {
            PMWeather.LOGGER.error("Failed to initialize Sable compatibility", var2);
            sablePresent = false;
            handler = null;
         }
      }
   }

   public static Vec3 getPos(Level level, BlockPos pos) {
      return !sablePresent ? pos.getCenter() : handler.getPos(level, pos);
   }

   public static double getDistance(Level level, Vec3 pos, Vec3 to) {
      return !sablePresent ? pos.distanceTo(to) : handler.getDistance(level, pos, to);
   }

   public static boolean exists() {
      return sablePresent;
   }

   public static SableHandler getHandler() {
      return handler;
   }
}
