package dev.protomanly.pmweather.shaders.data;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;

public class FBOManager {
   public static PrevCachedTextureFBO LOCAL_WIND = new PrevCachedTextureFBO(new Vector2i(64), PMWeather.getPath("local_wind"), "LocalWindSampler");
   public static Vec3 LAST_POSITION = Vec3.ZERO;
   public static Vec3 PREV_LAST_POSITION = Vec3.ZERO;
   public static long LAST_TICK = 0L;
   public static final long TICK_DELTA = 15L;
   private static boolean dirty = false;
   private static final float windScale = 500.0F;
   private static final int windUpscale = 4;

   public FBOManager() {
   }

   public static void uploadFBOs() {
      if (dirty) {
         LOCAL_WIND.upload();
         dirty = false;
      }
   }

   public static void reset() {
      LAST_TICK = -10000L;
      LAST_POSITION = Vec3.ZERO;
      LOCAL_WIND.clear();
      LOCAL_WIND.reset();
      dirty = true;
   }

   public static float getTimeDelta(float time) {
      return (time - (float)(LAST_TICK - 15L)) / 15.0F;
   }

   public static void tickFBOs(Vec3 curPos, Level level, long tick) {
      if (tick > LAST_TICK && ClientConfig.swayingGrass) {
         BlockPos curBlockPos = BlockPos.containing(curPos);
         Vector2i size = LOCAL_WIND.getSize();
         PREV_LAST_POSITION = LAST_POSITION;
         LOCAL_WIND.beginNew();
         LOCAL_WIND.write((x, y) -> {
            BlockPos pixelPos = curBlockPos.offset((x - size.x / 2) * 4, 0, (y - size.y / 2) * 4).atY(95);
            Vec3 pixelPosV3 = pixelPos.getCenter();
            Vec3 wind = WindEngine.getWind(pixelPosV3, level, false, false, false, true);
            wind = getFormattedMotion(wind);
            return Util.getRGBA(wind);
         });
         LAST_POSITION = curBlockPos.getCenter();
         LAST_TICK = tick + 15L;
         dirty = true;
      }
   }

   private static double absHalfSqrt(double v) {
      return Math.sqrt(Math.abs(2.0 * v)) / 2.0 * Math.signum(v);
   }

   private static Vec3 sqrtVec3(Vec3 vec3) {
      return new Vec3(absHalfSqrt(vec3.x), absHalfSqrt(vec3.y), absHalfSqrt(vec3.z));
   }

   public static Vec3 getFormattedMotion(Vec3 raw) {
      Vec3 out = new Vec3(raw.x, raw.y, raw.z);
      out = out.scale(0.002F);
      out = sqrtVec3(out);
      return out.add(0.5, 0.5, 0.5);
   }
}
