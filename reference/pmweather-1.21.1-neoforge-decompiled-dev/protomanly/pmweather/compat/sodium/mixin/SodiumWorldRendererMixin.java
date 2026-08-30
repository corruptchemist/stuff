package dev.protomanly.pmweather.compat.sodium.mixin;

import dev.protomanly.pmweather.compat.sodium.ModTerrainRenderPasses;
import dev.protomanly.pmweather.render.ModRenderTypes;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SodiumWorldRenderer.class})
public class SodiumWorldRendererMixin {
   @Shadow
   private RenderSectionManager renderSectionManager;

   public SodiumWorldRendererMixin() {
   }

   @Inject(
      method = {"drawChunkLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void injectSwaying(RenderType renderType, ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
      if (renderType == ModRenderTypes.swayingCutout()) {
         this.renderSectionManager.renderLayer(matrices, ModTerrainRenderPasses.SWAYING_CUTOUT, x, y, z);
      }
   }
}
