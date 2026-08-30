package dev.protomanly.pmweather.util;

import dev.protomanly.pmweather.PMWeather;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import net.minecraft.util.Mth;

public class Sampler2D {
   private final float[] data;
   private final int width;
   private final int height;
   private boolean bilinear = false;

   public Sampler2D(String path) {
      URL url = Thread.currentThread().getContextClassLoader().getResource(path);

      BufferedImage bufferedImage;
      try {
         bufferedImage = ImageIO.read(url);
      } catch (IOException var6) {
         PMWeather.LOGGER.error(var6.getMessage(), var6);
         bufferedImage = new BufferedImage(1, 1, 2);
      }

      this.width = bufferedImage.getWidth();
      this.height = bufferedImage.getHeight();
      this.data = new float[this.width * this.height];
      int[] pixels = new int[this.data.length];
      bufferedImage.getRGB(0, 0, this.width, this.height, pixels, 0, this.width);

      for (int i = 0; i < this.data.length; i++) {
         this.data[i] = (float)(pixels[i] & 0xFF) / 255.0F;
      }
   }

   private static float interpolate2D(float x, float y, float v1, float v2, float v3, float v4) {
      return Mth.lerp(y, Mth.lerp(x, v1, v2), Mth.lerp(x, v3, v4));
   }

   private static long wrap(long value, long side) {
      if (side != 0L && (side & side - 1L) == 0L) {
         return value & side - 1L;
      } else {
         long r = value - value / side * side;
         return r < 0L ? r + side : r;
      }
   }

   public float sample(float x, float y) {
      x *= (float)this.width;
      y *= (float)this.height;
      long x1 = (long)Mth.floor(x);
      long y1 = (long)Mth.floor(y);
      long x2 = wrap(x1 + 1L, (long)this.width);
      long y2 = wrap(y1 + 1L, (long)this.height);
      float dx = x - (float)x1;
      float dy = y - (float)y1;
      x1 = wrap(x1, (long)this.width);
      y1 = wrap(y1, (long)this.height);
      float a = this.data[this.getIndex((int)x1, (int)y1)];
      float b = this.data[this.getIndex((int)x2, (int)y1)];
      float c = this.data[this.getIndex((int)x1, (int)y2)];
      float d = this.data[this.getIndex((int)x2, (int)y2)];
      if (this.bilinear) {
         dx = this.smoothStep(dx);
         dy = this.smoothStep(dy);
      }

      return interpolate2D(dx, dy, a, b, c, d);
   }

   public void setBilinear(boolean bilinear) {
      this.bilinear = bilinear;
   }

   private int getIndex(int x, int y) {
      return y * this.width + x;
   }

   private float smoothStep(float x) {
      return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
   }
}
