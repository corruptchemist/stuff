package dev.protomanly.pmweather.util;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.tags.ModTags;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector3d;

public class Util {
   public static Vec3[] RAIN_POSITIONS = new Vec3[Util.MAX_RAIN_DROPS];
   public static int MAX_RAIN_DROPS = 2000;
   public static Map<Block, Block> STRIPPED_VARIANTS = new HashMap<Block, Block>() {
      {
         this.put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG);
         this.put(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG);
         this.put(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG);
         this.put(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG);
         this.put(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG);
         this.put(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG);
         this.put(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG);
         this.put(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG);
         this.put((Block)ModBlocks.ROTTED_LOG.get(), (Block)ModBlocks.STRIPPED_ROTTED_LOG.get());
      }
   };
   public static float ROCP;

   public Util() {
   }

   public static Vec3 fromCardial(double north, double east) {
      return new Vec3(east, 0.0, -north);
   }

   public static Vec3 fromAngle(double ang, double magnitude) {
      return new Vec3(Math.sin(ang) * magnitude, 0.0, -Math.cos(ang) * magnitude);
   }

   public static float toRad(float deg) {
      return deg * (float) (Math.PI / 180.0);
   }

   public static float toDeg(float rad) {
      return rad * (180.0F / (float)Math.PI);
   }

   public static double normalDist(Random random, double lower, double mean, double upper) {
      Vector2d u = new Vector2d(random.nextDouble(), random.nextDouble());
      double z = Math.clamp(Math.sqrt(-2.0 * Math.log(u.x)) * Math.cos((Math.PI * 2) * u.y) / 2.5, -1.0, 1.0);
      return z < 0.0 ? Mth.lerp(z + 1.0, lower, Math.max(lower, mean)) : Mth.lerp(z, Math.max(lower, mean), Math.max(upper, Math.max(mean, lower)));
   }

   public static double getWidthScaling() {
      return ServerConfig.maxTornadoWidth / 4185.0;
   }

   public static boolean hasCollision(BlockState state, BlockPos pos, Level level) {
      return !state.getCollisionShape(level, pos).isEmpty();
   }

   @Nullable
   public static Block tryGuessSapling(Block originalBlock, @Nullable Block orElse) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKeyOrNull(originalBlock);
      if (id == null) {
         return orElse;
      } else {
         String path = id.getPath().replaceAll("stripped_", "").replaceAll("_log", "").replaceAll("_wood", "").concat("_sapling");
         Optional<Block> result = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(id.getNamespace(), path));
         return result.orElse(orElse);
      }
   }

   @Nullable
   public static Block tryGuessSapling(Block originalBlock) {
      return tryGuessSapling(originalBlock, null);
   }

   public static boolean isBlacklisted(Block block, ServerLevel level, BlockPos blockPos) {
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      boolean blacklisted = ServerConfig.blacklistedBlocks.contains(block);
      if (!blacklisted) {
         for (TagKey<Block> tag : ServerConfig.blacklistedBlockTags) {
            if (block.defaultBlockState().is(tag)) {
               blacklisted = true;
               break;
            }
         }
      }

      if (blacklisted && reinforcementManager != null && reinforcementManager.isPlayerPlaced(blockPos)) {
         for (TagKey<Block> tagx : ServerConfig.playerPlacedWhitelist) {
            if (block.defaultBlockState().is(tagx)) {
               blacklisted = false;
               break;
            }
         }
      }

      return blacklisted;
   }

   public static void checkLogs(BlockState state, ServerLevel level, BlockPos pos) {
      if (ServerConfig.doRotting) {
         for (int y = -1; y <= 1; y++) {
            checkLogs(state, level, pos, y);
         }
      }
   }

   public static void checkLogs(BlockState state, ServerLevel level, BlockPos pos, int y) {
      if (ServerConfig.doRotting) {
         ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());

         for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
               BlockPos p = pos.offset(x, y, z);
               BlockState pState = level.getBlockState(p);
               if (pState.is(BlockTags.LOGS_THAT_BURN)) {
                  boolean canLive = canLogSurvive(pState, level, p, new ArrayList<>());
                  if (!canLive) {
                     Block sapling = tryGuessSapling(pState.getBlock(), Blocks.OAK_SAPLING);
                     BlockState newState = (BlockState)((Block)ModBlocks.ROTTED_LOG.get())
                        .defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, pState.getOptionalValue(RotatedPillarBlock.AXIS).orElse(Axis.Y));
                     if (newState.getValue(RotatedPillarBlock.AXIS) == Axis.Y
                        && level.getBlockState(p.below()).is(BlockTags.DIRT)
                        && reinforcementManager != null) {
                        reinforcementManager.setSaplingData(p, sapling);
                     }

                     level.setBlockAndUpdate(p, newState);
                     return;
                  }
               }
            }
         }
      }
   }

   public static boolean canLogSurvive(BlockState state, ServerLevel level, BlockPos pos, List<BlockPos> checked) {
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (reinforcementManager == null) {
         return true;
      } else if (reinforcementManager.isPlayerPlaced(pos)) {
         return true;
      } else {
         checked.add(pos);

         for (int y = 1; y >= -1; y--) {
            for (int x = -1; x <= 1; x++) {
               for (int z = -1; z <= 1; z++) {
                  BlockPos p = pos.offset(x, y, z);
                  if (!checked.contains(p)) {
                     BlockState pState = level.getBlockState(p);
                     if (pState.is(ModTags.Blocks.KEEPS_TREES_ALIVE)) {
                        return true;
                     }

                     if (pState.is(BlockTags.LOGS)) {
                        return canLogSurvive(pState, level, p, checked);
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   public static boolean canWindAffect(Vec3 pos, Level level) {
      BlockHitResult upRay = level.clip(
         new ClipContext(
            pos.add(0.0, 0.55, 0.0), pos.add(0.0, 128.0, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()
         )
      );
      BlockHitResult pxRay = level.clip(
         new ClipContext(
            pos.add(1.0, 0.55, 0.0), pos.add(64.0, 130.0, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()
         )
      );
      BlockHitResult nxRay = level.clip(
         new ClipContext(
            pos.add(-1.0, 0.55, 0.0), pos.add(-64.0, 130.0, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()
         )
      );
      BlockHitResult pzRay = level.clip(
         new ClipContext(
            pos.add(0.0, 0.55, 1.0), pos.add(0.0, 130.0, 64.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()
         )
      );
      BlockHitResult nzRay = level.clip(
         new ClipContext(
            pos.add(0.0, 0.55, -1.0), pos.add(0.0, 130.0, -64.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()
         )
      );
      return upRay.getType() == Type.MISS
         || pxRay.getType() == Type.MISS
         || nxRay.getType() == Type.MISS
         || pzRay.getType() == Type.MISS
         || nzRay.getType() == Type.MISS;
   }

   public static Vec2 mulVec2(Vec2 a, Vec2 b) {
      return new Vec2(a.x * b.x, a.y * b.y);
   }

   public static Vec2 mulVec2(Vec2 a, float b) {
      return new Vec2(a.x * b, a.y * b);
   }

   public static Vec2 nearestPoint(Vec2 v, Vec2 w, Vec2 p) {
      float l2 = v.distanceToSqr(w);
      float t = Mth.clamp(p.add(v.negated()).dot(w.add(v.negated())) / l2, 0.0F, 1.0F);
      return v.add(mulVec2(w.add(v.negated()), t));
   }

   public static float minimumDistance(Vec2 v, Vec2 w, Vec2 p) {
      float l2 = v.distanceToSqr(w);
      if (l2 == 0.0F) {
         return Mth.sqrt(p.distanceToSqr(v));
      } else {
         Vec2 proj = nearestPoint(v, w, p);
         return Mth.sqrt(p.distanceToSqr(proj));
      }
   }

   public static boolean isInteger(String string) {
      try {
         Integer.parseInt(string);
         return true;
      } catch (NumberFormatException var2) {
         return false;
      }
   }

   public static float celsiusToFahrenheit(float t) {
      return t * 1.8F + 32.0F;
   }

   public static float fahrenheitToCelsius(float t) {
      return (t - 32.0F) * 0.5555556F;
   }

   public static float celsiusToKelvin(float t) {
      return t + 273.15F;
   }

   public static float kelvinToCelsius(float t) {
      return t - 273.15F;
   }

   public static float MixingRatio(float vapprs, float prs, @Nullable Float molWeight) {
      if (molWeight == null) {
         molWeight = 0.62197F;
      }

      return molWeight * (vapprs / (prs - vapprs));
   }

   public static float SaturationVaporPressure(float t) {
      return 6.112F * (float)Math.exp((double)(17.67F * t / (t + 243.5F)));
   }

   public static String riskToString(float riskV) {
      String risk = "§l§fNONE§r (0/6)";
      if (riskV > 1.5F) {
         risk = "§l§dHIGH§r (6/6)";
      } else if (riskV > 1.2F) {
         risk = "§l§4MDT§r (5/6)";
      } else if (riskV > 0.8F) {
         risk = "§l§6ENH§r (4/6)";
      } else if (riskV > 0.6F) {
         risk = "§l§eSLGT§r (3/6)";
      } else if (riskV > 0.3F) {
         risk = "§l§2MRGL§r (2/6)";
      } else if (riskV > 0.15F) {
         risk = "§l§aTSTM§r (1/6)";
      }

      return risk;
   }

   public static float SaturationMixingRatio(float tp, float t) {
      return MixingRatio(SaturationVaporPressure(t), tp, null);
   }

   public static float getWorldTime(Level level, float partialTicks) {
      WeatherHandler weatherHandler;
      if (level.isClientSide) {
         weatherHandler = GameBusClientEvents.weatherHandler;
      } else {
         weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
      }

      return (float)(((double)level.getDayTime() + (double)weatherHandler.seed / 1.0E14) % 4800000.0) + partialTicks;
   }

   public static Vec3 rotatePoint(Vec3 point, Vec3 origin, double angle) {
      Vec3 p = point.subtract(origin);
      double x = p.x * Math.cos(angle) - p.z * Math.sin(angle);
      double z = p.z * Math.cos(angle) + p.x * Math.sin(angle);
      return new Vec3(x + origin.x, point.y, z + origin.z);
   }

   public static Vector2f rotatePoint(Vector2f point, Vector2f origin, double angle) {
      Vector2f p = new Vector2f(point).sub(origin);
      double x = (double)p.x * Math.cos(angle) - (double)p.y * Math.sin(angle);
      double z = (double)p.y * Math.cos(angle) + (double)p.x * Math.sin(angle);
      return new Vector2f((float)x + origin.x, (float)z + p.y);
   }

   public static int getRGBA(Vec3 vec3) {
      return getRGBA(new Color(Mth.clamp((float)vec3.x, 0.0F, 1.0F), Mth.clamp((float)vec3.y, 0.0F, 1.0F), Mth.clamp((float)vec3.z, 0.0F, 1.0F)));
   }

   public static int getRGBA(Color color) {
      return (color.getAlpha() & 0xFF) << 24 | (color.getBlue() & 0xFF) << 16 | (color.getGreen() & 0xFF) << 8 | color.getRed() & 0xFF;
   }

   @Nullable
   public static Vec3 getValidTropicalSystemSpawn(WeatherHandler weatherHandler, Vec3 origin, float area) {
      for (int i = 0; i < 35; i++) {
         Vec3 check = origin.add((double)PMWeather.RANDOM.nextFloat(-area, area), 0.0, (double)PMWeather.RANDOM.nextFloat(-area, area));
         Float sst = ThermodynamicEngine.GetSST(weatherHandler, check, weatherHandler.getWorld(), null, 0);
         if (sst != null && sst > 25.0F) {
            return check;
         }
      }

      return null;
   }

   static {
      float range = 10.0F;

      for (int i = 0; i < MAX_RAIN_DROPS; i++) {
         RAIN_POSITIONS[i] = new Vec3(
            (double)(PMWeather.RANDOM.nextFloat() * range - range / 2.0F),
            (double)(PMWeather.RANDOM.nextFloat() * range - range / 2.0F),
            (double)(PMWeather.RANDOM.nextFloat() * range - range / 2.0F)
         );
      }

      ROCP = 0.28571427F;
   }

   public static record RelativeMotion(Vector3d wind, Vector3d velocity, Vector3d relative) {
      public double getWindspeed() {
         return this.relative.length();
      }

      public double getSignedWindspeed() {
         double dot = this.wind.dot(this.velocity);
         double sign = Math.signum(dot);
         sign = sign == 0.0 ? 1.0 : sign;
         return this.getWindspeed() * sign;
      }
   }
}
