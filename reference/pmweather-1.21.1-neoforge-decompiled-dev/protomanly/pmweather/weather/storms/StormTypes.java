package dev.protomanly.pmweather.weather.storms;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import net.neoforged.neoforge.common.NeoForge;

public class StormTypes {
   @Deprecated
   private static HashMap<Integer, StormType> legacyId2StormType = new HashMap<>();
   private static HashMap<String, StormType> id2StormType = new HashMap<>();
   public static StormType SUPERCELL = register(new StormType("pmweather", "supercell", Supercell::new, 0), 0);
   public static StormType SQUALL = register(new StormType("pmweather", "squall", Squall::new, 1), 1);
   public static StormType CYCLONE = register(new StormType("pmweather", "cyclone", Cyclone::new, 2), 2);
   public static StormType FIRE_WHIRL = register(new StormType("pmweather", "fire_whirl", FireWhirl::new, 3), 3);

   public StormTypes() {
   }

   public static StormType register(StormType stormType) {
      if (id2StormType.containsKey(stormType.getId())) {
         throw new RuntimeException("Storm ID already registered!");
      } else {
         id2StormType.put(stormType.getId(), stormType);
         NeoForge.EVENT_BUS.post(new StormTypeRegisterEvent(stormType));
         return stormType;
      }
   }

   public static List<String> getStormTypes() {
      return ImmutableList.copyOf(id2StormType.keySet());
   }

   public static StormType getFromID(String id) {
      if (!id2StormType.containsKey(id)) {
         throw new RuntimeException("StormType ID is not registered!");
      } else {
         return id2StormType.get(id);
      }
   }

   @Deprecated
   public static StormType getFromLegacyID(int id) {
      return legacyId2StormType.get(id);
   }

   private static StormType register(StormType stormType, int legacyId) {
      register(stormType);
      legacyId2StormType.put(legacyId, stormType);
      return stormType;
   }
}
