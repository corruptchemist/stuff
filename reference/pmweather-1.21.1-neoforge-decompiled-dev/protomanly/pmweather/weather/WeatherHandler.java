package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.LevelSavedData;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.interfaces.IWorldData;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormType;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public abstract class WeatherHandler implements IWorldData {
   private List<Storm> storms = new ArrayList<>();
   private ResourceKey<Level> dimension;
   public HashMap<Long, Storm> lookupStormByID = new HashMap<>();
   public HashMap<UUID, Storm> lookupStormByUUID = new HashMap<>();
   public long seed;

   public WeatherHandler(ResourceKey<Level> dimension) {
      this.dimension = dimension;
   }

   public void tick() {
      Level level = this.getWorld();
      if (level != null) {
         List<Storm> stormList = this.getStorms();

         for (int i = 0; i < stormList.size(); i++) {
            Storm storm = stormList.get(i);
            if (this instanceof WeatherHandlerServer weatherHandlerServer && storm.dead) {
               this.removeStorm(storm.ID);
               weatherHandlerServer.syncStormRemove(storm);
               continue;
            }

            if (!storm.dead) {
               storm.tick();
            } else {
               this.removeStorm(storm.ID);
            }
         }
      }
   }

   public List<Storm> getStorms() {
      return this.storms;
   }

   public void addStorm(Storm storm) {
      if (!this.lookupStormByID.containsKey(storm.ID)) {
         this.storms.add(storm);
         this.lookupStormByID.put(storm.ID, storm);
         this.lookupStormByUUID.put(storm.uuid, storm);
      } else {
         PMWeather.LOGGER.warn("Tried to add a storm with existing ID: {}", storm.ID);
      }
   }

   public void removeStorm(long id) {
      Storm storm = this.lookupStormByID.get(id);
      if (storm != null) {
         storm.remove();
         this.storms.remove(storm);
         this.lookupStormByID.remove(id);
         this.lookupStormByUUID.remove(storm.uuid);
      } else {
         PMWeather.LOGGER.warn("Tried to remove a non-existent storm with ID: {}", id);
      }
   }

   private float _getPrecipitation(Vec3 pos, int advance) {
      float precip = 0.0F;
      if (!ServerConfig.validDimensions.contains(this.getWorld().dimension())) {
         return precip;
      } else {
         float cloudDensity = Clouds.getCloudDensity(this, new Vector2f((float)pos.x, (float)pos.z), 0.0F, advance);
         if (cloudDensity > 0.15F) {
            precip += (cloudDensity - 0.15F) * 2.0F;
         }

         return precip;
      }
   }

   public float getPrecipitation(Vec3 pos, int advance) {
      return Math.clamp(this._getPrecipitation(pos, advance) * (float)ServerConfig.rainStrength, 0.0F, 1.0F);
   }

   public float getHail(Vec3 pos) {
      float precip = 0.0F;
      if (!ServerConfig.validDimensions.contains(this.getWorld().dimension())) {
         return precip;
      } else {
         for (Storm storm : this.getStorms()) {
            if (!storm.visualOnly && storm.hasPrecipitation()) {
               precip += storm.getHail(pos);
            }
         }

         return Math.clamp(precip, 0.0F, 1.0F);
      }
   }

   public float getPrecipitation(Vec3 pos) {
      if (!ServerConfig.validDimensions.contains(this.getWorld().dimension())) {
         return 0.0F;
      } else {
         float precip = this._getPrecipitation(pos, 0);

         for (Storm storm : this.getStorms()) {
            if (!storm.visualOnly && storm.hasPrecipitation()) {
               precip = storm.addToPrecip(precip, storm.getPrecipitation(pos));
            }
         }

         return Math.clamp(precip * (float)ServerConfig.rainStrength, 0.0F, 1.0F);
      }
   }

   public abstract Level getWorld();

   @Override
   public CompoundTag save(CompoundTag data) {
      PMWeather.LOGGER.debug("WeatherHandler save");
      CompoundTag listStormsNBT = new CompoundTag();

      for (int i = 0; i < this.storms.size(); i++) {
         Storm storm = this.storms.get(i);
         storm.getNBTCache().setUpdateForced(true);
         storm.write();
         storm.getNBTCache().setUpdateForced(false);
         listStormsNBT.put("storm_" + storm.ID, storm.getNBTCache().getNewNBT());
         if (GameBusEvents.isStoppingServer) {
            storm.remove();
         }
      }

      data.put("stormData", listStormsNBT);
      data.putLong("lastUsedIDStorm", Storm.LastUsedStormID);
      return null;
   }

   public void read() {
      LevelSavedData savedData = (LevelSavedData)((ServerLevel)this.getWorld())
         .getDataStorage()
         .computeIfAbsent(LevelSavedData.factory(), "pmweather_weather_data");
      savedData.setDataHandler(this);
      PMWeather.LOGGER.debug("Weather Data: {}", savedData.getData());
      CompoundTag data = savedData.getData();
      Storm.LastUsedStormID = data.getLong("lastUsedIDStorm");
      CompoundTag storms = data.getCompound("stormData");
      Iterator var4 = storms.getAllKeys().iterator();

      while (true) {
         CompoundTag stormData;
         StormType stormType;
         while (true) {
            if (!var4.hasNext()) {
               return;
            }

            String tagName = (String)var4.next();
            stormData = storms.getCompound(tagName);
            int format = 0;
            if (stormData.contains("format")) {
               format = stormData.getInt("format");
            }

            if (format == 0) {
               stormType = StormTypes.getFromLegacyID(stormData.getInt("stormType"));
               break;
            }

            try {
               stormType = StormTypes.getFromID(stormData.getString("stormType"));
               break;
            } catch (Exception var12) {
               PMWeather.LOGGER.warn(var12.getMessage(), var12);
               PMWeather.LOGGER.warn("Skipping storm initialization, storm data will be lost.");
            }
         }

         Storm storm = stormType.create(new StormSpawnProperties(this, this.getWorld(), Vec3.ZERO, null));

         try {
            storm.getNBTCache().setNewNBT(stormData);
            storm.read();
            storm.getNBTCache().updateCacheFromNew();
         } catch (Exception var11) {
            PMWeather.LOGGER.error(var11.getMessage(), var11);
         }

         this.addStorm(storm);
      }
   }
}
