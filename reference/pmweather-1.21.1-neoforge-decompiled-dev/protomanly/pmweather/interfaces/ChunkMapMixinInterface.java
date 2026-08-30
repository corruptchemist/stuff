package dev.protomanly.pmweather.interfaces;

import net.minecraft.server.level.ChunkHolder;

public interface ChunkMapMixinInterface {
   Iterable<ChunkHolder> PMWGetLoadedChunks();
}
