package dev.protomanly.pmweather.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LavaFluid.class})
public class LavaFluidMixin {
   public LavaFluidMixin() {
   }

   @Inject(
      method = {"randomTick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo callbackInfo) {
      callbackInfo.cancel();
   }
}
