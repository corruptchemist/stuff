package dev.protomanly.pmweather.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.MetarBlock;
import dev.protomanly.pmweather.block.RadarBlock;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.compat.sable.SableMod;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.MetarData;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.multiblock.wsr88d.WSR88DCore;
import dev.protomanly.pmweather.shaders.data.PMWTexture;
import dev.protomanly.pmweather.util.ColorTables;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Clouds;
import dev.protomanly.pmweather.weather.Sounding;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.WindEngine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4fStack;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;

public class RadarRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
   private static PMWTexture radarTexture;
   public static int RenderedRadars = 0;

   public static float FBM(SimplexNoise noise, Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = 0.0;

      for (int i = 0; i < Math.max(octaves, 1); i++) {
         y += (double)amplitude * noise.getValue(pos.x, pos.y, pos.z);
         pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
         amplitude *= gain;
      }

      return (float)y;
   }

   public RadarRenderer(Context context) {
   }

   public void render(T blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int combinedLightIn, int combinedOverlayIn) {
      if (blockEntity instanceof RadarBlockEntity radarBlockEntity) {
         if (!(Boolean)radarBlockEntity.getBlockState().getValue(RadarBlock.ON)) {
            return;
         }

         LocalPlayer plr = Minecraft.getInstance().player;
         if (plr == null) {
            return;
         }

         Level level = blockEntity.getLevel();
         if (level == null) {
            return;
         }

         double plrDist = plr.position().distanceTo(blockEntity.getBlockPos().getCenter());
         if (plrDist > 20.0 || RenderedRadars > 2) {
            return;
         }

         if (plrDist > 5.0) {
            BlockHitResult clip = level.clip(
               new ClipContext(plr.getEyePosition(), blockEntity.getBlockPos().getCenter().add(0.0, 0.55, 0.0), Block.COLLIDER, Fluid.NONE, plr)
            );
            boolean valid = clip.getType() == Type.MISS
               || clip.getBlockPos().equals(blockEntity.getBlockPos())
               || clip.getBlockPos().equals(blockEntity.getBlockPos().above());
            if (!valid) {
               return;
            }
         }

         RenderedRadars++;
         boolean canRender = true;
         BlockPos pos = radarBlockEntity.getBlockPos();
         float sizeRenderDiameter = 1.5F;
         float simSize = 2048.0F;
         int resolution = ClientConfig.radarResolution;
         if (radarTexture == null) {
            radarTexture = new PMWTexture(new Vector2i(resolution), 9728);
         }

         radarTexture.resize(new Vector2i(resolution));
         if (radarBlockEntity.hasRangeUpgrade) {
            simSize *= 4.0F;
            if (!ClientConfig._3X3Radar) {
               sizeRenderDiameter = 3.0F;
            }
         }

         Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
         matrix4fStack.pushMatrix();
         matrix4fStack.mul(poseStack.last().pose());
         matrix4fStack.translate(0.5F, 1.05F, 0.5F);
         RenderSystem.applyModelViewMatrix();
         RenderSystem.enableBlend();
         RenderSystem.depthMask(true);
         RenderSystem.enableDepthTest();
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.defaultBlendFunc();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder bufferBuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         List<Storm> storms = new ArrayList<>(radarBlockEntity.storms);
         boolean update = false;
         ClientConfig.RadarMode radarMode = ClientConfig.radarMode;
         if (radarBlockEntity.lastUpdate < radarBlockEntity.tickCount) {
            radarBlockEntity.lastUpdate = radarBlockEntity.tickCount + 60;
            update = true;
         }

         if (ServerConfig.requireWSR88D && update) {
            canRender = false;
            int searchrange = 64;

            for (int x = -searchrange; x <= searchrange && !canRender; x++) {
               for (int y = -searchrange; y <= searchrange && !canRender; y++) {
                  for (int z = -searchrange * 2; z <= searchrange * 2; z++) {
                     BlockState state = level.getBlockState(pos.offset(x, y, z));
                     if (state.getBlock() instanceof WSR88DCore core && core.isComplete(state)) {
                        canRender = true;
                        break;
                     }
                  }
               }
            }
         }

         float _simSize = simSize;
         boolean _update = update;
         boolean _canRender = canRender;
         Vector2i centerPixel = radarTexture.getSize().div(2);
         RadarBlock.Mode mode = (RadarBlock.Mode)blockEntity.getBlockState().getValue(RadarBlock.RADAR_MODE);
         if (SableMod.exists()) {
            Vec3 _sableVec3Hack = radarBlockEntity.getBlockPos().getCenter().multiply(1.0, 0.0, 1.0);
            Vec2 var53 = new Vec2((float)_sableVec3Hack.x, (float)_sableVec3Hack.z).normalized();
         }

         if (radarBlockEntity.lastFrame == null || radarBlockEntity.lastMode != radarBlockEntity.getBlockState().getValue(RadarBlock.RADAR_MODE) || _update) {
            MetarData metarData = ((WeatherHandlerClient)GameBusClientEvents.weatherHandler).metarData;
            Map<BlockPos, Vec3> metarWinds = new HashMap<>();

            for (MetarBlock.PlacementState state : metarData.METARS) {
               metarWinds.put(state.pos(), WindEngine.getWind(state.pos().getCenter(), level, false, false, false, true, false, state.terrainHeight()));
            }

            radarTexture.write(
               (x, zx) -> {
                  Vector2i pixel = new Vector2i(x, zx);
                  double distToCenter = pixel.distance(centerPixel);
                  distToCenter /= (double)radarTexture.getSize().x;
                  if (!(distToCenter <= 0.0075) && !pixel.equals(centerPixel)) {
                     long id = (long)x.intValue() + (long)zx.intValue() * (long)radarTexture.getSize().x;
                     float dbz = radarBlockEntity.reflectivityMap.getOrDefault(id, 0.0F);
                     float temp = radarBlockEntity.temperatureMap.getOrDefault(id, 15.0F);
                     float vel = radarBlockEntity.velocityMap.getOrDefault(id, 0.0F);
                     float mwind = radarBlockEntity.metarMap.getOrDefault(id, 0.0F);
                     Color dbg = radarBlockEntity.debugMap.getOrDefault(id, new Color(0, 0, 0));
                     Vec3 normalPos = new Vec3(
                           (double)x.intValue() / (double)radarTexture.getSize().x, 0.0, (double)zx.intValue() / (double)radarTexture.getSize().y
                        )
                        .subtract(0.5, 0.0, 0.5)
                        .scale((double)_simSize * 2.0);
                     Vec3 worldPosx = normalPos.add(pos.getCenter());
                     if (_update) {
                        float clouds = Clouds.getCloudDensity(GameBusClientEvents.weatherHandler, new Vector2f((float)worldPosx.x, (float)worldPosx.z), 0.0F);
                        dbz = 0.0F;
                        mwind = 0.0F;
                        List<MetarData.WeightedState> metarStates = metarData.getWeightedStatesAt(BlockPos.containing(worldPosx));
                        if (!metarStates.isEmpty()) {
                           Vec3 mwindVec = Vec3.ZERO;
                           double totalWeight = 0.0;

                           for (MetarData.WeightedState state : metarStates) {
                              Vec3 wind = metarWinds.getOrDefault(state.state().pos(), Vec3.ZERO);
                              if (state.distance() <= 1.0) {
                                 totalWeight = 1.0;
                                 mwindVec = wind;
                                 break;
                              }

                              double weight = 1.0 / state.distance();
                              weight = Math.pow(weight, 3.5);
                              mwindVec = mwindVec.add(wind.scale(weight));
                              totalWeight += weight;
                           }

                           mwind = (float)mwindVec.scale(1.0 / totalWeight).length();
                        }

                        Vec2 f = new Vec2((float)normalPos.x, (float)normalPos.z).normalized();
                        Vec3 wind = WindEngine.getWind(
                           new Vec3(worldPosx.x, (double)(level.getMaxBuildHeight() + 1), worldPosx.z), level, false, false, false, true
                        );
                        Vec2 w = new Vec2((float)wind.x, (float)wind.z);
                        vel = f.dot(w);

                        for (Storm storm : storms) {
                           if (!storm.visualOnly && storm.hasRadarRepresentation()) {
                              double renderRange = (double)storm.getRadarRenderRange();
                              double dist = storm.position.multiply(1.0, 0.0, 1.0).distanceTo(worldPosx.multiply(1.0, 0.0, 1.0));
                              if (dist < renderRange) {
                                 dbz = Math.max(dbz, storm.getRadarReflectivityReturn(radarBlockEntity, worldPosx));
                              }
                           }
                        }

                        float v = Math.max(clouds - 0.15F, 0.0F) * 4.0F;
                        if (v > 0.3F) {
                           float dif = (v - 0.4F) / 1.5F;
                           v -= dif;
                        }

                        dbz = Math.max(dbz, v);
                        dbz += (PMWeather.RANDOM.nextFloat() - 0.5F) * 5.0F / 60.0F;
                        vel += (PMWeather.RANDOM.nextFloat() - 0.5F) * 3.0F;
                        if (dbz > 1.0F) {
                           dbz = (dbz - 1.0F) / 3.0F + 1.0F;
                        }

                        if (!_canRender) {
                           dbz = PMWeather.RANDOM.nextFloat() * 1.2F;
                           vel = (PMWeather.RANDOM.nextFloat() - 0.5F) * 300.0F;
                           temp = 15.0F;
                        } else {
                           temp = ThermodynamicEngine.samplePoint(GameBusClientEvents.weatherHandler, worldPosx, level, radarBlockEntity, 0).temperature();
                        }

                        radarBlockEntity.reflectivityMap.put(id, dbz);
                        radarBlockEntity.temperatureMap.put(id, temp);
                        radarBlockEntity.velocityMap.put(id, vel);
                        radarBlockEntity.metarMap.put(id, mwind);
                     }

                     float rdbz = dbz * 60.0F;
                     Color startColor = radarBlockEntity.terrainMap.getOrDefault(id, new Color(0, 0, 0));
                     if (radarBlockEntity.init && _update) {
                        Holder<Biome> biome = radarBlockEntity.getNearestBiome(new BlockPos((int)worldPosx.x, (int)worldPosx.y, (int)worldPosx.z));
                        String rn = biome.getRegisteredName().toLowerCase();
                        if (rn.contains("ocean") || rn.contains("river")) {
                           startColor = new Color(((Biome)biome.value()).getWaterColor());
                        } else if (rn.contains("beach") || rn.contains("desert")) {
                           startColor = new Color(227, 198, 150);
                        } else if (rn.contains("badlands")) {
                           startColor = new Color(214, 111, 42);
                        } else {
                           startColor = new Color(((Biome)biome.value()).getGrassColor(worldPosx.x, worldPosx.z));
                        }

                        startColor = ColorTables.lerp(0.5F, startColor, new Color(0, 0, 0));
                        radarBlockEntity.terrainMap.put(id, startColor);
                     }

                     Color color = ColorTables.getReflectivity(rdbz, startColor);
                     if (rdbz > 5.0F && !radarBlockEntity.hasRangeUpgrade) {
                        if (temp < 3.0F && temp > -1.0F) {
                           color = ColorTables.getMixedReflectivity(rdbz);
                        } else if (temp <= -1.0F) {
                           color = ColorTables.getSnowReflectivity(rdbz);
                        }
                     }

                     if (mode == RadarBlock.Mode.VELOCITY) {
                        color = new Color(0, 0, 0);
                        vel /= 1.75F;
                        color = ColorTables.lerp(
                           Mth.clamp(Math.max(rdbz, (Mth.abs(vel) - 90.0F) / 1.5F) / 15.0F, 0.0F, 1.0F), color, ColorTables.getVelocity(vel)
                        );
                     }

                     if (mode == RadarBlock.Mode.METAR_WIND) {
                        color = ColorTables.getHurricaneWindspeed(mwind);
                     }

                     if (ClientConfig.radarDebugging && _update) {
                        if (radarMode == ClientConfig.RadarMode.TEMPERATURE) {
                           float t = ThermodynamicEngine.samplePoint(GameBusClientEvents.weatherHandler, worldPosx, level, radarBlockEntity, 0).temperature();
                           if (t <= 0.0F) {
                              dbg = ColorTables.lerp(Mth.clamp(t / -40.0F, 0.0F, 1.0F), new Color(153, 226, 251, 255), new Color(29, 53, 221, 255));
                           } else if (t < 15.0F) {
                              dbg = ColorTables.lerp(Mth.clamp(t / 15.0F, 0.0F, 1.0F), new Color(255, 255, 255, 255), new Color(225, 174, 46, 255));
                           } else {
                              dbg = ColorTables.lerp(Mth.clamp((t - 15.0F) / 25.0F, 0.0F, 1.0F), new Color(225, 174, 46, 255), new Color(232, 53, 14, 255));
                           }
                        }

                        if (radarMode == ClientConfig.RadarMode.SST) {
                           Float t = ThermodynamicEngine.GetSST(GameBusClientEvents.weatherHandler, worldPosx, level, radarBlockEntity, 0);
                           if (t == null) {
                              dbg = new Color(0, 0, 0);
                           } else {
                              dbg = ColorTables.getSST(t);
                           }
                        }

                        if (radarMode == ClientConfig.RadarMode.CLOUDS) {
                           float cloudsx = Clouds.getCloudDensity(
                              GameBusClientEvents.weatherHandler, new Vector2f((float)worldPosx.x, (float)worldPosx.z), 0.0F
                           );
                           cloudsx = Mth.clamp(cloudsx, 0.0F, 1.0F);
                           dbg = new Color(cloudsx, cloudsx, cloudsx);
                        }

                        if (radarMode == ClientConfig.RadarMode.WINDFIELDS && GameBusClientEvents.weatherHandler != null) {
                           Vec3 wP = normalPos.scale(0.25).add(pos.getCenter());
                           float wind = 0.0F;

                           for (Storm stormx : storms) {
                              wind += stormx.getTornadicWind(wP);
                           }

                           dbg = ColorTables.getWindspeed(wind);
                        }

                        if (radarMode == ClientConfig.RadarMode.GLOBALWINDS && GameBusClientEvents.weatherHandler != null) {
                           int heightxx = GameBusClientEvents.weatherHandler.getWorld().getHeight(Types.MOTION_BLOCKING, (int)worldPosx.x, (int)worldPosx.z);
                           float wind = (float)WindEngine.getWind(
                                 new Vec3(worldPosx.x, (double)heightxx, worldPosx.z), GameBusClientEvents.weatherHandler.getWorld(), false, false, false, true
                              )
                              .length();
                           dbg = ColorTables.getHurricaneWindspeed(wind);
                        }

                        if (radarMode == ClientConfig.RadarMode.LOCALWINDS && GameBusClientEvents.weatherHandler != null) {
                           Vec3 wP = normalPos.scale(0.25).add(pos.getCenter());
                           int heightx = GameBusClientEvents.weatherHandler.getWorld().getHeight(Types.MOTION_BLOCKING, (int)wP.x, (int)wP.z);
                           float wind = (float)WindEngine.getWind(
                                 new Vec3(wP.x, (double)heightx, wP.z), GameBusClientEvents.weatherHandler.getWorld(), false, false, false, true
                              )
                              .length();
                           dbg = ColorTables.getHurricaneWindspeed(wind);
                        }

                        if (radarMode == ClientConfig.RadarMode.CAPE) {
                           Sounding sounding = new Sounding(GameBusClientEvents.weatherHandler, worldPosx, level, 500, 12000, radarBlockEntity);
                           Sounding.CAPE CAPE = sounding.getCAPE(sounding.getSBParcel());
                           dbg = ColorTables.lerp(Mth.clamp(CAPE.CAPE() / 6000.0F, 0.0F, 1.0F), new Color(0, 0, 0), new Color(255, 0, 0));
                        }

                        if (radarMode == ClientConfig.RadarMode.CAPE3KM) {
                           Sounding sounding = new Sounding(GameBusClientEvents.weatherHandler, worldPosx, level, 250, 4000, radarBlockEntity);
                           Sounding.CAPE CAPE = sounding.getCAPE(sounding.getSBParcel());
                           dbg = ColorTables.lerp(Mth.clamp(CAPE.CAPE3() / 1000.0F, 0.0F, 1.0F), new Color(0, 0, 0), new Color(255, 0, 0));
                        }

                        if (radarMode == ClientConfig.RadarMode.CINH) {
                           Sounding sounding = new Sounding(GameBusClientEvents.weatherHandler, worldPosx, level, 500, 12000, radarBlockEntity);
                           Sounding.CAPE CAPE = sounding.getCAPE(sounding.getSBParcel());
                           dbg = ColorTables.lerp(Mth.clamp(CAPE.CINH() / -250.0F, 0.0F, 1.0F), new Color(0, 0, 0), new Color(0, 0, 255));
                        }

                        if (radarMode == ClientConfig.RadarMode.LAPSERATE03) {
                           Sounding sounding = new Sounding(GameBusClientEvents.weatherHandler, worldPosx, level, 250, 4000, radarBlockEntity);
                           float lapse = (float)Math.floor((double)(sounding.getLapseRate(0, 3000) * 2.0F)) / 2.0F;
                           if (lapse > 5.0F) {
                              dbg = ColorTables.lerp(Mth.clamp((lapse - 5.0F) / 5.0F, 0.0F, 1.0F), new Color(255, 255, 0), new Color(255, 0, 0));
                           } else {
                              dbg = ColorTables.lerp(Mth.clamp(lapse / 5.0F, 0.0F, 1.0F), new Color(0, 255, 0), new Color(255, 255, 0));
                           }
                        }

                        if (radarMode == ClientConfig.RadarMode.LAPSERATE36) {
                           Sounding sounding = new Sounding(GameBusClientEvents.weatherHandler, worldPosx, level, 250, 7000, radarBlockEntity);
                           float lapse = (float)Math.floor((double)(sounding.getLapseRate(3000, 6000) * 2.0F)) / 2.0F;
                           if (lapse > 5.0F) {
                              dbg = ColorTables.lerp(Mth.clamp((lapse - 5.0F) / 5.0F, 0.0F, 1.0F), new Color(255, 255, 0), new Color(255, 0, 0));
                           } else {
                              dbg = ColorTables.lerp(Mth.clamp(lapse / 5.0F, 0.0F, 1.0F), new Color(0, 255, 0), new Color(255, 255, 0));
                           }
                        }

                        if (radarMode == ClientConfig.RadarMode.FIRE) {
                           Vec3 wP = normalPos.scale(0.25).add(pos.getCenter());
                           ChunkAccess chunkAccess = level.getChunk(new BlockPos((int)wP.x(), (int)wP.y(), (int)wP.z()));
                           float fireIntensity = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);
                           dbg = ColorTables.lerp(Mth.clamp(fireIntensity / 25.0F, 0.0F, 1.0F), new Color(255, 255, 255), new Color(255, 98, 0));
                        }

                        if (radarMode == ClientConfig.RadarMode.PRECIPITATION) {
                           float precipitation = GameBusClientEvents.weatherHandler.getPrecipitation(worldPosx);
                           float hail = GameBusClientEvents.weatherHandler.getHail(worldPosx);
                           dbg = ColorTables.lerp(Mth.clamp(precipitation, 0.0F, 1.0F), new Color(0, 0, 0), new Color(80, 88, 255));
                           dbg = ColorTables.lerp(Mth.clamp(hail, 0.0F, 1.0F), dbg, new Color(255, 84, 74));
                        }

                        radarBlockEntity.debugMap.put(id, dbg);
                     }

                     if (ClientConfig.radarDebugging) {
                        color = dbg;
                     }

                     float r = (float)color.getRed() / 255.0F;
                     float g = (float)color.getGreen() / 255.0F;
                     float b = (float)color.getBlue() / 255.0F;
                     float a = (float)color.getAlpha() / 255.0F * 0.75F + 0.25F;
                     return Util.getRGBA(new Color(r * a, g * a, b * a, 1.0F));
                  } else {
                     return Util.getRGBA(new Color(1.0F, 0.0F, 0.0F, 1.0F));
                  }
               }
            );
            if (radarBlockEntity.lastFrame != null) {
               boolean valid = new Vector2i(radarBlockEntity.lastFrame.getWidth(), radarBlockEntity.lastFrame.getHeight()).equals(radarTexture.getSize());
               if (!valid) {
                  radarBlockEntity.lastFrame.untrack();
                  radarBlockEntity.lastFrame.close();
                  radarBlockEntity.lastFrame = null;
               }
            }

            if (radarBlockEntity.lastFrame != null) {
               radarBlockEntity.lastFrame.copyFrom(radarTexture.getImage());
            } else {
               radarBlockEntity.lastFrame = radarTexture.getCopy();
            }

            radarBlockEntity.lastMode = (RadarBlock.Mode)radarBlockEntity.getBlockState().getValue(RadarBlock.RADAR_MODE);
         } else if (radarBlockEntity.lastFrame != null) {
            radarTexture.copyFrom(radarBlockEntity.lastFrame);
         }

         radarTexture.upload();
         RenderSystem.setShaderTexture(0, radarTexture.getId());
         Vector3f topLeft = new Vector3f(-sizeRenderDiameter, 0.0F, -sizeRenderDiameter);
         Vector3f bottomLeft = new Vector3f(-sizeRenderDiameter, 0.0F, sizeRenderDiameter);
         Vector3f bottomRight = new Vector3f(sizeRenderDiameter, 0.0F, sizeRenderDiameter);
         Vector3f topRight = new Vector3f(sizeRenderDiameter, 0.0F, -sizeRenderDiameter);
         bufferBuilder.addVertex(topLeft)
            .setUv(0.0F, 0.0F)
            .addVertex(bottomLeft)
            .setUv(0.0F, 1.0F)
            .addVertex(bottomRight)
            .setUv(1.0F, 1.0F)
            .addVertex(topRight)
            .setUv(1.0F, 0.0F);
         List<Vec3> linePoints = new ArrayList<>();
         List<Vec3> triPoints = new ArrayList<>();
         if (ClientConfig.radarDebugging) {
            if (!update) {
               linePoints = radarBlockEntity.linePoints;
               triPoints = radarBlockEntity.triPoints;
            } else {
               int debugBarbDensity = 10;
               if (radarMode == ClientConfig.RadarMode.GLOBALWINDS) {
                  for (int x = -10; x <= 10; x++) {
                     for (int zx = -10; zx <= 10; zx++) {
                        Vec3 percPos = new Vec3((double)x, 0.0, (double)zx).scale(0.1);
                        Vec3 renderPos = percPos.scale((double)sizeRenderDiameter);
                        Vec3 normalPos = percPos.scale((double)_simSize);
                        Vec3 worldPos = normalPos.add(pos.getCenter());
                        int height = GameBusClientEvents.weatherHandler.getWorld().getHeight(Types.MOTION_BLOCKING, (int)worldPos.x, (int)worldPos.z);
                        RadarRenderer.Barb barb = this.drawWindBarb(renderPos, new Vec3(worldPos.x, (double)height, worldPos.z), height);
                        linePoints.addAll(barb.linePoints);
                        triPoints.addAll(barb.triPoints);
                     }
                  }
               }

               if (radarMode == ClientConfig.RadarMode.LOCALWINDS) {
                  for (int x = -10; x <= 10; x++) {
                     for (int zx = -10; zx <= 10; zx++) {
                        Vec3 percPos = new Vec3((double)x, 0.0, (double)zx).scale(0.1);
                        Vec3 renderPos = percPos.scale((double)sizeRenderDiameter);
                        Vec3 normalPos = percPos.scale((double)_simSize);
                        Vec3 worldPos = normalPos.scale(0.25).add(pos.getCenter());
                        int height = GameBusClientEvents.weatherHandler.getWorld().getHeight(Types.MOTION_BLOCKING, (int)worldPos.x, (int)worldPos.z);
                        RadarRenderer.Barb barb = this.drawWindBarb(renderPos, new Vec3(worldPos.x, (double)height, worldPos.z), height);
                        linePoints.addAll(barb.linePoints);
                        triPoints.addAll(barb.triPoints);
                     }
                  }
               }

               radarBlockEntity.linePoints = linePoints;
               radarBlockEntity.triPoints = triPoints;
            }
         } else if (mode == RadarBlock.Mode.METAR_WIND) {
            if (!update) {
               linePoints = radarBlockEntity.linePoints;
               triPoints = radarBlockEntity.triPoints;
            } else {
               MetarData metarData = ((WeatherHandlerClient)GameBusClientEvents.weatherHandler).metarData;

               for (MetarBlock.PlacementState state : metarData.METARS) {
                  Vec3 worldPos = state.pos().getCenter();
                  Vec3 renderPos = worldPos.subtract(pos.getCenter()).multiply(1.0, 0.0, 1.0);
                  renderPos = renderPos.scale((double)(1.0F / _simSize));
                  if (!(renderPos.x > 1.0) && !(renderPos.x < -1.0) && !(renderPos.z > 1.0) && !(renderPos.z < -1.0)) {
                     renderPos = renderPos.scale((double)sizeRenderDiameter);
                     RadarRenderer.Barb barb = this.drawWindBarb(renderPos, worldPos, state.terrainHeight());
                     linePoints.addAll(barb.linePoints);
                     triPoints.addAll(barb.triPoints);
                  }
               }

               radarBlockEntity.linePoints = linePoints;
               radarBlockEntity.triPoints = triPoints;
            }
         }

         MeshData meshData = bufferBuilder.build();
         if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
         }

         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         matrix4fStack.translate(0.0F, 0.015F, 0.0F);
         RenderSystem.applyModelViewMatrix();
         BufferBuilder triBuilder = tesselator.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

         for (Vec3 p : triPoints) {
            triBuilder.addVertex(p.toVector3f()).setColor(0.0F, 0.0F, 0.0F, 1.0F);
         }

         meshData = triBuilder.build();
         if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
         }

         float lastLineWidth = RenderSystem.getShaderLineWidth();
         RenderSystem.lineWidth(lastLineWidth * 3.0F);
         BufferBuilder lineBuilder = tesselator.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

         for (Vec3 p : linePoints) {
            lineBuilder.addVertex(p.toVector3f()).setColor(0.0F, 0.0F, 0.0F, 1.0F);
         }

         meshData = lineBuilder.build();
         if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
         }

         matrix4fStack.mul(poseStack.last().pose().invert());
         matrix4fStack.translate(-0.5F, -1.0649999F, -0.5F);
         matrix4fStack.popMatrix();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.lineWidth(lastLineWidth);
      }
   }

   private RadarRenderer.Barb drawWindBarb(Vec3 renderPos, Vec3 worldPos, @Nullable Integer worldHeight) {
      List<Vec3> linePoints = new ArrayList<>();
      List<Vec3> triPoints = new ArrayList<>();
      Vec3 wind;
      if (worldHeight != null) {
         wind = WindEngine.getWind(worldPos, GameBusClientEvents.weatherHandler.getWorld(), false, false, false, true, false, worldHeight);
      } else {
         wind = WindEngine.getWind(worldPos, GameBusClientEvents.weatherHandler.getWorld(), false, false, false, true, false);
      }

      Vec3 norm = wind.multiply(1.0, 0.0, 1.0).normalize();
      Vec3 sideVec = new Vec3(norm.z, 0.0, -norm.x);
      double speed = wind.length();
      double kts = speed / 1.151;
      double barbLen = 0.1;
      if (kts < 1.0) {
         for (int i = 0; i < 10; i++) {
            double ang = (double)i / 10.0 * Math.PI * 2.0;
            double ang2 = (double)(i + 1) / 10.0 * Math.PI * 2.0;
            Vec3 dir = new Vec3(Math.sin(ang), 0.0, Math.cos(ang));
            Vec3 dir2 = new Vec3(Math.sin(ang2), 0.0, Math.cos(ang2));
            linePoints.add(renderPos.add(dir.scale(barbLen / 5.0)));
            linePoints.add(renderPos.add(dir2.scale(barbLen / 5.0)));
         }

         return new RadarRenderer.Barb(linePoints, triPoints);
      } else {
         for (int i = 0; i < 10; i++) {
            double ang = (double)i / 10.0 * Math.PI * 2.0;
            double ang2 = (double)(i + 1) / 10.0 * Math.PI * 2.0;
            Vec3 dir = new Vec3(Math.sin(ang), 0.0, Math.cos(ang));
            Vec3 dir2 = new Vec3(Math.sin(ang2), 0.0, Math.cos(ang2));
            triPoints.add(renderPos);
            triPoints.add(renderPos.add(dir.scale(barbLen / 12.0)));
            triPoints.add(renderPos.add(dir2.scale(barbLen / 12.0)));
         }

         Vec3 end = renderPos.subtract(norm.scale(barbLen));
         linePoints.add(renderPos);
         linePoints.add(end);
         if (kts < 5.0) {
            return new RadarRenderer.Barb(linePoints, triPoints);
         } else {
            kts = Math.floor(kts / 5.0) * 5.0;
            double spacing = 0.16666666666666666;
            if (kts <= 5.0) {
               Vec3 anchor = end.lerp(renderPos, spacing);
               Vec3 barbEnd = anchor.add(sideVec.scale(barbLen / 6.0)).subtract(norm.scale(barbLen / 12.0));
               linePoints.add(anchor);
               linePoints.add(barbEnd);
               return new RadarRenderer.Barb(linePoints, triPoints);
            } else {
               double currentSpace = 0.0;
               double tri50KTBarbPos = 0.0;

               while (kts >= 5.0 && currentSpace <= 1.0 - spacing) {
                  double added = 5.0;
                  if (kts >= 10.0) {
                     added = 10.0;
                  }

                  if (kts >= 50.0) {
                     added = 50.0;
                  }

                  if (added == 50.0) {
                     Vec3 anchor = end.subtract(norm.scale(barbLen * tri50KTBarbPos));
                     Vec3 anchorTop = anchor.subtract(norm.scale(barbLen * spacing * 2.0));
                     Vec3 barbEnd = anchorTop.add(sideVec.scale(barbLen / 3.0)).subtract(norm.scale(barbLen / 6.0));
                     triPoints.add(anchor);
                     triPoints.add(barbEnd);
                     triPoints.add(anchorTop);
                     linePoints.add(anchor);
                     linePoints.add(anchorTop);
                     tri50KTBarbPos += spacing * 2.0;
                  }

                  if (added == 10.0) {
                     Vec3 anchor = end.lerp(renderPos, currentSpace);
                     Vec3 barbEnd = anchor.add(sideVec.scale(barbLen / 3.0)).subtract(norm.scale(barbLen / 6.0));
                     linePoints.add(anchor);
                     linePoints.add(barbEnd);
                     currentSpace += spacing;
                  }

                  if (added == 5.0) {
                     Vec3 anchor = end.lerp(renderPos, currentSpace);
                     Vec3 barbEnd = anchor.add(sideVec.scale(barbLen / 6.0)).subtract(norm.scale(barbLen / 12.0));
                     linePoints.add(anchor);
                     linePoints.add(barbEnd);
                     currentSpace += spacing;
                  }

                  kts -= added;
               }

               return new RadarRenderer.Barb(linePoints, triPoints);
            }
         }
      }
   }

   public static record Barb(List<Vec3> linePoints, List<Vec3> triPoints) {
   }
}
