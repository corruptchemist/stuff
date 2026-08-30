package dev.protomanly.pmweather.shaders.post;

import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.shaders.PMWPostShader;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostPipeline.Context;
import org.joml.Vector3f;

public class SkyShader extends PMWPostShader {
   public SkyShader(String location) {
      super(location);
   }

   @Override
   public void pre(PostPipeline pipeline, Context context) {
      super.pre(pipeline, context);
      if (this.shouldRender()) {
         Vector3f sunDir = ModShadersVeil.Utils.GetSunDirection(this.level, this.partialTicks);
         pipeline.getUniformSafe("sunDir").setVector(sunDir);
         pipeline.getUniformSafe("lightingColor").setVector(ModShadersVeil.Utils.GetLightingColor(sunDir));
         pipeline.getUniformSafe("lunarPhase").setFloat(ModShadersVeil.Utils.GetLunarPhase(this.level, this.partialTicks));
      }
   }
}
