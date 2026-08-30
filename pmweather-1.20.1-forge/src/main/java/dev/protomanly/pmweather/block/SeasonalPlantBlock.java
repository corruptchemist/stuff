package dev.protomanly.pmweather.block;

import com.mojang.serialization.MapCodec;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import foundry.veil.api.util.FastNoiseLite;
import foundry.veil.api.util.FastNoiseLite.CellularReturnType;
import foundry.veil.api.util.FastNoiseLite.NoiseType;
import foundry.veil.api.util.FastNoiseLite.Vector2;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource.LegacyPositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SeasonalPlantBlock extends BushBlock {
   private static SimplexNoise noise = null;
   private static FastNoiseLite fastNoiseLite = null;
   private static double noiseScale = 85.0;
   protected static final VoxelShape[] SHAPES = new VoxelShape[]{
      Block.box(2.0, 0.0, 2.0, 14.0, 5.0, 14.0),
      Block.box(2.0, 0.0, 2.0, 14.0, 8.0, 14.0),
      Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0),
      Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0)
   };
   public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
   public static final BooleanProperty DEAD = BooleanProperty.create("dead");
   public static final MapCodec<SeasonalPlantBlock> CODEC = simpleCodec(SeasonalPlantBlock::new);
   private static final List<SeasonalPlantBlock> PLANTS = new ArrayList<>();

   private static float FBM(double x, double z, int octaves, float lacunarity, float gain, float amplitude) {
      double y = 0.0;

      for (int i = 0; i < Math.max(octaves, 1); i++) {
         y += (double)amplitude * noise.getValue(x, z);
         x *= (double)lacunarity;
         z *= (double)lacunarity;
         amplitude *= gain;
      }

      return (float)y;
   }

   public SeasonalPlantBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.defaultBlockState().setValue(STAGE, 0)).setValue(DEAD, false));
      PLANTS.add(this);
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPES[state.getValue(STAGE)];
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      int stage = (Integer)state.getValue(STAGE);
      if (random.nextInt(10) == 0 || stage == 2) {
         if ((Boolean)state.getValue(DEAD)) {
            if (random.nextInt(5) == 0) {
               setState(state, level, pos, Blocks.AIR.defaultBlockState());
            }
         } else {
            int target = GetTargetStage(state, level, pos);
            if (target >= 0) {
               if (target > stage && stage < 3) {
                  setState(state, level, pos, (BlockState)state.setValue(STAGE, stage + 1));
               }
            } else {
               if (random.nextInt(3) == 0) {
                  setState(state, level, pos, (BlockState)state.setValue(DEAD, true));
               }
            }
         }
      }
   }

   public static SeasonalPlantBlock GetPlantTypeFor(ServerLevel level, BlockPos pos) {
      if (PLANTS.size() <= 1) {
         return PLANTS.getFirst();
      } else {
         if (fastNoiseLite == null) {
            fastNoiseLite = new FastNoiseLite((int)(level.getSeed() / 16L));
         }

         fastNoiseLite.SetFrequency(0.005F);
         fastNoiseLite.SetNoiseType(NoiseType.Cellular);
         fastNoiseLite.SetCellularReturnType(CellularReturnType.CellValue);
         Vector2 npos = new Vector2((float)pos.getX(), (float)pos.getZ());
         fastNoiseLite.SetDomainWarpAmp(150.0F);
         fastNoiseLite.DomainWarp(npos);
         float nval = Mth.clamp(fastNoiseLite.GetNoise(npos.x, npos.y) / 2.0F + 0.5F, 0.0F, 1.0F);
         SeasonalPlantBlock nearest = PLANTS.getFirst();
         float neardif = nval;

         for (int i = 1; i < PLANTS.size(); i++) {
            float val = (float)i / (float)PLANTS.size();
            float dif = Mth.abs(nval - val);
            if (dif < neardif) {
               nearest = PLANTS.get(i);
               neardif = dif;
            }
         }

         return nearest;
      }
   }

   @Nullable
   public static BlockState GetStateFor(SeasonalPlantBlock block, ServerLevel level, BlockPos pos, float temp, @Nullable RandomSource random) {
      int target = GetTargetStage(level, pos, temp);
      if (random != null && random.nextInt(3) > target) {
         return null;
      } else {
         return target < 0 ? null : (BlockState)block.defaultBlockState().setValue(STAGE, target);
      }
   }

   @Nullable
   public static BlockState GetStateFor(SeasonalPlantBlock block, ServerLevel level, BlockPos pos, @Nullable RandomSource random) {
      int target = GetTargetStage(null, level, pos);
      if (random != null && random.nextInt(3) > 0) {
         return null;
      } else if (random != null && random.nextInt(3) <= target) {
         return null;
      } else {
         return target < 0 ? null : (BlockState)block.defaultBlockState().setValue(STAGE, target);
      }
   }

   protected static void setState(BlockState state, ServerLevel level, BlockPos pos, BlockState newState) {
      if (state.getBlock() instanceof SeasonalPlantBlock) {
         level.setBlock(pos, newState, 2, 1);
      }
   }

   public static int GetTargetStage(Level level, BlockPos pos, float temp) {
      RandomSource random = new LegacyPositionalRandomFactory(0L).at(pos);
      if (temp > 75.0F) {
         return -1;
      } else {
         float fTarget = temp >= 35.0F ? 4.0F : 4.0F * (1.0F - Mth.square(temp - 35.0F) / (Mth.square(temp) + 1050.0F));
         if (Float.isNaN(fTarget)) {
            fTarget = -1.0F;
         }

         fTarget = Mth.clamp(fTarget, -0.5F, 3.5F);
         int target = Mth.floor(fTarget);
         if (level instanceof ServerLevel serverLevel) {
            float fert = getSoilFertility(serverLevel, pos.getCenter().x, pos.getCenter().z);
            if (target >= 3 && fert > 0.0F) {
               fert = 1.0F;
            }

            if (random.nextInt(5) == 0) {
               fert = 1.0F;
            }

            fTarget = Math.min(fTarget, fert * 4.0F - 0.5F);
         }

         target = Mth.floor(fTarget);
         if (target == 2 && random.nextInt(3) != 0) {
            target = 3;
         }

         return Mth.clamp(target, -1, 3);
      }
   }

   public static int GetTargetStage(@Nullable BlockState state, Level level, BlockPos pos) {
      if (state != null && !(state.getBlock() instanceof SeasonalPlantBlock)) {
         return -1;
      } else {
         WeatherHandler weatherHandler;
         if (level.isClientSide) {
            weatherHandler = GameBusClientEvents.weatherHandler;
         } else {
            weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
         }

         if (weatherHandler == null) {
            return state != null ? (Integer)state.getValue(STAGE) : -1;
         } else {
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, pos.getCenter(), level, null, 0, pos.getY());
            float temp = dataPoint.temperature();
            return GetTargetStage(level, pos, temp);
         }
      }
   }

   public static float getSoilFertility(ServerLevel level, double x, double z) {
      if (noise == null) {
         noise = new SimplexNoise(new LegacyRandomSource(level.getSeed()));
      }

      double n = (double)FBM(x / noiseScale, z / noiseScale, 6, 2.0F, 0.5F, 1.0F);
      n -= 0.2F;
      n *= 1.25;
      n = Mth.clamp(n, 0.0, 1.0);
      n = Math.pow(n, 0.25);
      return (float)n;
   }

   protected float getMaxHorizontalOffset() {
      return 0.5F;
   }

   protected boolean isRandomlyTicking(BlockState state) {
      return true;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{STAGE});
      builder.add(new Property[]{DEAD});
   }

   public MapCodec<? extends SeasonalPlantBlock> codec() {
      return CODEC;
   }
}
