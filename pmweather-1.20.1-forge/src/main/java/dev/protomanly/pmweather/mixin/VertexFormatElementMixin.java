package dev.protomanly.pmweather.mixin;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VertexFormatElement.class})
public class VertexFormatElementMixin {
   public VertexFormatElementMixin() {
   }

   @Inject(
      method = {"supportsUsage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pmwsupportUsage(int index, Usage usage, CallbackInfoReturnable<Boolean> cir) {
      if (usage == Usage.GENERIC) {
         cir.setReturnValue(true);
      }
   }
}
