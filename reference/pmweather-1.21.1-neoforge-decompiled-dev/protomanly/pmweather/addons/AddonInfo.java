package dev.protomanly.pmweather.addons;

import dev.protomanly.pmweather.PMWeather;
import java.util.List;
import net.neoforged.fml.ModContainer;

public class AddonInfo {
   private ModContainer addonContainer;
   private List<String> targetedVersions;

   public AddonInfo(ModContainer addonContainer, List<String> supportedVersions) {
      this.addonContainer = addonContainer;
      this.targetedVersions = supportedVersions;
   }

   public String getNamespace() {
      return this.addonContainer.getNamespace();
   }

   public String getAddonVersion() {
      return this.addonContainer.getModInfo().getVersion().toString();
   }

   public boolean checkCompatibility() {
      String pmwVersion = PMWeather.getModContainer().getModInfo().getVersion().toString();

      for (String ver : this.targetedVersions) {
         if (pmwVersion.startsWith(ver)) {
            return true;
         }
      }

      return this.targetedVersions.contains(pmwVersion);
   }
}
