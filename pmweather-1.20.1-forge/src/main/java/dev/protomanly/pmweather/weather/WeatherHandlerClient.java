package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.MetarData;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.particle.ParticleCube;
import dev.protomanly.pmweather.sound.FireSoundStreamingSource;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.tags.ModTags;
import dev.protomanly.pmweather.weather.effects.ClientLightning;
import dev.protomanly.pmweather.weather.effects.Lightning;
import dev.protomanly.pmweather.weather.effects.PowerFlash;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormType;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class WeatherHandlerClient extends WeatherHandler {
   public List<ClientLightning> lightnings = new ArrayList<>();
   public List<PowerFlash> powerFlashes = new ArrayList<>();
   public List<VolumetricSmokeParticle> smokeParticles = new ArrayList<>();
   public MetarData metarData;
   public FireSoundStreamingSource fireLow = null;
   public FireSoundStreamingSource fireMid = null;
   public FireSoundStreamingSource fireHigh = null;
   public float cachedPlayerTemp = 0.0F;

   public WeatherHandlerClient(ResourceKey<Level> dimension) {
      super(dimension);
      this.metarData = new MetarData(this.getWorld());
   }

   public boolean fireAudiosOn() {
      return this.fireLow != null && this.fireMid != null && this.fireHigh != null;
   }

   public void killFireAudios() {
      if (this.fireAudiosOn()) {
         this.fireLow.cleanup();
         this.fireMid.cleanup();
         this.fireHigh.cleanup();
         this.fireLow = null;
         this.fireMid = null;
         this.fireHigh = null;
      }
   }

   public void createFireAudios() {
      if (!this.fireAudiosOn()) {
         this.killFireAudios();
         this.fireLow = new FireSoundStreamingSource(ModSounds.FIRE_LOW.get(), SoundSource.WEATHER, 0.15F, 1.0F);
         this.fireMid = new FireSoundStreamingSource(ModSounds.FIRE_MID.get(), SoundSource.WEATHER, 0.15F, 2.0F);
         this.fireHigh = new FireSoundStreamingSource(ModSounds.FIRE_HIGH.get(), SoundSource.WEATHER, 0.15F, 3.5F);
         this.fireLow.volMultiplier = 0.65F;
         this.fireMid.volMultiplier = 0.8F;
         this.fireLow.start(Minecraft.getInstance());
         this.fireMid.start(Minecraft.getInstance());
         this.fireHigh.start(Minecraft.getInstance());
      }
   }

   @Override
   public Level getWorld() {
      return Minecraft.getInstance().level;
   }

   public float getHail() {
      Player player = Minecraft.getInstance().player;
      return player == null ? 0.0F : this.getHail(player.position());
   }

   public float getPrecipitation() {
      Player player = Minecraft.getInstance().player;
      return player == null ? 0.0F : this.getPrecipitation(player.position());
   }

   public VolumetricSmokeParticle spawnSmoke(Vec3 pos, float intensity) {
      return this.spawnSmoke(
         pos,
         Mth.clamp(intensity * 30.0F, 50.0F, 200.0F),
         Mth.clamp(intensity, 0.0F, 1.0F),
         Math.min(intensity, 5.0F),
         Mth.clamp(Mth.ceil(intensity * 6.0F) * 20, 400, 2400)
      );
   }

   public VolumetricSmokeParticle spawnSmoke(Vec3 pos, float maxSize, float maxOpacity, float brightness, int lifetime) {
      VolumetricSmokeParticle particle = new VolumetricSmokeParticle(this.getWorld(), pos, maxSize, maxOpacity, brightness, lifetime);
      this.smokeParticles.add(particle);
      if (this.smokeParticles.size() >= 55) {
         this.smokeParticles.getFirst().startDeath();
      }

      if (this.smokeParticles.size() > 64) {
         this.smokeParticles.getFirst().kill();
      }

      return particle;
   }

   @Override
   public void tick() {
      super.tick();
      if (Minecraft.getInstance().player != null) {
         this.cachedPlayerTemp = ThermodynamicEngine.samplePoint(this, Minecraft.getInstance().player.position(), this.getWorld(), null, 0).temperature();
      }

      Iterator<Lightning.ClientLightningFuture> lightningFuturesIterator = Lightning.CLIENT_LIGHTNING_FUTURES.iterator();

      while (lightningFuturesIterator.hasNext()) {
         Lightning.ClientLightningFuture future = lightningFuturesIterator.next();
         if (future.level() != this.getWorld()) {
            lightningFuturesIterator.remove();
         } else if (this.getWorld().getGameTime() >= future.atTick()) {
            this.strike(future);
            lightningFuturesIterator.remove();
         }
      }

      Iterator<VolumetricSmokeParticle> smokeIterator = this.smokeParticles.iterator();

      while (smokeIterator.hasNext()) {
         VolumetricSmokeParticle particle = smokeIterator.next();
         if (!particle.dead && particle.level == this.getWorld()) {
            particle.tick();
         } else {
            smokeIterator.remove();
         }
      }

      Iterator<ClientLightning> iterator = this.lightnings.iterator();

      while (iterator.hasNext()) {
         Lightning lightning = iterator.next();
         if (!lightning.dead && lightning.level == this.getWorld()) {
            lightning.tick();
         } else {
            iterator.remove();
         }
      }

      Iterator<PowerFlash> powerFlashIterator = this.powerFlashes.iterator();

      while (powerFlashIterator.hasNext()) {
         PowerFlash powerFlash = powerFlashIterator.next();
         if (!powerFlash.dead && powerFlash.level == this.getWorld()) {
            powerFlash.tick();
         } else {
            powerFlashIterator.remove();
         }
      }

      Entity cameraEntity = Minecraft.getInstance().cameraEntity;
      Level level = this.getWorld();
      if (cameraEntity != null) {
         ChunkPos chunkPos = cameraEntity.chunkPosition();
         float maxFireIntensity = 0.0F;
         int inLevel = 0;
         Vec3 position = Vec3.ZERO;
         double fullWeight = 0.0;
         float highInt = 0.0F;
         float midInt = 0.0F;
         float lowInt = 0.0F;

         for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
               ChunkPos offsetPos = new ChunkPos(chunkPos.x + x, chunkPos.z + z);
               ChunkAccess chunkAccess = level.getChunk(offsetPos.x, offsetPos.z);
               Vec3 chunkMidPos = offsetPos.getMiddleBlockPosition(65).getCenter();
               double distance = chunkPos.getMiddleBlockPosition(65).getCenter().distanceTo(chunkMidPos);
               distance = Math.max(distance - 23.0, 0.0);
               distance /= 23.0;
               distance++;
               float intensity = (Float)chunkAccess.getData(DataAttachments.STABLE_FIRE_INTENSITY);
               float fireIntensity = intensity / (float)Math.pow(distance, 2.0);
               float high = Mth.clamp(intensity - 2.5F, 0.0F, 10.0F) / (float)Math.pow(distance, 1.5);
               float mid = Mth.clamp(intensity - 1.5F, 0.0F, 2.0F) / (float)Math.pow(distance, 2.0);
               float low = Mth.clamp(intensity - 0.5F, 0.0F, 1.0F) / (float)Math.pow(distance, 2.5);
               highInt = Math.max(highInt, high);
               midInt = Math.max(midInt, mid);
               lowInt = Math.max(lowInt, low);
               int fireLevel = 0;
               if (distance <= 8.0) {
                  if (intensity > 0.5F) {
                     fireLevel = 1;
                  }

                  if (intensity > 1.5F) {
                     fireLevel = 2;
                  }

                  if (intensity > 2.5F) {
                     fireLevel = 3;
                  }
               }

               if (fireLevel > inLevel) {
                  inLevel = fireLevel;
               }

               if (fireIntensity > maxFireIntensity) {
                  maxFireIntensity = fireIntensity;
               }

               fullWeight += (double)intensity;
               position = position.add(chunkMidPos.scale((double)intensity));
            }
         }

         position = position.scale(1.0 / fullWeight);
         if (fullWeight <= 0.01F) {
            position = cameraEntity.position();
         }

         position = new Vec3(position.x, cameraEntity.position().y, position.z);
         boolean playing = lowInt > 0.0F || midInt > 0.0F || highInt > 0.0F;
         if (this.fireAudiosOn() && (this.fireLow.getVolume() > 0.0F || this.fireMid.getVolume() > 0.0F || this.fireHigh.getVolume() > 0.0F)) {
            playing = true;
         }

         if (playing) {
            if (this.fireAudiosOn()) {
               this.fireLow.fireIntensity = lowInt;
               this.fireLow.position = inLevel > 0 ? cameraEntity.position() : position;
               this.fireLow.instantPos = inLevel > 0;
               this.fireMid.fireIntensity = midInt;
               this.fireMid.position = inLevel > 1 ? cameraEntity.position() : position;
               this.fireMid.instantPos = inLevel > 1;
               this.fireHigh.fireIntensity = highInt;
               this.fireHigh.position = inLevel > 2 ? cameraEntity.position() : position;
               this.fireHigh.instantPos = inLevel > 2;
            } else {
               this.createFireAudios();
            }
         } else if (this.fireAudiosOn()) {
            this.killFireAudios();
         }
      }
   }

   public void nbtSyncFromServer(CompoundTag compoundTag) {
      String command = compoundTag.getString("command");
      if (command.equals("syncStormNew")) {
         CompoundTag stormCompoundTag = compoundTag.getCompound("data");
         long ID = stormCompoundTag.getLong("ID");
         PMWeather.LOGGER.debug("syncStormNew, ID: {}", ID);
         int format = 0;
         if (stormCompoundTag.contains("format")) {
            format = stormCompoundTag.getInt("format");
         }

         StormType stormType;
         if (format == 0) {
            stormType = StormTypes.getFromLegacyID(stormCompoundTag.getInt("stormType"));
         } else {
            stormType = StormTypes.getFromID(stormCompoundTag.getString("stormType"));
         }

         Storm storm = stormType.create(new StormSpawnProperties(this, this.getWorld(), Vec3.ZERO, null));
         storm.getNBTCache().setNewNBT(stormCompoundTag);
         storm.nbtSyncFromServer();
         storm.getNBTCache().updateCacheFromNew();
         this.addStorm(storm);
      } else if (command.equals("syncStormRemove")) {
         CompoundTag stormCompoundTagx = compoundTag.getCompound("data");
         long IDx = stormCompoundTagx.getLong("ID");
         Storm storm = this.lookupStormByID.get(IDx);
         if (storm != null) {
            this.removeStorm(IDx);
         }
      } else if (command.equals("syncStormUpdate")) {
         CompoundTag stormCompoundTagx = compoundTag.getCompound("data");
         long IDx = stormCompoundTagx.getLong("ID");
         Storm storm = this.lookupStormByID.get(IDx);
         if (storm != null) {
            storm.getNBTCache().setNewNBT(stormCompoundTagx);
            storm.nbtSyncFromServer();
            storm.getNBTCache().updateCacheFromNew();
         }
      } else if (command.equals("syncBlockParticleNew")) {
         if ((double)PMWeather.RANDOM.nextFloat() > ClientConfig.debrisParticleDensity) {
            return;
         }

         CompoundTag nbt = compoundTag.getCompound("data");
         Vec3 pos = new Vec3((double)nbt.getInt("positionX"), (double)(nbt.getInt("positionY") + 1), (double)nbt.getInt("positionZ"));
         BlockState state = NbtUtils.readBlockState(this.getWorld().holderLookup(Registries.BLOCK), nbt.getCompound("blockstate"));
         long stormID = nbt.getLong("stormID");
         Storm storm = this.lookupStormByID.get(stormID);
         if (storm != null) {
            ParticleCube debris = new ParticleCube(
               (ClientLevel)this.getWorld(),
               pos.x + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F),
               pos.y + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F),
               pos.z + (double)((PMWeather.RANDOM.nextFloat() - PMWeather.RANDOM.nextFloat()) * 3.0F),
               0.0,
               0.0,
               0.0,
               state
            );
            GameBusClientEvents.particleBehavior.initParticleCube(debris);
            storm.listParticleDebris.add(debris);
            debris.ignoreWind = true;
            debris.renderRange = 256.0F;
            debris.spawnAsDebrisEffect();
         }
      } else if (command.equals("syncLightningNew")) {
         CompoundTag nbt = compoundTag.getCompound("data");
         Lightning.ClientLightningFuture strike = new Lightning.ClientLightningFuture(
            this.getWorld(),
            new Vec3(nbt.getDouble("positionX"), nbt.getDouble("positionY"), nbt.getDouble("positionZ")),
            nbt.getFloat("strength"),
            this.getWorld().getGameTime() + 60L
         );
         this.preStrike(strike);
         Lightning.CLIENT_LIGHTNING_FUTURES.add(strike);
      } else if (command.equals("syncPowerFlashNew")) {
         CompoundTag nbt = compoundTag.getCompound("data");
         this.powerFlashes
            .add(
               new PowerFlash(
                  new Vec3(nbt.getDouble("positionX"), nbt.getDouble("positionY"), nbt.getDouble("positionZ")), this.getWorld(), nbt.getFloat("voltage")
               )
            );
      }
   }

   public void preStrike(Lightning.ClientLightningFuture strike) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         double dist = player.position().distanceTo(strike.pos());
         if (!(dist > 40.0)) {
            ClientLevel level = (ClientLevel)strike.level();
            BlockPos strikePos = BlockPos.containing(strike.pos());
            float sqrtStrength = Mth.sqrt(strike.strength());
            int size = Mth.floor(7.5 * (double)sqrtStrength);

            for (int x = -size; x <= size; x++) {
               for (int y = -size * 4; y <= size * 4; y++) {
                  for (int z = -size; z <= size; z++) {
                     BlockPos offPos = strikePos.offset(x, y, z);
                     double d = offPos.getCenter().multiply(1.0, 0.25, 1.0).distanceTo(strikePos.getCenter().multiply(1.0, 0.25, 1.0));
                     if (!(d > (double)((float)size * 2.0F))
                        && !((double)level.random.nextFloat() < Mth.square(Mth.clamp(d / (double)((float)size * 3.0F), 0.0, 1.0)))) {
                        BlockState state = strike.level().getBlockState(offPos);
                        if (state.is(ModTags.Blocks.CONDUCTIVE)) {
                           level.playLocalSound(
                              offPos, ModSounds.METAL_HUM.get(), SoundSource.WEATHER, sqrtStrength * 0.65F, level.random.nextFloat() * 0.5F + 1.0F, false
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void strike(Lightning.ClientLightningFuture strike) {
      ClientLightning lightning = new ClientLightning(strike.pos(), this.getWorld(), strike.strength());
      this.lightnings.add(lightning);
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         float sqrtStrength = Mth.sqrt(strike.strength());
         double dist = player.position().multiply(1.0, 0.0, 1.0).distanceTo(strike.pos().multiply(1.0, 0.0, 1.0));
         if (dist < 64.0) {
            RandomSource rand = strike.level().random;
            int particles = Mth.ceil((float)rand.nextInt(40, 100) * sqrtStrength);

            for (int i = 0; i < particles; i++) {
               float yPerc = rand.nextFloat();
               Vec3 p = strike.pos()
                  .add(
                     (double)((rand.nextFloat() - 0.5F) * sqrtStrength * 5.0F * Mth.sqrt(yPerc)),
                     (double)(yPerc * sqrtStrength * 1.5F),
                     (double)((rand.nextFloat() - 0.5F) * sqrtStrength * 5.0F * Mth.sqrt(yPerc))
                  );
               Vec3 v = new Vec3(
                  (double)((rand.nextFloat() - 0.5F) * sqrtStrength * 2.0F),
                  (double)(rand.nextFloat() * 0.2F * sqrtStrength),
                  (double)((rand.nextFloat() - 0.5F) * sqrtStrength * 2.0F)
               );
               strike.level().addAlwaysVisibleParticle((ParticleOptions)ModParticleTypes.SPARK.get(), true, p.x, p.y, p.z, v.x, v.y, v.z);
            }
         }

         dist /= (double)sqrtStrength;
         if (strike.strength() > 9.0F) {
            if (dist > 400.0) {
               this.getWorld()
                  .playLocalSound(
                     strike.pos().x,
                     strike.pos().y,
                     strike.pos().z,
                     (SoundEvent)ModSounds.POSITIVE_FAR.value(),
                     SoundSource.WEATHER,
                     5000.0F * sqrtStrength,
                     PMWeather.RANDOM.nextFloat(0.8F, 1.0F),
                     true
                  );
            } else if (dist > 40.0) {
               this.getWorld()
                  .playLocalSound(
                     strike.pos().x,
                     strike.pos().y,
                     strike.pos().z,
                     (SoundEvent)ModSounds.POSITIVE_NEAR.value(),
                     SoundSource.WEATHER,
                     5000.0F * sqrtStrength,
                     PMWeather.RANDOM.nextFloat(0.8F, 1.0F),
                     true
                  );
            } else {
               this.getWorld()
                  .playLocalSound(
                     strike.pos().x,
                     strike.pos().y,
                     strike.pos().z,
                     (SoundEvent)ModSounds.POSITIVE_NEXT.value(),
                     SoundSource.WEATHER,
                     2500.0F * sqrtStrength,
                     PMWeather.RANDOM.nextFloat(0.8F, 1.1F),
                     false
                  );
            }

            return;
         }

         if (dist > 400.0) {
            this.getWorld()
               .playLocalSound(
                  strike.pos().x,
                  strike.pos().y,
                  strike.pos().z,
                  (SoundEvent)ModSounds.NEGATIVE_FAR.value(),
                  SoundSource.WEATHER,
                  5000.0F * sqrtStrength,
                  PMWeather.RANDOM.nextFloat(0.8F, 1.0F),
                  true
               );
         } else if (dist > 40.0) {
            this.getWorld()
               .playLocalSound(
                  strike.pos().x,
                  strike.pos().y,
                  strike.pos().z,
                  (SoundEvent)ModSounds.NEGATIVE_NEAR.value(),
                  SoundSource.WEATHER,
                  5000.0F * sqrtStrength,
                  PMWeather.RANDOM.nextFloat(0.8F, 1.0F),
                  true
               );
         } else {
            this.getWorld()
               .playLocalSound(
                  strike.pos().x,
                  strike.pos().y,
                  strike.pos().z,
                  (SoundEvent)ModSounds.NEGATIVE_NEXT.value(),
                  SoundSource.WEATHER,
                  2500.0F * sqrtStrength,
                  PMWeather.RANDOM.nextFloat(0.8F, 1.1F),
                  false
               );
         }
      }
   }
}
