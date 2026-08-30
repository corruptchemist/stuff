package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.weather.Storm;
import java.util.function.Function;

public class StormType {
   private final String namespace;
   private final String id;
   private final int shaderId;
   private final Function<StormSpawnProperties, Storm> constructor;

   public StormType(String namespace, String id, Function<StormSpawnProperties, Storm> constructor, int shaderId) {
      this.namespace = namespace;
      this.id = id;
      this.constructor = constructor;
      this.shaderId = shaderId;
   }

   public Storm create(StormSpawnProperties properties) {
      Storm storm = this.constructor.apply(properties);
      storm.stormType = this;
      return storm;
   }

   public String getId() {
      return String.join(":", this.namespace, this.id);
   }

   public int getShaderId() {
      return this.shaderId;
   }
}
