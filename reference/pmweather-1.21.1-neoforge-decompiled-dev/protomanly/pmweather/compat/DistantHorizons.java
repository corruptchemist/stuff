package dev.protomanly.pmweather.compat;

import dev.protomanly.pmweather.PMWeather;
import org.joml.Matrix4f;

public class DistantHorizons {
   private static boolean initialized = false;
   private static boolean dhPresent = false;
   private static DistantHorizonsHandler handler = null;
   private static final Matrix4f DEFAULT_MATRIX = new Matrix4f();
   private static final float DEFAULT_NEAR_PLANE = 0.05F;
   private static final float DEFAULT_FAR_PLANE = 1024.0F;
   private static final int DEFAULT_RENDER_DISTANCE = 256;

   public DistantHorizons() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;

         try {
            Class.forName("com.seibel.distanthorizons.api.DhApi");
            handler = new DistantHorizonsHandler();
            handler.initialize();
            dhPresent = true;
            PMWeather.LOGGER.info("Distant Horizons compatibility initialized");
         } catch (NoClassDefFoundError | ClassNotFoundException var1) {
            PMWeather.LOGGER.info("Distant Horizons not found, skipping integration");
            dhPresent = false;
            handler = null;
         } catch (Exception var2) {
            PMWeather.LOGGER.error("Failed to initialize Distant Horizons compatibility", var2);
            dhPresent = false;
            handler = null;
         }
      }
   }

   public static boolean isAvailable() {
      return dhPresent && handler != null && handler.isReady();
   }

   public static boolean isEnabled() {
      return isAvailable() && handler.isEnabled();
   }

   public static Matrix4f getDhProjectionMatrix() {
      return isAvailable() ? handler.getDhProjectionMatrix() : new Matrix4f(DEFAULT_MATRIX);
   }

   public static float getNearPlane() {
      return isAvailable() ? handler.getNearPlane() : 0.05F;
   }

   public static float getFarPlane() {
      return isAvailable() ? handler.getFarPlane() : 1024.0F;
   }

   public static int getChunkRenderDistance() {
      return isAvailable() ? handler.getChunkRenderDistance() : 256;
   }
}
