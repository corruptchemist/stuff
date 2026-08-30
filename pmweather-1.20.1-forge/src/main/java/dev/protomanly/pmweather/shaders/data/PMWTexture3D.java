package dev.protomanly.pmweather.shaders.data;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import org.apache.commons.lang3.function.TriFunction;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33;

public class PMWTexture3D {
   private Vector3i size;
   private int id = -1;
   private PMWNativeImage3D image;
   private final int filter;
   private final int wrapMode;

   public PMWTexture3D(Vector3i size) {
      this(size, 9729, 10497);
   }

   public PMWTexture3D(Vector3i size, int wrapMode) {
      this(size, 9729, wrapMode);
   }

   public PMWTexture3D(Vector3i size, int filter, int wrapMode) {
      this.size = size;
      this.filter = filter;
      this.wrapMode = wrapMode;
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
      if (this.image == null) {
         this.close();
         this.image = new PMWNativeImage3D(this.size.x, this.size.y, this.size.z, true);
         this.clear();
      }

      this.id = -1;
      this.bind();
      GL33.glTexParameteri(this.getTextureTarget(), 10241, this.filter);
      GL33.glTexParameteri(this.getTextureTarget(), 10240, this.filter);
      GL33.glTexParameteri(this.getTextureTarget(), 10242, this.wrapMode);
      GL33.glTexParameteri(this.getTextureTarget(), 10243, this.wrapMode);
      GL33.glTexParameteri(this.getTextureTarget(), 32882, this.wrapMode);
      GL33.glTexImage3D(
         this.getTextureTarget(),
         0,
         this.image.format().glFormat(),
         this.size.x,
         this.size.y,
         this.size.z,
         0,
         this.image.format().glFormat(),
         5121,
         this.image.getData()
      );
      this.unbind();
   }

   public void resize(Vector3i size) {
      if (!this.size.equals(size)) {
         this.size = size;
         this.reset();
      }
   }

   public void clear() {
      this.image.applyToAllPixels(x -> -16777216);
   }

   public void write(int x, int y, int z, int color) {
      this.image.setPixelRGBA(x, y, z, color);
   }

   public void write(TriFunction<Integer, Integer, Integer, Integer> function) {
      for (int x = 0; x < this.size.x; x++) {
         for (int y = 0; y < this.size.y; y++) {
            for (int z = 0; z < this.size.z; z++) {
               this.write(x, y, z, (Integer)function.apply(x, y, z));
            }
         }
      }
   }

   public int read(int x, int y, int z) {
      return this.image.getPixelRGBA(x, y, z);
   }

   public void upload() {
      RenderSystem.assertOnRenderThreadOrInit();
      this.bind();
      GL33.glTexImage3D(
         this.getTextureTarget(),
         0,
         this.image.format().glFormat(),
         this.size.x,
         this.size.y,
         this.size.z,
         0,
         this.image.format().glFormat(),
         5121,
         this.image.getData()
      );
      this.unbind();
   }

   public void bind() {
      GL11C.glBindTexture(this.getTextureTarget(), this.getId());
   }

   public void unbind() {
      GL11C.glBindTexture(this.getTextureTarget(), 0);
   }

   public int getId() {
      RenderSystem.assertOnRenderThreadOrInit();
      if (this.id == -1) {
         this.id = VeilRenderSystem.createTextures(this.getTextureTarget());
      }

      return this.id;
   }

   public Vector3i getSize() {
      return new Vector3i(this.size);
   }

   public int getTextureTarget() {
      return 32879;
   }

   public PMWNativeImage3D getImage() {
      return this.image;
   }
}
