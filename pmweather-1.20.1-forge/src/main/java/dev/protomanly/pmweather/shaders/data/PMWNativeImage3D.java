package dev.protomanly.pmweather.shaders.data;

import com.mojang.blaze3d.platform.DebugMemoryUntracker;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.function.IntUnaryOperator;
import org.joml.Vector3i;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

public class PMWNativeImage3D implements AutoCloseable {
   private final Vector3i dimensions;
   private final Format format;
   private final boolean useStbFree;
   private long pixels;
   private final long size;

   public PMWNativeImage3D(int width, int height, int depth, boolean useCalloc) {
      this(Format.RGBA, width, height, depth, useCalloc);
   }

   public PMWNativeImage3D(Format format, int width, int height, int depth, boolean useCalloc) {
      if (width > 0 && height > 0 && depth > 0) {
         this.format = format;
         this.dimensions = new Vector3i(width, height, depth);
         this.size = (long)width * (long)height * (long)depth * (long)format.components();
         this.useStbFree = false;
         if (useCalloc) {
            this.pixels = MemoryUtil.nmemCalloc(1L, this.size);
         } else {
            this.pixels = MemoryUtil.nmemAlloc(this.size);
         }

         if (this.pixels == 0L) {
            throw new IllegalStateException("Unable to allocate texture of size " + width + "x" + height + " (" + format.components() + " channels)");
         }
      } else {
         throw new IllegalArgumentException("Invalid texture size: " + width + "x" + height + "x" + depth);
      }
   }

   private PMWNativeImage3D(Format format, int width, int height, int depth, boolean useStbFree, long pixels) {
      if (width > 0 && height > 0 && depth > 0) {
         this.format = format;
         this.dimensions = new Vector3i(width, height, depth);
         this.useStbFree = useStbFree;
         this.pixels = pixels;
         this.size = (long)width * (long)height * (long)depth * (long)format.components();
      } else {
         throw new IllegalArgumentException("Invalid texture size: " + width + "x" + height + "x" + depth);
      }
   }

   @Override
   public String toString() {
      String var10000 = String.valueOf(this.format);
      return "PMWTexture3D["
         + var10000
         + " "
         + this.dimensions.x
         + "x"
         + this.dimensions.y
         + "x"
         + this.dimensions.z
         + "@"
         + this.pixels
         + (this.useStbFree ? "S" : "N")
         + "]";
   }

   private boolean isOutsideBounds(int x, int y, int z) {
      return x < 0 || x >= this.dimensions.x || y < 0 || y >= this.dimensions.y || z < 0 || z >= this.dimensions.z;
   }

   private void checkAllocated() {
      if (this.pixels == 0L) {
         throw new IllegalStateException("Image is not allocated.");
      }
   }

   @Override
   public void close() {
      if (this.pixels != 0L) {
         if (this.useStbFree) {
            STBImage.nstbi_image_free(this.pixels);
         } else {
            MemoryUtil.nmemFree(this.pixels);
         }
      }

      this.pixels = 0L;
   }

   public Vector3i getDimensions() {
      return this.dimensions;
   }

   public Format format() {
      return this.format;
   }

   public long getIndex(int x, int y, int z) {
      x = Math.floorMod(x, this.dimensions.x);
      y = Math.floorMod(y, this.dimensions.y);
      z = Math.floorMod(z, this.dimensions.z);
      long i = (long)x + (long)y * (long)this.dimensions.x;
      return i + (long)z * (long)this.dimensions.x * (long)this.dimensions.y;
   }

   public int getPixelRGBA(int x, int y, int z) {
      x = Math.floorMod(x, this.dimensions.x);
      y = Math.floorMod(y, this.dimensions.y);
      z = Math.floorMod(z, this.dimensions.z);
      if (this.format != Format.RGBA) {
         throw new IllegalArgumentException(String.format(Locale.ROOT, "getPixelRGBA only works on RGBA images; have %s", this.format));
      } else if (this.isOutsideBounds(x, y, z)) {
         throw new IllegalArgumentException(
            String.format(Locale.ROOT, "(%s, %s, %s) outside of image bounds (%s, %s, %s)", x, y, z, this.dimensions.x, this.dimensions.y, this.dimensions.z)
         );
      } else {
         this.checkAllocated();
         long i = this.getIndex(x, y, z) * 4L;
         return MemoryUtil.memGetInt(this.pixels + i);
      }
   }

   public void setPixelRGBA(int x, int y, int z, int abgrColor) {
      x = Math.floorMod(x, this.dimensions.x);
      y = Math.floorMod(y, this.dimensions.y);
      z = Math.floorMod(z, this.dimensions.z);
      if (this.format != Format.RGBA) {
         throw new IllegalArgumentException(String.format(Locale.ROOT, "setPixelRGBA only works on RGBA images; have %s", this.format));
      } else if (this.isOutsideBounds(x, y, z)) {
         throw new IllegalArgumentException(
            String.format(Locale.ROOT, "(%s, %s, %s) outside of image bounds (%s, %s, %s)", x, y, z, this.dimensions.x, this.dimensions.y, this.dimensions.z)
         );
      } else {
         this.checkAllocated();
         long i = this.getIndex(x, y, z) * 4L;
         MemoryUtil.memPutInt(this.pixels + i, abgrColor);
      }
   }

   public void setPixelRGBA(long index, int abgrColor) {
      long i = index * 4L;
      if (this.format != Format.RGBA) {
         throw new IllegalArgumentException(String.format(Locale.ROOT, "getPixelRGBA only works on RGBA images; have %s", this.format));
      } else if (i > this.size) {
         throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s) outside of image bounds (%s)", i, this.size));
      } else {
         this.checkAllocated();
         MemoryUtil.memPutInt(this.pixels + i, abgrColor);
      }
   }

   public void applyToAllPixels(IntUnaryOperator function) {
      if (this.format != Format.RGBA) {
         throw new IllegalArgumentException(String.format(Locale.ROOT, "function application only works on RGBA images; have %s", this.format));
      } else {
         this.checkAllocated();
         int i = this.dimensions.x * this.dimensions.y * this.dimensions.z;
         IntBuffer intbuffer = MemoryUtil.memIntBuffer(this.pixels, i);

         for (int j = 0; j < i; j++) {
            intbuffer.put(j, function.applyAsInt(intbuffer.get(j)));
         }
      }
   }

   public IntBuffer getData() {
      GlStateManager._pixelStore(3314, 0);
      GlStateManager._pixelStore(3315, 0);
      GlStateManager._pixelStore(3316, 0);
      this.format.setUnpackPixelStoreState();
      return MemoryUtil.memIntBuffer(this.pixels, this.dimensions.x * this.dimensions.y * this.dimensions.z);
   }

   public void untrack() {
      DebugMemoryUntracker.untrack(this.pixels);
   }
}
