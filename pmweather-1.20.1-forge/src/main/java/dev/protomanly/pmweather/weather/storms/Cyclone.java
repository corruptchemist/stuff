package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.render.RadarRenderer;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.sound.MovingSoundStreamingSource;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.Vorticy;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class Cyclone extends Storm {
   public Cyclone(StormSpawnProperties properties) {
      super(properties);
   }

   @Override
   public void calcRain() {
      this.rainIntensity = 1.0F;
   }

   @Override
   public void doLightning() {
   }

   @Override
   public void doGrowth() {
      int gs = this.growthSpeed / 2;
      if (this.tickCount % gs == 0 && !this.level.isClientSide()) {
         this.stage = 0;
         if (this.windspeed >= 15) {
            this.stage = 1;
         }

         if (this.windspeed >= 25) {
            this.stage = 2;
         }

         if (this.windspeed >= 40) {
            this.stage = 3;
         }

         Float sst = ThermodynamicEngine.GetSST(this.weatherHandler, this.position, this.level, null, 0);
         if (sst != null && this.tickCount <= 48000) {
            if (sst > 32.0F) {
               sst = 32.0F;
            }

            float v = 24.0F;
            if (this.cycloneWindspeed > 60.0F) {
               v += (this.cycloneWindspeed - 60.0F) / 18.5F;
            }

            float growth = (sst - v) / 4.5F;
            if ((float)this.windspeed > 165.0F) {
               growth -= ((float)this.windspeed - 165.0F) / 15.0F;
            }

            if (growth < 0.0F) {
               growth = Math.max(growth, -1.5F);
            } else {
               growth *= 1.25F;
               growth = Math.min(growth, 2.0F);
               if (this.cycloneWindspeed < 45.0F) {
                  growth /= 2.0F;
               }
            }

            this.cycloneWindspeed += growth;
         } else {
            float death = 1.0F;
            death += Math.max((this.cycloneWindspeed - 75.0F) / 100.0F, 0.0F);
            this.cycloneWindspeed -= death * 0.25F;
         }

         this.windspeed = Math.round(this.cycloneWindspeed);
         if (this.windspeed < -5) {
            this.dead = true;
         }
      }
   }

   @Override
   public void doWidth() {
      this.width = (float)this.maxWidth;
   }

   @Override
   public void iterateVorticies() {
      super.iterateVorticies();
      float vorticySpawnChance = 0.05F;
      if (this.isDying) {
         vorticySpawnChance = 0.25F;
      }

      vorticySpawnChance += Mth.clamp((float)Math.pow((double)(((float)this.windspeed - 100.0F) / 200.0F), 2.0), 0.0F, 0.5F);
      if ((float)this.windspeed >= 39.0F) {
         vorticySpawnChance *= 2.0F;
         if (!this.level.isClientSide && PMWeather.RANDOM.nextFloat() < vorticySpawnChance * 0.05F && this.vorticies.size() < 10) {
            Vorticy vorticy = new Vorticy(
               this,
               (float)Math.pow((double)PMWeather.RANDOM.nextFloat(), 0.75) * 0.1F,
               PMWeather.RANDOM.nextFloat() * 0.15F + 0.05F,
               0.075F,
               PMWeather.RANDOM.nextInt(900, 3000)
            );
            this.vorticies.add(vorticy);
         }
      }
   }

   @Override
   public void move() {
      Vec3 vel = this.velocity.multiply(0.05F, 0.05F, 0.05F).multiply(2.0, 0.0, 2.0);
      if (!this.aimedAtPlayer) {
         vel = vel.add(new Vec3(0.0, 0.0, -3.0).multiply((double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion)));
      }

      this.position = this.position.add(vel);
      if (!this.aimedAtPlayer) {
         this.velocity = this.velocity.multiply(0.985F, 0.985F, 0.985F);
         Vec3 baseWind = WindEngine.getWind(
            new Vec3(this.position.x, (double)(this.level.getMaxBuildHeight() + 1), this.position.z), this.level, true, true, false, true
         );
         float factor = 0.05F;
         Vec3 velAdd = new Vec3(baseWind.x, 0.0, baseWind.z).multiply((double)factor, 0.0, (double)factor);
         this.velocity = this.velocity.add(velAdd.multiply(0.05F, 0.05F, 0.05F));
      }
   }

   @OnlyIn(Dist.CLIENT)
   @Override
   public void tickClient() {
      super.tickClient();
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         this.smoothWidth = this.width;
         this.smoothWindspeed = Mth.lerp(0.1F, this.smoothWindspeed, (float)this.windspeed);
         if ((this.eyewallWind == null || this.eyewallWind.isStopped()) && !this.dead) {
            this.eyewallWind = new MovingSoundStreamingSource(
               this, (SoundEvent)ModSounds.EYEWALL_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, (float)this.maxWidth, true, 2
            );
            Minecraft.getInstance().getSoundManager().play(this.eyewallWind);
         }
      }
   }

   @Override
   public float getRadarRenderRange() {
      return (float)this.maxWidth * 12.0F;
   }

   @Override
   public float getRadarReflectivityReturn(RadarBlockEntity radarBlockEntity, Vec3 worldPos) {
      float localDBZ = 0.0F;
      float scale = (float)this.maxWidth / 3000.0F;
      scale *= 0.5F;
      double shapeNoise = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.x / (double)(750.0F * scale), worldPos.z / (double)(750.0F * scale));
      float fineShapeNoise = RadarRenderer.FBM(
         radarBlockEntity.noise,
         new Vec3((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.x / (double)(500.0F * scale), worldPos.z / (double)(500.0F * scale)),
         10,
         2.0F,
         0.75F,
         1.0F
      );
      double shapeNoise2 = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.z / (double)(750.0F * scale), worldPos.x / (double)(750.0F * scale));
      double shapeNoise3 = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 16000.0F), worldPos.x / (double)(4000.0F * scale), worldPos.z / (double)(4000.0F * scale));
      shapeNoise *= 0.5;
      shapeNoise2 *= 0.5;
      shapeNoise += 0.5;
      shapeNoise2 += 0.5;
      Vec3 wPos = worldPos;
      Vec3 cPos = this.position.multiply(1.0, 0.0, 1.0);

      for (Vorticy vorticy : this.vorticies) {
         Vec3 vPos = vorticy.getPosition();
         float width = vorticy.getWidth() * 0.35F;
         double d = wPos.multiply(1.0, 0.0, 1.0).distanceTo(vPos.multiply(1.0, 0.0, 1.0));
         if (d < (double)width) {
            double angle = Math.pow(1.0 - Math.clamp(d / (double)width, 0.0, 1.0), 3.75);
            angle *= (float) (Math.PI / 10);
            angle *= (double)Math.min(vorticy.windspeedMult * (float)this.windspeed, 6.0F);
            wPos = Util.rotatePoint(wPos, vPos, angle);
         }
      }

      double rawDist = wPos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      rawDist *= 1.0 + shapeNoise3 * 0.2F;
      float intensity = (float)Math.pow((double)Math.clamp((float)this.windspeed / 65.0F, 0.0F, 1.0F), 0.25);
      Vec3 relPos = cPos.subtract(wPos).multiply((double)scale, 0.0, (double)scale);
      double d = (double)((float)this.maxWidth / (3.0F + (float)this.windspeed / 12.0F));
      double d2 = (double)((float)this.maxWidth / (1.15F + (float)this.windspeed / 12.0F));
      double dE = (double)((float)this.maxWidth * 0.65F / (1.75F + (float)this.windspeed / 12.0F));
      double fac = 1.0 + Math.max((rawDist - (double)((float)this.maxWidth * 0.2F)) / (double)this.maxWidth, 0.0) * 2.0;
      d *= fac;
      d2 *= fac;
      double angle = Math.atan2(relPos.z, relPos.x) - rawDist / d;
      double angle2 = Math.atan2(relPos.z, relPos.x) - rawDist / d2;
      double angleE = Math.atan2(relPos.z, relPos.x) - rawDist / dE;
      float weak = 0.0F;
      float strong = 0.0F;
      float intense = 0.0F;
      float staticBands = (float)Math.sin(angle - (Math.PI / 2));
      staticBands *= (float)Math.pow(Math.clamp(rawDist / (double)((float)this.maxWidth * 0.25F), 0.0, 1.0), 0.1F);
      staticBands *= 1.25F * (float)Math.pow((double)intensity, 0.75);
      if (staticBands < 0.0F) {
         weak += Math.abs(staticBands);
      } else {
         weak += Math.abs(staticBands) * (float)Math.pow(1.0 - Math.clamp(rawDist / (double)((float)this.maxWidth * 0.65F), 0.0, 1.0), 0.5);
         weak *= Math.clamp(((float)this.windspeed - 70.0F) / 40.0F, 0.0F, 1.0F);
      }

      float rotatingBands = (float)Math.sin((angle2 + Math.toRadians((double)((float)this.tickCount / 8.0F))) * 6.0);
      rotatingBands *= (float)Math.pow(Math.clamp(rawDist / (double)((float)this.maxWidth * 0.25F), 0.0, 1.0), 0.1F);
      rotatingBands *= 1.25F * (float)Math.pow((double)intensity, 0.75);
      strong += Mth.lerp(0.45F, Math.abs(rotatingBands) * 0.3F + 0.7F, weak);
      intense += Mth.lerp(0.3F, Math.abs(rotatingBands) * 0.2F + 0.8F, weak);
      weak = (Math.abs(rotatingBands) * 0.3F + 0.6F) * weak;
      localDBZ += Mth.lerp(
         Math.clamp(((float)this.windspeed - 120.0F) / 60.0F, 0.0F, 1.0F),
         Mth.lerp(Math.clamp(((float)this.windspeed - 40.0F) / 90.0F, 0.0F, 1.0F), weak, strong),
         intense
      );
      float eye = (float)Math.sin((angleE + Math.toRadians((double)((float)this.tickCount / 4.0F))) * 2.0);
      float efc = Mth.lerp(Math.clamp(((float)this.windspeed - 100.0F) / 50.0F, 0.0F, 1.0F), 0.15F, 0.4F);
      localDBZ = Math.max(
         (float)Math.pow(1.0 - Math.clamp(rawDist / (double)((float)this.maxWidth * efc), 0.0, 1.0), 0.5) * (Math.abs(eye * 0.1F) + 0.9F) * 1.35F * intensity,
         localDBZ
      );
      localDBZ *= (float)Math.pow(1.0 - Math.clamp(rawDist / (double)this.maxWidth, 0.0, 1.0), 0.5);
      localDBZ *= Mth.lerp(
         0.5F + Math.clamp(((float)this.windspeed - 65.0F) / 40.0F, 0.0F, 1.0F) * 0.5F,
         1.0F,
         (float)Math.pow(Math.clamp(rawDist / (double)((float)this.maxWidth * 0.1F), 0.0, 1.0), 2.0)
      );
      localDBZ *= Mth.lerp(Math.clamp(((float)this.windspeed - 75.0F) / 50.0F, 0.0F, 1.0F), 0.8F + (float)shapeNoise2 * 0.4F, 1.0F);
      localDBZ *= 0.8F + (float)shapeNoise * 0.4F;
      localDBZ *= 1.0F + fineShapeNoise * Mth.lerp((float)Math.pow(Math.clamp(rawDist / (double)this.maxWidth, 0.0, 1.0), 1.5), 0.05F, 0.15F);
      localDBZ = (float)Math.pow((double)localDBZ, 1.75);
      if (localDBZ > 0.8F) {
         float dif = (localDBZ - 0.8F) / 1.25F;
         localDBZ -= dif;
      }

      return localDBZ;
   }

   @Override
   public float getPrecipitation(Vec3 pos) {
      float precip = 0.0F;
      double dist = pos.distanceTo(new Vec3(this.position.x, pos.y, this.position.z));
      if (dist > (double)this.maxWidth) {
         return precip;
      } else {
         Vec3 cPos = this.position.multiply(1.0, 0.0, 1.0);
         float intensity = (float)Math.pow((double)Math.clamp((float)this.windspeed / 65.0F, 0.0F, 1.0F), 0.85F);
         Vec3 relPos = cPos.subtract(pos);
         double d = (double)((float)this.maxWidth / (3.0F + (float)this.windspeed / 12.0F));
         double d2 = (double)((float)this.maxWidth / (1.15F + (float)this.windspeed / 12.0F));
         double dE = (double)((float)this.maxWidth * 0.65F / (1.75F + (float)this.windspeed / 12.0F));
         double fac = 1.0 + Math.max((dist - (double)((float)this.maxWidth * 0.2F)) / (double)this.maxWidth, 0.0) * 2.0;
         d *= fac;
         d2 *= fac;
         double angle = Math.atan2(relPos.z, relPos.x) - dist / d;
         double angle2 = Math.atan2(relPos.z, relPos.x) - dist / d2;
         double angleE = Math.atan2(relPos.z, relPos.x) - dist / dE;
         float weak = 0.0F;
         float strong = 0.0F;
         float intense = 0.0F;
         float staticBands = (float)Math.sin(angle - (Math.PI / 2));
         staticBands *= (float)Math.pow(Math.clamp(dist / (double)((float)this.maxWidth * 0.25F), 0.0, 1.0), 0.1F);
         staticBands *= 1.25F * (float)Math.pow((double)intensity, 0.75);
         if (staticBands < 0.0F) {
            weak += Math.abs(staticBands);
         } else {
            weak += Math.abs(staticBands) * (float)Math.pow(1.0 - Math.clamp(dist / (double)((float)this.maxWidth * 0.65F), 0.0, 1.0), 0.5);
            weak *= Math.clamp(((float)this.windspeed - 70.0F) / 40.0F, 0.0F, 1.0F);
         }

         float rotatingBands = (float)Math.sin((angle2 + Math.toRadians((double)((float)this.tickCount / 8.0F))) * 6.0);
         rotatingBands *= (float)Math.pow(Math.clamp(dist / (double)((float)this.maxWidth * 0.25F), 0.0, 1.0), 0.1F);
         rotatingBands *= 1.25F * (float)Math.pow((double)intensity, 0.75);
         strong += Mth.lerp(0.45F, Math.abs(rotatingBands) * 0.3F + 0.7F, weak);
         intense += Mth.lerp(0.3F, Math.abs(rotatingBands) * 0.2F + 0.8F, weak);
         weak = (Math.abs(rotatingBands) * 0.3F + 0.6F) * weak;
         float localRain = 0.0F;
         localRain += Mth.lerp(
            Math.clamp(((float)this.windspeed - 120.0F) / 60.0F, 0.0F, 1.0F),
            Mth.lerp(Math.clamp(((float)this.windspeed - 40.0F) / 90.0F, 0.0F, 1.0F), weak, strong),
            intense
         );
         float eye = (float)Math.sin((angleE + Math.toRadians((double)((float)this.tickCount / 4.0F))) * 2.0);
         float efc = Mth.lerp(Math.clamp(((float)this.windspeed - 100.0F) / 50.0F, 0.0F, 1.0F), 0.15F, 0.4F);
         localRain = Math.max(
            (float)Math.pow(1.0 - Math.clamp(dist / (double)((float)this.maxWidth * efc), 0.0, 1.0), 0.5) * (Math.abs(eye * 0.1F) + 0.9F) * 1.35F * intensity,
            localRain
         );
         localRain *= (float)Math.pow(1.0 - Math.clamp(dist / (double)this.maxWidth, 0.0, 1.0), 0.5);
         localRain *= Mth.lerp(
            0.5F + Math.clamp(((float)this.windspeed - 65.0F) / 40.0F, 0.0F, 1.0F) * 0.5F,
            1.0F,
            (float)Math.pow(Math.clamp(dist / (double)((float)this.maxWidth * 0.15F), 0.0, 1.0), 2.0)
         );
         if (localRain > 0.6F) {
            float dif = (localRain - 0.6F) / 2.5F;
            localRain -= dif;
         }

         return precip + Math.max(localRain - 0.15F, 0.0F) * 2.0F;
      }
   }

   @Override
   public Vec3 getRawWind(Vec3 pos) {
      Vec3 wind = Vec3.ZERO;
      float timeScale = 20000.0F;
      double distance = pos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      if (distance > (double)this.maxWidth) {
         return wind;
      } else {
         Vec3 relativePos = pos.subtract(this.position);
         Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
         Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
         double pullStrngth = (double)((float)this.windspeed * 0.3F);
         double rotStrngth = (double)((float)this.windspeed * 0.7F);
         float mult = (float)Math.pow(1.0 - Math.clamp(distance / (double)this.maxWidth, 0.0, 1.0), 3.0);
         if (distance < (double)((float)this.maxWidth * 0.1F)) {
            mult += (float)Math.pow(1.0 - Math.clamp(distance / (double)((float)this.maxWidth * 0.1F), 0.0, 1.0), 0.75) * 0.15F;
            mult = Math.clamp(mult, 0.0F, 1.0F);
         }

         double d = (double)((float)this.maxWidth / (1.5F + (float)this.windspeed / 30.0F));
         float effect = Math.clamp((float)distance / (float)this.maxWidth, 0.0F, 1.0F);
         float noiseX = (float)WindEngine.FBM(
            new Vec3(
               pos.x / (double)((float)this.maxWidth * 0.5F),
               pos.z / (double)((float)this.maxWidth * 0.5F),
               (double)((float)this.level.getGameTime() / timeScale)
            ),
            5,
            2.0F,
            0.2F,
            1.0F
         );
         float noiseZ = (float)WindEngine.FBM(
            new Vec3(
               pos.z / (double)((float)this.maxWidth * 0.5F),
               pos.x / (double)((float)this.maxWidth * 0.5F),
               (double)((float)this.level.getGameTime() / timeScale)
            ),
            5,
            2.0F,
            0.2F,
            1.0F
         );
         relativePos = relativePos.add(
            new Vec3((double)(noiseX * (float)this.maxWidth * 0.3F * effect), 0.0, (double)(noiseZ * (float)this.maxWidth * 0.3F * effect))
         );
         double angle = Math.atan2(relativePos.z, relativePos.x) - distance / d;
         float bands = (float)Math.sin((angle + Math.toRadians((double)((float)this.tickCount / 8.0F))) * 4.0);
         mult += Mth.lerp(
            1.0F - Math.clamp((float)distance / ((float)this.maxWidth * 0.35F), 0.0F, 1.0F),
            (float)Math.pow((double)Math.abs(bands), 2.0) * 0.5F * mult,
            0.5F * mult
         );
         float noise = (float)WindEngine.FBM(
            new Vec3(
               pos.x / (double)((float)this.maxWidth * 0.5F),
               pos.z / (double)((float)this.maxWidth * 0.5F),
               (double)((float)this.level.getGameTime() / timeScale)
            ),
            5,
            2.0F,
            0.2F,
            1.0F
         );
         mult *= Math.clamp(noise, 0.0F, 1.0F) * 0.2F + 0.8F;
         float noise2 = (float)this.FBM(
            new Vec3(
               pos.x / (double)((float)this.maxWidth * 0.1F),
               pos.z / (double)((float)this.maxWidth * 0.1F),
               (double)((float)this.level.getGameTime() / timeScale)
            ),
            5,
            2.0F,
            0.2F,
            1.0F
         );
         mult *= Math.clamp(noise2, 0.0F, 1.0F) * 0.1F + 0.9F;
         mult *= 1.15F
            + (float)Math.pow(1.0 - Math.clamp((distance - (double)((float)this.maxWidth * 0.1F)) / (double)((float)this.maxWidth * 0.1F), 0.0, 1.0), 2.5)
               * 0.35F;
         float eye = (float)Math.pow(
            Math.clamp(distance / (double)((float)this.maxWidth * 0.1F), 0.0, 1.0),
            (double)Mth.lerp(Math.clamp((float)this.windspeed / 120.0F, 0.0F, 1.0F), 0.5F, 4.0F)
         );
         mult *= Mth.lerp((float)Math.pow((double)Math.clamp((float)this.windspeed / 65.0F, 0.0F, 1.0F), 2.0), 1.0F, eye);
         Vec3 vec = inward.multiply(pullStrngth, 0.0, pullStrngth)
            .add(rotational.multiply(rotStrngth, 0.0, rotStrngth))
            .multiply((double)mult, 0.0, (double)mult);
         if (vec.length() > (double)this.windspeed) {
            double dif = vec.length() - (double)this.windspeed;
            vec = vec.subtract(new Vec3(dif, 0.0, dif));
         }

         wind = wind.add(vec);

         for (Vorticy vorticy : this.vorticies) {
            Vec3 vpos = vorticy.getPosition();
            Vec3 rPos = pos.subtract(vpos);
            Vec3 in = new Vec3(-rPos.x, 0.0, -rPos.z).normalize();
            Vec3 rot = new Vec3(rPos.z, 0.0, -rPos.x).normalize();
            float width = vorticy.getWidth();
            double dist = pos.multiply(1.0, 0.0, 1.0).distanceTo(vpos.multiply(1.0, 0.0, 1.0));
            double pullStrn = (double)(vorticy.windspeedMult * (float)this.windspeed * 0.3F);
            double rotStrn = (double)(vorticy.windspeedMult * (float)this.windspeed * 0.7F);
            float m = (float)Math.pow((double)(1.0F - Math.clamp((float)dist / width, 0.0F, 1.0F)), 3.75);
            m *= Math.clamp((float)dist / (width * 0.1F), 0.0F, 1.0F);
            m *= 7.0F;
            Vec3 v = in.multiply(pullStrn, 0.0, pullStrn).add(rot.multiply(rotStrn, 0.0, rotStrn)).multiply((double)m, 0.0, (double)m);
            wind = wind.add(v);
         }

         return wind;
      }
   }

   @Override
   public float addToPrecip(float original, float toAdd) {
      return Math.max(original, toAdd);
   }
}
