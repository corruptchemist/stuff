package dev.protomanly.pmweather.addons;

import dev.protomanly.pmweather.PMWeather;
import java.util.HashMap;
import java.util.Map;

public class AddonHelper {
   private static Map<String, AddonInfo> addons = new HashMap<>();
   private static boolean hasAddons = false;

   public AddonHelper() {
   }

   public static void registerAddon(AddonInfo addonInfo) {
      if (!hasAddons) {
         PMWeather.LOGGER.warn("PMWEATHER IS RUNNING WITH ADDONS, BEFORE REPORTING ISSUES TO THE PMWEATHER TEAM, PLEASE TRY WITHOUT ADDONS");
      }

      hasAddons = true;
      String namespace = addonInfo.getNamespace();
      if (addons.containsKey(namespace)) {
         throw new RuntimeException(String.format("Addon %s already registered!", namespace));
      } else {
         addons.put(namespace, addonInfo);
         PMWeather.LOGGER.info("Checking compat of addon {}", namespace);
         if (!addonInfo.checkCompatibility()) {
            String pmwVersion = PMWeather.getModContainer().getModInfo().getVersion().toString();
            throw new RuntimeException(
               String.format(
                  "%s is not currently marked as compatible for PMWeather %s, please remove this addon, or alternatively report this issue to the developer of the addon.",
                  namespace,
                  pmwVersion
               )
            );
         } else {
            PMWeather.LOGGER.info("Addon is compatible with PMWeather!");
         }
      }
   }
}
