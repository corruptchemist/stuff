package dev.protomanly.pmweather.compat.iris.mixin;

import dev.protomanly.pmweather.render.ModRenderTypes;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WorldRenderingPhase.class})
public class WorldRenderingPhaseMixin {
   public WorldRenderingPhaseMixin() {
   }

   @Inject(
      method = {"fromTerrainRenderType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void pmwfromTerrainRenderType(RenderType renderType, CallbackInfoReturnable<WorldRenderingPhase> cir) {
      if (renderType == ModRenderTypes.swayingCutout()) {
         cir.setReturnValue(WorldRenderingPhase.TERRAIN_CUTOUT);
      }
   }
}
