package dev.protomanly.pmweather.shaders;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PMWShaderProgramShard extends ShaderStateShard {
   private final ResourceLocation shader;
   private final Consumer<ShaderProgram> onSetup;

   public PMWShaderProgramShard(ResourceLocation shader, Consumer<ShaderProgram> onSetup) {
      this.shader = shader;
      this.onSetup = onSetup;
   }

   public void setupRenderState() {
      this.onSetup.accept(VeilRenderSystem.setShader(this.shader));
      if (VeilRenderSystem.getShader() == null) {
         throw new RuntimeException("Shader failed to apply!");
      }
   }

   @NotNull
   public String toString() {
      return this.name + "[" + this.shader + "]";
   }

   public ResourceLocation getShader() {
      return this.shader;
   }
}
