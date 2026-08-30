package dev.protomanly.pmweather.compat.sodium.mixin;

import dev.protomanly.pmweather.compat.sodium.ModTerrainRenderPasses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {DefaultTerrainRenderPasses.class},
   priority = 5000
)
public class DefaultTerrainRenderPassesMixin {
   @Shadow
   @Mutable
   @Final
   private static TerrainRenderPass[] ALL;

   public DefaultTerrainRenderPassesMixin() {
   }

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   )
   private static void changeRenderPasses(CallbackInfo ci) {
      List<TerrainRenderPass> newALL = new ArrayList<>(Arrays.stream(ALL).toList());
      newALL.add(ModTerrainRenderPasses.SWAYING_CUTOUT);
      ALL = newALL.toArray(new TerrainRenderPass[0]);
   }
}
