package dev.protomanly.pmweather.level.updaters;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.SeasonalPlantBlock;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.interfaces.ISnowLayerBlock;
import dev.protomanly.pmweather.level.LevelUpdater;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluids;

public class SeasonalLevelUpdater extends LevelUpdater {
   private final float timeStepPerc = 0.05F;

   public SeasonalLevelUpdater(List<ResourceKey<Level>> validDimensions) {
      super(validDimensions);
   }

   @Override
   public void update(ChunkAccess chunkAccess, long delta) {
      super.update(chunkAccess, delta);
      long d = Math.min(delta, 72000L);
      Level level = chunkAccess.getLevel();
      if (level != null) {
         WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
         if (weatherHandler != null) {
            BlockPos samplePos = chunkAccess.getPos().getMiddleBlockPosition(0);
            samplePos = samplePos.atY(chunkAccess.getHeight(Types.MOTION_BLOCKING, samplePos.getX(), samplePos.getZ()));
            float snowcover = Mth.square(Mth.clamp((float)d / 72000.0F, 0.0F, 1.0F)) * 0.3F;
            float cold = 0.0F;
            float lowestTemp = Float.MAX_VALUE;
            float highestTemp = -Float.MAX_VALUE;
            float watertemp = -Float.MAX_VALUE;

            for (float p = 1.0F; p >= 0.0F; p -= 0.05F) {
               long step = (long)((float)d * p);
               ThermodynamicEngine.AtmosphericDataPoint atmosphericDataPoint = ThermodynamicEngine.samplePoint(
                  weatherHandler, samplePos.getCenter(), level, null, -((int)step), samplePos.getY()
               );
               float curTemp = atmosphericDataPoint.temperature();
               Float sst = ThermodynamicEngine.GetSST(weatherHandler, samplePos.getCenter(), level, null, -((int)step));
               if (sst != null && sst > watertemp) {
                  watertemp = sst;
               }

               lowestTemp = Math.min(lowestTemp, curTemp);
               highestTemp = Math.max(highestTemp, curTemp);
               float precip = weatherHandler.getPrecipitation(samplePos.getCenter(), -((int)step));
               snowcover += precip * 0.05F * 5.0F;
               cold += Mth.clamp((curTemp - 2.0F) / -6.0F, 0.0F, 1.0F) * 0.05F * 5.0F;
               cold *= Mth.square(1.0F - Mth.clamp(curTemp / 3.0F, 0.0F, 1.0F));
               snowcover *= Mth.square(1.0F - Mth.clamp((curTemp + 2.0F) / 6.0F, 0.0F, 1.0F));
            }

            if (lowestTemp < 3.0F) {
               highestTemp = lowestTemp;
            }

            float tempTarg = (highestTemp * 2.0F + lowestTemp) / 3.0F;
            watertemp = Math.max(watertemp, highestTemp);
            cold = Mth.clamp(cold, 0.0F, 1.0F);
            snowcover = Mth.clamp(snowcover, 0.0F, 1.0F);
            float fwatercold = Math.min(1.0F - Mth.clamp((watertemp + 4.0F) / 5.0F, 0.0F, 1.0F), cold);
            int maxLayers = Math.min(ServerConfig.snowAccumulationHeight, 8);
            if (ServerConfig.doGrassGeneration) {
               chunkAccess.findBlocks(
                  state -> state.is(Blocks.GRASS_BLOCK) || state.is(ModBlocks.SCOURED_GRASS),
                  (blockPos, blockstate) -> {
                     BlockPos above = blockPos.above();
                     boolean hasLight = level.getBrightness(LightLayer.SKY, above.above()) > 8 || level.canSeeSky(above.above());
                     if (hasLight && !level.isOutsideBuildHeight(above)) {
                        BlockState state = chunkAccess.getBlockState(above);
                        if (ServerConfig.useSeasonalPlants) {
                           if (state.is(Blocks.GRASS)
                              || state.getBlock() instanceof SeasonalPlantBlock && (Boolean)state.getValue(SeasonalPlantBlock.DEAD)) {
                              level.setBlock(above, Blocks.AIR.defaultBlockState(), 2, 1);
                              return;
                           }

                           if (state.is(Blocks.TALL_GRASS)) {
                              level.setBlock(above, Blocks.AIR.defaultBlockState(), 2, 1);
                              level.setBlock(above.above(), Blocks.AIR.defaultBlockState(), 2, 1);
                              return;
                           }
                        }

                        if (state.isAir() || state.getBlock() instanceof SeasonalPlantBlock) {
                           SeasonalPlantBlock plantBlock = SeasonalPlantBlock.GetPlantTypeFor((ServerLevel)level, blockPos);
                           BlockState plantState = SeasonalPlantBlock.GetStateFor(plantBlock, (ServerLevel)level, above, tempTarg, level.random);
                           if (!ServerConfig.useSeasonalPlants && plantState != null) {
                              plantState = level.random.nextInt(8) == 0 ? Blocks.TALL_GRASS.defaultBlockState() : Blocks.GRASS.defaultBlockState();
                           }

                           if (ServerConfig.useSeasonalPlants && state.getBlock() instanceof SeasonalPlantBlock) {
                              if (plantState == null) {
                                 level.setBlock(above, Blocks.AIR.defaultBlockState(), 2, 1);
                              } else {
                                 level.setBlock(above, plantState, 2, 1);
                              }
                           } else if (plantState != null) {
                              if (plantState.getBlock() instanceof DoublePlantBlock) {
                                 DoublePlantBlock.placeAt(level, plantState, above, 2);
                              } else {
                                 level.setBlock(above, plantState, 2, 1);
                              }
                           }
                        }
                     }
                  }
               );
            }

            if (ServerConfig.doSeasonalLevelUpdater) {
               this.onSurface(chunkAccess, true, (blockPos, blockstate) -> {
                  float rand = level.random.nextFloat() * 0.3F + 0.85F;
                  int layers = Math.min(Mth.ceil((float)maxLayers * snowcover * rand), 8);
                  if (!blockstate.isAir() && !(blockstate.getBlock() instanceof SeasonalPlantBlock)) {
                     if (blockstate.is(Blocks.ICE)) {
                        if (level.random.nextFloat() >= fwatercold + 0.25F) {
                           level.setBlock(blockPos, Blocks.WATER.defaultBlockState(), 2, 1);
                        }
                     } else if (!blockstate.getFluidState().isEmpty() && blockstate.getFluidState().isSourceOfType(Fluids.WATER)) {
                        if (level.random.nextFloat() * 1.25F < fwatercold) {
                           level.setBlock(blockPos, Blocks.ICE.defaultBlockState(), 2, 1);
                        }
                     } else {
                        if (blockstate.is(Blocks.SNOW)) {
                           if (layers == 0) {
                              level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 2, 1);
                              return;
                           }

                           if (layers == (Integer)blockstate.getValue(SnowLayerBlock.LAYERS)) {
                              return;
                           }

                           BlockState blockstate1 = (BlockState)blockstate.setValue(SnowLayerBlock.LAYERS, layers);
                           Block.pushEntitiesUp(blockstate, blockstate1, level, blockPos);
                           level.setBlock(blockPos, blockstate1, 2, 1);
                        }
                     }
                  } else if (layers != 0) {
                     if (Blocks.SNOW instanceof ISnowLayerBlock iSnowLayerBlock) {
                        if (iSnowLayerBlock.survives(blockstate, level, blockPos)) {
                           level.setBlock(blockPos, (BlockState)Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2, 1);
                        }
                     }
                  }
               });
            }
         }
      }
   }

   @Override
   protected long minDeltaForChange() {
      return 12500L;
   }

   @Override
   protected boolean canChange() {
      return ServerConfig.doSeasonalLevelUpdater;
   }
}
