package dev.protomanly.pmweather.interfaces;

import java.util.Optional;
import net.minecraft.core.BlockPos;

public interface IServerLevel {
   Optional<BlockPos> pmwfindLightningRod(BlockPos var1);
}
