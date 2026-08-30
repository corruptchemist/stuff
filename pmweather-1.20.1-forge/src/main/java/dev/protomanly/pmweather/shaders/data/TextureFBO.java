package dev.protomanly.pmweather.shaders.data;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2i;

public class TextureFBO {
   protected Vector2i size;
   protected ResourceLocation location;
   protected PMWTexture texture;
   protected String name;

   public TextureFBO(Vector2i size, ResourceLocation location, String name) {
      this.size = size;
      this.location = location;
      this.name = name;
      this.reset();
   }

   public void reset() {
      RenderSystem.assertOnRenderThreadOrInit();
      if (this.texture != null) {
         this.texture.close();
      }

      VeilRenderer renderer = VeilRenderSystem.renderer();
      FramebufferManager framebufferManager = renderer.getFramebufferManager();
      this.texture = new PMWTexture(this.size);
      AdvancedFbo fbo = AdvancedFbo.withSize(this.size.x, this.size.y).setName(this.name).addColorTextureWrapper(this.texture.getId()).build(true);

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

   public void clear() {
      this.texture.clear();
   }

   public void write(int x, int y, int color) {
      this.texture.write(x, y, color);
   }

   public void setColor(int color) {
      this.texture.setColor(color);
   }

   public void write(BiFunction<Integer, Integer, Integer> function) {
      this.texture.write(function);
   }

   public void upload() {
      this.texture.upload();
   }

   public void bind() {
      this.texture.bind();
   }

   public void unbind() {
      this.texture.unbind();
   }

   public ResourceLocation getLocation() {
      return this.location;
   }

   public Vector2i getSize() {
      return this.size;
   }

   public int getId() {
      return this.texture.getId();
   }
}
