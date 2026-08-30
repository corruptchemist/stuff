package dev.protomanly.pmweather.shaders.data;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.mixin.pipeline.accessor.PipelineNativeImageAccessor;
import java.nio.IntBuffer;
import java.util.function.BiFunction;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

public class PMWTexture {
   private Vector2i size;
   private int id = -1;
   private NativeImage image;
   private final int filter;

   public PMWTexture(Vector2i size) {
      this(size, 9729);
   }

   public PMWTexture(Vector2i size, int filter) {
      this.size = size;
      this.filter = filter;
      this.reset();
   }

   public void close() {
      RenderSystem.assertOnRenderThreadOrInit();
      if (this.image != null) {
         this.image.untrack();
         this.image.close();
      }
   }

   public void reset() {
      RenderSystem.assertOnRenderThreadOrInit();
      this.close();
      this.image = new NativeImage(this.size.x, this.size.y, true);
      this.clear();
      this.bind();
      GlStateManager._texParameter(this.getTextureTarget(), 10241, this.filter);
      GlStateManager._texParameter(this.getTextureTarget(), 10240, this.filter);
      GlStateManager._texImage2D(
         this.getTextureTarget(), 0, this.image.format().glFormat(), this.size.x, this.size.y, 0, this.image.format().glFormat(), 5121, this.getData()
      );
      this.unbind();
   }

   public void resize(Vector2i size) {
      if (!this.size.equals(size)) {
         this.size = size;
         this.reset();
      }
   }

   private IntBuffer getData() {
      PipelineNativeImageAccessor accessor = (PipelineNativeImageAccessor)this.image;
      accessor.invokeCheckAllocated();
      Format format = this.image.format();
      GlStateManager._pixelStore(3314, 0);
      GlStateManager._pixelStore(3315, 0);
      GlStateManager._pixelStore(3316, 0);
      format.setUnpackPixelStoreState();
      long pixels = accessor.getPixels();
      return MemoryUtil.memIntBuffer(pixels, this.image.getWidth() * this.image.getHeight());
   }

   public void clear() {
      this.image.applyToAllPixels(x -> -16777216);
   }

   public void setColor(int color) {
      this.image.applyToAllPixels(x -> color);
   }

   public void write(int x, int y, int color) {
      this.image.setPixelRGBA(x, y, color);
   }

   public void copyFrom(PMWTexture from) {
      this.image.copyFrom(from.image);
   }

   public void copyFrom(NativeImage from) {
      this.image.copyFrom(from);
   }

   public NativeImage getCopy() {
      NativeImage copy = new NativeImage(this.size.x, this.size.y, false);
      copy.copyFrom(this.image);
      return copy;
   }

   public void write(BiFunction<Integer, Integer, Integer> function) {
      for (int x = 0; x < this.size.x; x++) {
         for (int y = 0; y < this.size.y; y++) {
            this.write(x, y, function.apply(x, y));
         }
      }
   }

   public void upload() {
      RenderSystem.assertOnRenderThreadOrInit();
      this.bind();
      GlStateManager._texImage2D(
         this.getTextureTarget(), 0, this.image.format().glFormat(), this.size.x, this.size.y, 0, this.image.format().glFormat(), 5121, this.getData()
      );
      this.unbind();
   }

   public int getId() {
      RenderSystem.assertOnRenderThreadOrInit();
      if (this.id == -1) {
         this.id = VeilRenderSystem.createTextures(this.getTextureTarget());
      }

      return this.id;
   }

   public void bind() {
      GL11C.glBindTexture(this.getTextureTarget(), this.getId());
   }

   public void unbind() {
      GL11C.glBindTexture(this.getTextureTarget(), 0);
   }

   public int getTextureTarget() {
      return 3553;
   }

   public Vector2i getSize() {
      return new Vector2i(this.size);
   }

   public NativeImage getImage() {
      return this.image;
   }
}
