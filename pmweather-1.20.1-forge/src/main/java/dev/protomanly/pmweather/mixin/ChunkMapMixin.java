package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.interfaces.ChunkMapMixinInterface;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ChunkMap.class})
public class ChunkMapMixin implements ChunkMapMixinInterface {
   public ChunkMapMixin() {
   }

   @Shadow
   protected Iterable<ChunkHolder> getChunks() {
      throw new AssertionError();
   }

   @Unique
   @Override
   public Iterable<ChunkHolder> PMWGetLoadedChunks() {
      return this.getChunks();
   }
}
