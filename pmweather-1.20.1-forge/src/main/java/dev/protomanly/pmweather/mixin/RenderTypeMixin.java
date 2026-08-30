package dev.protomanly.pmweather.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.protomanly.pmweather.render.ModRenderTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({RenderType.class})
public class RenderTypeMixin {
   public RenderTypeMixin() {
   }

   @ModifyReturnValue(
      method = {"chunkBufferLayers"},
      at = {@At("RETURN")}
   )
   private static List<RenderType> chunkBufferLayers(List<RenderType> original) {
      List<RenderType> mutableList = new ArrayList<>(original);
      mutableList.add(ModRenderTypes.swayingCutout());
      ModRenderTypes.swayingCutout().chunkLayerId = mutableList.size() - 1;
      return ImmutableList.copyOf(mutableList);
   }
}
