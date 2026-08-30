package dev.protomanly.pmweather.event;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.WeatherStationBlock;
import dev.protomanly.pmweather.command.WeatherCommands;
import dev.protomanly.pmweather.compat.sable.SableHandler;
import dev.protomanly.pmweather.compat.sable.SableMod;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.BlockDataHandler;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.entity.ModEntities;
import dev.protomanly.pmweather.entity.MovingBlock;
import dev.protomanly.pmweather.interfaces.ChunkMapMixinInterface;
import dev.protomanly.pmweather.interfaces.ConditionallyPulled;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.level.ChunkLoading;
import dev.protomanly.pmweather.level.LevelUpdateManager;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.seasons.MoistureHandler;
import dev.protomanly.pmweather.tags.ModTags;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Clouds;
import dev.protomanly.pmweather.weather.Sounding;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormTypeRegisterEvent;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent;
import net.neoforged.neoforge.event.level.LevelEvent.Load;
import net.neoforged.neoforge.event.level.LevelEvent.Unload;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import org.joml.Vector2f;

@EventBusSubscriber(
   modid = "pmweather"
)
public class GameBusEvents {
   public static final Map<ResourceKey<Level>, WeatherHandler> MANAGERS = new Reference2ObjectOpenHashMap();
   public static final Map<ResourceKey<Level>, ReinforcementManager> REINFORCEMENTMANAGERS = new Reference2ObjectOpenHashMap();
   public static final Map<String, WeatherHandler> MANAGERSLOOKUP = new HashMap<>();
   public static final long TimeSinceLastTickToReset = 120000L;
   public static boolean isStoppingServer = false;
   private static long lastTick = 0L;
   public static long ServerTicks = 0L;

   public GameBusEvents() {
   }

   @SubscribeEvent
   public static void onBlockDamaged(BlockDamageEvent event) {
      Level level = event.getLevel();
      if (!level.isClientSide) {
         if (level.random.nextInt(8) == 0) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            if (state.is(ModTags.Blocks.POWERFLASHES)) {
               if (!state.is(Blocks.REDSTONE_LAMP) || (Boolean)state.getValue(RedstoneLampBlock.LIT)) {
                  if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON) || (Boolean)state.getValue(PistonBaseBlock.EXTENDED)) {
                     double voltage = Mth.square(level.random.nextDouble()) * 1500.0;
                     voltage += 60.0;
                     ((WeatherHandlerServer)MANAGERS.get(level.dimension())).syncPowerFlashNew(pos.getCenter(), (float)voltage);
                  }
               }
            } else {
               int energy = -1;
               IEnergyStorage capability = (IEnergyStorage)level.getCapability(EnergyStorage.BLOCK, pos, state, null, null);
               if (capability != null) {
                  energy = Math.max(energy, capability.getEnergyStored());
               }

               if (energy > 0) {
                  double voltage = Math.sin((Mth.clamp((double)energy / 4500000.0, 0.0, 1.0) - 0.5) * Math.PI) + 1.0;
                  voltage /= 2.0;
                  voltage = Math.pow(voltage, 0.5) * 15000.0;
                  if (voltage > 50.0) {
                     ((WeatherHandlerServer)MANAGERS.get(level.dimension())).syncPowerFlashNew(pos.getCenter(), (float)voltage);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEntityDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof Cow cow && cow.level().random.nextInt(10000000) == 0) {
         cow.spawnAtLocation(ModItems.APPLE);
      }
   }

   @SubscribeEvent
   public static void onServerLoad(ServerStartingEvent event) {
      isStoppingServer = false;
      ((WeatherStationBlock)ModBlocks.WEATHER_STATION.get()).lastInteractions = new HashMap<>();
      ((WeatherStationBlock)ModBlocks.WEATHER_STATION.get()).riskCache = new HashMap<>();
   }

   @SubscribeEvent
   public static void onServerStop(ServerStoppingEvent event) {
      PMWeather.LOGGER.info("Cleaning storm objects properly");
      isStoppingServer = true;
   }

   @SubscribeEvent
   public static void onMultiBlockPlace(EntityMultiPlaceEvent event) {
      if (event.getEntity() instanceof Player player && !event.getPlacedBlock().is(BlockTags.SAPLINGS)) {
         Level level = player.level();
         BlockPos pos = event.getPos();
         ReinforcementManager reinforcementManager = REINFORCEMENTMANAGERS.get(level.dimension());
         if (reinforcementManager != null) {
            for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
               reinforcementManager.markPlayerPlaced(snapshot.getPos());
            }

            reinforcementManager.markPlayerPlaced(pos);
         }
      }
   }

   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      if (event.getEntity() instanceof Player player && !event.getPlacedBlock().is(BlockTags.SAPLINGS)) {
         Level level = player.level();
         BlockPos pos = event.getPos();
         ReinforcementManager reinforcementManager = REINFORCEMENTMANAGERS.get(level.dimension());
         if (reinforcementManager != null) {
            reinforcementManager.markPlayerPlaced(pos);
         }
      }
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      Player player = event.getPlayer();
      Level level = player.level();
      BlockPos pos = event.getPos();
      BlockState state = event.getState();
      ReinforcementManager reinforcementManager = REINFORCEMENTMANAGERS.get(level.dimension());
      if (reinforcementManager != null) {
         if (reinforcementManager.isReinforced(pos)) {
            reinforcementManager.removeReinforcement(pos, !player.isCreative());
         }

         reinforcementManager.unmarkPlayerPlaced(pos);
      }
   }

   @SubscribeEvent
   public static void onEntityTick(Post event) {
      Entity entity = event.getEntity();
      Level level = entity.level();
      if (!level.isClientSide()) {
         WeatherHandler weatherHandler = MANAGERS.get(level.dimension());
         if (entity instanceof LivingEntity livingEntity && weatherHandler != null && livingEntity.tickCount % 20 == 0) {
            Tuple<Float, Float> sample = ThermodynamicEngine.getFireTemperature(
               level, level.getChunk(livingEntity.blockPosition()), livingEntity.getEyePosition(), null
            );
            if ((Float)sample.getA() > 95.0F) {
               float over = (Float)sample.getA() - 95.0F;
               int damage = Mth.ceil(over / 20.0F);
               damage = Math.min(damage, 10);
               livingEntity.hurt(level.damageSources().inFire(), (float)damage);
            }
         }

         if (entity instanceof ItemEntity itemEntity && weatherHandler != null && !itemEntity.fireImmune()) {
            Tuple<Float, Float> sample = ThermodynamicEngine.getFireTemperature(level, level.getChunk(itemEntity.blockPosition()), itemEntity.position(), null);
            if ((Float)sample.getA() > 115.0F) {
               itemEntity.igniteForTicks(10);
               if (itemEntity.tickCount % 8 == 0) {
                  itemEntity.hurt(level.damageSources().inFire(), 2.0F);
               }
            }
         }

         if (entity instanceof MovingBlock) {
            Vec3 wind = WindEngine.getWind(entity.getEyePosition(1.0F), level, false, true, false);
            entity.addDeltaMovement(wind.multiply(0.05F, 0.0, 0.05F).multiply(0.01F, 0.0, 0.01F));
         }

         if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            if (entity instanceof ConditionallyPulled conditionallyPulled) {
               if (!conditionallyPulled.shouldPush()) {
                  return;
               }

               if (conditionallyPulled.onPush()) {
                  return;
               }
            }

            Vec3 wind = WindEngine.getWind(entity.getEyePosition(1.0F), level, false, true, false);
            if (player.isCrouching()) {
               wind = wind.scale(0.7);
            }

            if (wind.length() > 60.0) {
               double factor = Mth.lerp(Math.clamp(wind.length() / 125.0, 0.0, 1.0), 0.005, 0.02);
               float mult = 0.65F;
               if (!entity.onGround()) {
                  mult = 0.075F;
               }

               entity.addDeltaMovement(wind.multiply(0.05F, 0.0, 0.05F).multiply(factor, 0.0, factor).multiply((double)mult, (double)mult, (double)mult));
            }
         }

         if (entity instanceof LivingEntity && !(entity instanceof Player)) {
            if (entity instanceof ConditionallyPulled conditionallyPulled) {
               if (!conditionallyPulled.shouldPush()) {
                  return;
               }

               if (conditionallyPulled.onPush()) {
                  return;
               }
            }

            Vec3 windx = WindEngine.getWind(entity.getEyePosition(1.0F), level, false, true, false);
            if (windx.length() > 60.0) {
               double factor = Mth.lerp(Math.clamp(windx.length() / 125.0, 0.0, 1.0), 0.005, 0.02);
               float mult = 0.65F;
               if (!entity.onGround()) {
                  mult = 0.075F;
               }

               entity.addDeltaMovement(windx.multiply(0.05F, 0.0, 0.05F).multiply(factor, 0.0, factor).multiply((double)mult, (double)mult, (double)mult));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
      Level level = event.getLevel();
      if (!level.isClientSide() && level.dimension() == Level.OVERWORLD) {
         LevelUpdateManager.NewTick();
      }

      if (level instanceof ServerLevel serverLevel) {
         if (level.getServer().tickRateManager().runsNormally()) {
            WeatherHandler weatherHandler = MANAGERS.get(level.dimension());
            weatherHandler.tick();
         }

         if (level.getGameTime() % 60L == 1L) {
            ChunkLoading.update(serverLevel);
         }

         if (level.getGameTime() % 10L == 0L) {
            long divTick = level.getGameTime() / 10L;
            if (serverLevel.getChunkSource().chunkMap instanceof ChunkMapMixinInterface chunkMapMixinInterface) {
               for (ChunkHolder chunk : chunkMapMixinInterface.PMWGetLoadedChunks()) {
                  if (chunk.getTicketLevel() <= 33) {
                     ChunkAccess chunkAccess = chunk.getLatestChunk();
                     if (chunkAccess != null) {
                        long checkAt = divTick + chunkAccess.getPos().toLong();
                        if (checkAt % 8L == 0L) {
                           BlockDataHandler blockDataHandler = (BlockDataHandler)chunkAccess.getData(DataAttachments.BLOCK_DATA);
                           blockDataHandler.update(serverLevel, chunkAccess);
                        }
                     }
                  }
               }
            }
         }
      }

      if (level.dimension() == Level.OVERWORLD && level instanceof ServerLevel serverLevel) {
         ServerTicks++;
         if (ServerTicks % 20L == 0L) {
            WindEngine.FireIntensityCache.clear();
         }

         if (ServerTicks % 6000L == 0L) {
            ((WeatherStationBlock)ModBlocks.WEATHER_STATION.get()).riskCache = new HashMap<>();
         }

         if (level.getGameTime() < lastTick) {
            lastTick = 0L;
         }

         if (level.getGameTime() - lastTick > 20L) {
            lastTick = level.getGameTime();
            WeatherHandlerServer weatherHandler = (WeatherHandlerServer)MANAGERS.get(level.dimension());
            if (serverLevel.getChunkSource().chunkMap instanceof ChunkMapMixinInterface chunkMapMixinInterface) {
               for (ChunkHolder chunkx : chunkMapMixinInterface.PMWGetLoadedChunks()) {
                  if (chunkx.getTicketLevel() <= 33) {
                     ChunkAccess chunkAccess = chunkx.getLatestChunk();
                     if (chunkAccess != null && chunkAccess.hasData(DataAttachments.MOISTURE)) {
                        BlockPos pos = chunkAccess.getPos().getMiddleBlockPosition((int)ServerConfig.layer0Height);
                        float thunder = Clouds.getCloudDensity(weatherHandler, new Vector2f((float)pos.getX(), (float)pos.getZ()), 0.0F, true);
                        if (serverLevel.random.nextFloat() * 2500.0F < thunder) {
                           BlockPos lPos = pos.offset(serverLevel.random.nextInt(16) - 8, 0, serverLevel.random.nextInt(16) - 8);
                           int height = level.getHeightmapPos(Types.MOTION_BLOCKING, lPos).getY();
                           weatherHandler.syncLightningNew(new Vec3((double)lPos.getX(), (double)height, (double)lPos.getZ()));
                        }

                        long lastLoaded = (Long)chunkAccess.getData(DataAttachments.LAST_TICK);
                        float fireIntensity = (Float)chunkAccess.getData(DataAttachments.FIRE_INTENSITY);
                        float fireAftermath = (Float)chunkAccess.getData(DataAttachments.FIRE_AFTERMATH);
                        long timeSinceLastLoaded = level.getGameTime() - lastLoaded;
                        if (fireIntensity > fireAftermath) {
                           fireAftermath = fireIntensity;
                        }

                        float removal = fireIntensity * 0.05F;
                        removal += 0.01F;
                        fireIntensity -= removal;
                        fireAftermath *= 0.95F;
                        if (fireIntensity < 0.05F) {
                           fireIntensity = 0.0F;
                        }

                        if (fireAftermath < 0.25F) {
                           fireAftermath = 0.0F;
                        }

                        lastLoaded = level.getGameTime();
                        if (timeSinceLastLoaded >= 120000L) {
                           fireAftermath = 0.0F;
                        }

                        chunkAccess.setData(DataAttachments.FIRE_INTENSITY, fireIntensity);
                        chunkAccess.setData(DataAttachments.STABLE_FIRE_INTENSITY, fireIntensity);
                        chunkAccess.setData(DataAttachments.FIRE_AFTERMATH, fireAftermath);
                        chunkAccess.setData(DataAttachments.LAST_TICK, lastLoaded);
                        float precip = weatherHandler.getPrecipitation(chunkAccess.getPos().getMiddleBlockPosition(65).getCenter());
                        precip -= 0.075F;
                        if (precip > 0.0F) {
                           precip *= 6.0F;
                        } else {
                           precip *= 2.0F;
                        }

                        precip /= 30.0F;
                        float moisture = (Float)chunkAccess.getData(DataAttachments.MOISTURE) + precip;
                        moisture -= fireIntensity / 50.0F;
                        if (timeSinceLastLoaded >= 120000L && fireIntensity < 5.0F) {
                           moisture = MoistureHandler.getStartMoisture(level, chunkAccess);
                        }

                        MoistureHandler.setMoistureOnChunk(level, chunkAccess, Mth.clamp(moisture, 0.0F, 100.0F));
                     }
                  }
               }
            }
         }
      }

      if (level instanceof ServerLevel serverLevel && serverLevel.getChunkSource().chunkMap instanceof ChunkMapMixinInterface chunkMapMixinInterface) {
         for (ChunkHolder chunkxx : chunkMapMixinInterface.PMWGetLoadedChunks()) {
            if (chunkxx.getTicketLevel() <= 33) {
               ChunkAccess chunkAccess = chunkxx.getLatestChunk();
               if (chunkAccess != null && chunkAccess.hasData(DataAttachments.MOISTURE)) {
                  LevelUpdateManager.tick(chunkAccess);
               }
            }
         }
      }

      if (!level.isClientSide()
         && ServerConfig.validDimensions != null
         && level instanceof ServerLevel serverLevel
         && ServerConfig.validDimensions.contains(level.dimension())) {
         WeatherHandlerServer weatherHandler = (WeatherHandlerServer)MANAGERS.get(level.dimension());
         ReinforcementManager reinforcementManager = REINFORCEMENTMANAGERS.get(level.dimension());
         SableHandler sableHandler = SableMod.getHandler();
         if (sableHandler != null) {
            sableHandler.tick(serverLevel);
         }

         if (PMWeather.RANDOM.nextInt(2) == 0 && ServerConfig.doDamage) {
            List<ServerPlayer> validPlayers = new ArrayList<>();
            List<ServerPlayer> plrs = serverLevel.players();
            Collections.shuffle(plrs);

            for (ServerPlayer player : plrs) {
               boolean isTooNear = false;

               for (ServerPlayer existing : validPlayers) {
                  if (existing.distanceTo(player) <= 64.0F) {
                     isTooNear = true;
                     break;
                  }
               }

               if (!isTooNear) {
                  validPlayers.add(player);
               }
            }

            for (ServerPlayer player : validPlayers) {
               for (int i = 0; i < 200; i++) {
                  BlockPos check = player.blockPosition().offset(new Vec3i(PMWeather.RANDOM.nextInt(-64, 65), 50, PMWeather.RANDOM.nextInt(-64, 65)));
                  if (level.isLoaded(check)) {
                     check = level.getHeightmapPos(Types.MOTION_BLOCKING, check).below();
                     float wind = (float)WindEngine.getWind(
                           new Vec3((double)check.getX(), (double)check.getY(), (double)check.getZ()), level, false, true, false, true
                        )
                        .length();
                     float chance = wind / 140.0F;
                     if (wind > 45.0F && PMWeather.RANDOM.nextFloat() <= chance) {
                        check = check.below(PMWeather.RANDOM.nextInt(3));
                        BlockState state = level.getBlockState(check);
                        Block block = state.getBlock();
                        float blockStrength = Storm.getBlockStrength(block, level, check);
                        if (ServerConfig.blockStrengths.containsKey(block)) {
                           blockStrength = ServerConfig.blockStrengths.get(block);
                           if (reinforcementManager.isReinforced(check)) {
                              blockStrength *= 1.3F;
                           }
                        }

                        blockStrength *= 0.9F;
                        if (!Util.isBlacklisted(block, serverLevel, check)
                           && Util.canWindAffect(check.getCenter(), level)
                           && !state.is(ModBlocks.REINFORCED_GLASS)
                           && !state.is(ModBlocks.REINFORCED_GLASS_PANE)) {
                           if (!state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                              && !state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)) {
                              double percChance = Math.clamp(
                                 Math.pow(Math.clamp(Math.max((double)(wind - blockStrength), 0.0) / 20.0, 0.0, 1.0), 2.0) + 0.02, 0.0, 1.0
                              );
                              if (wind < blockStrength) {
                                 percChance = 0.0;
                              }

                              if (block.defaultDestroyTime() < 0.05F
                                 && block.defaultDestroyTime() >= 0.0F
                                 && (double)PMWeather.RANDOM.nextFloat() <= percChance
                                 && Util.canWindAffect(check.getCenter(), level)) {
                                 level.removeBlock(check, false);
                              } else if ((double)PMWeather.RANDOM.nextFloat() <= percChance && Util.canWindAffect(check.getCenter(), level)) {
                                 if (state.is(BlockTags.LEAVES)) {
                                    reinforcementManager.setLeafData(check, state.getBlock());
                                 }

                                 NeoForge.EVENT_BUS.post(new BlockDamageEvent(level, check, state));
                                 level.removeBlock(check, false);
                                 if (level.isLoaded(check) && PMWeather.RANDOM.nextFloat() <= Math.clamp(1.0F - chance, 0.2F, 1.0F) * 0.6F) {
                                    MovingBlock movingBlock = (MovingBlock)ModEntities.MOVING_BLOCK.get().create(level);
                                    if (movingBlock != null) {
                                       movingBlock.setStartPos(check);
                                       movingBlock.setBlockState(state);
                                       movingBlock.setPos((double)check.getX(), (double)check.getY(), (double)check.getZ());
                                       level.addFreshEntity(movingBlock);
                                    }
                                 }
                              }
                           } else {
                              double percChancex = (double)Math.clamp((wind - 55.0F) / 15.0F, 0.0F, 1.0F);
                              if ((double)PMWeather.RANDOM.nextFloat() <= percChancex && Util.canWindAffect(check.getCenter(), level)) {
                                 level.destroyBlock(check, false);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         int storms = weatherHandler.getStorms().size();
         int cyclones = 0;

         for (Storm storm : weatherHandler.getStorms()) {
            if (storm.is(StormTypes.CYCLONE)) {
               cyclones++;
            }
         }

         if (level.getGameTime() % 1200L == 0L) {
            PMWeather.LOGGER.debug("Checking for storm/cloud spawns");
            List<ServerPlayer> validPlayers = new ArrayList<>();
            List<ServerPlayer> plrs = serverLevel.players();
            Collections.shuffle(plrs);

            for (ServerPlayer player : plrs) {
               boolean isTooNear = false;

               for (ServerPlayer existingx : validPlayers) {
                  if (existingx.distanceTo(player) <= (float)ServerConfig.spawnRange / 2.0F) {
                     isTooNear = true;
                     break;
                  }
               }

               if (!isTooNear) {
                  validPlayers.add(player);
               }
            }

            PMWeather.LOGGER.debug("{} players available to spawn around", validPlayers.size());

            for (ServerPlayer player : validPlayers) {
               Vec3 posx = new Vec3(player.getX(), (double)level.getMaxBuildHeight(), player.getZ())
                  .add(
                     (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1),
                     0.0,
                     (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1)
                  );
               Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
               boolean squall = PMWeather.RANDOM.nextInt(ServerConfig.chanceInOneSquall) == 0 && ServerConfig.doSqualls;
               boolean cyclone = PMWeather.RANDOM.nextInt(ServerConfig.chanceInOneCyclone) == 0 && cyclones <= 0 && ServerConfig.doCyclones;
               if (cyclone) {
                  PMWeather.LOGGER.debug("Checking for cyclone spawn");
                  posx = Util.getValidTropicalSystemSpawn(weatherHandler, posx, (float)ServerConfig.spawnRange + 4000.0F);
               } else if (squall) {
                  PMWeather.LOGGER.debug("Checking for squall spawn");
                  float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 6.0F;
                  posx = posx.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
               } else {
                  float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 4.0F;
                  posx = posx.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
               }

               if (posx != null) {
                  for (Storm stormx : weatherHandler.getStorms()) {
                     double dist = posx.distanceTo(stormx.position);
                     if (stormx.is(StormTypes.CYCLONE) && dist < (double)((float)stormx.maxWidth / 1.5F)) {
                        posx = null;
                        break;
                     }
                  }
               }

               if (posx != null) {
                  PMWeather.LOGGER
                     .debug("Checking storm spawns around {} at {}, {}", new Object[]{player.getDisplayName().getString(), (int)posx.x, (int)posx.z});
               } else {
                  PMWeather.LOGGER.debug("Cyclone position check failed or position too near to cyclone");
               }

               double spawnChance = ServerConfig.stormSpawnChancePerMinute;
               Vec3 sfcPos = serverLevel.getHeightmapPos(Types.WORLD_SURFACE_WG, player.blockPosition()).getCenter();
               Sounding sounding = new Sounding(weatherHandler, sfcPos, level, 250, 16000, 5400);
               ThermodynamicEngine.AtmosphericDataPoint sfc = ThermodynamicEngine.samplePoint(weatherHandler, sfcPos, level, null, 5400);
               float riskV = sounding.getRisk(5400);
               if (ServerConfig.environmentSystem) {
                  if (cyclone) {
                     if (posx != null) {
                        Float sst = ThermodynamicEngine.GetSST(weatherHandler, posx, weatherHandler.getWorld(), null, 0);
                        if (sst != null) {
                           spawnChance *= (double)Math.clamp((sst - 22.0F) / 4.0F, 0.0F, 2.0F);
                        } else {
                           spawnChance = 0.0;
                        }
                     } else {
                        spawnChance = 0.0;
                     }
                  } else if (squall) {
                     spawnChance *= Math.pow((double)riskV, 0.75) + 0.035F;
                  } else {
                     spawnChance *= Math.pow((double)riskV, 0.75) * 1.25 + 0.02;
                  }

                  if (sfc.temperature() < 3.0F) {
                     spawnChance += (double)(Math.clamp((sfc.temperature() - 3.0F) / -6.0F, 0.0F, 1.0F) * 0.035F);
                  }

                  PMWeather.LOGGER.debug("W/ spawn chance: {}%\nRisk: {}", (int)(spawnChance * 100.0), (int)(riskV * 100.0F));
               }

               if ((double)PMWeather.RANDOM.nextFloat() <= spawnChance && storms < ServerConfig.maxStorms && posx != null) {
                  if (cyclone) {
                     Storm stormxx = StormTypes.CYCLONE.create(new StormSpawnProperties(weatherHandler, level, posx, riskV));
                     stormxx.width = PMWeather.RANDOM.nextFloat(7500.0F, 16000.0F);
                     stormxx.windspeed = 0;
                     stormxx.velocity = Vec3.ZERO;
                     stormxx.initFirstTime();
                     stormxx.maxWindspeed = 0;
                     stormxx.maxWidth = Math.round(stormxx.width);
                     weatherHandler.addStorm(stormxx);
                     weatherHandler.syncStormNew(stormxx);
                     storms++;
                     cyclones++;
                  } else if (squall) {
                     if (sfc.temperature() < 3.0F) {
                        riskV += Math.clamp((sfc.temperature() - 3.0F) / -6.0F, 0.0F, 1.0F) * 0.25F;
                     }

                     Storm stormxx = StormTypes.SQUALL.create(new StormSpawnProperties(weatherHandler, level, posx, riskV));
                     stormxx.width = 0.0F;
                     stormxx.windspeed = 0;
                     stormxx.stage = 0;
                     if (wind.length() < 6.0) {
                        wind = wind.normalize().multiply(6.0, 0.0, 6.0);
                     }

                     double fac = 0.85;
                     Vec3 offset = new Vec3(1.0 + PMWeather.RANDOM.nextDouble() * fac, 0.0, 1.0 + PMWeather.RANDOM.nextDouble() * fac)
                        .subtract(new Vec3(fac / 2.0, 0.0, fac / 2.0));
                     stormxx.velocity = wind.multiply(0.15, 0.0, 0.15).multiply(offset);
                     stormxx.energy = 0;
                     if (ServerConfig.environmentSystem) {
                        if (PMWeather.RANDOM.nextFloat() <= riskV * 2.5F) {
                           stormxx.maxStage = Math.max(stormxx.maxStage, 1);
                        }

                        if (PMWeather.RANDOM.nextFloat() <= riskV * 2.0F) {
                           stormxx.maxStage = Math.max(stormxx.maxStage, 2);
                        }

                        if (PMWeather.RANDOM.nextFloat() <= riskV * 1.5F) {
                           stormxx.maxStage = Math.max(stormxx.maxStage, 3);
                        }

                        stormxx.recalc(riskV);
                     }

                     stormxx.initFirstTime();
                     weatherHandler.addStorm(stormxx);
                     weatherHandler.syncStormNew(stormxx);
                     storms++;
                  } else {
                     Storm stormxxx = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandler, level, posx, riskV));
                     stormxxx.width = 0.0F;
                     stormxxx.windspeed = 0;
                     stormxxx.stage = 0;
                     stormxxx.velocity = Vec3.ZERO;
                     stormxxx.energy = 0;
                     if (ServerConfig.environmentSystem) {
                        if (PMWeather.RANDOM.nextFloat() <= riskV * 2.5F) {
                           stormxxx.maxStage = Math.max(stormxxx.maxStage, 1);
                        }

                        if (PMWeather.RANDOM.nextFloat() <= riskV * 1.25F) {
                           stormxxx.maxStage = Math.max(stormxxx.maxStage, 2);
                        }

                        if (PMWeather.RANDOM.nextFloat() <= riskV * 0.75F) {
                           stormxxx.maxStage = Math.max(stormxxx.maxStage, 3);
                        }

                        stormxxx.recalc(riskV);
                     }

                     stormxxx.initFirstTime();
                     if (!ServerConfig.doTornadoes && stormxxx.maxStage >= 3) {
                        stormxxx.maxStage = 2;
                     }

                     weatherHandler.addStorm(stormxxx);
                     weatherHandler.syncStormNew(stormxxx);
                     storms++;
                  }

                  PMWeather.LOGGER.debug("Spawned storm at {}, {}", (int)posx.x, (int)posx.z);
               } else {
                  PMWeather.LOGGER.debug("Storm spawn failed, rolled bad number or too many storms");
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLevelLoad(Load event) {
      LevelAccessor level = event.getLevel();
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         ResourceKey<Level> dimension = serverLevel.dimension();
         WeatherHandlerServer weatherHandler = new WeatherHandlerServer(serverLevel);
         weatherHandler.read();
         MANAGERS.put(dimension, weatherHandler);
         MANAGERSLOOKUP.put(dimension.location().toString(), weatherHandler);
         ReinforcementManager reinforcementManager = new ReinforcementManager(serverLevel);
         reinforcementManager.read();
         REINFORCEMENTMANAGERS.put(dimension, reinforcementManager);
         if (WindEngine.simplexNoise == null) {
            WindEngine.init(weatherHandler);
         }

         weatherHandler.metarData.syncToPlayers();
      }
   }

   @SubscribeEvent
   public static void onLevelUnload(Unload event) {
      LevelAccessor level = event.getLevel();
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         MANAGERS.remove(serverLevel.dimension());
         MANAGERSLOOKUP.remove(serverLevel.dimension().toString());
         REINFORCEMENTMANAGERS.remove(serverLevel.dimension());
      }
   }

   @SubscribeEvent
   public static void onCommandsRegister(RegisterCommandsEvent event) {
      new WeatherCommands(event.getDispatcher(), event.getBuildContext());
   }

   @SubscribeEvent
   public static void onChunkSent(Sent event) {
      ServerLevel level = event.getLevel();
      LevelChunk chunk = event.getChunk();
      ServerPlayer player = event.getPlayer();
      if (level.dimension() == ServerLevel.OVERWORLD) {
         CompoundTag data = new CompoundTag();
         data.putString("packetCommand", "Chunk");
         data.putString("command", "sendData");
         if (!chunk.hasData(DataAttachments.MOISTURE)) {
            chunk.setData(DataAttachments.MOISTURE, MoistureHandler.getStartMoisture(level, chunk));
         }

         float moisture = (Float)chunk.getData(DataAttachments.MOISTURE);
         float fireIntensity = (Float)chunk.getData(DataAttachments.FIRE_INTENSITY);
         float stableFireIntensity = (Float)chunk.getData(DataAttachments.STABLE_FIRE_INTENSITY);
         float fireAftermath = (Float)chunk.getData(DataAttachments.FIRE_AFTERMATH);
         data.putInt("chunkX", chunk.getPos().x);
         data.putInt("chunkZ", chunk.getPos().z);
         data.putFloat("moisture", moisture);
         data.putFloat("fireIntensity", fireIntensity);
         data.putFloat("stableFireIntensity", stableFireIntensity);
         data.putFloat("fireAftermath", fireAftermath);
         ModNetworking.serverSendToClientPlayer(data, player);
      }
   }

   @SubscribeEvent
   public static void onRegisterStormType(StormTypeRegisterEvent event) {
      PMWeather.LOGGER.info("Registered new StormType object of ID {}", event.getStormType().getId());
   }

   public static void playerRequestsFullSync(ServerPlayer player) {
      WeatherHandler weatherHandler = MANAGERS.get(player.level().dimension());
      if (weatherHandler instanceof WeatherHandlerServer weatherHandlerServer) {
         weatherHandlerServer.playerJoinedWorldSyncFull(player);
      }
   }
}
