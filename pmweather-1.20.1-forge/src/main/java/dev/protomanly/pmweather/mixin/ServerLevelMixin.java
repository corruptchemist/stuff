package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.IcicleBlock;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.SeasonalPlantBlock;
import dev.protomanly.pmweather.block.interfaces.BurningBlockInterface;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.interfaces.IServerLevel;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerLevel.class})
public class ServerLevelMixin implements BurningBlockInterface, IServerLevel {
   public ServerLevelMixin() {
   }

   @Shadow
   private Optional<BlockPos> findLightningRod(BlockPos pos) {
      throw new AssertionError();
   }

   @Inject(
      method = {"tickChunk"},
      at = {@At("TAIL")}
   )
   public void editTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo callbackInfo) {
      Block fireBlock = (Block)ModBlocks.FIRE.get();
      ServerLevel self = (ServerLevel)this;
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(self.dimension());
      ChunkPos chunkpos = chunk.getPos();
      WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(self.dimension());
      int i = chunkpos.getMinBlockX();
      int j = chunkpos.getMinBlockZ();
      if (randomTickSpeed > 0) {
         LevelChunkSection[] alevelchunksection = chunk.getSections();
         if (weatherHandler != null && ServerConfig.doWildfires) {
            Tuple<Float, Float> fireData = ThermodynamicEngine.getFireTemperature(self, chunk, chunkpos.getMiddleBlockPosition(256).getCenter(), 0);
            float temperature = (Float)fireData.getA();
            if (temperature >= 100.0F) {
               for (int j1 = 0; j1 < alevelchunksection.length; j1++) {
                  LevelChunkSection levelchunksection = alevelchunksection[j1];
                  if (!levelchunksection.hasOnlyAir()) {
                     int k1 = chunk.getSectionYFromSectionIndex(j1);
                     int k = SectionPos.sectionToBlockCoord(k1);

                     for (int l = 0; l < randomTickSpeed * 15; l++) {
                        BlockPos blockpos1 = self.getBlockRandomPos(i, k, j, 15);
                        int y = self.getHeight(Types.WORLD_SURFACE_WG, blockpos1.getX(), blockpos1.getZ()) - 8;
                        if (blockpos1.getY() > y - 8) {
                           BlockState blockstate = levelchunksection.getBlockState(blockpos1.getX() - i, blockpos1.getY() - k, blockpos1.getZ() - j);
                           if (this.doThermalShock(blockstate)) {
                              self.destroyBlock(blockpos1, false);
                           } else {
                              int chance = temperature > 500.0F ? 2 : 6;
                              if (temperature > 300.0F && self.random.nextInt(chance) == 1) {
                                 this.doScorching(blockstate, blockpos1, self);
                                 this.spread(null, null, blockstate, blockpos1, self);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         int mult = 15;
         if ((Float)chunk.getData(DataAttachments.STABLE_FIRE_INTENSITY) <= 0.1F) {
            mult = 5;
         }

         int chunkH = self.getHeight(Types.WORLD_SURFACE_WG, i, j);

         for (int j1x = 0; j1x < alevelchunksection.length; j1x++) {
            LevelChunkSection levelchunksection = alevelchunksection[j1x];
            if (!levelchunksection.hasOnlyAir()) {
               int k1 = chunk.getSectionYFromSectionIndex(j1x);
               int k = SectionPos.sectionToBlockCoord(k1);
               int m = mult;
               if (k > chunkH - 16) {
                  m = 15;
               }

               int logRotChance = 800;
               if (!levelchunksection.isRandomlyTicking()) {
                  logRotChance /= 15;
               }

               int grassSpawnChance = 150;
               if (!levelchunksection.isRandomlyTicking()) {
                  grassSpawnChance /= 15;
               }

               int icicleSpawnChance = 150;
               if (!levelchunksection.isRandomlyTicking()) {
                  icicleSpawnChance /= 15;
               }

               for (int lx = 0; lx < randomTickSpeed * m; lx++) {
                  boolean valid = levelchunksection.isRandomlyTicking() || self.random.nextInt(m) == 0;
                  if (valid) {
                     BlockPos blockpos1 = self.getBlockRandomPos(i, k, j, 15);
                     BlockState blockstate = levelchunksection.getBlockState(blockpos1.getX() - i, blockpos1.getY() - k, blockpos1.getZ() - j);
                     boolean isFire = blockstate.is(fireBlock);
                     if (blockstate.isRandomlyTicking() && isFire) {
                        blockstate.randomTick(self, blockpos1, self.random);
                     }

                     Block aState = blockstate.getBlock();
                     if (aState instanceof BurningBlockInterface) {
                        BurningBlockInterface burningBlockInterface = (BurningBlockInterface)aState;
                        if (!isFire) {
                           burningBlockInterface.doExtinguishLogic(blockstate, self, blockpos1, self.random);
                           continue;
                        }
                     }

                     if (self.random.nextInt(logRotChance) == 0 && blockstate.is(BlockTags.LOGS) && !reinforcementManager.isPlayerPlaced(blockpos1)) {
                        Util.checkLogs(blockstate, self, blockpos1);
                     } else {
                        if (self.random.nextInt(75) == 0) {
                           if (blockstate.is(Blocks.SNOW) || blockstate.is(ModBlocks.SLEET_LAYER) || blockstate.is(ModBlocks.ICE_LAYER)) {
                              ThermodynamicEngine.AtmosphericDataPoint data = ThermodynamicEngine.samplePoint(
                                 weatherHandler, blockpos1.getCenter(), self, null, 0
                              );
                              int c = 50;
                              if (data.temperature() > 2.0F) {
                                 c = 30;
                              }

                              if (data.temperature() > 5.0F) {
                                 c = 20;
                              }

                              if (data.temperature() > 10.0F) {
                                 c = 10;
                              }

                              if (levelchunksection.isRandomlyTicking()) {
                                 c /= 15;
                              }

                              int decrease = 1;
                              if (data.temperature() > 8.0F) {
                                 decrease = 2;
                              }

                              if (data.temperature() > 10.0F) {
                                 decrease = 3;
                              }

                              if (data.temperature() > 0.0F && self.random.nextInt(c + 1) == 0) {
                                 int h = (Integer)blockstate.getValue(SnowLayerBlock.LAYERS);
                                 if (h > decrease) {
                                    BlockState blockstate1 = (BlockState)blockstate.setValue(SnowLayerBlock.LAYERS, h - decrease);
                                    Block.pushEntitiesUp(blockstate, blockstate1, self, blockpos1);
                                    self.setBlockAndUpdate(blockpos1, blockstate1);
                                 } else {
                                    self.removeBlock(blockpos1, false);
                                 }
                              }
                              continue;
                           }

                           if (blockstate.is(Blocks.ICE)) {
                              ThermodynamicEngine.AtmosphericDataPoint datax = ThermodynamicEngine.samplePoint(
                                 weatherHandler, blockpos1.getCenter(), self, null, 0
                              );
                              Float sst = ThermodynamicEngine.GetSST(weatherHandler, blockpos1.getCenter(), self, null, 0);
                              float temp = datax.temperature();
                              if (sst != null) {
                                 temp = sst;
                              }

                              int cx = 50;
                              if (temp > 2.0F) {
                                 cx = 30;
                              }

                              if (temp > 5.0F) {
                                 cx = 20;
                              }

                              if (temp > 10.0F) {
                                 cx = 10;
                              }

                              if (levelchunksection.isRandomlyTicking()) {
                                 cx /= 15;
                              }

                              if (temp > 0.0F && self.random.nextInt(cx + 1) == 0) {
                                 self.setBlockAndUpdate(blockpos1, Blocks.WATER.defaultBlockState());
                              }
                              continue;
                           }
                        }

                        if (ServerConfig.doIcicles
                           && self.random.nextInt(icicleSpawnChance) == 0
                           && !blockstate.is(ModBlocks.ICICLE)
                           && !self.isOutsideBuildHeight(blockpos1.below())
                           && self.getBrightness(LightLayer.SKY, blockpos1.below()) > 13
                           && self.getBlockState(blockpos1.below()).isAir()) {
                           BlockState icicleState = (BlockState)((IcicleBlock)ModBlocks.ICICLE.get())
                              .defaultBlockState()
                              .setValue(IcicleBlock.DIRECTION, Direction.DOWN);
                           if (IcicleBlock.isValidIciclePlacement(self, blockpos1.below(), Direction.DOWN)
                              && ((IcicleBlock)ModBlocks.ICICLE.get()).canGrow(self, blockpos1.below(), icicleState, null)) {
                              self.setBlockAndUpdate(blockpos1.below(), icicleState);
                           }
                        }

                        if (ServerConfig.useSeasonalPlants
                           && blockstate.is(Blocks.GRASS_BLOCK)
                           && self.getBrightness(LightLayer.SKY, blockpos1.above()) > 8
                           && !self.isOutsideBuildHeight(blockpos1.above())
                           && self.random.nextInt(grassSpawnChance) == 0) {
                           BlockPos aPos = blockpos1.above();
                           BlockState aStatex = self.getBlockState(aPos);
                           if (aStatex.isAir()) {
                              int target = SeasonalPlantBlock.GetTargetStage(null, self, aPos);
                              if (target >= 0) {
                                 SeasonalPlantBlock plantBlock = SeasonalPlantBlock.GetPlantTypeFor(self, aPos);
                                 self.setBlockAndUpdate(aPos, plantBlock.defaultBlockState());
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean shouldFreeze(ServerLevel level, BlockPos water, boolean mustBeAtEdge) {
      if (water.getY() >= level.getMinBuildHeight() && water.getY() < level.getMaxBuildHeight() && level.getBrightness(LightLayer.BLOCK, water) < 10) {
         BlockState blockstate = level.getBlockState(water);
         FluidState fluidstate = level.getFluidState(water);
         if (fluidstate.getType() == Fluids.WATER && blockstate.getBlock() instanceof LiquidBlock) {
            if (!mustBeAtEdge) {
               return true;
            }

            boolean flag = level.isWaterAt(water.west()) && level.isWaterAt(water.east()) && level.isWaterAt(water.north()) && level.isWaterAt(water.south());
            if (!flag) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean shouldSnow(ServerLevel level, BlockPos pos) {
      if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
         BlockState blockstate = level.getBlockState(pos);
         if ((blockstate.isAir() || blockstate.is(Blocks.SNOW) || blockstate.getBlock() instanceof SeasonalPlantBlock)
            && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
            return true;
         }
      }

      return false;
   }

   public boolean shouldIce(ServerLevel level, BlockPos pos) {
      if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
         BlockState blockstate = level.getBlockState(pos);
         if ((blockstate.isAir() || blockstate.is((Block)ModBlocks.ICE_LAYER.get()) || blockstate.getBlock() instanceof SeasonalPlantBlock)
            && ((Block)ModBlocks.ICE_LAYER.get()).defaultBlockState().canSurvive(level, pos)) {
            return true;
         }
      }

      return false;
   }

   public boolean shouldSleet(ServerLevel level, BlockPos pos) {
      if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
         BlockState blockstate = level.getBlockState(pos);
         if ((blockstate.isAir() || blockstate.is((Block)ModBlocks.SLEET_LAYER.get()) || blockstate.getBlock() instanceof SeasonalPlantBlock)
            && ((Block)ModBlocks.SLEET_LAYER.get()).defaultBlockState().canSurvive(level, pos)) {
            return true;
         }
      }

      return false;
   }

   @Inject(
      method = {"tickPrecipitation"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editTickPrecipitation(BlockPos blockPos, CallbackInfo callbackInfo) {
      ServerLevel level = (ServerLevel)this;
      BlockPos blockpos = level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos);
      BlockPos blockpos1 = blockpos.below();
      WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
      ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, blockpos.getCenter(), level, null, 0);
      float rain = weatherHandler.getPrecipitation(blockpos.getCenter());
      BlockState blockstate = level.getBlockState(blockpos);
      BlockState blockState1 = level.getBlockState(blockpos1);
      float waterTemp = dataPoint.temperature();
      Float sst = ThermodynamicEngine.GetSST(weatherHandler, blockpos1.getCenter(), level, null, 0);
      if (sst != null) {
         waterTemp = sst;
      }

      if (level.isAreaLoaded(blockpos1, 1) && waterTemp <= 0.0F && this.shouldFreeze(level, blockpos1, true)) {
         level.setBlockAndUpdate(blockpos1, Blocks.ICE.defaultBlockState());
      }

      int c = 12;
      if (dataPoint.temperature() > 2.0F) {
         c = 8;
      }

      if (dataPoint.temperature() > 4.0F) {
         c = 4;
      }

      if (dataPoint.temperature() > 6.0F) {
         c = 1;
      }

      int decrease = 1;
      if (dataPoint.temperature() > 8.0F) {
         decrease = 2;
      }

      if (dataPoint.temperature() > 10.0F) {
         decrease = 3;
      }

      if ((blockstate.is(Blocks.SNOW) || blockstate.is((Block)ModBlocks.SLEET_LAYER.get()) || blockstate.is((Block)ModBlocks.ICE_LAYER.get()))
         && dataPoint.temperature() > 0.0F
         && PMWeather.RANDOM.nextInt(c) == 0) {
         int j = (Integer)blockstate.getValue(SnowLayerBlock.LAYERS);
         if (j > decrease) {
            BlockState blockstate1 = (BlockState)blockstate.setValue(SnowLayerBlock.LAYERS, j - decrease);
            Block.pushEntitiesUp(blockstate, blockstate1, level, blockpos);
            level.setBlockAndUpdate(blockpos, blockstate1);
         } else {
            level.removeBlock(blockpos, false);
         }
      }

      if (blockState1.is(Blocks.ICE) && waterTemp > 0.0F && PMWeather.RANDOM.nextInt(c) == 0) {
         level.setBlockAndUpdate(blockpos1, Blocks.WATER.defaultBlockState());
         callbackInfo.cancel();
      }

      if (blockState1.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
         callbackInfo.cancel();
      }

      if (!blockState1.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON) && Block.isFaceFull(blockState1.getCollisionShape(level, blockpos1), Direction.UP)) {
         callbackInfo.cancel();
      }

      if (rain > 0.05F) {
         int i = ServerConfig.snowAccumulationHeight;
         ThermodynamicEngine.Precipitation precip = ThermodynamicEngine.getPrecipitationType(weatherHandler, blockpos.getCenter(), level, 500);
         boolean smoothPrecip = true;
         if (precip == ThermodynamicEngine.Precipitation.WINTRY_MIX) {
            smoothPrecip = false;

            precip = switch (PMWeather.RANDOM.nextInt(3)) {
               case 0 -> ThermodynamicEngine.Precipitation.SNOW;
               case 1 -> ThermodynamicEngine.Precipitation.SLEET;
               case 2 -> ThermodynamicEngine.Precipitation.FREEZING_RAIN;
               default -> ThermodynamicEngine.Precipitation.WINTRY_MIX;
            };
         }

         if (precip == ThermodynamicEngine.Precipitation.SNOW) {
            BlockState blockstateW = level.getBlockState(blockpos);
            if (smoothPrecip && (blockstateW.is(ModBlocks.SLEET_LAYER) || blockstateW.is(ModBlocks.ICE_LAYER))) {
               blockstateW = (BlockState)Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, (Integer)blockstateW.getValue(SnowLayerBlock.LAYERS));
               level.setBlockAndUpdate(blockpos, blockstateW);
            }

            if (i > 0 && this.shouldSnow(level, blockpos)) {
               if (blockstateW.is(Blocks.SNOW)) {
                  int j = (Integer)blockstateW.getValue(SnowLayerBlock.LAYERS);
                  if (j < Math.min(i, 8)) {
                     BlockState blockstate1 = (BlockState)blockstateW.setValue(SnowLayerBlock.LAYERS, j + 1);
                     Block.pushEntitiesUp(blockstateW, blockstate1, level, blockpos);
                     level.setBlockAndUpdate(blockpos, blockstate1);
                  }
               } else {
                  level.setBlockAndUpdate(blockpos, Blocks.SNOW.defaultBlockState());
               }
            }
         }

         if (precip == ThermodynamicEngine.Precipitation.SLEET && i > 0 && this.shouldSleet(level, blockpos)) {
            BlockState blockstateWx = level.getBlockState(blockpos);
            if (blockstateWx.is((Block)ModBlocks.SLEET_LAYER.get())) {
               int j = (Integer)blockstateWx.getValue(SnowLayerBlock.LAYERS);
               if (j < Math.min(i, 8)) {
                  BlockState blockstate1 = (BlockState)blockstateWx.setValue(SnowLayerBlock.LAYERS, j + 1);
                  Block.pushEntitiesUp(blockstateWx, blockstate1, level, blockpos);
                  level.setBlockAndUpdate(blockpos, blockstate1);
               }
            } else {
               level.setBlockAndUpdate(blockpos, ((Block)ModBlocks.SLEET_LAYER.get()).defaultBlockState());
            }
         }

         if (precip == ThermodynamicEngine.Precipitation.FREEZING_RAIN && i > 0 && this.shouldIce(level, blockpos)) {
            BlockState blockstateWx = level.getBlockState(blockpos);
            if (blockstateWx.is((Block)ModBlocks.ICE_LAYER.get())) {
               int j = (Integer)blockstateWx.getValue(SnowLayerBlock.LAYERS);
               if (j < Math.min(i, 8) && PMWeather.RANDOM.nextInt(3) == 0) {
                  BlockState blockstate1 = (BlockState)blockstateWx.setValue(SnowLayerBlock.LAYERS, j + 1);
                  Block.pushEntitiesUp(blockstateWx, blockstate1, level, blockpos);
                  level.setBlockAndUpdate(blockpos, blockstate1);
               }
            } else {
               level.setBlockAndUpdate(blockpos, ((Block)ModBlocks.ICE_LAYER.get()).defaultBlockState());
            }
         }

         Precipitation biome$precipitation = Precipitation.NONE;
         if (rain > 0.1F) {
            biome$precipitation = Precipitation.RAIN;
            if (dataPoint.temperature() <= 0.0F) {
               biome$precipitation = Precipitation.SNOW;
            }
         }

         if (biome$precipitation != Precipitation.NONE) {
            BlockState blockstate2 = level.getBlockState(blockpos1);
            blockstate2.getBlock().handlePrecipitation(blockstate2, level, blockpos1, biome$precipitation);
         }
      }

      callbackInfo.cancel();
   }

   @Override
   public int getBurnChance() {
      return 0;
   }

   @Override
   public int getSpreadChance() {
      return 0;
   }

   @Override
   public Block getBurnsInto() {
      return null;
   }

   @Override
   public Optional<BlockPos> pmwfindLightningRod(BlockPos blockPos) {
      return this.findLightningRod(blockPos);
   }
}
