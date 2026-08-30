package dev.protomanly.pmweather.weather.storms;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.interfaces.ConditionallyPulled;
import dev.protomanly.pmweather.interfaces.ParticleData;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.sound.MovingSoundStreamingSource;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.Vorticy;
import dev.protomanly.pmweather.weather.WindEngine;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class FireWhirl extends Storm {
   public FireWhirl(StormSpawnProperties properties) {
      super(properties);
      this.growthSpeed = PMWeather.RANDOM.nextInt(4, 12);
      this.maxWidth = PMWeather.RANDOM.nextInt(2, 8);
   }

   @Override
   public void recalc(@Nullable Float risk) {
      super.recalc(risk);
      this.growthSpeed = PMWeather.RANDOM.nextInt(4, 12);
      this.maxWidth = PMWeather.RANDOM.nextInt(2, 8);
   }

   @Override
   public void calcRain() {
      this.rainIntensity = 0.0F;
   }

   @Override
   public void iterateVorticies() {
      if ((float)this.windspeed >= 40.0F) {
         this.tornadoOnGroundTicks++;
      }
   }

   @Override
   public BlockPos getBlockPos() {
      BlockPos blockPos = super.getBlockPos();
      if (!this.shouldLoadChunks()) {
         return blockPos;
      } else {
         if (!this.level.isClientSide()) {
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
      this.position = new Vec3(this.position.x, Mth.lerp(0.05F, this.position.y, (double)blockPos.getY()), this.position.z);
   }

   @Override
   public void doLightning() {
   }

   @Override
   public void doGrowth() {
      int gs = this.growthSpeed / 2;
      if (this.tickCount % gs == 0) {
         ChunkAccess chunkAccess = this.level.getChunkAt(new BlockPos((int)this.position.x, (int)this.position.y, (int)this.position.z));
         float fireIntensity = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);
         int maxWindspeedFI = (int)Math.min(Math.max(fireIntensity - 3.0F, 0.0F) * 14.0F, 100.0F);
         maxWindspeedFI -= 7;
         if (this.windspeed > maxWindspeedFI && this.level.random.nextInt(3) == 0) {
            this.windspeed--;
         } else if (this.windspeed < maxWindspeedFI) {
            this.windspeed++;
         }

         if (this.windspeed < -5) {
            this.dead = true;
         }
      }
   }

   @Override
   public void doWidth() {
      this.width = (float)this.maxWidth;
   }

   @Override
   public void move() {
      Vec3 vel = this.velocity.multiply(0.05F, 0.05F, 0.05F).multiply(2.0, 0.0, 2.0);
      if (!this.aimedAtPlayer) {
         vel = vel.add(new Vec3(0.0, 0.0, -3.0).multiply((double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion), (double)(0.05F * this.occlusion)));
      }

      this.position = this.position.add(vel);
      if (!this.aimedAtPlayer) {
         this.velocity = this.velocity.multiply(0.985F, 0.985F, 0.985F);
         Vec3 baseWind = WindEngine.getWind(
            new Vec3(this.position.x, (double)(this.level.getMaxBuildHeight() + 1), this.position.z), this.level, false, true, false, true
         );
         float factor = 0.01F;
         Vec3 velAdd = new Vec3(baseWind.x, 0.0, baseWind.z).multiply((double)factor, 0.0, (double)factor);
         this.velocity = this.velocity.add(velAdd.multiply(0.05F, 0.05F, 0.05F));
      }
   }

   @Override
   public boolean ignoresCharred() {
      return true;
   }

   @Override
   public Vec3 getTornadicWindVector(Vec3 pos, boolean forParticles) {
      int windfieldWidth = Math.max((int)this.width, !forParticles ? 20 : 40);
      float rF = forParticles ? 1.0E7F : this.rankineFactor;
      Vec3 spos = this.position;
      if (forParticles && this.lastPosition != null) {
         spos = this.lastPosition;
      }

      double dist = spos.multiply(1.0, 0.0, 1.0).distanceTo(pos.multiply(1.0, 0.0, 1.0));
      float perc = this.getRankine(dist, windfieldWidth, rF);
      float affectPerc = (float)Math.sqrt(1.0 - dist / (double)((float)windfieldWidth * 2.0F));
      if (!forParticles) {
         affectPerc = 0.0F;
      }

      Vec3 relativePos = pos.subtract(spos);
      Vec3 rotational = new Vec3(relativePos.z, 0.0, -relativePos.x).normalize();
      Vec3 rPosNoise = this.rotateV3(relativePos, (double)this.tickCount / 60.0);
      double wNoise = this.FBM(new Vec3(rPosNoise.x / 100.0, rPosNoise.z / 100.0, (double)this.tickCount / 200.0), 5, 2.0F, 0.5F, 1.0F);
      double realWind = (double)this.windspeed * (1.0 + wNoise * 0.1);
      if (!forParticles) {
         realWind *= 1.5;
      }

      Vec3 motion = rotational.multiply(realWind * (double)perc, 0.0, realWind * (double)perc);
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

      return motion;
   }

   @OnlyIn(Dist.CLIENT)
   @Override
   public void pull(Particle particle, float multiplier) {
      int windfieldWidth = Math.max((int)this.width, 20);
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
      }

      int windfieldWidth = Math.max((int)this.width, 20);
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
                  if (dist <= (double)(this.width / this.rankineFactor)) {
                     pullFactor = -1.5;
                  }

                  Vec3 add = inward.multiply(effectStrength * pullFactor, effectStrength * pullFactor, effectStrength * pullFactor)
                     .add(rotational.multiply(effectStrength, effectStrength, effectStrength));
                  add = add.add(new Vec3(0.0, effectStrength, 0.0));
                  if (entity instanceof ConditionallyPulled conditionallyPulled && conditionallyPulled.onPull(this, add.scale(0.05F))) {
                     return;
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

   @Override
   public int getUpdateRate() {
      return super.getUpdateRate() / 4;
   }

   @OnlyIn(Dist.CLIENT)
   @Override
   public void tickClient() {
      super.tickClient();
      Player player = Minecraft.getInstance().player;
      if (player != null && this.level instanceof ClientLevel clientLevel) {
         this.smoothWindspeed = Mth.lerp(0.1F, this.smoothWindspeed, (float)this.windspeed);
         this.smoothWidth = Mth.lerp(0.05F, this.smoothWidth, this.width);
         float intenseMod = Math.min((float)this.windspeed / 15.0F, 2.0F);

         for (int i = 0; i < this.windspeed / 10; i++) {
            double y = this.position.y() + 0.5;
            double x = this.position.x() + (this.level.random.nextDouble() - 0.5) * (double)Math.max(this.width, 20.0F) * 0.5;
            double z = this.position.z() + (this.level.random.nextDouble() - 0.5) * (double)Math.max(this.width, 20.0F) * 0.5;
            double flameIntensity = this.level.random.nextDouble() * Math.pow((double)intenseMod, 1.25) * 0.35 + 0.15;
            clientLevel.addAlwaysVisibleParticle(
               (ParticleOptions)ModParticleTypes.FLAME.get(),
               true,
               x,
               y,
               z,
               this.level.random.nextDouble() * (double)intenseMod * 0.1,
               flameIntensity,
               this.level.random.nextDouble() * (double)intenseMod * 0.1
            );
         }

         if ((this.tornadicWind == null || this.tornadicWind.isStopped()) && !this.dead) {
            this.tornadicWind = new MovingSoundStreamingSource(
               this, (SoundEvent)ModSounds.TORNADIC_WIND.value(), SoundSource.WEATHER, 0.1F, 1.0F, this.width, true, 1
            );
            Minecraft.getInstance().getSoundManager().play(this.tornadicWind);
         }

         if (this.windspeed >= 40 && !player.isCreative() && !player.isSpectator()) {
            this.pull(player, 2.5F);
         }
      }
   }

   @Override
   public void tickServer(BlockPos blockPos) {
      super.tickServer(blockPos);
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
               int x = (int)(PMWeather.RANDOM.nextFloat() * (float)windfieldWidth * 2.0F - (float)windfieldWidth);
               int z = (int)(PMWeather.RANDOM.nextFloat() * (float)windfieldWidth * 2.0F - (float)windfieldWidth);
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
                     if (this.level.isInWorldBounds(bPos)) {
                        BlockPos blockPosTop = this.level.getHeightmapPos(Types.MOTION_BLOCKING, bPos).below();
                        double windEffect = (double)this.getTornadicWind(blockPosTop.getCenter());
                        if (!(windEffect < 40.0)) {
                           ChunkPos chunkPos = new ChunkPos(
                              SectionPos.blockToSectionCoord(blockPosTop.getX()), SectionPos.blockToSectionCoord(blockPosTop.getZ())
                           );
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

   @Override
   public boolean hasRadarRepresentation() {
      return false;
   }

   @Override
   public boolean hasPrecipitation() {
      return false;
   }

   @Override
   public boolean isTornadic() {
      return true;
   }
}
