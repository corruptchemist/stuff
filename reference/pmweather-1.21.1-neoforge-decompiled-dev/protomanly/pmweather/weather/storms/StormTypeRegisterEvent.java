package dev.protomanly.pmweather.weather.storms;

import net.neoforged.bus.api.Event;

public class StormTypeRegisterEvent extends Event {
   private final StormType stormType;

   public StormTypeRegisterEvent(StormType stormType) {
      this.stormType = stormType;
   }

   public StormType getStormType() {
      return this.stormType;
   }
}
