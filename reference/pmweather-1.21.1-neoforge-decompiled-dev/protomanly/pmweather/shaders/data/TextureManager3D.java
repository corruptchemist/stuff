package dev.protomanly.pmweather.shaders.data;

import org.joml.SimplexNoise;
import org.joml.Vector3i;

public class TextureManager3D {
   private static final int TEX_SIZE = 256;
   public static PMWTexture3D NOISE3D = new PMWTexture3D(new Vector3i(256));

   public TextureManager3D() {
   }

   private static float FBM(float x, float y, float z, float w, int octaves, float lacunarity, float gain, float amplitude) {
      float v = 0.0F;

      for (int i = 0; i < Math.max(octaves, 1); i++) {
         v += amplitude * SimplexNoise.noise(x, y, z, w);
         x *= lacunarity;
         y *= lacunarity;
         z *= lacunarity;
         w *= lacunarity;
         amplitude *= gain;
      }

      return v;
   }

   public static void refresh() {
      NOISE3D.reset();
      NOISE3D.upload();
   }
}
