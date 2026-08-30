package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.weather.WeatherHandler;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StormSpawnProperties {
   public WeatherHandler weatherHandler;
   public Level level;
   @Nullable
   public Float risk;
   public Vec3 position;

   public StormSpawnProperties(WeatherHandler weatherHandler, Level level, Vec3 position, @Nullable Float risk) {
      this.weatherHandler = weatherHandler;
      this.level = level;
      this.position = position;
      this.risk = risk;
   }
}
