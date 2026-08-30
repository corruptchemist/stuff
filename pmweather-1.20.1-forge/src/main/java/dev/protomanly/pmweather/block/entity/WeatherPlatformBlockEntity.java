package dev.protomanly.pmweather.block.entity;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.weather.Sounding;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WeatherPlatformBlockEntity extends BlockEntity {
   public Vec3 velocity = Vec3.ZERO;
   public Vec3 position = Vec3.ZERO;
   public Vec3 target = Vec3.ZERO;
   public boolean active = false;
   public int lastSample = 0;
   public Sounding sounding = null;
   private int tickCount = 0;
   private final boolean debug = false;

   public WeatherPlatformBlockEntity(BlockPos pos, BlockState blockState) {
      super(ModBlockEntities.WEATHER_PLATFORM_BE.get(), pos, blockState);
   }

   public CompoundTag serializeNBT(BlockPos blockPos) {
      CompoundTag data = new CompoundTag();
      data.put("blockPos", NbtUtils.writeBlockPos(blockPos));
      data.putDouble("velocityX", this.velocity.x);
      data.putDouble("velocityY", this.velocity.y);
      data.putDouble("velocityZ", this.velocity.z);
      data.putDouble("positionX", this.position.x);
      data.putDouble("positionY", this.position.y);
      data.putDouble("positionZ", this.position.z);
      data.putBoolean("active", this.active);
      return data;
   }

   public void deserializeNBT(CompoundTag data) {
      this.velocity = new Vec3(data.getDouble("velocityX"), data.getDouble("velocityY"), data.getDouble("velocityZ"));
      this.target = new Vec3(data.getDouble("positionX"), data.getDouble("positionY"), data.getDouble("positionZ"));
      if (!this.active) {
         this.position = new Vec3(data.getDouble("positionX"), data.getDouble("positionY"), data.getDouble("positionZ"));
      }

      this.active = data.getBoolean("active");
   }

   public void syncAll(Level level, BlockPos blockPos) {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "WeatherPlatform");
      data.putString("command", "sync");
      data.put("data", this.serializeNBT(blockPos));
      ModNetworking.serverSendToClientNear(data, blockPos.getCenter(), 256.0, level);
   }

   public void syncSounding(Level level, BlockPos blockPos) {
      if (this.sounding != null) {
         CompoundTag data = new CompoundTag();
         data.putString("packetCommand", "WeatherPlatform");
         data.putString("command", "syncSounding");
         data.put("data", this.sounding.serializeNBT());
         data.put("blockPos", NbtUtils.writeBlockPos(blockPos));
         ModNetworking.serverSendToClientDimension(data, level);
      }
   }

   public void activate(Level level, BlockPos blockPos, BlockState blockState) {
      if (!level.isClientSide()) {
         PMWeather.LOGGER.debug("Balloon started");
         this.active = true;
         this.position = blockPos.getCenter().add(new Vec3(0.0, 0.5, 0.0));
         this.velocity = Vec3.ZERO;
         this.sounding = new Sounding(GameBusEvents.MANAGERS.get(level.dimension()), this.position);
         BlockPos samplePos = blockPos.above();
         this.sounding.data.put(samplePos.getY(), ThermodynamicEngine.samplePoint(this.sounding.weatherHandler, samplePos.getCenter(), level, null, 0));
         this.lastSample = samplePos.getY();
         this.syncAll(level, blockPos);
         this.syncSounding(level, blockPos);
      }
   }

   public void tick(Level level, BlockPos blockPos, BlockState blockState) {
      this.tickCount++;
      if (this.active) {
         Vec3 wind = WindEngine.getWind(this.position, level);
         if (!level.isClientSide()) {
            this.position = this.position.add(this.velocity.multiply(0.05F, 0.05F, 0.05F));
            this.velocity = this.velocity.multiply(0.98F, 0.995F, 0.98F);
            this.velocity = this.velocity.add(wind.multiply(0.05F, 0.05F, 0.05F).multiply(0.05F, 0.05F, 0.05F));
            if (this.position.y > 300.0) {
               this.velocity = this.velocity.add(0.0, 1.0, 0.0);
            } else {
               this.velocity = this.velocity.add(0.0, 0.2F, 0.0);
            }

            if (this.tickCount % 10 == 0) {
               this.syncAll(level, blockPos);
            }

            if (this.position.y - (double)this.lastSample > 250.0) {
               this.lastSample = (int)this.position.y;
               this.sounding.data.put(this.lastSample, ThermodynamicEngine.samplePoint(this.sounding.weatherHandler, this.position, level, null, 0));
               this.syncSounding(level, blockPos);
            }

            if (this.position.y > 16000.0) {
               this.active = false;
               this.syncAll(level, blockPos);
            }
         } else {
            this.position = this.position.lerp(this.target, 0.05F);
         }
      } else {
         this.lastSample = 0;
      }

      if (!level.isClientSide() && this.tickCount % 200 == 0) {
         this.syncSounding(level, blockPos);
      }
   }
}
