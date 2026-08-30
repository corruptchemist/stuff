package dev.protomanly.pmweather.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class ChunkCoordinatesBlock extends BlockPos {
   public Block block;

   public ChunkCoordinatesBlock(int x, int y, int z, Block block) {
      super(x, y, z);
      this.block = block;
   }

   public ChunkCoordinatesBlock(BlockPos blockPos, Block block) {
      super(blockPos);
      this.block = block;
   }
}
