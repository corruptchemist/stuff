package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.Config;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.entity.MovingBlock;
import dev.protomanly.pmweather.interfaces.ConditionallyPulled;
import dev.protomanly.pmweather.interfaces.ParticleData;
import dev.protomanly.pmweather.level.ChunkLoading;
import dev.protomanly.pmweather.particle.EntityRotFX;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.sound.MovingSoundStreamingSource;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.Vorticy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

public class Supercell extends Storm {
   private int lastGatherBreak = -100000;

   public Supercell(StormSpawnProperties properties) {
      super(properties);
      if (!this.level.isClientSide()) {
         this.maxStage = 0;
         this.maxProgress = PMWeather.RANDOM.nextInt(25, 99);
         float stage1Chance = 1.0F / (float)ServerConfig.chanceInOneStage1;
         float stage2Chance = 1.0F / (float)ServerConfig.chanceInOneStage2;
         float stage3Chance = 1.0F / (float)ServerConfig.chanceInOneStage3;
         if (properties.risk != null && ServerConfig.environmentSystem) {
            stage1Chance *= properties.risk * 1.75F + 0.05F;
            stage2Chance *= properties.risk;
            stage3Chance *= properties.risk * 0.5F;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage1Chance) {
            this.maxStage = 1;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage2Chance) {
            this.maxStage = 2;
         }

         if (PMWeather.RANDOM.nextFloat() <= stage3Chance) {
            this.maxStage = 3;
         }

         if (this.maxStage == 3) {
            this.maxProgress = 100;
            float mW;
            if (properties.risk != null && ServerConfig.environmentSystem) {
               mW = properties.risk * 110.0F;
            } else {
               mW = 125.0F;
            }

            mW += 55.0F;
            this.maxWindspeed = Math.min((int)Mth.lerp(Mth.sqrt(PMWeather.RANDOM.nextFloat()), 55.0F, mW), 250);
            this.touchdownSpeed = PMWeather.RANDOM.nextInt(75, Math.max(25 + (int)((float)this.maxWindspeed * 1.1F), 100));
         }

         this.recalc(properties.risk);
      }
   }

   @Override
   public void recalc(@Nullable Float risk) {
      super.recalc(risk);
      if (this.maxStage == 3) {
         this.maxProgress = 100;
         float mW;
         if (risk != null && ServerConfig.environmentSystem) {
            mW = risk * 110.0F;
         } else {
            mW = 125.0F;
         }

         mW += 55.0F;
         this.maxWindspeed = Math.min((int)Mth.lerp(Mth.sqrt(PMWeather.RANDOM.nextFloat()), 55.0F, mW), 250);
         this.recalcTorWidth();
      }
   }

   @Override
   public boolean shouldLoadChunks() {
      return this.level.getNearestPlayer(this.position.x, this.position.y, this.position.z, 2048.0, null) != null;
   }

   @Override
   public boolean shouldLoadChunk(ChunkPos chunkPos, double padding) {
      if (!this.shouldLoadChunks()) {
         return false;
      } else if (this.level instanceof ServerLevel serverLevel) {
         ChunkPos var12 = new ChunkPos(BlockPos.containing(this.position));
         double dist = Math.sqrt((double)chunkPos.distanceSquared(var12));
         boolean hasWind = dist <= 2.0;
         if (!hasWind) {
            float wind = this.getTornadicWind(chunkPos.getMiddleBlockPosition((int)this.position.y).getCenter());
            double threshold = 50.0 * Math.clamp(3.0 - padding * 2.0, 0.5, 1.5);
            hasWind = (double)wind >= threshold;
         }

         return this.windspeed > 40
            && serverLevel.isInWorldBounds(chunkPos.getWorldPosition())
            && dist <= (double)(this.width / 16.0F) * Math.clamp(padding, 0.0, 1.5)
            && hasWind;
      } else {
         return false;
      }
   }

   @Override
   public void forceChunks(ServerLevel serverLevel, BlockPos blockPos) {
      if ((long)(this.tickCount - this.lastGatherBreak) >= 5L) {
         if (this.windspeed > 40 && this.shouldLoadChunks()) {
            ChunkPos cChunkPos = new ChunkPos(blockPos);
            List<ChunkPos> toCheck = new ArrayList<>();
            toCheck.add(cChunkPos);

            for (int x = -((int)this.width); (float)x <= this.width; x += 16) {
               for (int z = -((int)this.width); (float)z <= this.width; z += 16) {
                  ChunkPos chunkPos = new ChunkPos(blockPos.offset(x, 0, z));
                  if (this.shouldLoadChunk(chunkPos) && !toCheck.contains(chunkPos)) {
                     toCheck.add(chunkPos);
                  }
               }
            }

            int loaded = 0;

            for (ChunkPos cpos : toCheck) {
               if (this.shouldLoadChunk(cpos) && ChunkLoading.force(this, cpos, true)) {
                  if (++loaded > ServerConfig.maxChunksLoadedPer5Ticks) {
                     this.lastGatherBreak = this.tickCount;
                     break;
                  }
               }
            }
         } else {
            super.forceChunks(serverLevel, blockPos);
         }
      }
   }

   @Override
   public void iterateVorticies() {
      super.iterateVorticies();
      float vorticySpawnChance = 0.05F;
      if (this.isDying) {
         vorticySpawnChance = 0.25F;
      }

      vorticySpawnChance += Mth.clamp(Mth.square(((float)this.windspeed - 100.0F) / 200.0F), 0.0F, 0.5F);
      float scaling = (float)Util.getWidthScaling();
      float effWidth = this.width * scaling;
      vorticySpawnChance += Mth.clamp(Mth.square(effWidth / 1200.0F), 0.0F, 1.0F) * 0.4F;
      if (this.stage == 3 && (float)this.windspeed >= 40.0F) {
         this.tornadoOnGroundTicks++;
         if (!this.level.isClientSide && PMWeather.RANDOM.nextFloat() < vorticySpawnChance * 0.05F && this.vorticies.size() < 10) {
            float widthEffect = Mth.clamp(Mth.square(effWidth / 2600.0F), 0.0F, 1.0F) * 0.65F;
            widthEffect *= 1.0F - Mth.clamp(((float)this.windspeed - 55.0F) / 55.0F, 0.0F, 1.0F);
            Vorticy vorticy = new Vorticy(
               this,
               (float)Math.pow((double)PMWeather.RANDOM.nextFloat(), 0.75) * (0.4F + widthEffect),
               PMWeather.RANDOM.nextFloat() * 0.3F + 0.05F,
               1.0F / this.rankineFactor * 0.5F,
               PMWeather.RANDOM.nextInt(35, 120)
            );
            this.vorticies.add(vorticy);
         }
      }
   }

   @Override
   public BlockPos getBlockPos() {
      BlockPos blockPos = super.getBlockPos();
      if (!this.shouldLoadChunks()) {
         return blockPos;
      } else {
         if (!this.level.isClientSide() && this.stage >= 2) {
            float y = 0.0F;
            int count = 0;

            for (int x = -1; x <= 1; x++) {
               for (int z = -1; z <= 1; z++) {
                  float r = Math.max(this.width, 30.0F);
                  BlockPos samplePos = BlockPos.containing(this.position.add((double)((float)x * r * 0.5F), 0.0, (double)((float)z * r * 0.5F)));
                  if (this.level.isLoaded(samplePos)) {
                     BlockPos sample = this.level.getHeightmapPos(Types.WORLD_SURFACE_WG, samplePos);
                     y += (float)sample.getY();
                     count++;
                  }
               }
            }

            if (count <= 0) {
               return blockPos;
            }

            y /= (float)count;
            blockPos = new BlockPos((int)this.position.x, (int)y, (int)this.position.z);
         }

         return blockPos;
      }
   }

   @Override
   public void changeY(BlockPos blockPos) {
      if (this.stage <= 2) {
         this.position = new Vec3(this.position.x, 69.0, this.position.z);
      } else {
         this.position = new Vec3(this.position.x, Mth.lerp(0.01F, this.position.y, (double)blockPos.getY()), this.position.z);
      }
   }

   @Override
   public void doGrowth() {
      int gs = this.growthSpeed / 2;
      if (this.stage < 3) {
         gs = (int)((float)gs / 1.5F);
      }

      if (this.tickCount % gs == 0) {
         if (!this.isDying) {
            int targetProgress = this.maxProgress;
            if (this.maxStage > this.stage) {
               targetProgress = 100;
            }

            if (this.energy < targetProgress) {
               this.energy++;
            }

            if (this.stage < 3) {
               if (this.stage >= this.maxStage && this.energy >= targetProgress) {
                  this.isDying = true;
                  this.growthSpeed = PMWeather.RANDOM.nextInt(40, 80);
               }
            } else {
               if (this.windspeed < this.maxWindspeed) {
                  this.windspeed++;
                  this.occlusion = Math.clamp(this.occlusion - 0.025F, 0.0F, 1.0F);
               }

               if (this.windspeed >= this.maxWindspeed) {
                  this.isDying = true;
                  if (this.growthSpeed < 45) {
                     this.growthSpeed = PMWeather.RANDOM.nextInt(45, 90);
                  } else {
                     this.growthSpeed = PMWeather.RANDOM.nextInt(15, 45);
                  }
               }
            }

            if (this.energy >= 100) {
               this.energy = 0;
               if (this.stage < 3 && this.stage < this.maxStage) {
                  this.stage++;
                  if (this.stage == 3) {
                     this.windspeed = 0;
                     this.growthSpeed = PMWeather.RANDOM.nextInt(15, 90);
                  }
               }
            }
         } else if (this.ticksSinceDying > 1200) {
            if (this.stage < 3) {
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
            } else {
               if (this.windspeed >= 85 || this.windspeed <= 15) {
                  this.windspeed--;
               } else if (PMWeather.RANDOM.nextInt(2) == 0 && !this.level.isClientSide()) {
                  this.windspeed--;
               }

               this.occlusion = Math.clamp(this.occlusion + 0.015F, 0.0F, 1.0F);
               if (this.windspeed <= 0) {
                  this.windspeed = 0;
                  this.stage--;
                  this.energy = 100;
                  this.growthSpeed = PMWeather.RANDOM.nextInt(40, 80);
               }
            }
         }

         if (Config.DEBUG) {
            PMWeather.LOGGER.debug("Stage: {}, Energy: {}, Windspeed: {}, Width: {}", new Object[]{this.stage, this.energy, this.windspeed, this.width});
         }
      }
   }

   @Override
   public void doWidth() {
      float p = Mth.clamp((float)this.windspeed / (float)this.maxWindspeed, 0.0F, 1.0F);
      p = -Mth.cos(p * (float) Math.PI) / 2.0F;
      p += 0.5F;
      if (!this.isDying) {
         p = (float)Math.pow((double)p, (double)(300.0F / (float)this.maxWindspeed));
         p *= 0.9F;
         p += 0.1F;
      } else {
         float m = 1.0F + (float)this.maxWidth / 1000.0F * 0.25F;
         p = Mth.clamp((float)this.windspeed * m / (float)this.maxWindspeed, 0.0F, 1.0F);
         p = -Mth.cos(p * (float) Math.PI) / 2.0F;
         p += 0.5F;
         p = (float)Math.pow((double)p, 0.25 + (double)this.maxWidth / 200.0);
         p *= 0.95F;
         p += 0.05F;
      }

      p = Mth.clamp(p, 0.0F, 1.0F);
      double seconds = (double)this.tickCount / 20.0;
      float noise = (float)this.FBM(new Vec3(this.position.x / 1000.0, seconds / 1000.0, this.position.z / 1000.0), 3, 2.0F, 0.5F, 1.0F);
      p *= 1.0F + noise * 0.3F;
      this.width = Mth.lerp(0.025F, this.width, Math.max(5.0F, p * (float)this.maxWidth));
   }

   @Override
   public void move() {
      super.move();
      if (!this.aimedAtPlayer && !this.level.isClientSide() && this.stage >= 3 && ServerConfig.aimAtPlayer) {
         this.aimAtPlayer();
      }
   }

   @Override
   public int getUpdateRate() {
      return this.stage >= 3 ? super.getUpdateRate() / 4 : super.getUpdateRate();
   }

   @Override
   public void tickServer(BlockPos blockPos) {
      super.tickServer(blockPos);
      if (this.stage >= 3) {
         if (this.windspeed >= 40 && ServerConfig.doDamage) {
            AABB aabb = new AABB(this.position.x, this.position.y, this.position.z, this.position.x, this.position.y, this.position.z);
            aabb = aabb.inflate((double)this.width / 2.0, 85.0, (double)this.width / 2.0);

            for (Entity entity : this.level.getEntities(null, aabb)) {
               if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                  this.pull(entity, 2.5F);
                  continue;
               }

               if (!(entity instanceof Player)) {
                  this.pull(entity, 2.5F);
               }
            }

            boolean dd = this.tickCount % 5 == 0 || !ServerConfig.damageEvery5thTick;
            if (dd && this.shouldLoadChunks()) {
               int windfieldWidth = Math.max((int)this.width, 40);
               int numBlocks = Math.min(windfieldWidth * Math.max(windfieldWidth / 2, 20) + this.windspeed * 3 + 300, ServerConfig.maxBlocksDamagedPerTick);
               Map<Vec3i, Boolean> checkedMap = new HashMap<>();
               Map<ChunkPos, LevelChunk> chunkMap = new HashMap<>();
               int damaged = 0;
               int damageMax = (500 + (int)this.width) / 3;

               for (int i = 0; i < numBlocks && damaged < damageMax; i++) {
                  double ang = PMWeather.RANDOM.nextDouble() * Math.PI * 2.0;
                  double r = PMWeather.RANDOM.nextDouble();
                  double along = (double)windfieldWidth;
                  if (r > 0.5) {
                     along *= 3.0 * Mth.square(r - 0.5) + 0.25;
                  } else {
                     along *= 2.0 * Math.pow(r - 0.5, 3.0) + 0.25;
                  }

                  int x = Mth.floor(Math.sin(ang) * along);
                  int z = Mth.floor(Math.cos(ang) * along);
                  Vec3i off = new Vec3i(x, 0, z);
                  if (!checkedMap.containsKey(off)) {
                     checkedMap.put(off, true);
                     double dist = off.distSqr(Vec3i.ZERO);
                     if (!(dist > (double)(windfieldWidth * windfieldWidth))) {
                        float percAdj = 16.0F;
                        if (ServerConfig.damageEvery5thTick) {
                           percAdj *= 5.0F;
                        }

                        BlockPos bPos = blockPos.offset(off.getX(), 60, off.getZ());
                        if (this.level.isInWorldBounds(bPos) && this.level.isLoaded(bPos)) {
                           BlockPos blockPosTop = this.level.getHeightmapPos(Types.MOTION_BLOCKING, bPos).below();
                           double windEffect = (double)this.getTornadicWind(blockPosTop.getCenter());
                           if (!(windEffect < 40.0)) {
                              ChunkPos chunkPos = new ChunkPos(
                                 SectionPos.blockToSectionCoord(blockPosTop.getX()), SectionPos.blockToSectionCoord(blockPosTop.getZ())
                              );
                              if (this.level.hasChunk(chunkPos.x, chunkPos.z)) {
                                 LevelChunk chunk;
                                 if (chunkMap.containsKey(chunkPos)) {
                                    chunk = chunkMap.get(chunkPos);
                                 } else {
                                    chunk = this.level.getChunk(chunkPos.x, chunkPos.z);
                                    chunkMap.put(chunkPos, chunk);
                                 }

                                 this.doDamage(chunk, blockPosTop, windEffect, percAdj, windfieldWidth);
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

   @OnlyIn(Dist.CLIENT)
   @Override
   public void tickClient() {
      super.tickClient();
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         this.smoothWindspeed = Mth.lerp(0.1F, this.smoothWindspeed, (float)this.windspeed);
         this.smoothWidth = Mth.lerp(0.05F, this.smoothWidth, this.width);
         if (this.stage >= 3) {
            if ((this.tornadicWind == null || this.tornadicWind.isStopped()) && !this.dead) {
               this.tornadicWind = new MovingSoundStreamingSource(
                  this, (SoundEvent)ModSounds.TORNADIC_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 1
               );
               Minecraft.getInstance().getSoundManager().play(this.tornadicWind);
            }

            if ((this.tornadicDamage == null || this.tornadicDamage.isStopped()) && !this.dead) {
               this.tornadicDamage = new MovingSoundStreamingSource(
                  this, (SoundEvent)ModSounds.TORNADIC_DAMAGE.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 4
               );
               Minecraft.getInstance().getSoundManager().play(this.tornadicDamage);
            }

            if (this.windspeed >= 40 && !player.isCreative() && !player.isSpectator()) {
               this.pull(player, 2.5F);
            }
         }

         if (this.stage >= 2 && (this.supercellWind == null || this.supercellWind.isStopped()) && !this.dead) {
            this.supercellWind = new MovingSoundStreamingSource(
               this, (SoundEvent)ModSounds.SUPERCELL_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 0
            );
            Minecraft.getInstance().getSoundManager().play(this.supercellWind);
         }

         if (this.stage < 3 && this.tornadicWind != null) {
            this.tornadicWind.stopPlaying();
            this.tornadicWind = null;
         }

         if (this.stage < 2 && this.supercellWind != null) {
            this.supercellWind.stopPlaying();
            this.supercellWind = null;
         }

         for (int i = 0; i < this.listParticleDebris.size(); i++) {
            EntityRotFX debris = this.listParticleDebris.get(i);
            if (!debris.isAlive()) {
               this.listParticleDebris.remove(debris);
            } else {
               this.pull(debris, 1.0F);
            }
         }
      }
   }

   @Override
   public boolean triggerTornadoSensorAndSiren() {
      return this.stage >= 3;
   }

   @Override
   public boolean isTornadic() {
      return this.stage >= 3;
   }

   @Override
   public float getRadarRenderRange() {
      return super.getRadarRenderRange() * 8.0F;
   }

   @Override
   public float getRadarReflectivityReturn(RadarBlockEntity radarBlockEntity, Vec3 worldPos) {
      float localDBZ = 0.0F;
      float stormSize = (float)ServerConfig.stormSize * 2.0F;
      Vec3 samplePos = new Vec3(worldPos.x, 0.0, worldPos.z);
      double scale = (double)stormSize / 1200.0;
      double shapeNoise = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.x / (750.0 * scale), worldPos.z / (750.0 * scale));
      double shapeNoise2 = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.z / (750.0 * scale), worldPos.x / (750.0 * scale));
      double shapeNoise4 = radarBlockEntity.noise
         .getValue((double)((float)radarBlockEntity.tickCount / 8000.0F), worldPos.z / (250.0 * scale), worldPos.x / (250.0 * scale));
      samplePos = samplePos.add(shapeNoise * 25.0, 0.0, shapeNoise2 * 25.0);
      float rain = this.getPrecipitation(samplePos);
      float hail = this.getHail(samplePos);
      localDBZ = Mth.sqrt(rain) * 0.8F;
      localDBZ += hail * 1.15F;
      localDBZ *= 1.0F + (float)(shapeNoise4 - 0.2) * 0.15F;
      return localDBZ * Math.min((float)this.stage + (float)this.energy / 100.0F, 1.0F);
   }

   @Override
   public float getPrecipitation(Vec3 pos) {
      float lcl = (float)ServerConfig.layer0Height;
      float el = 2500.0F + lcl;
      float disorg = 0.0F;
      float size = (float)ServerConfig.stormSize * 20.0F;
      Vec3 relPos = pos.subtract(this.position.multiply(1.0, 0.0, 1.0));
      Vec3 percentPos = relPos.multiply(1.0 / (double)size, 1.0 / ((2500.0 + ServerConfig.layer0Height) * 1.05), 1.0 / (double)size);
      percentPos = percentPos.subtract(Util.fromCardial(0.005, 0.015));
      if (!(percentPos.x < -0.2) && !(percentPos.x > 0.8) && !(percentPos.y > 1.0) && !(percentPos.z < -0.8) && !(percentPos.z > 0.25)) {
         float fel = 65000.0F;
         float flcl = lcl / el * fel;
         Vec3 fLocalPos = percentPos.multiply(65000.0, 68250.0, 65000.0);
         Vec3 fRawLocalPos = percentPos.multiply(65000.0, 68250.0, 65000.0);
         double height = percentPos.y * 1.05F;
         float precip = 0.0F;
         float smoothStage = (float)this.stage + (float)this.energy / 100.0F;
         float stormStrength = Mth.clamp(smoothStage / 1.75F, 0.0F, 1.0F);
         if (this.stage == 3) {
            smoothStage = 3.0F;
         }

         double dstxm = 1.0;
         double dstzm = 1.0;
         if (fLocalPos.x > 0.0) {
            dstxm = 0.5;
         }

         if (fLocalPos.z < 0.0) {
            dstzm = 0.3;
         }

         double dist = Math.sqrt(Mth.square(fLocalPos.x / dstxm) + Mth.square(fLocalPos.z / dstzm));
         double clipSize = 260000.0;
         if (dist > clipSize) {
            return 0.0F;
         } else {
            fel = Mth.lerp(stormStrength, flcl, fel);
            Vec3 updraftOff = Util.fromAngle(Math.toRadians(59.0), 1.25);
            Vec3 updraftPos = Vec3.ZERO;
            Vec3 relUpdraft = updraftPos.subtract(fLocalPos);
            double angDist = Math.sqrt(Mth.square(relUpdraft.x / 1.15) + Mth.square(relUpdraft.z));
            double angSize = 5000.0 + angDist / 4.0;
            double ang = Math.toRadians(
               Mth.lerp(Mth.clamp((double)this.windspeed / 300.0, 0.0, 1.0), 250.0 * (double)Mth.clamp(smoothStage - 1.0F, 0.0F, 1.0F), 400.0)
            );
            ang *= (double)Mth.square(stormStrength) * (1.0 - (double)disorg * 0.2) * Math.pow(1.0 - Mth.clamp(angDist / angSize, 0.0, 1.0), 4.0);
            ang += Math.toRadians(15.0);
            Vec3 hookOffset = Util.fromCardial(-600.0, -600.0);
            Vec3 rotated = Util.rotatePoint(fLocalPos, updraftPos.add(hookOffset), ang);
            double stormDist = rotated.length();
            Vec3 updraftTopPos = updraftOff.multiply(1.0, 1.0, 0.6666666666666666).scale(5000.0);
            double distToUpdraft = rotated.multiply(1.0, 0.0, 1.0).distanceTo(updraftPos.multiply(1.0, 0.0, 1.0));
            relUpdraft = updraftPos.subtract(rotated);
            Vec3 relUpdraftTop = updraftTopPos.subtract(rotated);
            double xM = 1.0;
            double zM = 1.0;
            if (rotated.x > updraftPos.x) {
               xM = 0.05;
            }

            if (rotated.z < updraftPos.z) {
               zM = 0.2;
            }

            double shearedDistanceToUpdraft = Math.sqrt(Mth.square(relUpdraft.x) * xM + Mth.square(relUpdraft.z) * zM);
            xM = 1.0;
            zM = 1.0;
            double xMW = 0.3;
            double zMW = 0.8;
            if (rotated.x > updraftTopPos.x) {
               xM = 0.05;
               xMW = 1.0;
            }

            if (rotated.z < updraftTopPos.z) {
               zM = 0.2;
               zMW = 0.3;
            }

            double shearedDistanceToUpdraftTop = Math.sqrt(Mth.square(relUpdraftTop.x) * xM + Mth.square(relUpdraftTop.z) * zM);
            double westShearedDistanceToUpdraft = Math.sqrt(Mth.square(relUpdraftTop.x) * xMW + Mth.square(relUpdraftTop.z) * zMW);
            double bestDist = Math.min(shearedDistanceToUpdraftTop, westShearedDistanceToUpdraft);
            double topSize = 15000.0 * (double)stormStrength;
            double minUpdraftWidth = 2500.0 + (double)this.width * 1.25;
            double bottomSize = topSize / 2.5;
            double rainSizeMod = 0.85;
            float rainAdd = 1.0F - (float)Mth.clamp(shearedDistanceToUpdraftTop / (rainSizeMod * topSize * Math.pow((double)stormStrength, 5.0)), 0.0, 1.0);
            rainAdd = (float)Math.pow((double)rainAdd, 0.25) * 0.5F;
            precip += rainAdd;
            rainAdd = 1.0F - (float)Mth.clamp(bestDist / (rainSizeMod * bottomSize), 0.0, 1.0);
            rainAdd = Mth.sqrt(rainAdd) * 0.4F;
            precip += rainAdd;
            float mult = (float)Mth.clamp((fRawLocalPos.x - 2000.0) / -3000.0, 0.0, 1.0) * 0.15F * this.rainIntensity;
            mult *= (float)Mth.clamp((fRawLocalPos.z + 3000.0) / 4000.0, 0.0, 1.0);
            precip *= 1.0F + mult;
            double torDist = pos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
            rainAdd = (float)(Math.sqrt(1.0 - Mth.clamp(torDist / (500.0 * rainSizeMod), 0.0, 1.0)) * (double)Mth.square(stormStrength) * 1.2);
            rainAdd *= Math.min(this.rainIntensity, 0.7F);
            precip = Math.max(precip, rainAdd);
            float p = 1.0F - (float)Mth.clamp((dist - (clipSize - 5000.0)) / 5000.0, 0.0, 1.0);
            precip *= p;
            precip *= 1.2F;
            float cutoff = 0.4F;
            precip = Math.max(precip - cutoff, 0.0F);
            return (float)Math.pow((double)precip, 3.0) * 3.5F * (this.rainIntensity / 2.0F + 0.7F) * stormStrength;
         }
      } else {
         return 0.0F;
      }
   }

   @Override
   public float getHail(Vec3 pos) {
      float hail = this.getPrecipitation(pos);
      hail = Math.max(hail - 0.25F, 0.0F);
      hail *= Math.min((float)this.maxWindspeed / 150.0F, 1.25F) + 1.25F;
      hail *= 1.75F;
      if (this.stage == 2) {
         hail *= (float)this.energy / 100.0F;
      }

      if (this.stage < 2) {
         hail *= 0.0F;
      }

      double dist = pos.multiply(1.0, 0.0, 1.0).distanceTo(this.position.multiply(1.0, 0.0, 1.0));
      hail *= (float)Mth.square(Mth.clamp(dist / (ServerConfig.stormSize / 1.5), 0.0, 1.0));
      hail *= 1.0F - (float)Mth.clamp(dist / (ServerConfig.stormSize * 2.0), 0.0, 1.0);
      return hail * 1.25F;
   }

   @Override
   public Vec3 getTornadicWindVector(Vec3 pos, boolean forParticles) {
      int windfieldWidth = Math.max((int)this.width, 40);
      float rF = forParticles ? 1.0E7F : this.rankineFactor;
      Vec3 spos = this.position;
      if (forParticles && this.lastPosition != null) {
         spos = this.lastPosition;
      }

      double dist = spos.multiply(1.0, 0.0, 1.0).distanceTo(pos.multiply(1.0, 0.0, 1.0));
      float perc = this.getRankine(dist, windfieldWidth, rF);
      float affectPerc = (float)Math.sqrt(1.0 - dist / (double)((float)windfieldWidth * 2.5F));
      Vec3 relativePos = pos.subtract(spos);
      Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
      Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
      double inPerc = Mth.lerp(Mth.clamp((double)Mth.square(affectPerc), 0.0, 1.0), 0.85, 0.15);
      Vec3 rPosNoise = this.rotateV3(relativePos, (double)this.tickCount / 60.0);
      double wNoise = this.FBM(new Vec3(rPosNoise.x / 100.0, rPosNoise.z / 100.0, (double)this.tickCount / 200.0), 5, 2.0F, 0.5F, 1.0F);
      double realWind = (double)this.windspeed * (1.0 + wNoise * 0.1);
      Vec3 motion = rotational.multiply(realWind * (double)perc * (1.0 - inPerc), 0.0, realWind * (double)perc * (1.0 - inPerc))
         .add(inward.multiply(realWind * (double)perc * inPerc, 0.0, realWind * (double)perc * inPerc));
      motion = motion.add(this.velocity.multiply((double)(15.0F * affectPerc), 0.0, (double)(15.0F * affectPerc)));

      for (Vorticy vorticy : this.vorticies) {
         double d = vorticy.getPosition().multiply(1.0, 0.0, 1.0).distanceTo(pos.multiply(1.0, 0.0, 1.0));
         Vec3 rPos = pos.subtract(vorticy.getPosition());
         Vec3 rot = new Vec3(rPos.z, 0.0, -rPos.x).normalize();
         int windWid = (int)((float)windfieldWidth * vorticy.widthPerc);
         float p = this.getRankine(d, windWid, rF);
         float wind = vorticy.windspeedMult * (float)this.windspeed;
         motion = motion.add(rot.multiply((double)(wind * p), 0.0, (double)(wind * p)));
      }

      double oLen = motion.length();
      motion = motion.add(0.0, Mth.square(Math.min(motion.length() / 200.0, 1.0)) * 90.0, 0.0);
      return motion.normalize().scale(oLen);
   }

   private Pair<Double, Double> getNearWindComponents(Vec3 normRelPos) {
      double dist = normRelPos.length();
      double angBaseRot = 0.84;
      double angTotalRot = 0.4;
      double angRot = angBaseRot + angTotalRot;
      Vec3 relPosOff = normRelPos.multiply(1.0, 0.0, -1.0).subtract(0.15 * Math.cos(angRot), 0.0, 0.15 * Math.sin(angRot));
      double sub = 0.7 * Mth.clamp(dist, 0.0, 1.0);
      double distPerc = Mth.clamp(dist * ServerConfig.stormSize * 4.0 / (double)Math.max(Float.isNaN(this.width) ? 0.0F : this.width, 100.0F), 0.0, 1.0);
      double ang = Math.atan2(-relPosOff.z, relPosOff.x);
      ang -= dist * 8.0;
      ang = ++ang + angTotalRot;
      double v = Math.sin(ang);
      v *= Mth.clamp(relPosOff.length() / 0.25, 0.0, 1.0);
      v *= distPerc;
      v *= Math.pow(1.0 - Mth.clamp(dist * 1.5, 0.0, 1.0), 0.75);
      v -= sub;
      double rfd = v;
      double inflow = -v * Mth.square(1.0 - Mth.clamp(dist / 0.34, 0.0, 1.0));
      inflow *= Mth.square(distPerc);
      double x = normRelPos.x + Math.max(-normRelPos.z / 3.0, 0.0);
      double y = normRelPos.z - Math.pow(x, 3.0) / 2.0;
      y *= -1.0;
      double wmax = 0.34 * Math.sqrt(Mth.clamp(1.0 - x, 0.0, 1.0));
      if (y < 0.0) {
         wmax *= 1.0 + Mth.clamp(Math.pow(x, 3.0), 0.0, 1.0);
      } else {
         wmax *= 1.0 + 2.0 * x;
      }

      double inflow2 = Mth.clamp(x, 0.0, 1.0) * Math.sqrt(1.0 - Mth.clamp(Math.abs(y) / wmax, 0.0, 1.0));
      inflow2 *= Mth.clamp(1.0 - x, 0.0, 1.0);
      inflow += 0.1 * inflow2;
      if (v < 0.0 || Double.isNaN(v)) {
         rfd = 0.0;
      }

      if (inflow < 0.0 || Double.isNaN(inflow)) {
         inflow = 0.0;
      }

      return new Pair(Math.pow(rfd, 0.75), Math.pow(inflow * 5.0, 0.5));
   }

   private Vec3 getRFDVector(Vec3 normRelPos) {
      Vec3 weighted = Vec3.ZERO;
      double totalWeight = (Double)this.getNearWindComponents(normRelPos).getA();
      if (totalWeight <= 0.0) {
         return weighted;
      } else {
         for (float i = 0.0F; i < 1.0F; i += 0.1F) {
            double ang = (double)i * Math.PI * 2.0;
            Vec3 sampleOff = new Vec3(Math.sin(ang), 0.0, Math.cos(ang)).scale(0.1);
            double rfd = (Double)this.getNearWindComponents(normRelPos.add(sampleOff)).getA();
            if (!(rfd <= 0.0)) {
               totalWeight += rfd;
               weighted = weighted.add(sampleOff.scale(rfd));
            }
         }

         Vec3 vec = weighted.scale(1.0 / totalWeight);
         vec = vec.add(0.0, 0.07, 0.0);
         return vec.normalize();
      }
   }

   @Override
   public Vec3 getBaseWind(Vec3 pos) {
      Vec3 wind = Vec3.ZERO;
      float smoothStage = (float)this.stage + (float)this.energy / 100.0F;
      Vec3 relativePos = pos.subtract(this.position).multiply(1.0, 0.0, 1.0);
      Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
      Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
      Vec3 normRelPos = relativePos.scale(1.0 / ServerConfig.stormSize);
      normRelPos = normRelPos.scale(0.25);
      double percDist = normRelPos.length();
      Pair<Double, Double> v = this.getNearWindComponents(normRelPos);
      float rfdStrength = Math.max(Mth.clamp(smoothStage - 1.75F, 0.0F, 1.0F) * 60.0F, (float)this.windspeed / 2.0F);
      if ((Double)v.getA() > 0.0 && rfdStrength > 0.0F) {
         Vec3 rfdDir = this.getRFDVector(normRelPos);
         rfdDir = rfdDir.subtract(rotational.scale(0.5)).normalize();
         rfdDir = rfdDir.multiply(1.0, 0.9, 1.0);
         wind = wind.subtract(rfdDir.scale((Double)v.getA() * (double)rfdStrength * 2.25));
      }

      float inflowStrength = rfdStrength / 4.0F;
      inflowStrength += Mth.clamp(smoothStage - 1.75F, 0.0F, 1.0F) * Math.max(60.0F, (float)this.maxWindspeed / 2.0F);
      if ((Double)v.getB() > 0.0 && inflowStrength > 0.0F) {
         Vec3 inflowDir = rotational.add(inward).normalize();
         Vec3 distantVec = new Vec3(-1.0, 0.0, 0.2).normalize();
         double distantDelta = 1.0 - Mth.clamp(percDist / 0.5 + Math.max(-normRelPos.z * 3.0, 0.0), 0.0, 1.0);
         distantDelta = Math.sqrt(distantDelta);
         distantVec = distantVec.lerp(new Vec3(0.3, 0.0, -1.0).normalize(), distantDelta).normalize();
         inflowDir = inflowDir.lerp(distantVec, Mth.square(Mth.clamp(percDist / 0.45, 0.0, 1.0))).normalize();
         wind = wind.add(inflowDir.scale((Double)v.getB() * (double)inflowStrength * 1.9));
      }

      if (Double.isNaN(wind.length())) {
         wind = Vec3.ZERO;
      }

      return wind;
   }

   @OnlyIn(Dist.CLIENT)
   @Override
   public void pull(Particle particle, float multiplier) {
      int windfieldWidth = Math.max((int)this.width, 40);
      BlockPos blockPos = new BlockPos((int)particle.getPos().x, (int)particle.getPos().y, (int)particle.getPos().z);
      int worldHeight = this.level.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY();
      if (worldHeight <= blockPos.getY()) {
         double dist = particle.getPos().distanceTo(new Vec3(this.position.x, particle.getPos().y, this.position.z));
         if (!(dist > (double)windfieldWidth)) {
            Vec3 relativePos = particle.getPos().subtract(this.position);
            double heightDifference = particle.getPos().y - this.position.y;
            if (!(Math.abs(heightDifference) > 150.0)) {
               Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
               Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
               double windEffect = (double)this.getTornadicWind(particle.getPos());
               double effectStrength = Math.clamp(windEffect / (double)Math.max((float)this.windspeed, 130.0F), 0.0, 1.0) * (double)multiplier;
               double pullFactor = 4.0;
               pullFactor -= Math.max(heightDifference, 0.0) / 100.0 * 3.0;
               pullFactor /= (double)Math.max(this.width / 100.0F, 1.0F);
               if (dist <= (double)(this.width / (this.rankineFactor * 2.0F))) {
                  pullFactor = -1.5;
               }

               Vec3 add = inward.multiply(effectStrength * pullFactor, effectStrength * pullFactor, effectStrength * pullFactor)
                  .add(rotational.multiply(effectStrength, effectStrength, effectStrength));
               add = add.add(new Vec3(0.0, effectStrength, 0.0));
               if (particle instanceof ParticleData particleData) {
                  particleData.addVelocity(add.multiply(0.05F, 0.05F, 0.05F));
               }
            }
         }
      }
   }

   @Override
   public void pull(Entity entity, float multiplier) {
      if (entity instanceof ConditionallyPulled conditionallyPulled && !conditionallyPulled.shouldPull(this)) {
         return;
      }

      if (entity.level().isLoaded(entity.blockPosition())) {
         int windfieldWidth = Math.max((int)this.width, 40);
         int worldHeight = this.level.getHeightmapPos(Types.MOTION_BLOCKING, entity.blockPosition()).getY();
         if (worldHeight <= entity.blockPosition().getY()) {
            double dist = entity.position().distanceTo(new Vec3(this.position.x, entity.position().y, this.position.z));
            if (!(dist > (double)windfieldWidth)) {
               Vec3 relativePos = entity.position().subtract(this.position);
               double heightDifference = entity.position().y - this.position.y;
               if (!(Math.abs(heightDifference) > 150.0)) {
                  Vec3 inward = new Vec3(-relativePos.x, 0.0, -relativePos.z).normalize();
                  Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
                  double windEffect = (double)this.getTornadicWind(entity.position());
                  if (!(windEffect < 60.0)) {
                     double effectStrength = Math.clamp((windEffect - 60.0) / (double)Math.max((float)this.windspeed * 1.2F, 130.0F), 0.0, 1.0)
                        * (double)multiplier
                        * 1.5;
                     double pullFactor = 4.0;
                     pullFactor -= Math.max(heightDifference, 0.0) / 65.0 * 3.0;
                     boolean isMovingBlock = entity instanceof MovingBlock;
                     if (dist <= (double)(this.width / this.rankineFactor) / 1.25 && !isMovingBlock) {
                        pullFactor = -1.5;
                     }

                     if (isMovingBlock) {
                        pullFactor *= 1.15F;
                     }

                     Vec3 add = inward.multiply(effectStrength * pullFactor, effectStrength * pullFactor, effectStrength * pullFactor)
                        .add(rotational.multiply(effectStrength, effectStrength, effectStrength));
                     add = add.add(new Vec3(0.0, effectStrength * 1.25, 0.0));
                     if (entity instanceof ConditionallyPulled conditionallyPulled && conditionallyPulled.onPull(this, add.scale(0.05F))) {
                        return;
                     }

                     if (isMovingBlock) {
                        add = add.multiply(0.4, 0.2, 0.4);
                     }

                     entity.addDeltaMovement(add.multiply(0.05F, 0.05F, 0.05F));
                     Vec3 motion = entity.getDeltaMovement();
                     if (motion.y > -0.25) {
                        entity.fallDistance = 0.0F;
                     }
                  }
               }
            }
         }
      }
   }
}
