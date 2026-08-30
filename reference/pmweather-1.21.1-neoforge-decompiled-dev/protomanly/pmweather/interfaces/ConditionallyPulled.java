package dev.protomanly.pmweather.interfaces;

import dev.protomanly.pmweather.weather.Storm;
import net.minecraft.world.phys.Vec3;

public interface ConditionallyPulled {
   boolean shouldPull(Storm var1);

   default boolean onPull(Storm actor, Vec3 force) {
      return false;
   }

   default boolean shouldPush() {
      return true;
   }

   default boolean onPush() {
      return false;
   }
}
