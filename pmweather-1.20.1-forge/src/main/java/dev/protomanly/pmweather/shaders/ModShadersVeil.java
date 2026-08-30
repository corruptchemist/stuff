package dev.protomanly.pmweather.shaders;

import dev.protomanly.pmweather.shaders.data.TextureManager3D;
import dev.protomanly.pmweather.shaders.post.SkyShader;
import dev.protomanly.pmweather.shaders.post.VolumeShader;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostPipeline.Context;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL33;

public class ModShadersVeil {
   private static final PMWPostShader SKY = new SkyShader("sky");
   private static final PMWPostShader VOLUMES = new VolumeShader("volumes");
   private static final int PMW_NOISE3D_ORDINAL = 9;

   public ModShadersVeil() {
   }

   public static void VeilRendererAvailable(VeilRenderer renderer) {
      InitShaders();
   }

   public static void PreVeilPostProcessing(ResourceLocation name, PostPipeline pipeline, Context context) {
      PMWPostShader shader = PMWPostShader.getShader(name);
      if (shader != null) {
         shader.pre(pipeline, context);
      }
   }

   public static void PreBlitPostStageDraw(Context context, ShaderProgram program, ResourceLocation shader) {
      if (shader.getNamespace().equals("pmweather")) {
         String shaderName = shader.getPath();
         if (shaderName.equals("clouds")) {
            GL33.glActiveTexture(33993);
            TextureManager3D.NOISE3D.bind();
            program.getUniformSafe("Noise3DSampler").setInt(9);
         }
      }
   }

   public static void VeilCompileShaders(ShaderManager manager, Map<ResourceLocation, ShaderProgram> shaders) {
      shaders.forEach((location, shaderProgram) -> {
         if (location.getNamespace().equalsIgnoreCase("pmweather")) {
            String shaderPath = location.getPath();
            if (shaderPath.equalsIgnoreCase("clouds")) {
               VOLUMES.onCompile(shaderProgram, location);
            }
         }
      });
   }

   public static void tick() {
      PMWPostShader.SHADERS.forEach((name, shader) -> shader.tick());
   }

   public static void InitShaders() {
      PMWPostShader.SHADERS.forEach((name, shader) -> shader.init());
   }

   public static void ClearShaders() {
      PMWPostShader.SHADERS.forEach((name, shader) -> shader.clear());
   }

   public static enum Quality {
      POTATO,
      LOW,
      MEDIUM,
      HIGH,
      PC_KILLER;

      private Quality() {
      }
   }

   public static class Utils {
      public Utils() {
      }

      public static Vector3f GetLightingColor(Vector3f sunDir) {
         Vector3f lightingColor = new Vector3f(1.0F, 0.992F, 0.957F).mul(1.05F);
         lightingColor = lightingColor.lerp(new Vector3f(0.741F, 0.318F, 0.227F), (float)Math.pow((double)(1.0F - sunDir.y), 2.5));
         return lightingColor.lerp(new Vector3f(0.314F, 0.408F, 0.525F), Mth.clamp((sunDir.y + 0.1F) / -0.1F, 0.0F, 1.0F));
      }

      public static Vector3f GetSunDirection(ClientLevel level, float partialTicks) {
         float sunAng = level.getSunAngle(partialTicks);
         return new Vector3f(-Mth.sin(sunAng), Mth.cos(sunAng), 0.0F);
      }

      public static float GetLunarPhase(ClientLevel level, float partialTicks) {
         return (float)level.getMoonPhase() / 8.0F * 2.0F - 1.0F;
      }
   }
}
