package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.Config;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.entity.ModEntities;
import dev.protomanly.pmweather.entity.MovingBlock;
import dev.protomanly.pmweather.event.BlockDamageEvent;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.sound.MovingSoundStreamingSource;
import dev.protomanly.pmweather.util.CachedNBTTagCompound;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags.Blocks;

public class Storm {
   public static long LastUsedStormID = 0L;
   public static final float resistance = 0.985F;
   public static final float tickConversion = 0.05F;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource tornadicWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource tornadicDamage;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource supercellWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource eyewallWind;
   @OnlyIn(Dist.CLIENT)
   public MovingSoundStreamingSource undergroundWind;
   public long ID;
   public UUID uuid = null;
   public WeatherHandler weatherHandler;
   public Vec3 position;
   public Vec3 lastPosition;
   public Vec3 velocity;
   public int windspeed;
   public float cycloneWindspeed = 0.0F;
   public float smoothWindspeed = 0.0F;
   public float smoothStage = 0.0F;
   public float width = 15.0F;
   public float smoothWidth = 15.0F;
   public float rainIntensity = 1.0F;
   public float tornadoShape = PMWeather.RANDOM.nextFloat() * 10.0F + 6.0F;
   public float spin = 0.0F;
   public float lastSpin = 0.0F;
   public int energy;
   public StormType stormType;
   public int stage;
   public int tickCount = 0;
   public int tornadoOnGroundTicks = 0;
   public boolean dead = false;
   public Level level;
   private final CachedNBTTagCompound nbtCache;
   public SimplexNoise simplexNoise;
   public float rankineFactor = 4.5F;
   public List<EntityRotFX> listParticleDebris;
   public int maxStage = 0;
   public int maxProgress = 0;
   public boolean isDying = false;
   public int growthSpeed = 20;
   public int maxWindspeed = 0;
   public int maxWidth = 15;
   public int ticksSinceDying = 0;
   public int touchdownSpeed = PMWeather.RANDOM.nextInt(65, 120);
   public boolean onWater = false;
   public float occlusion = 0.0F;
   public boolean visualOnly = false;
   public boolean cirus = false;
   public boolean aimedAtPlayer = false;
   public int maxColdEnergy = 300;
   public int coldEnergy = 0;
   public List<Vorticy> vorticies = new ArrayList<>();

   public double FBM(Vec3 pos, int octaves, float lacunarity, float gain, float amplitude) {
      double y = 0.0;

      for (int i = 0; i < Math.max(octaves, 1); i++) {
         y += (double)amplitude * this.simplexNoise.getValue(pos.x, pos.y, pos.z);
         pos = pos.multiply((double)lacunarity, (double)lacunarity, (double)lacunarity);
         amplitude *= gain;
      }

      return y;
   }

   public Vec3 rotateV3(Vec3 x, double angle) {
      double rx = x.x * Math.cos(angle) - x.z * Math.sin(angle);
      double rz = x.x * Math.sin(angle) + x.z * Math.cos(angle);
      return new Vec3(rx, x.y, rz);
   }

   public Storm(StormSpawnProperties properties) {
      this(properties.weatherHandler, properties.level, properties.risk);
      this.position = properties.position;
   }

   public Storm(WeatherHandler weatherHandler, Level level, @Nullable Float risk) {
      this.weatherHandler = weatherHandler;
      this.level = level;
      this.simplexNoise = new SimplexNoise(new LegacyRandomSource(weatherHandler.seed));
      this.nbtCache = new CachedNBTTagCompound();
      if (level.isClientSide()) {
         this.listParticleDebris = new ArrayList<>();
      } else {
         this.maxStage = 0;
         this.maxProgress = PMWeather.RANDOM.nextInt(25, 99);
         float stage1Chance = 1.0F / (float)ServerConfig.chanceInOneStage1;
         float stage2Chance = 1.0F / (float)ServerConfig.chanceInOneStage2;
         float stage3Chance = 1.0F / (float)ServerConfig.chanceInOneStage3;
         if (PMWeather.RANDOM.nextFloat() <= stage1Chance) {
            this.maxStage = 1;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage2Chance) {
            this.maxStage = 2;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage3Chance) {
            this.maxStage = 3;
         }

         this.growthSpeed = PMWeather.RANDOM.nextInt(30, 80);
         this.recalcTorWidth();
      }
   }

   public void recalcTorWidth() {
      double max = 2285.0;
      double mean = 45.0;
      float correctedWindspeed = (float)this.maxWindspeed * 1.4F;
      if (correctedWindspeed > 85.0F) {
         max = 1750.0;
         mean = 50.0;
      }

      if (correctedWindspeed > 110.0F) {
         max = 2500.0;
         mean = 100.0;
      }

      if (correctedWindspeed > 135.0F) {
         max = 3230.0;
         mean = 250.0;
      }

      if (correctedWindspeed > 165.0F) {
         max = 3540.0;
         mean = 350.0;
      }

      if (correctedWindspeed > 200.0F) {
         max = 4185.0;
         mean = 900.0;
      }

      double scaling = Util.getWidthScaling();
      max *= scaling;
      mean *= scaling;
      this.maxWidth = Mth.clamp(Mth.ceil(Util.normalDist(PMWeather.RANDOM, 15.0, mean, max)), 15, Mth.ceil(ServerConfig.maxTornadoWidth));
      this.touchdownSpeed = PMWeather.RANDOM.nextInt(55, 95) + Math.min(Mth.ceil((double)this.maxWidth / scaling / 25.0), 70);
   }

   public void recalc(@Nullable Float risk) {
      this.growthSpeed = PMWeather.RANDOM.nextInt(30, 80);
      this.recalcTorWidth();
      this.calcRain();
   }

   public void calcRain() {
      if (this.position == null) {
         PMWeather.LOGGER.warn("Storm position is null! Should not happen! Check calcRain calls.");
      }

      ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(this.weatherHandler, this.position, this.level, null, 0);
      float roughHumidity = (float)ThermodynamicEngine.getHumidity(dataPoint.temperature(), dataPoint.dewpoint());
      roughHumidity += Math.max((dataPoint.dewpoint() - 15.0F) / 40.0F, 0.0F);
      this.rainIntensity = Mth.sqrt(Mth.clamp((roughHumidity - 0.5F) * 2.0F, 0.0F, 1.0F));
   }

   public void aimAtPlayer() {
      Player nearest = this.level.getNearestPlayer(this.position.x, this.position.y, this.position.z, 4096.0, false);
      if (nearest != null) {
         Vec3 aimPos = nearest.position()
            .add(
               new Vec3(
                  (double)(PMWeather.RANDOM.nextFloat() - 0.5F) * ServerConfig.aimAtPlayerOffset,
                  0.0,
                  (double)(PMWeather.RANDOM.nextFloat() - 0.5F) * ServerConfig.aimAtPlayerOffset
               )
            );
         if (this.position.distanceTo(aimPos) >= ServerConfig.aimAtPlayerOffset) {
            Vec3 toward = this.position.subtract(new Vec3(aimPos.x, this.position.y, aimPos.z)).multiply(1.0, 0.0, 1.0).normalize();
            double speed = PMWeather.RANDOM.nextDouble() * 5.0 + 1.0;
            this.velocity = toward.multiply(-speed, 0.0, -speed);
         }

         this.aimedAtPlayer = true;
      }
   }

   public boolean shouldLoadChunks() {
      return this.level.getNearestPlayer(this.position.x, this.position.y, this.position.z, 1024.0, null) != null;
   }

   public boolean shouldLoadChunk(ChunkPos chunkPos) {
      return this.level.hasChunk(chunkPos.x, chunkPos.z) ? false : this.shouldLoadChunk(chunkPos, 1.0);
   }

   public boolean shouldLoadChunk(ChunkPos chunkPos, double padding) {
      return false;
   }

   public void forceChunks(ServerLevel serverLevel, BlockPos blockPos) {
   }

   public void iterateVorticies() {
      Iterator<Vorticy> vorts = this.vorticies.iterator();

      while (vorts.hasNext()) {
         Vorticy vorticy = vorts.next();
         vorticy.tick();
         if (vorticy.dead) {
            vorts.remove();
         }
      }
   }

   public BlockPos getBlockPos() {
      return BlockPos.containing(this.position).atY(60);
   }

   public void changeY(BlockPos blockPos) {
   }

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

         if (!this.isDying) {
            lightningChance += (float)this.maxWindspeed / 250.0F * lightningChance * 0.5F;
         }

         lightningChance = Math.min(lightningChance * 0.035F, 0.1F);
         lightningChance *= 2.0F;
         if (PMWeather.RANDOM.nextFloat() <= lightningChance * 0.5F) {
            Vec3 lPos = this.position
               .add(
                  (double)(PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 2.0F),
                  0.0,
                  (double)(PMWeather.RANDOM.nextFloat((float)(-ServerConfig.stormSize), (float)ServerConfig.stormSize) / 2.0F)
               );
            int height = 64;
            if (this.level.isLoaded(BlockPos.containing(lPos))) {
               height = this.level.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(lPos)).getY();
            }

            ((WeatherHandlerServer)this.weatherHandler).syncLightningNew(new Vec3(lPos.x, (double)height, lPos.z));
         }
      }
   }

   public void doGrowth() {
      int gs = this.growthSpeed / 2;
      if (this.tickCount % gs == 0) {
         if (this.isDying) {
            if (this.ticksSinceDying > 1200) {
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

   public void doWidth() {
   }

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
         float factor = 0.018181818F;
         Vec3 velAdd = new Vec3(baseWind.x, 0.0, baseWind.z).multiply((double)factor, 0.0, (double)factor);
         this.velocity = this.velocity.add(velAdd.multiply(0.05F, 0.05F, 0.05F));
      }
   }

   public void tick() {
      this.tickCount++;
      this.iterateVorticies();
      if (this.isDying) {
         this.ticksSinceDying++;
      }

      BlockPos blockPos = this.getBlockPos();
      if (!this.level.isClientSide()) {
         this.changeY(blockPos);
      }

      if (this.tickCount % 5 == 0 && !this.level.isClientSide() && this.level instanceof ServerLevel serverLevel) {
         this.forceChunks(serverLevel, blockPos);
      }

      if (this.tickCount % 10 == 0 && !this.level.isClientSide() && this.level instanceof ServerLevel) {
         this.doLightning();
      }

      this.doWidth();
      this.doGrowth();
      this.move();
      if (!this.level.isClientSide() && this.tickCount % this.getUpdateRate() == 0) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)this.weatherHandler;
         weatherHandlerServer.syncStormUpdate(this);
      }

      if (this.level.isClientSide()) {
         this.tickClient();
      } else {
         this.tickServer(blockPos);
      }
   }

   public boolean ignoresCharred() {
      return false;
   }

   public void tickServer(BlockPos blockPos) {
   }

   public void doDamage(LevelChunk chunk, BlockPos blockPosTop, double windEffect, float percAdj, int windfieldWidth) {
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(this.level.dimension());
      BlockState state = chunk.getBlockState(blockPosTop);
      BlockPos randomDown = blockPosTop.below(PMWeather.RANDOM.nextInt(10));
      BlockState stateDown = chunk.getBlockState(randomDown);
      if (!Util.isBlacklisted(stateDown.getBlock(), (ServerLevel)this.level, randomDown)
         && !stateDown.is(ModBlocks.REINFORCED_GLASS)
         && !stateDown.is(ModBlocks.REINFORCED_GLASS_PANE)) {
         if (stateDown.is(Blocks.GLASS_BLOCKS) || stateDown.is(Blocks.GLASS_PANES)) {
            double percChance = Mth.clamp((windEffect - 75.0) / 15.0, 0.0, 1.0);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.3F * percAdj) && Util.canWindAffect(randomDown.getCenter(), this.level)) {
               this.level.destroyBlock(randomDown, false);
            }
         }

         if (stateDown.is(BlockTags.LOGS) && !stateDown.is(Blocks.STRIPPED_LOGS) && ServerConfig.doDebarking) {
            double percChance = Mth.clamp((windEffect - 140.0) / 20.0, 0.0, 1.0);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChance * (double)(0.5F * percAdj) && Util.canWindAffect(randomDown.getCenter(), this.level)) {
               Block replacement = Util.STRIPPED_VARIANTS.getOrDefault(stateDown.getBlock(), net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG);
               this.level
                  .setBlockAndUpdate(
                     randomDown,
                     (BlockState)replacement.defaultBlockState()
                        .trySetValue(BlockStateProperties.AXIS, stateDown.getOptionalValue(BlockStateProperties.AXIS).orElse(Axis.Y))
                  );
            }
         }
      }

      BlockState aboveState = chunk.getBlockState(blockPosTop.above());
      if (!aboveState.isAir()) {
         Block aboveBlock = aboveState.getBlock();
         float blockStrength = getBlockStrength(aboveBlock, this.level, blockPosTop.above());
         double percChance = Mth.clamp(Math.pow(Mth.clamp(Math.max(windEffect - (double)blockStrength, 0.0) / 20.0, 0.0, 1.0), 4.0) + 0.02, 0.0, 1.0)
            * 0.05
            * (double)percAdj;
         if (windEffect < (double)blockStrength) {
            percChance = 0.0;
         }

         if (aboveBlock.defaultDestroyTime() < 0.05F
            && aboveBlock.defaultDestroyTime() >= 0.0F
            && !Util.isBlacklisted(aboveBlock, (ServerLevel)this.level, blockPosTop.above())
            && !aboveState.is(ModBlocks.FIRE)
            && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
            this.level.removeBlock(blockPosTop.above(), false);
            return;
         }

         boolean blacklisted = Util.isBlacklisted(aboveBlock, (ServerLevel)this.level, blockPosTop.above());
         if (aboveBlock.defaultBlockState().is(ModBlocks.FIRE)) {
            blacklisted = true;
         }

         if (windEffect >= (double)blockStrength
            && aboveBlock.defaultDestroyTime() > 0.0F
            && !blacklisted
            && state.getFluidState().isEmpty()
            && (double)PMWeather.RANDOM.nextFloat() <= percChance) {
            if (aboveState.is(BlockTags.LEAVES)) {
               reinforcementManager.setLeafData(blockPosTop.above(), aboveBlock);
            }

            MinecraftForge.EVENT_BUS.post(new BlockDamageEvent(this.level, blockPosTop.above(), aboveState));
            this.level.removeBlock(blockPosTop.above(), false);
         }
      }

      if (!this.ignoresCharred()
         || !state.is(ModBlocks.CHARRED_DIRT)
            && !state.is(ModBlocks.SMOLDERING_DIRT)
            && !state.is(ModBlocks.CHARRED_DIRT_SLIGHT)
            && !state.is(ModBlocks.SMOLDERING_DIRT_SLIGHT)) {
         boolean isGrass = state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || state.is(ModBlocks.SCOURED_GRASS);
         isGrass = isGrass
            || state.is(ModBlocks.CHARRED_DIRT)
            || state.is(ModBlocks.SMOLDERING_DIRT)
            || state.is(ModBlocks.CHARRED_DIRT_SLIGHT)
            || state.is(ModBlocks.SMOLDERING_DIRT_SLIGHT);
         if (isGrass) {
            double percChancex = Mth.clamp((windEffect - 140.0) / 80.0, 0.0, 1.0);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChancex * (double)(0.02F * percAdj)) {
               this.level.setBlockAndUpdate(blockPosTop, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            }
         } else if (state.is(net.minecraft.world.level.block.Blocks.DIRT)) {
            double percChancex = Mth.clamp((windEffect - 170.0) / 40.0, 0.0, 1.0);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChancex * (double)(0.02F * percAdj)) {
               this.level.setBlockAndUpdate(blockPosTop, ((Block)ModBlocks.MEDIUM_SCOURING.get()).defaultBlockState());
            }
         } else if (state.is((Block)ModBlocks.MEDIUM_SCOURING.get())) {
            double percChancex = Mth.clamp((windEffect - 200.0) / 50.0, 0.0, 1.0);
            if ((double)PMWeather.RANDOM.nextFloat() <= percChancex * (double)(0.02F * percAdj)) {
               this.level.setBlockAndUpdate(blockPosTop, ((Block)ModBlocks.HEAVY_SCOURING.get()).defaultBlockState());
            }
         } else {
            Block block = state.getBlock();
            float blockStrengthx = getBlockStrength(block, this.level, blockPosTop);
            if (state.is(Blocks.STRIPPED_LOGS)) {
               blockStrengthx *= 2.0F;
            }

            if (ServerConfig.blockStrengths.containsKey(block)) {
               blockStrengthx = ServerConfig.blockStrengths.get(block);
               if (reinforcementManager.isReinforced(blockPosTop)) {
                  blockStrengthx *= 1.3F;
               }
            }

            double stretch = 35.0;
            if (state.is(BlockTags.LEAVES)) {
               stretch = 70.0;
            } else if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
               stretch = 50.0;
            }

            double percChancex = Mth.clamp(Math.pow(Mth.clamp(Math.max(windEffect - (double)blockStrengthx, 0.0) / stretch, 0.0, 1.0), 4.0) + 0.02, 0.0, 1.0)
               * 0.05
               * (double)percAdj;
            if (windEffect < (double)blockStrengthx) {
               percChancex = 0.0;
            }

            if (block.defaultDestroyTime() < 0.05F
               && block.defaultDestroyTime() >= 0.0F
               && !Util.isBlacklisted(block, (ServerLevel)this.level, blockPosTop)
               && (double)PMWeather.RANDOM.nextFloat() <= percChancex) {
               this.level.removeBlock(blockPosTop, false);
            } else {
               if (windEffect >= (double)blockStrengthx
                  && block.defaultDestroyTime() > 0.0F
                  && !Util.isBlacklisted(block, (ServerLevel)this.level, blockPosTop)
                  && state.getFluidState().isEmpty()
                  && (double)PMWeather.RANDOM.nextFloat() <= percChancex) {
                  if (state.is(BlockTags.LEAVES)) {
                     reinforcementManager.setLeafData(blockPosTop, state.getBlock());
                  }

                  MinecraftForge.EVENT_BUS.post(new BlockDamageEvent(this.level, blockPosTop, state));
                  this.level.removeBlock(blockPosTop, false);
                  Player nearest = this.level.getNearestPlayer((double)blockPosTop.getX(), (double)blockPosTop.getY(), (double)blockPosTop.getZ(), 256.0, null);
                  float flyingBlockChance = (float)ServerConfig.flyingBlockEntityChance;
                  int entities = ((ServerLevel)this.level).getEntities(EntityTypeTest.forClass(MovingBlock.class), e -> true).size();
                  int maxEntity = 750;
                  float chanceMod = 1.0F - Mth.clamp((float)entities / (float)maxEntity, 0.0F, 1.0F);
                  flyingBlockChance *= chanceMod;
                  if (PMWeather.RANDOM.nextInt(Math.max(2, windfieldWidth / 80)) == 0
                     && PMWeather.RANDOM.nextFloat() < flyingBlockChance
                     && nearest != null
                     && nearest.position().distanceTo(blockPosTop.getCenter()) < 256.0) {
                     MovingBlock movingBlock = (MovingBlock)ModEntities.MOVING_BLOCK.get().create(this.level);
                     if (movingBlock != null) {
                        movingBlock.setStartPos(blockPosTop);
                        movingBlock.setBlockState(state);
                        movingBlock.setPos((double)blockPosTop.getX(), (double)blockPosTop.getY(), (double)blockPosTop.getZ());
                        if (this.level.isLoaded(blockPosTop)) {
                           this.level.addFreshEntity(movingBlock);
                        } else {
                           movingBlock.discard();
                        }
                     }
                  } else {
                     ((WeatherHandlerServer)this.weatherHandler).syncBlockParticleNew(blockPosTop, state, this);
                  }
               }
            }
         }
      }
   }

   public float getRankine(double dist, int windfieldWidth) {
      return this.getRankine(dist, windfieldWidth, this.rankineFactor);
   }

   public float getRankine(double dist, int windfieldWidth, float rF) {
      float rankineWidth = (float)windfieldWidth / rF;
      if (rF > 100.0F) {
         rankineWidth = 0.0F;
      }

      float perc = 0.0F;
      if (dist <= (double)(rankineWidth / 2.0F)) {
         perc = (float)dist / (rankineWidth / 2.0F);
      } else if (dist <= (double)((float)windfieldWidth * 2.0F)) {
         float e = 1.5F;
         e += Math.max((float)this.windspeed - 110.0F, 0.0F) / 110.0F;
         perc = Mth.clamp(
            (float)Math.pow(1.0 - (dist - (double)(rankineWidth / 2.0F)) / (double)(((float)windfieldWidth * 2.0F - rankineWidth) / 2.0F), (double)e),
            0.0F,
            1.0F
         );
      }

      if (Float.isNaN(perc)) {
         perc = 0.0F;
      }

      return perc;
   }

   public float getTornadicWind(Vec3 pos) {
      return this.getTornadicWind(pos, false);
   }

   public float getTornadicWind(Vec3 pos, boolean forParticles) {
      return (float)this.getTornadicWindVector(pos, forParticles).length();
   }

   public Vec3 getTornadicWindVector(Vec3 pos, boolean forParticles) {
      return Vec3.ZERO;
   }

   public void initFirstTime() {
      this.ID = LastUsedStormID++;
      this.generateUUID();
   }

   public void generateUUID() {
      if (this.uuid == null) {
         String strToUse = this.stormType.getId() + this.ID;
         this.uuid = UUID.nameUUIDFromBytes(strToUse.getBytes(StandardCharsets.UTF_8));
      }
   }

   public void pull(Particle particle, float multiplier) {
   }

   public void pull(Entity entity, float multiplier) {
   }

   @OnlyIn(Dist.CLIENT)
   public void tickClient() {
      Player player = Minecraft.getInstance().player;
      if (player != null && (this.undergroundWind == null || this.undergroundWind.isStopped()) && !this.dead) {
         this.undergroundWind = new MovingSoundStreamingSource(
            this, (SoundEvent)ModSounds.UNDERGROUND_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, (float)this.maxWidth, true, 3
         );
         Minecraft.getInstance().getSoundManager().play(this.undergroundWind);
      }
   }

   public void remove() {
      this.dead = true;
      if (EffectiveSide.get().equals(LogicalSide.CLIENT)) {
         this.cleanupClient();
      }

      this.cleanup();
   }

   public void cleanup() {
      this.weatherHandler = null;
   }

   @OnlyIn(Dist.CLIENT)
   public void cleanupAudios() {
      if (this.tornadicWind != null) {
         this.tornadicWind.stopPlaying();
         this.tornadicWind = null;
      }

      if (this.tornadicDamage != null) {
         this.tornadicDamage.stopPlaying();
         this.tornadicDamage = null;
      }

      if (this.supercellWind != null) {
         this.supercellWind.stopPlaying();
         this.supercellWind = null;
      }

      if (this.eyewallWind != null) {
         this.eyewallWind.stopPlaying();
         this.eyewallWind = null;
      }

      if (this.undergroundWind != null) {
         this.undergroundWind.stopPlaying();
         this.undergroundWind = null;
      }
   }

   @OnlyIn(Dist.CLIENT)
   public void cleanupClient() {
      this.cleanupAudios();
   }

   public void read() {
      this.nbtSyncFromServer();
   }

   public void write() {
      this.nbtSyncForClient();
   }

   public int getUpdateRate() {
      return 40;
   }

   private int getFormat() {
      return 1;
   }

   public void nbtSyncFromServer() {
      CachedNBTTagCompound nbt = this.getNBTCache();
      this.ID = nbt.getLong("ID");
      this.onWater = nbt.getBoolean("onWater");
      this.position = new Vec3(nbt.getDouble("positionX"), nbt.getDouble("positionY"), nbt.getDouble("positionZ"));
      this.velocity = new Vec3(nbt.getDouble("velocityX"), nbt.getDouble("velocityY"), nbt.getDouble("velocityZ"));
      this.windspeed = nbt.getInt("windspeed");
      this.cycloneWindspeed = (float)this.windspeed;
      this.width = nbt.getFloat("width");
      this.energy = nbt.getInt("energy");
      this.coldEnergy = nbt.getInt("coldEnergy");
      this.stage = nbt.getInt("stage");
      this.dead = nbt.getBoolean("dead");
      this.isDying = nbt.getBoolean("isDying");
      this.maxWidth = nbt.getInt("maxWidth");
      this.maxWindspeed = nbt.getInt("maxWindspeed");
      this.maxStage = nbt.getInt("maxStage");
      this.maxProgress = nbt.getInt("maxProgress");
      this.ticksSinceDying = nbt.getInt("ticksSinceDying");
      this.growthSpeed = nbt.getInt("growthSpeed");
      this.visualOnly = nbt.getBoolean("visualOnly");
      this.aimedAtPlayer = nbt.getBoolean("aimedAtPlayer");
      this.cirus = nbt.getBoolean("cirus");
      this.touchdownSpeed = nbt.getInt("touchdownSpeed");
      this.occlusion = nbt.getFloat("occlusion");
      this.rainIntensity = nbt.getFloat("rainIntensity");
      CompoundTag vorticiesData = nbt.get("vorticies");
      int vorticyCount = vorticiesData.getInt("vorticyCount");
      this.vorticies.clear();

      for (int i = 0; i < vorticyCount; i++) {
         CompoundTag vorticyData = vorticiesData.getCompound("vorticy" + i);
         Vorticy vorticy = new Vorticy(
            this,
            vorticyData.getFloat("maxWindspeedMult"),
            vorticyData.getFloat("widthPerc"),
            vorticyData.getFloat("distancePerc"),
            vorticyData.getInt("lifetime")
         );
         vorticy.dead = vorticyData.getBoolean("dead");
         vorticy.angle = vorticyData.getFloat("angle");
         vorticy.tickCount = vorticyData.getInt("tickCount");
         vorticy.windspeedMult = vorticyData.getFloat("windspeedMult");
         this.vorticies.add(vorticy);
      }

      this.readData(nbt);
      this.generateUUID();
   }

   public void readData(CachedNBTTagCompound data) {
   }

   public void nbtSyncForClient() {
      CachedNBTTagCompound nbt = this.getNBTCache();
      CompoundTag vorticiesData = new CompoundTag();
      vorticiesData.putInt("vorticyCount", this.vorticies.size());

      for (int i = 0; i < this.vorticies.size(); i++) {
         Vorticy vorticy = this.vorticies.get(i);
         CompoundTag vorticyData = new CompoundTag();
         vorticyData.putBoolean("dead", vorticy.dead);
         vorticyData.putFloat("windspeedMult", vorticy.windspeedMult);
         vorticyData.putFloat("maxWindspeedMult", vorticy.maxWindspeedMult);
         vorticyData.putFloat("widthPerc", vorticy.widthPerc);
         vorticyData.putFloat("distancePerc", vorticy.distancePerc);
         vorticyData.putFloat("angle", vorticy.angle);
         vorticyData.putInt("lifetime", vorticy.lifetime);
         vorticyData.putInt("tickCount", vorticy.tickCount);
         vorticiesData.put("vorticy" + i, vorticyData);
      }

      nbt.put("vorticies", vorticiesData);
      nbt.putBoolean("onWater", this.onWater);
      nbt.putInt("touchdownSpeed", this.touchdownSpeed);
      nbt.putBoolean("cirus", this.cirus);
      nbt.putBoolean("aimedAtPlayer", this.aimedAtPlayer);
      nbt.putBoolean("visualOnly", this.visualOnly);
      nbt.putBoolean("isDying", this.isDying);
      nbt.putInt("maxWidth", this.maxWidth);
      nbt.putInt("maxWindspeed", this.maxWindspeed);
      nbt.putInt("maxStage", this.maxStage);
      nbt.putInt("maxProgress", this.maxProgress);
      nbt.putInt("ticksSinceDying", this.ticksSinceDying);
      nbt.putInt("growthSpeed", this.growthSpeed);
      nbt.putFloat("occlusion", this.occlusion);
      nbt.putDouble("positionX", this.position.x);
      nbt.putDouble("positionY", this.position.y);
      nbt.putDouble("positionZ", this.position.z);
      nbt.putDouble("velocityX", this.velocity.x);
      nbt.putDouble("velocityY", this.velocity.y);
      nbt.putDouble("velocityZ", this.velocity.z);
      nbt.putLong("ID", this.ID);
      nbt.getNewNBT().putLong("ID", this.ID);
      nbt.putInt("windspeed", this.windspeed);
      nbt.putFloat("width", this.width);
      nbt.putInt("energy", this.energy);
      nbt.putInt("coldEnergy", this.coldEnergy);
      nbt.putString("stormType", this.stormType.getId());
      nbt.putInt("stage", this.stage);
      nbt.putFloat("rainIntensity", this.rainIntensity);
      nbt.putBoolean("dead", this.dead);
      nbt.putInt("format", this.getFormat());
      this.addData(nbt);
   }

   public void addData(CachedNBTTagCompound data) {
   }

   public CachedNBTTagCompound getNBTCache() {
      return this.nbtCache;
   }

   public static float getBlockStrength(Block block, Level level, @Nullable BlockPos blockPos) {
      ItemStack item = new ItemStack(Items.IRON_AXE);
      float destroySpeed = block.defaultBlockState().getDestroySpeed(level, blockPos != null ? blockPos : BlockPos.ZERO);

      try {
         destroySpeed /= item.getDestroySpeed(block.defaultBlockState());
      } catch (Exception var7) {
         PMWeather.LOGGER.warn(var7.getMessage());
      }

      float val = 60.0F + Mth.sqrt(destroySpeed) * 60.0F;
      if (!level.isClientSide() && blockPos != null) {
         ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
         if (reinforcementManager.isReinforced(blockPos)) {
            val *= 1.3F;
         }
      }

      return val;
   }

   public boolean triggerTornadoSensorAndSiren() {
      return false;
   }

   public boolean isTornadic() {
      return false;
   }

   public boolean hasRadarRepresentation() {
      return true;
   }

   public boolean hasPrecipitation() {
      return true;
   }

   public float getRadarRenderRange() {
      return (float)ServerConfig.stormSize * 2.0F;
   }

   public float getRadarReflectivityReturn(RadarBlockEntity radarBlockEntity, Vec3 worldPos) {
      return 0.0F;
   }

   public float getPrecipitation(Vec3 pos) {
      return 0.0F;
   }

   public float getHail(Vec3 pos) {
      return 0.0F;
   }

   public float addToPrecip(float original, float toAdd) {
      return original + toAdd;
   }

   public Vec3 getBaseWind(Vec3 pos) {
      return Vec3.ZERO;
   }

   public Vec3 getRawWind(Vec3 pos) {
      return Vec3.ZERO;
   }

   public float getTemperatureOffset(Vec3 pos) {
      return 0.0F;
   }

   public boolean is(StormType type) {
      return this.stormType.getId().equals(type.getId());
   }
}
