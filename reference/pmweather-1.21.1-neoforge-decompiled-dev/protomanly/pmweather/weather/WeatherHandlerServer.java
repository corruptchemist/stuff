package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.data.ServerMetarData;
import dev.protomanly.pmweather.interfaces.IServerLevel;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.util.CachedNBTTagCompound;
import dev.protomanly.pmweather.weather.effects.Lightning;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public class WeatherHandlerServer extends WeatherHandler {
   private final ServerLevel level;
   public final ServerMetarData metarData;

   public WeatherHandlerServer(ServerLevel level) {
      super(level.dimension());
      this.level = level;
      this.seed = level.getSeed();
      this.metarData = new ServerMetarData(level);
      this.metarData.read();
   }

   @Override
   public Level getWorld() {
      return this.level;
   }

   @Override
   public void tick() {
      super.tick();
      Iterator<Lightning.LightningFuture> lightningFuturesIterator = Lightning.LIGHTNING_FUTURES.iterator();

      while (lightningFuturesIterator.hasNext()) {
         Lightning.LightningFuture strike = lightningFuturesIterator.next();
         if (strike.level() == this.level && strike.atTick() <= this.level.getGameTime()) {
            strike.strike();
            lightningFuturesIterator.remove();
         }
      }

      if (this.level.getGameTime() % 200L == 1L) {
         this.metarData.syncToPlayers();
      }

      if (this.level.getGameTime() % 600L == 0L) {
         this.metarData.validateAll();
      }
   }

   public void syncStormRemove(Storm storm) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncStormRemove");
      storm.nbtSyncForClient();
      data.put("data", storm.getNBTCache().getNewNBT());
      data.getCompound("data").putBoolean("removed", true);
      ModNetworking.serverSendToClientDimension(data, this.getWorld());
   }

   public void syncStormNew(Storm storm) {
      this.syncStormNew(storm, null);
   }

   public void syncLightningNew(Vec3 pos) {
      float strength = this.level.random.nextFloat() * 2.0F + 1.0F;
      if (this.level.random.nextInt(100) == 0) {
         strength *= 10.0F;
      }

      this.syncLightningNew(pos, strength);
   }

   public void syncLightningNew(Vec3 pos, float strength) {
      BlockPos bPos = BlockPos.containing(pos);
      boolean rod = false;
      if (this.level.isLoaded(bPos)) {
         Optional<BlockPos> lightningRod = ((IServerLevel)this.level).pmwfindLightningRod(bPos);
         boolean checkForEntities = true;
         boolean rodIgnored = false;
         if (lightningRod.isPresent()) {
            if (this.level.random.nextInt(25) == 0) {
               rodIgnored = true;
            } else {
               pos = lightningRod.get().getCenter();
               checkForEntities = this.level.random.nextInt(10) == 0;
            }
         }

         if (checkForEntities) {
            Vec3 lPos = pos;
            List<? extends LivingEntity> struckEntities = this.level.getEntities(EntityTypeTest.forClass(LivingEntity.class), entity -> {
               if (!this.level.canSeeSky(entity.blockPosition())) {
                  return false;
               } else {
                  double heightDif = entity.getEyePosition().y - lPos.y;
                  double dist = entity.position().multiply(1.0, 0.0, 1.0).distanceTo(lPos.multiply(1.0, 0.0, 1.0));
                  return heightDif < dist / 2.0 ? false : entity.position().multiply(1.0, 0.0, 1.0).distanceTo(lPos.multiply(1.0, 0.0, 1.0)) < 32.0;
               }
            });
            if (!struckEntities.isEmpty()) {
               pos = struckEntities.get(this.level.random.nextInt(struckEntities.size())).getEyePosition();
            }
         }

         rod = lightningRod.isPresent() && !rodIgnored;
      }

      strength = Math.min(strength, 20.0F);
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncLightningNew");
      CompoundTag compoundTag = new CompoundTag();
      compoundTag.putDouble("positionX", pos.x);
      compoundTag.putDouble("positionY", pos.y);
      compoundTag.putDouble("positionZ", pos.z);
      compoundTag.putFloat("strength", strength);
      data.put("data", compoundTag);
      Lightning.LIGHTNING_FUTURES.add(new Lightning.LightningFuture(this.level, BlockPos.containing(pos), rod, strength, this.level.getGameTime() + 60L));
      ModNetworking.serverSendToClientNear(data, pos, 6144.0, this.level);
   }

   public void syncPowerFlashNew(Vec3 pos, float voltage) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncPowerFlashNew");
      CompoundTag compoundTag = new CompoundTag();
      compoundTag.putDouble("positionX", pos.x);
      compoundTag.putDouble("positionY", pos.y);
      compoundTag.putDouble("positionZ", pos.z);
      compoundTag.putFloat("voltage", voltage);
      data.put("data", compoundTag);
      ModNetworking.serverSendToClientNear(data, pos, 2048.0, this.level);
   }

   public void syncStormNew(Storm storm, @Nullable ServerPlayer player) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncStormNew");
      CachedNBTTagCompound cache = storm.getNBTCache();
      cache.setUpdateForced(true);
      storm.nbtSyncForClient();
      cache.setUpdateForced(false);
      data.put("data", cache.getNewNBT());
      if (player == null) {
         ModNetworking.serverSendToClientDimension(data, storm.level);
      } else {
         ModNetworking.serverSendToClientPlayer(data, player);
      }
   }

   public void syncStormUpdate(Storm storm) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncStormUpdate");
      storm.getNBTCache().setNewNBT(new CompoundTag());
      storm.nbtSyncForClient();
      data.put("data", storm.getNBTCache().getNewNBT());
      ModNetworking.serverSendToClientDimension(data, this.getWorld());
   }

   public void playerJoinedWorldSyncFull(ServerPlayer player) {
      if (this.getWorld() instanceof ServerLevel serverLevel) {
         CompoundTag data = new CompoundTag();
         data.putString("packetCommand", "LevelData");
         data.putString("command", "syncMisc");
         data.putLong("seed", this.seed);
         ModNetworking.serverSendToClientPlayer(data, player);

         for (Storm storm : this.getStorms()) {
            this.syncStormNew(storm, player);
         }

         this.metarData.syncToPlayer(player);
      }
   }

   public void clearAllStorms() {
      for (Storm storm : this.getStorms()) {
         storm.remove();
         this.syncStormRemove(storm);
      }

      this.getStorms().clear();
      this.lookupStormByID.clear();
      this.lookupStormByUUID.clear();
   }

   public void syncBlockParticleNew(BlockPos pos, BlockState state, Storm storm) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherData");
      data.putString("command", "syncBlockParticleNew");
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("positionX", pos.getX());
      nbt.putInt("positionY", pos.getY());
      nbt.putInt("positionZ", pos.getZ());
      nbt.put("blockstate", NbtUtils.writeBlockState(state));
      nbt.putLong("stormID", storm.ID);
      data.put("data", nbt);
      ModNetworking.serverSendToClientNear(data, new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()), 356.0, this.level);
   }
}
