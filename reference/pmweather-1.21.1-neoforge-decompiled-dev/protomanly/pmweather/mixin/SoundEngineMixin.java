package dev.protomanly.pmweather.mixin;

import javax.annotation.Nullable;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public abstract class SoundEngineMixin {
   public SoundEngineMixin() {
   }

   @Shadow
   public abstract float getVolume(@Nullable SoundSource var1);

   @Inject(
      method = {"calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void fixCalculateVolume(float volMult, SoundSource soundSource, CallbackInfoReturnable<Float> cir) {
      if (soundSource == SoundSource.WEATHER) {
         cir.setReturnValue(Math.clamp((Float)cir.getReturnValue() * this.getVolume(soundSource), 0.0F, 1.0F));
      }
   }
}
