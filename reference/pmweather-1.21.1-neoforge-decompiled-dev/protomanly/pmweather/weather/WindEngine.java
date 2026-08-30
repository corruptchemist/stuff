package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

public class WindEngine {
   public static SimplexNoise simplexNoise;
   public static Map<ChunkPos, Float> FireIntensityCache = new HashMap<>();

   public WindEngine() {
   }

   public static float getFireIntensity(Level level, ChunkPos pos) {
      if (FireIntensityCache.containsKey(pos)) {
         Float rtrn = FireIntensityCache.get(pos);
         if (rtrn != null) {
            return rtrn;
         }
      }

      ChunkAccess chunkAccess = level.getChunk(pos.x, pos.z);
      float fireIntensity = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);
      FireIntensityCache.put(pos, fireIntensity);
      return fireIntensity;
   }

   public static void init(WeatherHandler weatherHandler) {
      simplexNoise = new SimplexNoise(new LegacyRandomSource(weatherHandler.seed));
   }

   public static double FBM(Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = 0.0;
      if (simplexNoise != null) {
         for (int i = 0; i < Math.max(octaves, 1); i++) {
            y += (double)amplitude * simplexNoise.getValue(pos.x, pos.y, pos.z);
            pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
            amplitude *= gain;
         }
      }

      return y;
   }

   public static float getSwirl(Vec3 position, Level level, float sampleSize) {
      Vec3 sample1Z = getWind(position.add(0.0, 0.0, (double)sampleSize), level).normalize();
      Vec3 sample2Z = getWind(position.add(0.0, 0.0, (double)(-sampleSize)), level).normalize();
      Vec3 sample1X = getWind(position.add((double)(-sampleSize), 0.0, 0.0), level).normalize();
      Vec3 sample2X = getWind(position.add((double)sampleSize, 0.0, 0.0), level).normalize();
      double compZ = (-sample1Z.dot(sample2Z) + 1.0) / 2.0;
      double compX = (-sample1X.dot(sample2X) + 1.0) / 2.0;
      return (float)(compZ * compX);
   }

   public static Vec3 getWind(Vec3 position, Level level) {
      return getWind(position, level, false, false, true, false);
   }

   public static Vec3 getWind(Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck) {
      return getWind(position, level, ignoreStorms, ignoreTornadoes, windCheck, false);
   }

   public static Vec3 getWind(Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck, boolean windAnyway) {
      return getWind(position, level, ignoreStorms, ignoreTornadoes, windCheck, windAnyway, false);
   }

   public static Vec3 getWind(
      Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck, boolean windAnyway, boolean forParticles
   ) {
      int worldHeight = level.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(position)).getY();
      return getWind(position, level, ignoreStorms, ignoreTornadoes, windCheck, windAnyway, forParticles, worldHeight);
   }

   public static Vec3 getWind(
      Vec3 position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck, boolean windAnyway, boolean forParticles, int worldHeight
   ) {
      Vec3 wind = Vec3.ZERO;
      Vec3 rawWind = Vec3.ZERO;
      BlockPos blockPos = BlockPos.containing(position);
      List<Storm> tornadicStorms = new ArrayList<>();
      if (level == null) {
         PMWeather.LOGGER.warn("Level is null");
         return wind;
      } else {
         if (windCheck && !windAnyway) {
            if (!Util.canWindAffect(position, level)) {
               return wind;
            }
         } else if (!windAnyway && position.y < (double)worldHeight) {
            return wind;
         }

         if (simplexNoise != null) {
            float timeScale = 20000.0F;
            float scale = 12000.0F;
            double ang = FBM(
               new Vec3(position.x / (double)(scale * 3.0F), position.z / (double)(scale * 3.0F), (double)((float)level.getGameTime() / (timeScale * 6.0F))),
               5,
               2.0F,
               0.1F,
               1.0F
            );
            ang *= Math.PI;
            Vec3 dir = new Vec3(Math.cos(ang), 0.0, Math.sin(ang)).normalize();
            double speed = Math.max(
                  simplexNoise.getValue(-position.z / (double)scale, -position.x / (double)scale, (double)(-((float)level.getGameTime()) / timeScale)) + 1.0,
                  0.0
               )
               * 12.0;
            speed *= ServerConfig.backgroundWindMultiplier;
            float windMult = 1.0F + (SeasonHandler.getSeasonEffectSine(level, 6.0F) + 1.0F) * 0.4F;
            speed *= (double)windMult;
            wind = wind.add(dir.multiply(speed, speed, speed));
            WeatherHandler weatherHandler;
            if (level.isClientSide()) {
               weatherHandler = GameBusClientEvents.weatherHandler;
            } else {
               weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
            }

            if (weatherHandler != null && !ignoreStorms) {
               if (position.y() > (double)(worldHeight - 16)) {
                  Vec3 samplePos = new Vec3(
                     position.x / (double)(scale / 100.0F), position.z / (double)(scale / 100.0F), (double)((float)level.getGameTime() / (timeScale / 15.0F))
                  );
                  double nx = FBM(samplePos, 5, 2.0F, 0.5F, 1.0F);
                  double nz = FBM(new Vec3(samplePos.y, samplePos.x, -samplePos.z), 5, 2.0F, 0.5F, 1.0F);
                  nx *= 8.0;
                  nz *= 8.0;
                  BlockPos tornadicEffectNew = blockPos.offset((int)nx, 0, (int)nz);
                  Vec3 offsetTotal = Vec3.ZERO;
                  int total = 0;

                  for (int x = -1; x <= 1; x++) {
                     for (int z = -1; z <= 1; z++) {
                        total++;
                        ChunkPos adjChunkPos = new ChunkPos(tornadicEffectNew.offset(x * 16, 0, z * 16));
                        Vec3 vec = new Vec3((double)x, 0.0, (double)z);
                        if (vec.length() > 0.01F) {
                           vec = vec.normalize().scale(1.0 / Math.pow(vec.length(), 1.5));
                        }

                        float fireIntensity = Math.min(getFireIntensity(level, adjChunkPos), 10.0F);
                        vec = vec.scale((double)(Mth.square(fireIntensity / 3.0F) * 15.0F * 3.0F));
                        offsetTotal = offsetTotal.add(vec);
                     }
                  }

                  offsetTotal = offsetTotal.scale((double)(1.0F / (float)total));
                  wind = wind.add(offsetTotal);
               }

               for (Storm storm : weatherHandler.getStorms()) {
                  if (!storm.visualOnly) {
                     if (storm.isTornadic()) {
                        tornadicStorms.add(storm);
                     }

                     rawWind = rawWind.add(storm.getRawWind(position));
                     wind = wind.add(storm.getBaseWind(position));
                  }
               }
            }
         }

         if (wind.length() > 30.0) {
            double over = wind.length() - 30.0;
            double val = 30.0 + over / 3.0;
            wind = wind.normalize().multiply(val, val, val);
         }

         if (blockPos.getY() > 85) {
            float val = Math.clamp((float)(blockPos.getY() - 85) / 40.0F, 0.0F, 1.0F) / 2.0F + 1.0F;
            wind = wind.multiply((double)val, (double)val, (double)val);
         }

         wind = wind.add(rawWind);
         int heightAbove = blockPos.getY() - worldHeight;
         if (heightAbove > 0) {
            float val = Math.clamp((float)heightAbove / 15.0F, 0.0F, 1.0F) / 3.0F + 1.0F;
            wind = wind.multiply((double)val, (double)val, (double)val);
         }

         float tornadicEffect = 0.0F;
         Vec3 tornadicWindForNormal = Vec3.ZERO;
         double maxTorMag = 0.0;
         if (!ignoreStorms && !ignoreTornadoes) {
            for (Storm tornadicStorm : tornadicStorms) {
               Vec3 relativePos = position.subtract(tornadicStorm.position);
               if (forParticles) {
                  float noiseX = (float)FBM(
                     new Vec3(-position.y / 10.0, -position.y / 10.0, (double)((float)level.getGameTime() / 100.0F)), 5, 2.0F, 0.2F, 1.0F
                  );
                  float noiseZ = (float)FBM(new Vec3(position.y / 10.0, position.y / 10.0, (double)((float)level.getGameTime() / 100.0F)), 5, 2.0F, 0.2F, 1.0F);
                  float eff = (float)Math.clamp(relativePos.y() / 80.0, 0.0, 1.0) * 20.0F;
                  relativePos = relativePos.add((double)(noiseX * eff), 0.0, (double)(noiseZ * eff));
               }

               Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
               Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
               double distance = relativePos.multiply(1.0, 0.0, 1.0).length();
               if (!(distance > (double)((float)Math.max((int)tornadicStorm.width, tornadicStorm.is(StormTypes.FIRE_WHIRL) && !forParticles ? 20 : 40) * 2.0F))
                  )
                {
                  double windEffect = (double)tornadicStorm.getTornadicWind(position, forParticles);
                  float tornadicEffectNew = 0.0F;
                  if (tornadicStorm.is(StormTypes.SUPERCELL) || forParticles) {
                     tornadicEffectNew = Math.clamp(
                        (float)windEffect / Math.clamp((float)tornadicStorm.windspeed, 60.0F, Math.max((float)tornadicStorm.windspeed / 1.5F, 60.0F)),
                        0.0F,
                        1.0F
                     );
                  }

                  double inPerc = 0.35;
                  if (forParticles) {
                     inPerc = 0.65F;
                     windEffect = Math.sqrt(windEffect / 15.0) * 15.0;
                  }

                  if (Double.isNaN(windEffect)) {
                     windEffect = 0.0;
                  }

                  if (!Float.isNaN(tornadicEffectNew)) {
                     tornadicEffect = Math.max(tornadicEffect, tornadicEffectNew);
                  }

                  Vec3 pointTo = inward.multiply(inPerc, 0.0, inPerc).add(rotational.multiply(1.0 - inPerc, 0.0, 1.0 - inPerc)).normalize();
                  Vec3 torWindToAdd = Vec3.ZERO;
                  if (tornadicStorm.is(StormTypes.FIRE_WHIRL)) {
                     if (forParticles) {
                        torWindToAdd = pointTo.scale(3.0 * windEffect).add(0.0, windEffect / 2.3333333F, 0.0);
                     } else {
                        wind = wind.add(pointTo.scale(windEffect)).add(0.0, windEffect / 7.0, 0.0);
                     }
                  } else {
                     torWindToAdd = pointTo.scale(windEffect);
                  }

                  tornadicWindForNormal = tornadicWindForNormal.add(torWindToAdd);
                  double mag = torWindToAdd.length();
                  maxTorMag = Math.max(mag, maxTorMag);
               }
            }
         }

         if (tornadicEffect > 0.0F) {
            Vec3 windNormal = wind.normalize();
            Vec3 tornadicNormal = tornadicWindForNormal.normalize();
            Vec3 normalToUse = windNormal.lerp(tornadicNormal, (double)tornadicEffect).normalize();
            double windspeedToUse = Math.max(wind.length(), maxTorMag);
            return normalToUse.scale(windspeedToUse);
         } else {
            return wind;
         }
      }
   }

   public static Vec3 getWind(BlockPos position, Level level, boolean ignoreStorms, boolean ignoreTornadoes, boolean windCheck) {
      return getWind(
         new Vec3((double)position.getX(), (double)(position.getY() + 1), (double)position.getZ()), level, ignoreStorms, ignoreTornadoes, windCheck, false
      );
   }

   public static Vec3 getWind(BlockPos position, Level level) {
      return getWind(new Vec3((double)position.getX(), (double)(position.getY() + 1), (double)position.getZ()), level, false, false, true, false);
   }
}
