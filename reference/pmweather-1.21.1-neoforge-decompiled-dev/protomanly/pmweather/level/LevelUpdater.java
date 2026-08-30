package dev.protomanly.pmweather.level;

import dev.protomanly.pmweather.data.DataAttachments;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class LevelUpdater {
   private final List<ResourceKey<Level>> validDimensions;

   public LevelUpdater(List<ResourceKey<Level>> validDimensions) {
      this.validDimensions = validDimensions;
   }

   public boolean tick(ChunkAccess chunkAccess) {
      if (chunkAccess.getLevel() == null) {
         return false;
      } else if (!chunkAccess.getLevel().isLoaded(chunkAccess.getPos().getMiddleBlockPosition(60))) {
         return false;
      } else if (!this.isValidDimension(chunkAccess.getLevel())) {
         return false;
      } else {
         boolean firstLoad = !chunkAccess.hasData(DataAttachments.LAST_TIME);
         long lastTick = (Long)chunkAccess.getData(DataAttachments.LAST_TIME);
         long curTick = chunkAccess.getLevel().getDayTime();
         long delta = curTick - lastTick;
         if ((delta < this.minDeltaForChange() || !this.canChange()) && !firstLoad) {
            return false;
         } else {
            this.update(chunkAccess, Math.min(firstLoad ? this.minDeltaForChange() * 20L : delta, 48000L));
            return true;
         }
      }
   }

   public void update(ChunkAccess chunkAccess, long delta) {
   }

   protected void onSurface(ChunkAccess chunkAccess, BiConsumer<BlockPos, BlockState> biConsumer) {
      this.onSurface(chunkAccess, false, biConsumer);
   }

   protected void onSurface(ChunkAccess chunkAccess, boolean above, BiConsumer<BlockPos, BlockState> biConsumer) {
      if (chunkAccess.getLevel() != null) {
         BlockPos corner = chunkAccess.getPos().getWorldPosition();

         for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
               BlockPos flatPos = corner.offset(x, 0, z);
               int y = chunkAccess.getHeight(Types.MOTION_BLOCKING, flatPos.getX(), flatPos.getZ());
               flatPos = flatPos.atY(y);
               if (y < chunkAccess.getMaxBuildHeight() - 1 && y > chunkAccess.getMinBuildHeight()) {
                  BlockState state = chunkAccess.getBlockState(flatPos);
                  biConsumer.accept(flatPos, state);
                  if (above) {
                     flatPos = flatPos.above();
                     BlockState state1 = chunkAccess.getBlockState(flatPos);
                     biConsumer.accept(flatPos, state1);
                  }
               }
            }
         }
      }
   }

   private boolean isValidDimension(Level level) {
      return this.validDimensions.contains(level.dimension());
   }

   protected long minDeltaForChange() {
      return 24000L;
   }

   protected boolean canChange() {
      return true;
   }
}
