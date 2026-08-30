package dev.protomanly.pmweather.compat.veil.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import foundry.veil.api.client.render.post.PostPipeline.Context;
import foundry.veil.api.client.render.post.stage.BlitPostStage;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlitPostStage.class})
public class BlitPostStageMixin {
   @Shadow
   @Final
   private ResourceLocation shader;

   public BlitPostStageMixin() {
   }

   @Inject(
      method = {"apply"},
      at = {@At(
         value = "INVOKE",
         target = "Lfoundry/veil/api/client/render/VeilRenderSystem;drawScreenQuad()V",
         shift = Shift.BEFORE
      )}
   )
   private void preDraw(Context context, CallbackInfo ci, @Local ShaderProgram program) {
      ModShadersVeil.PreBlitPostStageDraw(context, program, this.shader);
   }
}
