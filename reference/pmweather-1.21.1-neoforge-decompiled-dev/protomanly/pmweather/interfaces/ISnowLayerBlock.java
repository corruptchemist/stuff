package dev.protomanly.pmweather.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface ISnowLayerBlock {
   boolean survives(BlockState var1, LevelReader var2, BlockPos var3);
}
