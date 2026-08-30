package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

public class ThermodynamicEngine {
   public static SimplexNoise noise = null;
   public static float xzScale = 15000.0F;
   public static float yScale = 2000.0F;
   public static float timeScale = 20000.0F;
   public static float cachedBiomeTemp = 0.0F;
   public static float cachedHumidity = 0.0F;
   public static float cachedPBLHeight = 0.0F;
   public static float cachedSfcTNoise = 0.0F;
   public static float cachedPNoise = 0.0F;
   public static float cachedNoise = 0.0F;
   public static float cachedTime = 0.0F;
   public static Vec3 cachedPos = null;

   public ThermodynamicEngine() {
   }

   public static float FBM(Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = 0.0;

      for (int i = 0; i < Math.max(octaves, 1); i++) {
         y += (double)amplitude * noise.getValue(pos.x, pos.y, pos.z);
         pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
         amplitude *= gain;
      }

      return (float)y;
   }

   public static ThermodynamicEngine.Precipitation getPrecipitationType(WeatherHandler weatherHandler, Vec3 pos, Level level, int advance) {
      return getPrecipitationType(weatherHandler, pos, level, advance, 250);
   }

   public static ThermodynamicEngine.Precipitation getPrecipitationType(WeatherHandler weatherHandler, Vec3 pos, Level level, int advance, int delta) {
      int start = 4000;
      ThermodynamicEngine.Precipitation precip = ThermodynamicEngine.Precipitation.SNOW;
      float groundTemp = samplePoint(weatherHandler, pos, level, null, advance).temperature();
      int y = start;

      while (y >= 0) {
         float rainTemp = samplePoint(weatherHandler, pos.add(0.0, (double)y, 0.0), level, null, advance).temperature();
         if (rainTemp < 3.0F && rainTemp > -1.0F) {
            precip = ThermodynamicEngine.Precipitation.WINTRY_MIX;
         } else if (rainTemp <= 0.0F) {
            precip = switch (precip) {
               case RAIN, WINTRY_MIX -> ThermodynamicEngine.Precipitation.SLEET;
               default -> precip;
            };
         } else {
            precip = switch (precip) {
               case SLEET, SNOW, WINTRY_MIX -> ThermodynamicEngine.Precipitation.RAIN;
               default -> precip;
            };
         }

         y -= delta;
      }

      if ((precip == ThermodynamicEngine.Precipitation.RAIN || precip == ThermodynamicEngine.Precipitation.WINTRY_MIX) && groundTemp <= 0.0F) {
         precip = ThermodynamicEngine.Precipitation.FREEZING_RAIN;
      }

      return precip;
   }

   public static ThermodynamicEngine.AtmosphericDataPoint samplePoint(
      WeatherHandler weatherHandler, Vec3 pos, Level level, @Nullable RadarBlockEntity radarBlockEntity, int advance
   ) {
      return samplePoint(weatherHandler, pos, level, radarBlockEntity, advance, radarBlockEntity != null ? radarBlockEntity.getBlockPos().getY() : null, true);
   }

   @Nullable
   public static Float GetSST(WeatherHandler weatherHandler, Vec3 pos, Level level, @Nullable RadarBlockEntity radarBlockEntity, int advance) {
      BlockPos blockPos = new BlockPos((int)pos.x, level.getSeaLevel(), (int)pos.z);
      float sst = 0.0F;
      noise = WindEngine.simplexNoise;
      if (noise == null) {
         return null;
      } else {
         float time = (float)(level.getDayTime() + (long)advance);
         float biomeTemp = 0.0F;
         float humidity = 0.0F;
         int c = 0;
         boolean isOcean = false;

         for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
               if (Mth.abs(x) != 1 || Mth.abs(z) != 1) {
                  c++;
                  BlockPos p = blockPos.offset(new Vec3i(x * 64, 0, z * 64));
                  Holder<Biome> biome;
                  if (radarBlockEntity != null && radarBlockEntity.init) {
                     biome = radarBlockEntity.getNearestBiome(p);
                  } else {
                     biome = level.getBiome(p);
                  }

                  String rn = biome.getRegisteredName().toLowerCase();
                  boolean ocean = rn.contains("ocean");
                  if (x == 0 && z == 0) {
                     isOcean = ocean;
                  }

                  float bt = ((Biome)biome.value()).getBaseTemperature();
                  if (ocean) {
                     if (rn.contains("frozen")) {
                        bt -= 0.5F;
                        humidity -= 0.4F;
                     }

                     if (rn.contains("cold")) {
                        bt -= 0.35F;
                        humidity -= 0.2F;
                     }

                     if (rn.contains("lukewarm")) {
                        bt += 0.05F;
                        humidity += 0.35F;
                     } else if (rn.contains("warm")) {
                        bt += 0.25F;
                        humidity += 0.6F;
                     }
                  }

                  bt += 0.075F;
                  biomeTemp += bt;
                  humidity += Math.max(((Biome)biome.value()).getModifiedClimateSettings().downfall(), 0.0F);
               }
            }
         }

         humidity = Math.max(humidity, 0.0F);
         if (!isOcean) {
            return null;
         } else {
            float daytime = (float)(level.getDayTime() + (long)advance) / 24000.0F;
            double x = ((double)daytime - 0.18) * Math.PI * 2.0;
            double timeFactor = Math.sin(x + Math.sin(x) / -2.0);
            humidity /= (float)c;
            biomeTemp /= (float)c;
            biomeTemp -= 0.15F;
            float sfcTNoise = FBM(
               pos.multiply((double)(1.0F / xzScale), 0.0, (double)(1.0F / xzScale)).add(0.0, (double)(time / (timeScale * 15.0F)), 0.0), 6, 2.0F, 0.5F, 1.0F
            );
            sfcTNoise *= 2.0F;
            sfcTNoise += 6.5F;
            if (biomeTemp <= 0.0F) {
               sst = Mth.lerp(-biomeTemp, 0.0F, -25.0F + sfcTNoise);
            } else {
               sst = Mth.lerp((float)Math.pow((double)biomeTemp / 1.85, 0.5), 0.0F, 30.0F + sfcTNoise);
            }

            sst += humidity * 3.0F;
            float sfcTempTimeMod = (float)timeFactor * 5.0F * Math.max(1.0F - humidity, 0.05F);
            sfcTempTimeMod += 5.0F;
            sst += sfcTempTimeMod / 8.5F;
            sst += 6.0F;
            if (ServerConfig.doSeasons) {
               float seasonEffect = SeasonHandler.getSeasonEffectSine(level, 1.5F);
               if (seasonEffect < 0.0F) {
                  seasonEffect /= 1.25F;
               }

               sst += seasonEffect * 2.5F;
            }

            return sst - 2.5F;
         }
      }
   }

   public static ThermodynamicEngine.AtmosphericDataPoint samplePoint(
      WeatherHandler weatherHandler, Vec3 pos, Level level, @Nullable RadarBlockEntity radarBlockEntity, int advance, @Nullable Integer groundHeight
   ) {
      return samplePoint(weatherHandler, pos, level, radarBlockEntity, advance, groundHeight, true);
   }

   public static ThermodynamicEngine.AtmosphericDataPoint samplePoint(
      WeatherHandler weatherHandler,
      Vec3 pos,
      Level level,
      @Nullable RadarBlockEntity radarBlockEntity,
      int advance,
      @Nullable Integer groundHeight,
      boolean doFireAffect
   ) {
      return samplePoint(weatherHandler, pos, level, radarBlockEntity, advance, groundHeight, doFireAffect, true);
   }

   public static Tuple<Float, Float> getFireTemperature(Level level, ChunkAccess chunkAccess, Vec3 pos, @Nullable Integer terrainHeight) {
      float fireIntensity = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);
      if (terrainHeight == null) {
         terrainHeight = level.getHeight(Types.MOTION_BLOCKING, (int)pos.x, (int)pos.z);
      }

      float below = Math.min((float)pos.y() - (float)terrainHeight.intValue(), 0.0F);
      float percFalloff = 1.0F - Mth.clamp((below + 4.0F) / -8.0F, 0.0F, 1.0F);
      fireIntensity *= percFalloff;
      return new Tuple(Math.min(Mth.square(fireIntensity) * 12.5F, 1000.0F), fireIntensity);
   }

   public static ThermodynamicEngine.AtmosphericDataPoint samplePoint(
      WeatherHandler weatherHandler,
      Vec3 pos,
      Level level,
      @Nullable RadarBlockEntity radarBlockEntity,
      int advance,
      @Nullable Integer groundHeight,
      boolean doFireAffect,
      boolean stormsAffect
   ) {
      BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
      ThermodynamicEngine.noise = WindEngine.simplexNoise;
      if (ThermodynamicEngine.noise == null) {
         return new ThermodynamicEngine.AtmosphericDataPoint(30.0F, 30.0F, 1013.0F, 30.0F);
      } else {
         float time = (float)(level.getDayTime() + (long)advance);
         float biomeTemp = 0.0F;
         float humidity = 0.0F;
         float tropAdd = 0.0F;
         int c = 0;
         boolean cached = false;
         if (cachedPos != null && cachedPos.equals(pos.multiply(1.0, 0.0, 1.0)) && Math.abs(time - cachedTime) < 20.0F) {
            biomeTemp = cachedBiomeTemp;
            humidity = cachedHumidity;
            cached = true;
         } else {
            for (int x = -1; x <= 1; x++) {
               for (int z = -1; z <= 1; z++) {
                  if (Mth.abs(x) != 1 || Mth.abs(z) != 1) {
                     c++;
                     BlockPos p = blockPos.offset(new Vec3i(x * 64, 0, z * 64));
                     Holder<Biome> biome;
                     if (radarBlockEntity != null && radarBlockEntity.init) {
                        biome = radarBlockEntity.getNearestBiome(p);
                     } else {
                        biome = level.getBiome(p);
                     }

                     String rn = biome.getRegisteredName().toLowerCase();
                     boolean ocean = rn.contains("ocean");
                     if (ocean) {
                        if (x == 0 && z == 0) {
                           humidity += 0.45F;
                        } else {
                           humidity += 0.3F;
                        }

                        if (tropAdd < 0.3F) {
                           tropAdd = 0.3F;
                        }
                     }

                     biomeTemp += ((Biome)biome.value()).getBaseTemperature();
                     humidity += Math.max(((Biome)biome.value()).getModifiedClimateSettings().downfall(), 0.0F);
                  }
               }
            }

            humidity /= (float)c;
            biomeTemp /= (float)c;
            cachedPos = pos.multiply(1.0, 0.0, 1.0);
            cachedBiomeTemp = biomeTemp;
            cachedHumidity = humidity;
            cachedTime = time;
         }

         humidity = Math.min(humidity, 1.0F);
         biomeTemp -= 0.175F;
         tropAdd -= Math.max(-(biomeTemp - 0.3F), 0.0F) * 1.75F;
         if (groundHeight == null) {
            groundHeight = level.getHeight(Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
         }

         int elevation = Math.max(level.getSeaLevel(), groundHeight);
         ChunkAccess chunkAccess = level.getChunk(blockPos);
         Holder<Biome> biomex = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
         float gBiomeTemp = ((Biome)biomex.value()).getBaseTemperature();
         float gHumidity = Math.max(((Biome)biomex.value()).getModifiedClimateSettings().downfall(), 0.0F);
         humidity = Mth.lerp(Mth.clamp((float)pos.y() / 16000.0F, 0.0F, 0.15F), humidity, gHumidity);
         biomeTemp = Mth.lerp(Mth.clamp((float)pos.y() / 16000.0F, 0.0F, 0.15F), biomeTemp, gBiomeTemp - 0.15F);
         float tropical = 0.0F;
         if (humidity > 0.8F) {
            tropical = humidity - 0.8F;
            tropical /= 0.2F;
            tropical = Mth.sqrt(tropical);
         }

         tropical = Mth.clamp(tropical + tropAdd, 0.0F, 1.0F);
         if (humidity > 0.4F) {
            humidity -= 0.4F;
            humidity /= 2.0F;
            humidity += 0.4F;
         }

         humidity = (float)Math.pow((double)humidity, 0.3F);
         int elevationSeaLevel = elevation - level.getSeaLevel();
         float aboveSeaLevel = (float)pos.y() - (float)level.getSeaLevel();
         float altitude = Math.max((float)pos.y() - (float)elevation, 0.0F);
         float daytime = (float)(level.getDayTime() + (long)advance) / 24000.0F;
         double x = ((double)daytime - 0.18) * Math.PI * 2.0;
         double timeFactor = Math.sin(x + Math.sin(x) / -2.0);
         float pblHeight;
         if (cached) {
            pblHeight = cachedPBLHeight;
         } else {
            pblHeight = FBM(
               pos.multiply((double)(1.0F / xzScale), 0.0, (double)(1.0F / xzScale)).add(0.0, (double)(time / timeScale), 0.0), 2, 2.0F, 0.5F, 1.0F
            );
         }

         cachedPBLHeight = pblHeight;
         pblHeight = (Mth.clamp(pblHeight + 1.0F, 0.0F, 2.0F) + 1.0F) * 500.0F;
         double timeFactorHeightAffected = Mth.lerp((double)Mth.clamp(altitude / pblHeight, 0.0F, 1.0F), timeFactor, 1.0);
         float sfcPressure = 1013.25F;
         float sfcTNoise;
         if (cached) {
            sfcTNoise = cachedSfcTNoise;
         } else {
            sfcTNoise = FBM(
               pos.multiply((double)(1.0F / xzScale), 0.0, (double)(1.0F / xzScale)).add(0.0, (double)(time / timeScale), 0.0), 3, 2.0F, 0.5F, 1.0F
            );
         }

         cachedSfcTNoise = sfcTNoise;
         sfcTNoise *= 5.0F;
         float sfcTemp;
         if (biomeTemp <= 0.0F) {
            sfcTemp = Mth.lerp((float)Math.pow((double)(-biomeTemp), 1.5), 0.0F, -60.0F + sfcTNoise);
         } else {
            sfcTemp = Mth.lerp((float)Math.pow((double)biomeTemp / 1.85, 0.5), 0.0F, 35.0F + sfcTNoise);
         }

         sfcTemp += humidity * 3.0F;
         float sfcTempTimeMod = (float)timeFactorHeightAffected * 5.0F * Math.max(1.0F - humidity, 0.05F);
         sfcTempTimeMod += 5.0F;
         sfcTemp += sfcTempTimeMod;
         float tNoise = sfcTNoise / 5.0F;
         sfcTemp += tNoise * 2.0F;
         sfcTemp -= (float)elevationSeaLevel / 20.0F;
         if (ServerConfig.doSeasons) {
            float seasonEffect = SeasonHandler.getSeasonEffectSine(level, 0.0F);
            if (seasonEffect > 0.0F) {
               seasonEffect /= 4.0F;
            }

            if (humidity > 0.75F) {
               seasonEffect *= 1.0F - (humidity - 0.75F) * 4.0F;
            }

            if (tropical > 0.0F) {
               seasonEffect *= 1.0F - Math.min(tropical, 1.0F);
               seasonEffect += tropical * 0.5F;
            }

            sfcTemp += seasonEffect * 34.0F;
         }

         float fireIntensity = 0.0F;
         if (doFireAffect) {
            Tuple<Float, Float> rtrn = getFireTemperature(level, chunkAccess, pos, groundHeight);
            sfcTemp += rtrn.getA();
            fireIntensity = (Float)rtrn.getB();
         }

         float pNoise;
         if (cached) {
            pNoise = cachedPNoise;
         } else {
            pNoise = FBM(pos.multiply((double)(1.0F / -xzScale), 0.0, (double)(1.0F / -xzScale)).add(0.0, (double)(time / timeScale), 0.0), 3, 2.0F, 0.5F, 1.0F);
         }

         cachedPNoise = pNoise;
         sfcPressure += pNoise * 7.0F;
         float stormCooling = 0.0F;
         if (stormsAffect) {
            for (Storm storm : weatherHandler.getStorms()) {
               stormCooling += storm.getTemperatureOffset(pos);
            }
         }

         stormCooling *= 1.0F - Mth.clamp((float)advance / 12000.0F, 0.0F, 1.0F);
         sfcTemp -= stormCooling * Mth.clamp(1.0F - altitude / 3000.0F, 0.0F, 1.0F);
         if (tropical > 0.0F && sfcTemp < 10.0F) {
            float under = 10.0F - sfcTemp;
            sfcTemp += under * Math.min(tropical, 0.9F);
         }

         float var99;
         if (humidity > 0.5F) {
            var99 = (float)Math.pow((double)(2.0F * (humidity - 0.5F)), 0.25) + 0.5F;
         } else {
            var99 = (float)Math.pow((double)(2.0F * humidity), 4.0) * 0.5F;
         }

         float dewP = Mth.clamp(
            (float)Mth.lerp(
               0.7F, (ThermodynamicEngine.noise.getValue(pos.z / 2200.0, (double)(time / 9000.0F) + pos.y / 100.0, pos.x / 300.0) + 1.0) / 2.0, (double)var99
            ),
            0.2F,
            1.0F
         );
         float sfcDew = Math.min(sfcTemp - sfcTempTimeMod, 32.0F) - Mth.clamp((1.0F - dewP) * (sfcTemp - sfcTempTimeMod), 0.0F, 15.0F);
         if (sfcDew > 0.0F) {
            sfcDew *= humidity * 0.9F + 0.1F;
         }

         sfcDew -= Mth.lerp(1.0F - var99, 0.0F, 5.0F);
         sfcDew -= Mth.square(fireIntensity) * 3.0F;
         if (sfcDew < -10.0F) {
            sfcDew = -10.0F;
         }

         if (ServerConfig.doSeasons) {
            float seasonEffectx = SeasonHandler.getSeasonEffectSine(level, 0.0F);
            if (seasonEffectx > 0.0F) {
               seasonEffectx /= 7.0F;
            }

            if (seasonEffectx < 0.0F && humidity > 0.75F) {
               seasonEffectx *= 1.0F - (humidity - 0.75F) * 4.0F;
            }

            sfcDew += seasonEffectx * 8.0F;
            float humidEffect = SeasonHandler.getSeasonEffectSine(weatherHandler.getWorld(), 3.5F) + 1.0F;
            if (humidity > 0.75F) {
               humidEffect *= 1.0F - (humidity - 0.75F) * 4.0F;
            }

            sfcDew -= humidEffect * 5.0F;
         }

         sfcDew = Math.min(sfcDew, sfcTemp);
         sfcPressure = getPressureAtHeight((float)elevationSeaLevel, sfcTemp, sfcPressure);
         float lapseRate = 5.5F;
         float lrNoise = tNoise;
         if (tNoise > 0.0F) {
            lrNoise = (float)Math.pow((double)tNoise, 1.25);
            lrNoise *= 2.0F;
         }

         lapseRate += lrNoise;
         lapseRate *= 0.4F + (1.0F - humidity);
         float dewRatio = Mth.lerp((tNoise + 1.0F) / 2.0F, Mth.lerp(humidity, 0.4F, 0.1F), Mth.lerp(humidity, 0.65F, 0.3F));
         float var101 = sfcTemp - lapseRate * (altitude / 1000.0F);
         float var104 = sfcDew - lapseRate * (altitude / 1000.0F) * dewRatio;
         float noise;
         if (cached) {
            noise = cachedNoise;
         } else {
            noise = FBM(pos.multiply((double)(1.0F / xzScale), 0.0, (double)(1.0F / -xzScale)).add(0.0, (double)(time / timeScale), 0.0), 2, 2.0F, 0.5F, 1.0F);
         }

         cachedNoise = noise;
         float bumpH = (float)elevation + Mth.clamp(noise + 0.5F, 0.5F, 1.5F) * 1250.0F;
         noise = FBM(pos.multiply((double)(1.0F / -xzScale), 0.0, (double)(1.0F / xzScale)).add(0.0, (double)(time / timeScale), 0.0), 2, 2.0F, 0.5F, 1.0F);
         float bumpStrength = Mth.clamp(noise + 0.5F, 0.0F, 1.5F) * 5.5F * Mth.clamp(1.0F - humidity, 0.0F, 1.0F);
         bumpStrength -= 4.0F * humidity;
         if (altitude > bumpH) {
            float i = Mth.clamp((altitude - bumpH) / 150.0F, 0.0F, 1.0F);
            var101 += Mth.lerp(i, 0.0F, bumpStrength);
            var104 -= Mth.lerp(i, 0.0F, bumpStrength);
         }

         float a = Mth.clamp(altitude, 0.0F, 1000.0F);
         var101 -= lapseRate * (a / 1000.0F) * 0.25F;
         var104 -= lapseRate * (a / 1000.0F) * dewRatio * 0.25F;
         noise = FBM(pos.multiply((double)(1.0F / xzScale), 0.0, (double)(1.0F / xzScale)).add(0.0, (double)(time / timeScale), 0.0), 2, 2.0F, 0.5F, 1.0F);
         float inversionHeight = (float)elevationSeaLevel + Mth.lerp(Mth.clamp(noise, 0.0F, 1.0F), 12000.0F, 16000.0F);
         if (altitude > inversionHeight) {
            float dif = altitude - inversionHeight;
            float i = Mth.clamp(dif / 1500.0F, 0.0F, 1.0F);
            var101 += Mth.lerp(i, 0.0F, lapseRate * (dif / 1000.0F));
            var104 += Mth.lerp(i, 0.0F, lapseRate * (dif / 1000.0F) * dewRatio);
         }

         float offset = FBM(
            pos.multiply((double)(1.0F / xzScale), (double)(1.0F / yScale), (double)(1.0F / xzScale)).add(0.0, (double)(time / -timeScale), 0.0),
            4,
            2.0F,
            0.5F,
            1.0F
         );
         offset *= 1.5F;
         var101 += offset;
         var104 -= offset * 1.5F;
         float px = getPressureAtHeight(aboveSeaLevel, var101, (float)elevationSeaLevel, sfcPressure);
         float dewMin = FBM(
            pos.multiply((double)(1.0F / xzScale), (double)(1.0F / yScale), (double)(1.0F / xzScale)).add(0.0, (double)(time / -timeScale), 0.0),
            4,
            2.0F,
            0.5F,
            1.0F
         );
         dewMin = Mth.clamp(dewMin + 1.0F, 0.0F, 2.0F) * 2.0F;
         dewMin += (float)Math.pow(pos.y / 16000.0, 2.0) * 40.0F * (1.0F - humidity);
         float td = var101 - dewMin;
         if (var104 > td) {
            float dif = var104 - td;
            var104 -= dif * Mth.clamp(dif / 4.0F, 0.0F, 1.0F);
         }

         var104 = Math.min(var101, var104);
         return new ThermodynamicEngine.AtmosphericDataPoint(var101, var104, px, calcVTemp(var101, var104, sfcPressure));
      }
   }

   public static float getPressureAtHeight(float altitude, float temp, float sfcPressure) {
      return getPressureAtHeight(altitude, temp, 0.0F, sfcPressure);
   }

   public static float getPressureAtHeight(float altitude, float temp, float refAltitude, float refPressure) {
      return refPressure * (float)Math.exp((double)(-(0.2841926F * (altitude - refAltitude) / (8.31432F * celsiusToKelvin(temp)))));
   }

   public static float kelvinToCelsius(float k) {
      return k - 273.15F;
   }

   public static float celsiusToKelvin(float c) {
      return c + 273.15F;
   }

   public static float calcVTemp(float t, float dp, float p) {
      return kelvinToCelsius(celsiusToKelvin(t) / (1.0F - 0.379F * (6.11F * (float)Math.pow(10.0, (double)(7.5F * dp / (237.3F + dp))) / p)));
   }

   public static ThermodynamicEngine.AtmosphericDataPoint deserializeDataPoint(CompoundTag data) {
      return new ThermodynamicEngine.AtmosphericDataPoint(
         data.getFloat("temperature"), data.getFloat("dewpoint"), data.getFloat("pressure"), data.getFloat("virtualTemperature")
      );
   }

   public static double getHumidity(float temp, float dew) {
      return Mth.clamp(
         Math.pow(Math.E, 17.625 * (double)dew / (243.04 + (double)dew)) / Math.pow(Math.E, 17.625 * (double)temp / (243.04 + (double)temp)), 0.0, 1.0
      );
   }

   public static record AtmosphericDataPoint(float temperature, float dewpoint, float pressure, float virtualTemperature) {
      @Override
      public String toString() {
         return String.format(
            "Temperature: %s, DewPoint: %s, Pressure: %s, Virtual Temperature: %s",
            Math.floor((double)(this.temperature * 10.0F)) / 10.0,
            Math.floor((double)(this.dewpoint * 10.0F)) / 10.0,
            Math.floor((double)(this.pressure * 10.0F)) / 10.0,
            Math.floor((double)(this.virtualTemperature * 10.0F)) / 10.0
         );
      }

      public CompoundTag serializeNBT() {
         CompoundTag data = new CompoundTag();
         data.putFloat("temperature", this.temperature);
         data.putFloat("dewpoint", this.dewpoint);
         data.putFloat("pressure", this.pressure);
         data.putFloat("virtualTemperature", this.virtualTemperature);
         return data;
      }
   }

   public static enum Precipitation {
      RAIN,
      FREEZING_RAIN,
      SLEET,
      SNOW,
      WINTRY_MIX,
      HAIL;

      private Precipitation() {
      }
   }
}
