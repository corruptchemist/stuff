package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.Config;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class Squall extends Storm {
   public Squall(StormSpawnProperties properties) {
      super(properties);
      this.growthSpeed = PMWeather.RANDOM.nextInt(40, 70);
   }

   @Override
   public void recalc(@Nullable Float risk) {
      super.recalc(risk);
      this.growthSpeed = PMWeather.RANDOM.nextInt(40, 70);
   }

   @Override
   public void calcRain() {
      if (this.position == null) {
         PMWeather.LOGGER.warn("Storm position is null! Should not happen! Check calcRain calls.");
      }

      ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(this.weatherHandler, this.position, this.level, null, 0);
      float roughHumidity = (float)ThermodynamicEngine.getHumidity(dataPoint.temperature(), dataPoint.dewpoint());
      roughHumidity += Math.max((dataPoint.dewpoint() - 15.0F) / 40.0F, 0.0F);
      roughHumidity += 0.25F;
      this.rainIntensity = Mth.sqrt(Mth.clamp((roughHumidity - 0.5F) * 2.0F, 0.25F, 1.0F));
   }

   @Override
   public void aimAtPlayer() {
   }

   @Override
   public void iterateVorticies() {
   }

   @Override
   public void doLightning() {
      if (!this.visualOnly) {
         float lightningChance = 0.0F;
         if (this.stage == 1) {
            lightningChance = (float)this.energy / 100.0F;
         } else if (this.stage == 2) {
            lightningChance = 1.0F + (float)this.energy / 100.0F;
         } else if (this.stage > 2) {
            lightningChance = 2.0F;
         }

         lightningChance = Math.min(lightningChance * 0.035F, 0.1F);
         lightningChance *= 10.0F;
         if (PMWeather.RANDOM.nextFloat() <= lightningChance * 0.5F) {
            Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
            Vec2 right = new Vec2(stormVel.y, -stormVel.x).normalized();
            Vec2 fwd = stormVel.normalized();
            right = Util.mulVec2(right, PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) * 14.0F);
            fwd = Util.mulVec2(fwd, PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 3.0F);
            Vec3 lPos = this.position.add(new Vec3((double)right.x, 0.0, (double)right.y)).add(new Vec3((double)fwd.x, 0.0, (double)fwd.y));
            int height = 64;
            if (this.level.isLoaded(BlockPos.containing(lPos))) {
               height = this.level.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(lPos)).getY();
            }

            ((WeatherHandlerServer)this.weatherHandler).syncLightningNew(new Vec3(lPos.x, (double)height, lPos.z));
         }
      }
   }

   @Override
   public void doGrowth() {
      int gs = this.growthSpeed / 2;
      if (this.tickCount % gs == 0) {
         if (this.isDying) {
            if (this.ticksSinceDying > 2400) {
               this.energy--;
               if (this.energy <= 0) {
                  this.energy = 100;
                  this.stage--;
                  if (this.stage < 0) {
                     this.energy = 0;
                     this.stage = 0;
                     if (this.coldEnergy > 0) {
                        this.coldEnergy--;
                     } else {
                        this.dead = true;
                     }
                  }
               }
            }
         } else {
            int targetProgress = this.maxProgress;
            if (this.maxStage > this.stage) {
               targetProgress = 100;
            }

            if (this.energy < targetProgress) {
               this.energy++;
               this.coldEnergy = Mth.clamp((long)(this.coldEnergy + 1), 0, this.maxColdEnergy);
            }

            if (this.stage >= this.maxStage && this.energy >= targetProgress) {
               this.isDying = true;
               this.growthSpeed = PMWeather.RANDOM.nextInt(40, 80);
               if (PMWeather.RANDOM.nextInt(2) == 0 || this.maxWidth > 200) {
                  this.maxWidth = Math.min(this.maxWidth, PMWeather.RANDOM.nextInt(5, 35));
               }
            }

            if (this.energy >= 100) {
               this.energy = 0;
               if (this.stage < this.maxStage) {
                  this.stage++;
               }
            }
         }

         if (Config.DEBUG) {
            PMWeather.LOGGER.debug("Stage: {}, Energy: {}, Windspeed: {}, Width: {}", new Object[]{this.stage, this.energy, this.windspeed, this.width});
         }
      }
   }

   @Override
   public void move() {
      Vec3 vel = this.velocity.multiply(0.05F, 0.05F, 0.05F).multiply(2.0, 0.0, 2.0);
      this.position = this.position.add(vel);
   }

   @Override
   public float getRadarRenderRange() {
      return (float)ServerConfig.stormSize * 32.0F;
   }

   @Override
   public float getRadarReflectivityReturn(RadarBlockEntity radarBlockEntity, Vec3 worldPos) {
      float localDBZ = 0.0F;
      double stormSize = ServerConfig.stormSize * 2.0;
      double scale = stormSize / 1200.0;
      double shapeNoise = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.x / (750.0 * scale), worldPos.z / (750.0 * scale));
      double shapeNoise2 = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.z / (750.0 * scale), worldPos.x / (750.0 * scale));
      shapeNoise *= 0.5;
      shapeNoise2 *= 0.5;
      shapeNoise += 0.5;
      shapeNoise2 += 0.5;
      float smoothStage = (float)this.stage + (float)this.energy / 100.0F;
      double rawDist = worldPos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      Vec2 v2fWorldPos = new Vec2((float)worldPos.x, (float)worldPos.z);
      Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
      Vec2 v2fStormPos = new Vec2((float)this.position.x, (float)this.position.z);
      Vec2 right = new Vec2(stormVel.y, -stormVel.x).normalized();
      Vec2 fwd = stormVel.normalized();
      Vec2 le = Util.mulVec2(right, -3000.0F * (float)scale);
      Vec2 ri = Util.mulVec2(right, 3000.0F * (float)scale);
      Vec2 off = Util.mulVec2(fwd, -((float)Math.pow(Mth.clamp(rawDist / (3000.0 * scale), 0.0, 1.0), 2.0)) * 900.0F * (float)scale);
      le = le.add(off);
      ri = ri.add(off);
      le = le.add(v2fStormPos);
      ri = ri.add(v2fStormPos);
      float dist = Util.minimumDistance(le, ri, v2fWorldPos);

      float intensity = switch (this.stage) {
         case 1 -> 0.1F + (float)this.energy / 100.0F * 0.7F;
         case 2 -> 0.8F + (float)this.energy / 100.0F * 0.4F;
         case 3 -> 1.2F + (float)this.energy / 100.0F;
         default -> (float)this.energy / 100.0F * 0.1F;
      };
      if (intensity > 0.8F) {
         intensity = 0.8F + (intensity - 0.8F) / 1.5F;
      }

      Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
      Vec2 facing = v2fWorldPos.add(nearPoint.negated());
      float behind = -facing.dot(fwd);
      behind += (float)shapeNoise * 600.0F * (float)scale * 0.2F;
      float sze = 600.0F * (float)scale * 1.5F * 3.0F;
      behind += (float)stormSize / 2.0F;
      if (behind > 0.0F) {
         sze *= Mth.lerp(Mth.clamp(smoothStage - 1.0F, 0.0F, 1.0F), 1.0F, 4.0F);
         float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
         float start = 0.06F;
         if (p <= start) {
            p /= start;
            localDBZ += (float)Math.pow((double)p, 2.0);
         } else {
            p = 1.0F - (p - start) / (1.0F - start);
            localDBZ += (float)Math.pow((double)p, 4.0);
         }
      }

      localDBZ *= Mth.sqrt(1.0F - Mth.clamp(dist / sze, 0.0F, 1.0F));
      if (smoothStage > 3.0F) {
         float p = Mth.clamp((smoothStage - 3.0F) / 2.0F, 0.0F, 0.5F);
         localDBZ *= 0.8F + (float)shapeNoise2 * 0.4F * (1.0F - p);
         localDBZ *= 0.8F + (float)shapeNoise * 0.4F * (1.0F - p);
         localDBZ *= 1.0F + p * 0.25F;
      } else {
         localDBZ *= 0.8F + (float)shapeNoise2 * 0.4F;
         localDBZ *= 0.8F + (float)shapeNoise * 0.4F;
      }

      return localDBZ * Mth.sqrt(intensity);
   }

   @Override
   public float getPrecipitation(Vec3 pos) {
      float precip = 0.0F;
      double dist = pos.distanceTo(new Vec3(this.position.x, pos.y, this.position.z));
      float smoothStage = (float)this.stage + (float)this.energy / 100.0F;
      if (this.stage == 3) {
         smoothStage = 3.0F;
      }

      Vec2 v2fWorldPos = new Vec2((float)pos.x, (float)pos.z);
      Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
      Vec2 v2fStormPos = new Vec2((float)this.position.x, (float)this.position.z);
      Vec2 right = new Vec2(stormVel.y, -stormVel.x).normalized();
      Vec2 fwd = stormVel.normalized();
      Vec2 le = Util.mulVec2(right, -((float)ServerConfig.stormSize) * 5.0F);
      Vec2 ri = Util.mulVec2(right, (float)ServerConfig.stormSize * 5.0F);
      Vec2 off = Util.mulVec2(
         fwd, -((float)Math.pow(Mth.clamp(dist / (double)((float)ServerConfig.stormSize * 5.0F), 0.0, 1.0), 2.0)) * (float)ServerConfig.stormSize * 1.5F
      );
      le = le.add(off);
      ri = ri.add(off);
      le = le.add(v2fStormPos);
      ri = ri.add(v2fStormPos);
      float d = Util.minimumDistance(le, ri, v2fWorldPos);
      if ((double)d > ServerConfig.stormSize * 16.0) {
         return 0.0F;
      } else {
         Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
         Vec2 facing = v2fWorldPos.add(nearPoint.negated());
         float behind = -facing.dot(fwd);
         float sze = (float)ServerConfig.stormSize * 1.5F;
         sze *= Mth.lerp(Mth.clamp(smoothStage - 1.0F, 0.0F, 1.0F), 4.0F, 12.0F);
         behind += (float)ServerConfig.stormSize / 2.0F;
         if (behind > 0.0F) {
            float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
            float start = 0.06F;
            if (p <= start) {
               p /= start;
            } else {
               p = 1.0F - (p - start) / (1.0F - start);
            }

            precip = (float)Math.pow((double)Mth.clamp(p, 0.0F, 1.0F), 3.0);
         }

         if (this.stage <= 0) {
            precip = 0.0F;
         } else if (this.stage == 1) {
            precip *= (float)this.energy / 100.0F;
         }

         return precip * Mth.sqrt(1.0F - Mth.clamp(d / sze, 0.0F, 1.0F)) * this.rainIntensity;
      }
   }

   @Override
   public float getHail(Vec3 pos) {
      float hail = this.getPrecipitation(pos);
      hail = Math.max(hail - 0.35F, 0.0F);
      hail *= 1.4F;
      if (this.stage == 2) {
         hail *= (float)this.energy / 100.0F;
      }

      if (this.stage < 2) {
         hail *= 0.0F;
      }

      return hail;
   }

   @Override
   public Vec3 getBaseWind(Vec3 pos) {
      Vec3 wind = Vec3.ZERO;
      float timeScale = 20000.0F;
      double distance = pos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      Vec2 v2fWorldPos = new Vec2((float)pos.x, (float)pos.z);
      Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
      Vec2 v2fStormPos = new Vec2((float)this.position.x, (float)this.position.z);
      Vec2 right = new Vec2(stormVel.y, -stormVel.x).normalized();
      Vec2 fwd = stormVel.normalized();
      Vec2 le = Util.mulVec2(right, -((float)ServerConfig.stormSize) * 5.0F);
      Vec2 ri = Util.mulVec2(right, (float)ServerConfig.stormSize * 5.0F);
      Vec2 off = Util.mulVec2(
         fwd, -((float)Math.pow(Mth.clamp(distance / (double)((float)ServerConfig.stormSize * 5.0F), 0.0, 1.0), 2.0)) * (float)ServerConfig.stormSize * 1.5F
      );
      le = le.add(off);
      ri = ri.add(off);
      le = le.add(v2fStormPos);
      ri = ri.add(v2fStormPos);
      float d = Util.minimumDistance(le, ri, v2fWorldPos);
      Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
      Vec2 facing = v2fWorldPos.add(nearPoint.negated());
      float behind = -facing.dot(fwd);
      behind += (float)WindEngine.FBM(
            new Vec3(pos.x / (ServerConfig.stormSize * 2.0), pos.z / (ServerConfig.stormSize * 2.0), (double)((float)this.level.getGameTime() / timeScale)),
            5,
            2.0F,
            0.2F,
            1.0F
         )
         * (float)ServerConfig.stormSize
         * 0.25F;
      float perc = 0.0F;
      float sze = (float)ServerConfig.stormSize * 4.0F;
      behind += (float)ServerConfig.stormSize;
      if (behind > 0.0F) {
         float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
         float start = 0.06F;
         if (this.stage >= 3) {
            start = Mth.lerp((float)this.energy / 100.0F, start, start * 2.5F);
         }

         if (p <= start) {
            p /= start;
         } else {
            p = 1.0F - (p - start) / (1.0F - start);
            if (this.stage >= 3) {
               p = (float)Math.pow((double)p, (double)Mth.lerp((float)this.energy / 100.0F, 1.0F, 0.75F));
            }
         }

         perc = Mth.clamp(p, 0.0F, 1.0F);
      }

      if (this.stage < 1) {
         perc *= Mth.square((float)this.energy / 200.0F);
      } else if (this.stage == 1) {
         perc *= (float)this.energy / 100.0F + 0.5F;
      } else if (this.stage == 2) {
         perc *= (float)this.energy / 125.0F + 1.5F;
      } else {
         perc *= (float)this.energy / 100.0F + 2.3F;
      }

      float gustNoise = (float)this.FBM(
         new Vec3(pos.z / (ServerConfig.stormSize * 2.0), pos.x / (ServerConfig.stormSize * 2.0), (double)((float)this.level.getGameTime() / timeScale)),
         7,
         2.0F,
         0.4F,
         1.0F
      );
      if (this.stage >= 3) {
         float px = (float)this.energy / 100.0F;
         gustNoise *= 1.0F - px;
         perc *= 1.0F + px * 0.3F;
      }

      perc *= Mth.lerp(Mth.clamp(behind / ((float)ServerConfig.stormSize * 3.0F), 0.0F, 1.0F), (float)Math.pow((double)(0.8F + gustNoise * 0.5F), 1.5), 0.5F);
      perc *= Mth.sqrt(1.0F - Mth.clamp(d / sze, 0.0F, 1.0F));
      return wind.add(
         this.velocity
            .multiply((double)(perc * 15.0F) * ServerConfig.squallStrengthMultiplier, 0.0, (double)(perc * 15.0F) * ServerConfig.squallStrengthMultiplier)
      );
   }

   @Override
   public float getTemperatureOffset(Vec3 pos) {
      float offset = 0.0F;
      double distance = pos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      Vec2 v2fWorldPos = new Vec2((float)pos.x, (float)pos.z);
      Vec2 stormVel = new Vec2((float)this.velocity.x, (float)this.velocity.z);
      Vec2 v2fStormPos = new Vec2((float)this.position.x, (float)this.position.z);
      Vec2 right = new Vec2(stormVel.y, -stormVel.x).normalized();
      Vec2 fwd = stormVel.normalized();
      Vec2 le = Util.mulVec2(right, -((float)ServerConfig.stormSize) * 5.0F);
      Vec2 ri = Util.mulVec2(right, (float)ServerConfig.stormSize * 5.0F);
      Vec2 off = Util.mulVec2(
         fwd, -((float)Math.pow(Mth.clamp(distance / (double)((float)ServerConfig.stormSize * 5.0F), 0.0, 1.0), 2.0)) * (float)ServerConfig.stormSize * 1.5F
      );
      le = le.add(off);
      ri = ri.add(off);
      le = le.add(v2fStormPos);
      ri = ri.add(v2fStormPos);
      Vec2 nearPoint = Util.nearestPoint(le, ri, v2fWorldPos);
      Vec2 facing = v2fWorldPos.add(nearPoint.negated());
      float behind = -facing.dot(fwd);
      behind += ThermodynamicEngine.FBM(
            new Vec3(pos.x / (ServerConfig.stormSize * 2.0), pos.z / (ServerConfig.stormSize * 2.0), (double)((float)this.level.getGameTime() / 20000.0F)),
            5,
            2.0F,
            0.2F,
            1.0F
         )
         * (float)ServerConfig.stormSize
         * 0.25F;
      behind += (float)ServerConfig.stormSize;
      float sze = (float)ServerConfig.stormSize * 12.0F;
      if (behind > 0.0F) {
         float p = Mth.clamp(Math.abs(behind) / sze, 0.0F, 1.0F);
         float start = 0.02F;
         if (p <= start) {
            p /= start;
         } else {
            p = 1.0F - (p - start) / (1.0F - start);
         }

         offset = Math.max(offset, Mth.clamp(p, 0.0F, 1.0F) * 15.0F * (float)Math.pow((double)((float)this.coldEnergy / (float)this.maxColdEnergy), 0.75));
      }

      return offset;
   }
}
