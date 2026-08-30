package dev.protomanly.pmweather.event;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.command.ClientWeatherCommands;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.debug.DebugKeybinds;
import dev.protomanly.pmweather.interfaces.ParticleData;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.networking.PacketVersionFromClient;
import dev.protomanly.pmweather.particle.EmberParticle;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.particle.FireSmokeParticle;
import dev.protomanly.pmweather.particle.FlameParticle;
import dev.protomanly.pmweather.particle.FoamParticle;
import dev.protomanly.pmweather.particle.ParticleHail;
import dev.protomanly.pmweather.particle.ParticleManager;
import dev.protomanly.pmweather.particle.ParticleRegistry;
import dev.protomanly.pmweather.particle.ParticleTexExtraRender;
import dev.protomanly.pmweather.particle.ParticleTexFX;
import dev.protomanly.pmweather.particle.behavior.ParticleBehavior;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import dev.protomanly.pmweather.shaders.data.TextureManager3D;
import dev.protomanly.pmweather.shaders.post.VolumeShader;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.util.ChunkCoordinatesBlock;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.ViewportEvent.RenderFog;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(
   modid = "pmweather",
   value = {Dist.CLIENT}
)
public class GameBusClientEvents {
   public static Level lastLevel;
   public static WeatherHandler weatherHandler;
   public static ParticleManager particleManager;
   public static ParticleManager particleManagerDebris;
   public static ParticleBehavior particleBehavior = new ParticleBehavior(null);
   public static float AshTarget = 0.0F;
   public static float FireTarget = 0.0F;
   public static int RandomMonth = 1;
   public static List<Block> LEAVES_BLOCKS = new ArrayList<Block>() {
      {
         this.add(Blocks.ACACIA_LEAVES);
         this.add(Blocks.AZALEA_LEAVES);
         this.add(Blocks.BIRCH_LEAVES);
         this.add(Blocks.DARK_OAK_LEAVES);
         this.add(Blocks.CHERRY_LEAVES);
         this.add(Blocks.FLOWERING_AZALEA_LEAVES);
         this.add(Blocks.MANGROVE_LEAVES);
         this.add(Blocks.OAK_LEAVES);
         this.add(Blocks.JUNGLE_LEAVES);
         this.add(Blocks.SPRUCE_LEAVES);
      }
   };
   public static ArrayList<ChunkCoordinatesBlock> soundLocations = new ArrayList<>();
   public static HashMap<ChunkCoordinatesBlock, Long> soundTimeLocations = new HashMap<>();
   public static long ClientTicks = 0L;
   public static long lastAmbientTick;
   public static long lastAmbientTickThreaded;
   public static long lastWindSoundTick;

   public GameBusClientEvents() {
   }

   public static void resetColors(ClientLevel clientLevel, LevelRenderer levelRenderer) {
      clientLevel.clearTintCaches();
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         SectionPos center = SectionPos.of(player);
         int viewDistance = (int)levelRenderer.getLastViewDistance();

         for (int x = center.x() - viewDistance; x <= center.x() + viewDistance; x++) {
            for (int z = center.z() - viewDistance; z <= center.z() + viewDistance; z++) {
               for (int y = center.y() - 5; y <= center.y() + 5; y++) {
                  if (clientLevel.getChunk(x, z, ChunkStatus.FULL, false) != null) {
                     levelRenderer.setSectionDirty(x, y, z);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPostTick(Post event) {
      if (((KeyMapping)DebugKeybinds.RERENDER_CHUNKS.get()).consumeClick()) {
         ClientLevel level = Minecraft.getInstance().level;
         if (level != null) {
            resetColors(level, Minecraft.getInstance().levelRenderer);
         }
      }

      if (((KeyMapping)DebugKeybinds.TOGGLE_RADAR_DEBUG.get()).consumeClick()) {
         ClientConfig.radarDebugging = !ClientConfig.radarDebugging;
      }
   }

   @SubscribeEvent
   public static void fogEvent(RenderFog event) {
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      if (level != null && ClientConfig.baseGameFog) {
         RenderSystem.setShaderFogStart(10000.0F);
         RenderSystem.setShaderFogEnd(40000.0F);
      }
   }

   @SubscribeEvent
   public static void onStageRenderTick(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES && weatherHandler != null) {
         particleManagerDebris.render(
            event.getPoseStack(),
            null,
            Minecraft.getInstance().gameRenderer.lightTexture(),
            event.getCamera(),
            event.getPartialTick().getGameTimeDeltaPartialTick(false),
            event.getFrustum()
         );
      }
   }

   public static void doSnowParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 40.0F) + 40;
      int spawns = 0;
      int spawnAreaSize = 50;
      Vec3 wind = WindEngine.getWind(minecraft.player.position(), level, false, false, true);

      for (int i = 0; i < ClientConfig.rainParticleDensity; i++) {
         BlockPos pos = minecraft.player
            .blockPosition()
            .offset(
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2,
               -5 + PMWeather.RANDOM.nextInt(25),
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2
            );
         Vec3 offset = wind.multiply(1.0, 0.0, 1.0).scale(0.3333333333333333);
         pos = pos.subtract(BlockPos.containing(offset));
         if (canPrecipitateAt(level, pos)) {
            TextureAtlasSprite particle = switch (PMWeather.RANDOM.nextInt(4)) {
               case 1 -> ParticleRegistry.snow1;
               case 2 -> ParticleRegistry.snow2;
               case 3 -> ParticleRegistry.snow3;
               default -> ParticleRegistry.snow;
            };
            ParticleTexExtraRender snow = new ParticleTexExtraRender(
               (ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 0.0, 0.0, 0.0, particle
            );
            snow.fullAlphaTarget = 1.0F;
            snow.renderOrder = 3;
            particleBehavior.initParticleSnow(snow, Math.max((int)(10.0F * precip), 1) + 3, (float)(wind.length() / 45.0));
            snow.setScale(Math.max(precip * 0.03F + (PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 0.02F, 0.01F) * 3.0F);
            snow.windWeight = 0.15F;
            snow.renderOrder = 3;
            snow.spawnAsWeatherEffect();
            if (++spawns > spawnsNeeded) {
               break;
            }
         }
      }
   }

   public static void doSleetParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 30.0F);
      int spawns = 0;
      int spawnAreaSize = 30;

      for (int i = 0; i < ClientConfig.rainParticleDensity; i++) {
         BlockPos pos = minecraft.player
            .blockPosition()
            .offset(
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2,
               -5 + PMWeather.RANDOM.nextInt(25),
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2
            );
         if (canPrecipitateAt(level, pos)) {
            ParticleTexExtraRender sleet = new ParticleTexExtraRender(
               (ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 0.0, 0.0, 0.0, ParticleRegistry.sleet
            );
            sleet.fullAlphaTarget = 1.0F;
            sleet.renderOrder = 3;
            particleBehavior.initParticleSleet(sleet, Math.max((int)(20.0F * precip), 1));
            sleet.setScale(Math.max(precip * 0.14F + (PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 0.02F, 0.02F) * 0.8F);
            sleet.renderOrder = 3;
            sleet.spawnAsWeatherEffect();
            if (++spawns > spawnsNeeded) {
               break;
            }
         }
      }
   }

   public static void doRainParticles(float precip, Minecraft minecraft, Level level) {
      int spawnsNeeded = (int)(precip * 300.0F);
      int spawns = 0;
      int spawnAreaSize = 30;
      double windspeed = 0.0;
      if (weatherHandler != null) {
         windspeed = WindEngine.getWind(minecraft.player.position(), level, false, false, false, true).length();
      }

      for (int i = 0; i < ClientConfig.rainParticleDensity; i++) {
         BlockPos pos = minecraft.player
            .blockPosition()
            .offset(
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2,
               -5 + PMWeather.RANDOM.nextInt(25),
               PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2
            );
         if (canPrecipitateAt(level, pos)) {
            boolean isLight = PMWeather.RANDOM.nextFloat() > (precip - 0.15F) * 3.0F;
            ParticleTexExtraRender rain = new ParticleTexExtraRender(
               (ClientLevel)level,
               (double)pos.getX(),
               (double)pos.getY(),
               (double)pos.getZ(),
               0.0,
               0.0,
               0.0,
               isLight ? ParticleRegistry.lightRain : ParticleRegistry.rain
            );
            rain.fullAlphaTarget = Mth.lerp(precip, 0.3F, 1.0F);
            rain.renderOrder = 3;
            particleBehavior.initParticleRain(rain, Math.max((int)(20.0F * precip), 1));
            if (isLight) {
               rain.scale(0.6F);
               rain.setGravity(1.4F);
            }

            if (windspeed > 50.0 && i < ClientConfig.rainParticleDensity / 3) {
               float strength = precip * (float)Mth.clamp((windspeed - 50.0) / 50.0, 0.0, 1.0);
               ParticleTexExtraRender mist = new ParticleTexExtraRender(
                  (ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 0.0, 0.0, 0.0, ParticleRegistry.mist
               );
               mist.fullAlphaTarget = Mth.lerp(strength, 0.3F, 1.0F);
               mist.renderOrder = 4;
               particleBehavior.initParticleRain(mist, Math.max((int)(5.0F * strength), 1));
               mist.setScale(0.5F + strength);
               mist.setColor(0.9F, 0.9F, 0.9F);
               mist.setGravity(0.5F);
            }

            if (++spawns > spawnsNeeded) {
               break;
            }
         }
      }

      if (precip > 0.15F) {
         int var17 = 40;

         for (int ix = 0; (float)ix < (float)(ClientConfig.rainParticleDensity * 3) * precip; ix++) {
            BlockPos pos = minecraft.player
               .blockPosition()
               .offset(PMWeather.RANDOM.nextInt(var17) - var17 / 2, -5 + PMWeather.RANDOM.nextInt(25), PMWeather.RANDOM.nextInt(var17) - var17 / 2);
            pos = level.getHeightmapPos(Types.MOTION_BLOCKING, pos).below();
            BlockState state = level.getBlockState(pos);
            double maxY = 0.0;
            double minY = 0.0;
            VoxelShape shape = state.getShape(level, pos);
            if (!shape.isEmpty()) {
               minY = shape.bounds().minY;
               maxY = shape.bounds().maxY;
            }

            if (!(pos.distSqr(minecraft.player.blockPosition()) > (double)var17 / 2.0 * ((double)var17 / 2.0)) && canPrecipitateAt(level, pos.above())) {
               if (level.getBlockState(pos).getBlock().defaultMapColor() == MapColor.WATER) {
                  pos = pos.offset(0, 1, 0);
               }

               ParticleTexFX rainx = new ParticleTexFX(
                  (ClientLevel)level,
                  (double)((float)pos.getX() + PMWeather.RANDOM.nextFloat()),
                  (double)pos.getY() + 0.01 + maxY,
                  (double)((float)pos.getZ() + PMWeather.RANDOM.nextFloat()),
                  0.0,
                  0.0,
                  0.0,
                  ParticleRegistry.splash
               );
               rainx.fullAlphaTarget = Mth.lerp(precip, 0.2F, 0.8F) / 2.0F;
               rainx.renderOrder = 5;
               particleBehavior.initParticleGroundSplash(rainx);
               rainx.spawnAsWeatherEffect();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onTick(Pre event) {
      ClientTicks++;
      if (ClientTicks % 20L == 0L) {
         WindEngine.FireIntensityCache.clear();
         RandomMonth = PMWeather.RANDOM.nextInt(12) + 1;
      }

      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      if (level != null && !minecraft.isPaused()) {
         getClientWeather();
         tryAmbientSounds();
         trySounds();
         weatherHandler.tick();
         particleManager.tick();
         particleManagerDebris.tick();
         ModShadersVeil.tick();
         WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)weatherHandler;
         if (minecraft.player != null) {
            ChunkAccess chunkAccess = level.getChunk(minecraft.player.blockPosition());
            ChunkPos chunkPos = chunkAccess.getPos();
            AshTarget = (Float)chunkAccess.getData(DataAttachments.FIRE_AFTERMATH);
            FireTarget = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);

            for (int x = -2; x <= 2; x++) {
               for (int z = -2; z <= 2; z++) {
                  if (x != 0 || z != 0) {
                     ChunkAccess check = level.getChunk(chunkPos.x + x, chunkPos.z + z);
                     FireTarget = Math.max(FireTarget, (Float)check.getData(DataAttachments.STABLE_FIRE_INTENSITY));
                  }
               }
            }

            float ash = VolumeShader.Ash;
            if (level.random.nextFloat() <= ash / 8.0F) {
               int count = Mth.ceil(75.0F * ash);

               for (int i = 0; i < count; i++) {
                  Vec3 pos = minecraft.player.position();
                  pos = pos.add(
                     (double)((level.random.nextFloat() - 0.5F) * 32.0F),
                     (double)((level.random.nextFloat() - 0.5F) * 64.0F),
                     (double)((level.random.nextFloat() - 0.5F) * 32.0F)
                  );
                  if (canPrecipitateAt(level, new BlockPos((int)pos.x(), (int)pos.y(), (int)pos.z()))) {
                     level.addAlwaysVisibleParticle(ParticleTypes.ASH, pos.x(), pos.y(), pos.z(), 0.0, -0.5, 0.0);
                  }
               }
            }

            Entity entity = minecraft.player;
            Vec3 w = WindEngine.getWind(entity.getEyePosition(1.0F), level, false, true, false);
            if (minecraft.player.isCrouching()) {
               w = w.scale(0.7);
            }

            if (w.length() > 60.0 && !minecraft.player.isCreative() && !minecraft.player.isSpectator()) {
               double factor = Mth.lerp(Mth.clamp(w.length() / 125.0, 0.0, 1.0), 0.005, 0.02);
               float mult = 0.65F;
               if (!entity.onGround()) {
                  mult = 0.075F;
               }

               entity.addDeltaMovement(w.multiply(0.05F, 0.0, 0.05F).multiply(factor, 0.0, factor).multiply((double)mult, (double)mult, (double)mult));
            }

            minecraft.particleEngine
               .iterateParticles(
                  particle -> {
                     if (particle instanceof ParticleData particleData) {
                        boolean affect = true;
                        if (particle instanceof EntityRotFX entityRotFX) {
                           affect = !entityRotFX.ignoreWind;
                        }

                        if (particle instanceof FoamParticle) {
                           affect = false;
                        }

                        if (affect) {
                           Vec3 wind = Vec3.ZERO;
                           float scale = 1.0F;
                           boolean initWind = false;
                           float whirlEffect = 0.0F;
                           if (!(particle instanceof FireSmokeParticle) && !(particle instanceof EmberParticle) && !(particle instanceof FlameParticle)) {
                              wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                              initWind = true;
                           } else {
                              for (Storm storm : weatherHandler.getStorms()) {
                                 if (storm.is(StormTypes.FIRE_WHIRL) && storm.lastPosition != null) {
                                    double dist = particle.getPos().distanceTo(storm.lastPosition);
                                    whirlEffect = Math.max(
                                       whirlEffect, Mth.square(1.0F - (float)Mth.clamp(dist / (double)Math.max(storm.width, 20.0F), 0.0, 1.0))
                                    );
                                 }
                              }

                              if (whirlEffect <= 0.001F || ClientTicks % 5L == 0L) {
                                 wind = WindEngine.getWind(particle.getPos(), level, false, false, false, true, true);
                                 wind = wind.scale(3.0);
                                 scale = 0.25F;
                                 initWind = true;
                              } else if (whirlEffect > 0.001F) {
                                 wind = WindEngine.getWind(particle.getPos(), level, false, false, false, true, true);
                                 initWind = true;
                              }
                           }

                           if (Float.isNaN(whirlEffect)) {
                              whirlEffect = 0.0F;
                           }

                           particleData.addVelocity(wind.multiply(0.05F, 0.05F, 0.05F).multiply(0.04F, 0.04F, 0.04F));
                           particleData.setVelocity(particleData.getVelocity().multiply(0.98, 0.98, 0.98));
                           if (initWind) {
                              double l = wind.length() * 0.025 * (double)scale;
                              if (particleData.getVelocity().multiply(1.0, 0.0, 1.0).length() > l) {
                                 particleData.setVelocity(
                                    particleData.getVelocity().normalize().multiply(1.0, 0.0, 1.0).scale(l).add(0.0, particleData.getVelocity().y(), 0.0)
                                 );
                              }

                              if (particleData.getVelocity().y() > Math.max(wind.y() * 0.01 * (double)scale, 0.6)) {
                                 particleData.setVelocity(particleData.getVelocity().multiply(1.0, 0.75, 1.0));
                              }
                           }

                           particleData.setVelocity(particleData.getVelocity().lerp(wind.scale((double)(0.04F * scale)), (double)whirlEffect));
                        }
                     }
                  }
               );
            particleManager.getParticles()
               .forEach(
                  (particleRenderType, particles) -> {
                     for (Particle particle : particles) {
                        if (particle instanceof ParticleData) {
                           ParticleData particleData = (ParticleData)particle;
                           float affect = 1.0F;
                           if (particle instanceof EntityRotFX) {
                              EntityRotFX entityRotFX = (EntityRotFX)particle;
                              if (entityRotFX.ignoreWind) {
                                 affect = 0.0F;
                              } else {
                                 affect = entityRotFX.windWeight;
                              }
                           }

                           if (affect > 0.0F) {
                              Vec3 wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                              particleData.addVelocity(
                                 wind.multiply(0.05F, 0.05F, 0.05F).multiply(0.04F, 0.04F, 0.04F).multiply((double)affect, (double)affect, (double)affect)
                              );
                              double l = wind.length() * 0.01;
                              if (particleData.getVelocity().length() < l) {
                                 particleData.setVelocity(particleData.getVelocity().normalize().multiply(l, l, l));
                              }
                           }
                        }
                     }
                  }
               );
            particleManagerDebris.getParticles()
               .forEach(
                  (particleRenderType, particles) -> {
                     for (Particle particle : particles) {
                        if (particle instanceof ParticleData) {
                           ParticleData particleData = (ParticleData)particle;
                           float affect = 1.0F;
                           if (particle instanceof EntityRotFX) {
                              EntityRotFX entityRotFX = (EntityRotFX)particle;
                              if (entityRotFX.ignoreWind) {
                                 affect = 0.0F;
                              } else {
                                 affect = entityRotFX.windWeight;
                              }
                           }

                           if (affect > 0.0F) {
                              Vec3 wind = WindEngine.getWind(particle.getPos(), level, false, false, false);
                              particleData.addVelocity(
                                 wind.multiply(0.05F, 0.05F, 0.05F).multiply(0.04F, 0.04F, 0.04F).multiply((double)affect, (double)affect, (double)affect)
                              );
                           }
                        }
                     }
                  }
               );
            float hail = weatherHandlerClient.getHail();
            float precip = weatherHandlerClient.getPrecipitation();
            if (precip > 0.0F) {
               ThermodynamicEngine.Precipitation precipType = ThermodynamicEngine.getPrecipitationType(
                  weatherHandlerClient, minecraft.player.position(), level, 0
               );
               if (precipType == ThermodynamicEngine.Precipitation.RAIN
                  || precipType == ThermodynamicEngine.Precipitation.FREEZING_RAIN
                  || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doRainParticles(precip, minecraft, level);
               }

               if (precipType == ThermodynamicEngine.Precipitation.SLEET || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doSleetParticles(precip, minecraft, level);
               }

               if (precipType == ThermodynamicEngine.Precipitation.SNOW || precipType == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
                  doSnowParticles(precip * 2.5F, minecraft, level);
               }
            }

            if (hail > 0.0F) {
               int spawnsNeeded = (int)(hail * 80.0F);
               int spawns = 0;
               int spawnAreaSize = 30;

               for (int ix = 0; ix < 15; ix++) {
                  BlockPos pos = minecraft.player
                     .blockPosition()
                     .offset(
                        PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2,
                        -5 + PMWeather.RANDOM.nextInt(25),
                        PMWeather.RANDOM.nextInt(spawnAreaSize) - spawnAreaSize / 2
                     );
                  if (canPrecipitateAt(level, pos)) {
                     ParticleHail hailP = new ParticleHail(
                        (ClientLevel)level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 0.0, 0.0, 0.0, Blocks.PACKED_ICE.defaultBlockState()
                     );
                     particleBehavior.initParticleHail(hailP);
                     hailP.setScale(0.01F + PMWeather.RANDOM.nextFloat() * hail * 0.08F);
                     hailP.renderOrder = 3;
                     hailP.spawnAsDebrisEffect();
                     if (++spawns >= spawnsNeeded) {
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean canPrecipitateAt(Level level, BlockPos pos, boolean force) {
      return (double)pos.getY() > ServerConfig.layer0Height && !force ? false : level.getHeightmapPos(Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
   }

   public static boolean canPrecipitateAt(Level level, BlockPos pos) {
      return canPrecipitateAt(level, pos, false);
   }

   public static void resetClientWeather() {
      weatherHandler = null;
   }

   public static WeatherHandlerClient getClientWeather() {
      try {
         Level level = Minecraft.getInstance().level;
         if (weatherHandler == null || level != lastLevel) {
            init(level);
         }
      } catch (Exception var1) {
         PMWeather.LOGGER.error(var1.getMessage(), var1);
      }

      return (WeatherHandlerClient)weatherHandler;
   }

   public static void trySounds() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         Level level = minecraft.level;
         Player player = minecraft.player;
         if (player == null || level == null) {
            return;
         }

         float hail = ((WeatherHandlerClient)weatherHandler).getHail();
         if (hail > 0.0F) {
            int chance = (int)Mth.lerp(hail, 20.0F, 2.0F);
            if (PMWeather.RANDOM.nextInt(chance) == 0) {
               BlockPos pos = player.blockPosition().offset(PMWeather.RANDOM.nextInt(-15, 16), 15, PMWeather.RANDOM.nextInt(-15, 16));
               pos = level.getHeightmapPos(Types.MOTION_BLOCKING, pos);
               if (canPrecipitateAt(level, pos) && pos.distSqr(player.blockPosition()) < 225.0) {
                  level.playLocalSound(
                     pos, (SoundEvent)ModSounds.HAIL.value(), SoundSource.WEATHER, hail * 3.5F, 2.0F + PMWeather.RANDOM.nextFloat() * 0.5F, false
                  );
               }
            }
         }

         if (lastWindSoundTick < System.currentTimeMillis()) {
            lastWindSoundTick = System.currentTimeMillis() + 4000L + (long)PMWeather.RANDOM.nextInt(0, 3000);
            Vec3 wind = WindEngine.getWind(player.getEyePosition(), level);
            double windspeed = wind.length();
            if (windspeed > 55.0) {
               ModSounds.playPlayerLockedSound(
                  player.getEyePosition(), (SoundEvent)ModSounds.WIND_STRONG.value(), (float)(windspeed / 200.0), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F
               );
            }

            if (windspeed > 35.0) {
               ModSounds.playPlayerLockedSound(
                  player.getEyePosition(), (SoundEvent)ModSounds.WIND_MED.value(), (float)(windspeed / 200.0), 0.9F + PMWeather.RANDOM.nextFloat() * 0.2F
               );
            }

            if (windspeed > 5.0) {
               ModSounds.playPlayerLockedSound(
                  player.getEyePosition(),
                  (SoundEvent)ModSounds.WIND_CALM.value(),
                  Math.min((float)(windspeed / 100.0), 0.1F),
                  0.9F + PMWeather.RANDOM.nextFloat() * 0.2F
               );
            }
         }

         if (lastAmbientTick < System.currentTimeMillis()) {
            lastAmbientTick = System.currentTimeMillis() + 500L;
            int size = 32;
            int hSize = size / 2;
            BlockPos curBlockPos = player.blockPosition();

            for (int i = 0; i < soundLocations.size(); i++) {
               ChunkCoordinatesBlock chunkCoord = soundLocations.get(i);
               if (Math.sqrt(chunkCoord.distSqr(curBlockPos)) > (double)size) {
                  soundLocations.remove(i--);
                  soundTimeLocations.remove(chunkCoord);
               } else {
                  Block block = level.getBlockState(chunkCoord).getBlock();
                  if (block != null && (block.defaultMapColor() == MapColor.WATER || block.defaultMapColor() == MapColor.PLANT)) {
                     long lastPlayTime = 0L;
                     float soundMuffle = 0.6F;
                     if (soundTimeLocations.containsKey(chunkCoord)) {
                        lastPlayTime = soundTimeLocations.get(chunkCoord);
                     }

                     float maxLeavesVolume = 1.0F;
                     soundMuffle *= (float)ClientConfig.leavesVolume;
                     if (lastPlayTime < System.currentTimeMillis() && LEAVES_BLOCKS.contains(chunkCoord.block)) {
                        Vec3 windx = WindEngine.getWind(curBlockPos, level, false, false, false);
                        double windspeedx = windx.length();
                        soundTimeLocations.put(chunkCoord, System.currentTimeMillis() + 12000L + (long)PMWeather.RANDOM.nextInt(50));
                        minecraft.level
                           .playLocalSound(
                              chunkCoord,
                              (SoundEvent)ModSounds.CALM_AMBIENCE.value(),
                              SoundSource.AMBIENT,
                              (float)Math.min((double)maxLeavesVolume, windspeedx * (double)soundMuffle * 0.05F),
                              0.9F + PMWeather.RANDOM.nextFloat() * 0.2F,
                              false
                           );
                     }
                  } else {
                     soundLocations.remove(i);
                     soundTimeLocations.remove(chunkCoord);
                  }
               }
            }
         }
      } catch (Exception var17) {
         PMWeather.LOGGER.error(var17.getMessage(), var17);
      }
   }

   public static void tryAmbientSounds() {
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft.level;
      Player player = minecraft.player;
      if (lastAmbientTickThreaded < System.currentTimeMillis() && ClientConfig.leavesVolume > 0.0) {
         lastAmbientTickThreaded = System.currentTimeMillis() + 500L;
         int size = 32;
         int hSize = size / 2;
         BlockPos curBlockPos = player.blockPosition();

         for (int x = curBlockPos.getX() - hSize; x < curBlockPos.getX() + hSize; x++) {
            for (int y = curBlockPos.getY() - hSize; y < curBlockPos.getY() + hSize; y++) {
               for (int z = curBlockPos.getZ() - hSize; z < curBlockPos.getZ() + hSize; z++) {
                  Block block = level.getBlockState(new BlockPos(x, y, z)).getBlock();
                  if (block.defaultMapColor() == MapColor.PLANT) {
                     boolean proxFail = false;

                     for (ChunkCoordinatesBlock soundLocation : soundLocations) {
                        if (Math.sqrt(soundLocation.distSqr(new BlockPos(x, y, z))) < 15.0) {
                           proxFail = true;
                           break;
                        }
                     }

                     if (!proxFail) {
                        soundLocations.add(new ChunkCoordinatesBlock(x, y, z, block));
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      if (event.getEntity() instanceof Player player && Minecraft.getInstance().player != null && player.is(Minecraft.getInstance().player)) {
         ModShadersVeil.InitShaders();
         FBOManager.reset();
         TextureManager3D.refresh();
         PacketDistributor.sendToServer(
            new PacketVersionFromClient(PMWeather.getModContainer().getModInfo().getVersion().toString()), new CustomPacketPayload[0]
         );
         if (ModList.get().isLoaded("iris")) {
            player.sendSystemMessage(Component.translatable("compat.pmweather.warning.iris"));
         }
      }
   }

   @SubscribeEvent
   public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent.Post event) {
      Level level = event.getLevel();
      Minecraft minecraft = Minecraft.getInstance();
      if (level instanceof ClientLevel clientLevel && minecraft.cameraEntity != null) {
         if (level.getGameTime() % 1200L == 0L) {
            resetColors(clientLevel, Minecraft.getInstance().levelRenderer);
         }

         CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();
         Vector3f camPos = matrices.getCameraPosition();
         Vec3 mojangCamPos = new Vec3(camPos);
         FBOManager.tickFBOs(mojangCamPos, level, (long)minecraft.cameraEntity.tickCount);
      }
   }

   public static void init(Level level) {
      lastLevel = level;
      if (weatherHandler instanceof WeatherHandlerClient weatherHandlerClient) {
         weatherHandlerClient.killFireAudios();
      }

      if (level != null) {
         weatherHandler = new WeatherHandlerClient(level.dimension());
         Minecraft minecraft = Minecraft.getInstance();
         if (particleManager == null) {
            particleManager = new ParticleManager(minecraft.level, minecraft.getTextureManager());
         } else {
            particleManager.setLevel((ClientLevel)level);
         }

         if (particleManagerDebris == null) {
            particleManagerDebris = new ParticleManager(minecraft.level, minecraft.getTextureManager());
         } else {
            particleManagerDebris.setLevel((ClientLevel)level);
         }

         CompoundTag data = new CompoundTag();
         data.putString("command", "syncFull");
         data.putString("packetCommand", "WeatherData");
         ModNetworking.clientSendToServer(data);
      }
   }

   @SubscribeEvent
   public static void onCommandsRegister(RegisterClientCommandsEvent event) {
      new ClientWeatherCommands(event.getDispatcher(), event.getBuildContext());
   }
}
