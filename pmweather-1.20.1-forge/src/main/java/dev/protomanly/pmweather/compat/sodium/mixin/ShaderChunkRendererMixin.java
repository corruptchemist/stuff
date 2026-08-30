package dev.protomanly.pmweather.compat.sodium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram.Builder;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ShaderChunkRenderer.class})
public class ShaderChunkRendererMixin {
   public ShaderChunkRendererMixin() {
   }

   @WrapOperation(
      method = {"createShader"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder;bindAttribute(Ljava/lang/String;I)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder;",
         ordinal = 3
      )}
   )
   private Builder addAttributes(Builder instance, String name, int index, Operation<Builder> orig) {
      return ((Builder)orig.call(new Object[]{instance, name, index}))
         .bindAttribute("veil_normal", 4)
         .bindAttribute("pmw_blockId", 5)
         .bindAttribute("pmw_midBlock", 6);
   }
}
