package dev.protomanly.pmweather.shaders.post;

import dev.protomanly.pmweather.compat.DistantHorizons;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.shaders.PMWPostShader;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.VolumetricSmokeParticle;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.effects.ClientLightning;
import dev.protomanly.pmweather.weather.effects.PowerFlash;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostPipeline.Context;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class VolumeShader extends PMWPostShader {
   private static float snow = 0.0F;
   private static float lastSnow = 0.0F;
   public static float Ash = 0.0F;
   public static float Fire = 0.0F;
   private static final int MAX_STORMS = 16;
   private static final int MAX_POINT_LIGHTS = 32;
   private static final int MAX_SMOKE = 64;

   public VolumeShader(String location) {
      super(location);
   }

   @Override
   public void tick() {
      super.tick();
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      ClientLevel cLevel = Minecraft.getInstance().level;
      WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
      Ash = Mth.lerp(0.01F, Ash, GameBusClientEvents.AshTarget);
      Fire = Mth.lerp(0.04F, Fire, GameBusClientEvents.FireTarget);
      if (player != null && weatherHandler != null && cLevel != null) {
         for (Storm storm : weatherHandler.getStorms()) {
            if (storm.lastPosition == null) {
               storm.lastPosition = storm.position;
            } else {
               storm.lastPosition = storm.lastPosition.lerp(storm.position, 0.05F);
            }

            storm.lastSpin = storm.spin;
            storm.spin = storm.spin + storm.smoothWindspeed * 0.01F / Math.max(storm.smoothWidth, 20.0F);
            float smoothStage = (float)storm.stage + (float)storm.energy / 100.0F;
            storm.smoothStage = Mth.lerp(0.06666667F, storm.smoothStage, smoothStage);
         }

         ThermodynamicEngine.Precipitation precip = ThermodynamicEngine.getPrecipitationType(weatherHandler, player.position(), cLevel, 0);
         lastSnow = snow;
         if (precip != ThermodynamicEngine.Precipitation.SNOW && precip != ThermodynamicEngine.Precipitation.WINTRY_MIX) {
            snow = Mth.lerp(0.05F, snow, 0.0F);
         } else {
            float rain = weatherHandler.getPrecipitation(player.position());
            Vec3 wind = WindEngine.getWind(player.position(), cLevel);
            float snowBlindness = (float)Math.clamp(Math.pow(wind.length() / 45.0, 2.0) * (double)rain, 0.0, 1.0);
            snow = Mth.lerp(0.05F, snow, snowBlindness);
         }
      }
   }

   @Override
   public void pre(PostPipeline pipeline, Context context) {
      super.pre(pipeline, context);
      if (this.shouldRender()) {
         CameraMatrices cameraMatrices = VeilRenderSystem.renderer().getCameraMatrices();
         Vector3f camPos = cameraMatrices.getCameraPosition();
         Vector3f sunDir = ModShadersVeil.Utils.GetSunDirection(this.level, this.partialTicks);
         Vector3f lightingColor = ModShadersVeil.Utils.GetLightingColor(sunDir);
         pipeline.getUniformSafe("dhRenderDistance").setFloat(DistantHorizons.isEnabled() ? (float)DistantHorizons.getChunkRenderDistance() * 16.0F : -256.0F);
         if (DistantHorizons.isEnabled()) {
            pipeline.getUniformSafe("dhProjectionInv").setMatrix(new Matrix4f(DistantHorizons.getDhProjectionMatrix()).invert());
            pipeline.getUniformSafe("dhPlanes").setVector(DistantHorizons.getNearPlane(), DistantHorizons.getFarPlane());
         }

         pipeline.getUniformSafe("sunDir").setVector(sunDir);
         pipeline.getUniformSafe("lightingColor").setVector(lightingColor);
         pipeline.getUniformSafe("layer0height").setFloat((float)ServerConfig.layer0Height);
         pipeline.getUniformSafe("layerCheight").setFloat((float)ServerConfig.layerCHeight);
         pipeline.getUniformSafe("stormSize").setFloat((float)ServerConfig.stormSize * 2.0F);
         pipeline.getUniformSafe("rainStrength").setFloat((float)ServerConfig.rainStrength);
         pipeline.getUniformSafe("rain").setFloat(this.weatherHandler.getPrecipitation());
         pipeline.getUniformSafe("snow").setFloat(Mth.lerp(this.partialTicks, lastSnow, snow));
         pipeline.getUniformSafe("fire").setFloat(Fire);
         pipeline.getUniformSafe("ash").setFloat(Ash);
         Vector3f[] stormPositions = new Vector3f[16];
         Arrays.fill(stormPositions, new Vector3f(0.0F));
         Vector2f[] stormVelocities = new Vector2f[16];
         Arrays.fill(stormVelocities, new Vector2f(0.0F));
         float[] stormSmoothStages = new float[16];
         int[] stormTypes = new int[16];
         float[] stormOcclusions = new float[16];
         float[] tornadoWindspeeds = new float[16];
         float[] tornadoWidths = new float[16];
         float[] tornadoTouchdownSpeeds = new float[16];
         int[] visualOnlys = new int[16];
         int[] stormDyings = new int[16];
         float[] stormSpins = new float[16];
         float[] tornadoShapes = new float[16];
         float[] rainIntensities = new float[16];
         List<Storm> storms = this.weatherHandler.getStorms();
         int count = 0;

         for (int i = 0; i < storms.size(); i++) {
            Storm storm = storms.get(i);
            if (storm.lastPosition != null) {
               Vector3f stormPos = storm.lastPosition.toVector3f();
               if (!(
                     (double)stormPos.distance(camPos.x, stormPos.y, camPos.z)
                        > Math.max(46.0 * ServerConfig.stormSize, (double)((float)storm.maxWidth * 2.0F))
                  )
                  && (storm.stage > 0 || storm.energy > 0 || storm.is(StormTypes.CYCLONE) || storm.is(StormTypes.FIRE_WHIRL))
                  && (!storm.is(StormTypes.FIRE_WHIRL) || storm.windspeed > 0)) {
                  stormPositions[count] = stormPos;
                  stormVelocities[count] = new Vector2f((float)storm.velocity.x, (float)storm.velocity.z);
                  stormSmoothStages[count] = storm.smoothStage;
                  tornadoWindspeeds[count] = storm.smoothWindspeed;
                  tornadoWidths[count] = storm.smoothWidth;
                  tornadoTouchdownSpeeds[count] = (float)storm.touchdownSpeed;
                  stormSpins[count] = Mth.lerp(this.partialTicks, storm.lastSpin, storm.spin);
                  tornadoShapes[count] = storm.tornadoShape;
                  stormTypes[count] = storm.stormType.getShaderId();
                  stormOcclusions[count] = storm.occlusion;
                  rainIntensities[count] = storm.rainIntensity;
                  visualOnlys[count] = storm.visualOnly ? 1 : 0;
                  stormDyings[count] = storm.isDying ? 1 : 0;
                  if (++count >= 16) {
                     break;
                  }
               }
            }
         }

         pipeline.getUniformSafe("stormCount").setInt(count);
         pipeline.getUniformSafe("stormPositions").setVectors(stormPositions);
         pipeline.getUniformSafe("stormVelocities").setVectors(stormVelocities);
         pipeline.getUniformSafe("stormSmoothStages").setFloats(stormSmoothStages);
         pipeline.getUniformSafe("stormTypes").setInts(stormTypes);
         pipeline.getUniformSafe("stormOcclusions").setFloats(stormOcclusions);
         pipeline.getUniformSafe("tornadoWindspeeds").setFloats(tornadoWindspeeds);
         pipeline.getUniformSafe("tornadoWidths").setFloats(tornadoWidths);
         pipeline.getUniformSafe("tornadoTouchdownSpeeds").setFloats(tornadoTouchdownSpeeds);
         pipeline.getUniformSafe("visualOnlys").setInts(visualOnlys);
         pipeline.getUniformSafe("stormDyings").setInts(stormDyings);
         pipeline.getUniformSafe("stormSpins").setFloats(stormSpins);
         pipeline.getUniformSafe("tornadoShapes").setFloats(tornadoShapes);
         pipeline.getUniformSafe("rainIntensities").setFloats(rainIntensities);
         int pointLightCount = 0;
         Vector4f[] pointLights = new Vector4f[32];
         Arrays.fill(pointLights, new Vector4f(0.0F));
         Vector3f[] pointLightColors = new Vector3f[32];
         Arrays.fill(pointLightColors, new Vector3f(0.0F));
         float[] pointLightStretches = new float[32];

         for (PowerFlash powerFlash : this.weatherHandler.powerFlashes) {
            if (pointLightCount > 32) {
               break;
            }

            float camDist = (float)Math.sqrt(Mth.square((double)camPos.x - powerFlash.position.x) + Mth.square((double)camPos.z - powerFlash.position.z));
            float mod = Mth.clamp(camDist / 250.0F, 0.25F, 1.0F);
            pointLights[pointLightCount] = new Vector4f(
               (float)powerFlash.position.x,
               (float)powerFlash.position.y,
               (float)powerFlash.position.z,
               powerFlash.getSize(this.partialTicks) * (mod * 0.25F + 0.75F)
            );
            pointLightColors[pointLightCount] = powerFlash.getColor(this.partialTicks).toVector3f().mul(mod * 0.75F + 0.25F);
            pointLightStretches[pointLightCount] = 0.5F;
            pointLightCount++;
         }

         for (ClientLightning lightning : this.weatherHandler.lightnings) {
            if (pointLightCount > 32) {
               break;
            }

            pointLights[pointLightCount] = new Vector4f((float)lightning.position.x, (float)ServerConfig.layer0Height, (float)lightning.position.z, 750.0F);
            float p = Math.clamp(((float)lightning.ticks + this.partialTicks) / (float)lightning.lifetime, 0.0F, 1.0F);
            Vector3f color = new Vector3f(
               (float)lightning.color.getRed() / 255.0F, (float)lightning.color.getGreen() / 255.0F, (float)lightning.color.getBlue() / 255.0F
            );
            color = color.mul((float)Math.abs(Math.cos(Math.sqrt((double)p) * Math.PI * 3.0)) * (1.0F - p) * 5.0F * Mth.sqrt(lightning.strength));
            pointLightColors[pointLightCount] = color;
            pointLightStretches[pointLightCount] = 0.05F;
            pointLightCount++;
         }

         pipeline.getUniformSafe("pointLightCount").setInt(Math.min(pointLightCount, 32));
         pipeline.getUniformSafe("pointLights").setVectors(pointLights);
         pipeline.getUniformSafe("pointLightStretches").setFloats(pointLightStretches);
         pipeline.getUniformSafe("pointLightColors").setVectors(pointLightColors);
         Vector3f[] smokeParticlePositions = new Vector3f[64];
         Arrays.fill(smokeParticlePositions, new Vector3f(0.0F));
         float[] smokeParticleSizes = new float[64];
         float[] smokeParticleBrightness = new float[64];
         float[] smokeParticleOpacity = new float[64];
         List<VolumetricSmokeParticle> smokeParticles = this.weatherHandler.smokeParticles;

         for (int ix = 0; ix < Math.min(smokeParticles.size(), 64); ix++) {
            VolumetricSmokeParticle particle = smokeParticles.get(ix);
            Vec3 pos = particle.lastPos.lerp(particle.position, (double)this.partialTicks);
            smokeParticlePositions[ix] = pos.toVector3f();
            smokeParticleSizes[ix] = Mth.lerp(this.partialTicks, particle.lastSize, particle.size);
            smokeParticleBrightness[ix] = Mth.lerp(this.partialTicks, particle.lastBright, particle.brightness);
            smokeParticleOpacity[ix] = Mth.lerp(this.partialTicks, particle.lastOpacity, particle.opacity);
         }

         pipeline.getUniformSafe("smokeParticleCount").setInt(Math.min(smokeParticles.size(), 64));
         pipeline.getUniformSafe("smokeParticlePositions").setVectors(smokeParticlePositions);
         pipeline.getUniformSafe("smokeParticleSizes").setFloats(smokeParticleSizes);
         pipeline.getUniformSafe("smokeParticleBrightness").setFloats(smokeParticleBrightness);
         pipeline.getUniformSafe("smokeParticleOpacity").setFloats(smokeParticleOpacity);
         float downsample = (float)ClientConfig.volumetricsDownsample;
         float targetRes = 1080.0F;
         float curRes = (float)Minecraft.getInstance().getWindow().getHeight();
         if (curRes > targetRes && downsample > 1.0F) {
            float m = curRes / targetRes - 1.0F;
            m = Math.max(1.0F + Math.min((downsample - 1.0F) / 1.5F, 1.0F) * m, 1.0F);
            downsample *= m;
         }

         pipeline.getUniformSafe("downsample").setFloat(downsample);
         pipeline.getUniformSafe("glowFix").setInt(ClientConfig.glowFix ? 1 : 0);
         pipeline.getUniformSafe("doBlur").setInt(ClientConfig.volumetricsBlur ? 1 : 0);
         pipeline.getUniformSafe("computationalFBMNoise").setInt(ClientConfig.computationalNoise ? 1 : 0);
         pipeline.getUniformSafe("simpleLighting").setInt(ClientConfig.simpleLighting ? 0 : 1);
         float seasonEffect = SeasonHandler.getSeasonEffectSine(this.weatherHandler.getWorld(), 3.5F) + 1.0F;
         double overcastDampen = (double)seasonEffect * 0.15;
         pipeline.getUniformSafe("overcastPerc").setFloat((float)Math.max(ServerConfig.overcastPercent - overcastDampen, 0.0));
         pipeline.getUniformSafe("worldTime").setFloat(Util.getWorldTime(this.level, this.partialTicks));
         pipeline.getUniformSafe("time").setFloat((float)this.player.tickCount + this.partialTicks);

         int quality = switch (ClientConfig.volumetricsQuality) {
            case POTATO -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case PC_KILLER -> 4;
         };
         pipeline.getUniformSafe("quality").setInt(quality);
      }
   }
}
