package dev.protomanly.pmweather.level;

import dev.protomanly.pmweather.data.DataAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.chunk.ChunkAccess;

public class LevelUpdateManager {
   private static final List<LevelUpdater> LEVEL_UPDATERS = new ArrayList<>();
   private static int UPDATES = 0;

   public LevelUpdateManager() {
   }

   public static void NewTick() {
      UPDATES = 0;
   }

   public static void AddLevelUpdater(LevelUpdater levelUpdater) {
      LEVEL_UPDATERS.add(levelUpdater);
   }

   public static void tick(ChunkAccess chunkAccess) {
      if (chunkAccess.getLevel() != null && UPDATES < 2) {
         boolean dirty = false;

         for (LevelUpdater levelUpdater : LEVEL_UPDATERS) {
            dirty = dirty || levelUpdater.tick(chunkAccess);
         }

         if (dirty) {
            UPDATES++;
         }

         chunkAccess.setData(DataAttachments.LAST_TIME, chunkAccess.getLevel().getDayTime());
      }
   }
}
