package dev.protomanly.pmweather.compat.create;

import dev.protomanly.pmweather.PMWeather;

public class CreateMod {
   private static boolean initialized = false;
   private static boolean createPresent = false;
   private static CreateHandler handler = null;

   public CreateMod() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;

         try {
            Class.forName("com.simibubi.create.Create");
            handler = new CreateHandler();
            handler.initialize();
            createPresent = true;
            PMWeather.LOGGER.info("Create compatibility initialized");
         } catch (NoClassDefFoundError | ClassNotFoundException var1) {
            PMWeather.LOGGER.info("Create not found, skipping integration");
            createPresent = false;
            handler = null;
         } catch (Exception var2) {
            PMWeather.LOGGER.error("Failed to initialize Create compatibility", var2);
            createPresent = false;
            handler = null;
         }
      }
   }

   public static CreateHandler getHandler() {
      return handler;
   }
}
