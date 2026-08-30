package dev.protomanly.pmweather.compat.sodium.mixin;

import dev.protomanly.pmweather.compat.sodium.ModTerrainRenderPasses;
import dev.protomanly.pmweather.render.ModRenderTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({DefaultMaterials.class})
public class DefaultMaterialsMixin {
   @Unique
   private static final Material SWAYING_CUTOUT = new Material(ModTerrainRenderPasses.SWAYING_CUTOUT, AlphaCutoffParameter.ONE_TENTH, false);

   public DefaultMaterialsMixin() {
   }

   @Inject(
      method = {"forRenderLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void pmwForRenderLayer(RenderType layer, CallbackInfoReturnable<Material> cir) {
      if (layer == ModRenderTypes.swayingCutout()) {
         cir.setReturnValue(SWAYING_CUTOUT);
      }
   }
}
