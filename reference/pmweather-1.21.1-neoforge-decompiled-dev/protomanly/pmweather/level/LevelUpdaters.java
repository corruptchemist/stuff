package dev.protomanly.pmweather.level;

import dev.protomanly.pmweather.level.updaters.SeasonalLevelUpdater;
import java.util.List;
import net.minecraft.world.level.Level;

public class LevelUpdaters {
   public LevelUpdaters() {
   }

   public static void Init() {
      LevelUpdateManager.AddLevelUpdater(new SeasonalLevelUpdater(List.of(Level.OVERWORLD)));
   }
}
