package dev.protomanly.pmweather.shaders.data;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2i;

public class PrevCachedTextureFBO extends TextureFBO {
   private PMWTexture prevTex;

   public PrevCachedTextureFBO(Vector2i size, ResourceLocation location, String name) {
      super(size, location, name);
   }

   @Override
   public void reset() {
      RenderSystem.assertOnRenderThreadOrInit();
      if (this.texture != null) {
         this.texture.close();
      }

      if (this.prevTex != null) {
         this.prevTex.close();
      }

      VeilRenderer renderer = VeilRenderSystem.renderer();
      FramebufferManager framebufferManager = renderer.getFramebufferManager();
      this.texture = new PMWTexture(this.size);
      this.prevTex = new PMWTexture(this.size);
      AdvancedFbo fbo = AdvancedFbo.withSize(this.size.x, this.size.y)
         .setName(this.name)
         .addColorTextureWrapper(this.texture.getId())
         .setName(this.name + "Previous")
         .addColorTextureWrapper(this.prevTex.getId())
         .build(true);

      try {
         framebufferManager.setFramebuffer(this.location, fbo);
      } catch (Throwable var7) {
         if (fbo != null) {
            try {
               fbo.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (fbo != null) {
         fbo.close();
      }
   }

   @Override
   public void upload() {
      super.upload();
      this.prevTex.upload();
   }

   public void beginNew() {
      this.prevTex.copyFrom(this.texture);
   }

   public int getPrevId() {
      return this.prevTex.getId();
   }
}
