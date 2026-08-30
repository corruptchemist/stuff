package dev.protomanly.pmweather.compat.iris.mixin;

import dev.protomanly.pmweather.compat.sodium.ModTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SodiumPrograms.class})
public class SodiumProgramsMixin {
   public SodiumProgramsMixin() {
   }

   @Inject(
      method = {"mapTerrainRenderPass"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pmwmapTerrainRenderPass(TerrainRenderPass pass, CallbackInfoReturnable<Pass> cir) {
      if (pass == ModTerrainRenderPasses.SWAYING_CUTOUT) {
         cir.setReturnValue(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? Pass.SHADOW_CUTOUT : Pass.TERRAIN_CUTOUT);
      }
   }
}
