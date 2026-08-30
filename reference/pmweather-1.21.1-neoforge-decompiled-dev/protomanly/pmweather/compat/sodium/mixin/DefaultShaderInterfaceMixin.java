package dev.protomanly.pmweather.compat.sodium.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.protomanly.pmweather.compat.sodium.interfaces.IDefaultShaderInterface;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DefaultShaderInterface.class})
public class DefaultShaderInterfaceMixin implements IDefaultShaderInterface {
   @Unique
   @Final
   private GlUniformFloat VeilRenderTime;
   @Unique
   @Final
   private GlUniformInt doesSway;
   @Unique
   @Final
   private GlUniformFloat pmwtime;
   @Unique
   @Final
   private GlUniformFloat3v cameraPos;
   @Unique
   @Final
   private GlUniformInt _pmwWindTex;
   @Unique
   @Final
   private GlUniformInt _pmwPrevWindTex;
   @Unique
   @Final
   private GlUniformFloat3v fboOffset;
   @Unique
   @Final
   private GlUniformFloat3v prevFboOffset;
   @Unique
   @Final
   private GlUniformFloat fboTimeDelta;
   @Unique
   @Final
   private static int PMW_WIND_ORDINAL = 5;

   public DefaultShaderInterfaceMixin() {
   }

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void onInit(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
      this.VeilRenderTime = (GlUniformFloat)context.bindUniform("VeilRenderTime", GlUniformFloat::new);
      this.doesSway = (GlUniformInt)context.bindUniform("doesSway", GlUniformInt::new);
      this.pmwtime = (GlUniformFloat)context.bindUniform("pmwtime", GlUniformFloat::new);
      this.cameraPos = (GlUniformFloat3v)context.bindUniform("cameraPos", GlUniformFloat3v::new);
      this._pmwWindTex = (GlUniformInt)context.bindUniformOptional("_pmwWindTex", GlUniformInt::new);
      this._pmwPrevWindTex = (GlUniformInt)context.bindUniformOptional("_pmwPrevWindTex", GlUniformInt::new);
      this.fboOffset = (GlUniformFloat3v)context.bindUniformOptional("fboOffset", GlUniformFloat3v::new);
      this.prevFboOffset = (GlUniformFloat3v)context.bindUniformOptional("prevFboOffset", GlUniformFloat3v::new);
      this.fboTimeDelta = (GlUniformFloat)context.bindUniformOptional("fboTimeDelta", GlUniformFloat::new);
   }

   @Inject(
      method = {"setupState"},
      at = {@At("TAIL")}
   )
   private void injectSetupState(CallbackInfo ci) {
      if (this._pmwWindTex != null) {
         int windTexId = FBOManager.LOCAL_WIND.getId();
         GlStateManager._activeTexture(33984 + PMW_WIND_ORDINAL);
         GlStateManager._bindTexture(windTexId);
         this._pmwWindTex.set(PMW_WIND_ORDINAL);
         if (this._pmwPrevWindTex != null) {
            int windPrevTexId = FBOManager.LOCAL_WIND.getPrevId();
            GlStateManager._activeTexture(33984 + PMW_WIND_ORDINAL + 1);
            GlStateManager._bindTexture(windPrevTexId);
            this._pmwPrevWindTex.set(PMW_WIND_ORDINAL + 1);
         }
      }
   }

   @Override
   public void setPMWDefaults(Vec3 cameraPos, float time, int doesSway) {
      if (ClientConfig.swayingGrass && doesSway != 0) {
         this.cameraPos.set((float)(cameraPos.x % 4096.0), (float)(cameraPos.y % 4096.0), (float)(cameraPos.z % 4096.0));
         this.pmwtime.set(time);
         this.doesSway.set(1);
         this.VeilRenderTime.set((float)(System.currentTimeMillis() % 3600000L) / 1000.0F);
         if (this.fboOffset != null) {
            Vec3 offset = cameraPos.subtract(FBOManager.LAST_POSITION);
            this.fboOffset.set((float)offset.x, (float)offset.y, (float)offset.z);
            if (this.prevFboOffset != null) {
               Vec3 prevOffset = cameraPos.subtract(FBOManager.PREV_LAST_POSITION);
               this.prevFboOffset.set((float)prevOffset.x, (float)prevOffset.y, (float)prevOffset.z);
               this.fboTimeDelta.set(FBOManager.getTimeDelta(time));
            }
         }
      } else {
         this.doesSway.set(0);
      }
   }
}
