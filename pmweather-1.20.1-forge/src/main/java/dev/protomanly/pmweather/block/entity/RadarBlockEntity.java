package dev.protomanly.pmweather.block.entity;

import com.mojang.blaze3d.platform.NativeImage;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.RadarBlock;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

public class RadarBlockEntity extends BlockEntity {
   public List<Storm> storms = new ArrayList<>();
   public int tickCount;
   public int updateCount;
   public SimplexNoise noise;
   public Map<Long, Float> reflectivityMap = new HashMap<>();
   public Map<Long, Float> temperatureMap = new HashMap<>();
   public Map<Long, Float> velocityMap = new HashMap<>();
   public Map<Long, Float> metarMap = new HashMap<>();
   public Map<Long, Color> debugMap = new HashMap<>();
   public Map<Long, Color> terrainMap = new HashMap<>();
   public List<RadarBlockEntity.BiomeData> biomeData = new ArrayList<>();
   public List<Vec3> linePoints = new ArrayList<>();
   public List<Vec3> triPoints = new ArrayList<>();
   public RadarBlock.Mode lastMode = null;
   public NativeImage lastFrame = null;
   public boolean init = false;
   public int lastUpdate = 0;
   public int ticksNoPacket = 0;
   public boolean hasRangeUpgrade = false;
   public Map<BlockPos, Holder<Biome>> biomeCache = new HashMap<>();

   public RadarBlockEntity(BlockPos pos, BlockState blockState) {
      super(ModBlockEntities.RADAR_BE.get(), pos, blockState);
      this.noise = new SimplexNoise(new LegacyRandomSource(0L));
   }

   public void setRemoved() {
      if (this.lastFrame != null) {
         this.lastFrame.untrack();
         this.lastFrame.close();
         this.lastFrame = null;
      }

      super.setRemoved();
   }

   public void onChunkUnloaded() {
      if (this.lastFrame != null) {
         this.lastFrame.close();
         this.lastFrame.untrack();
         this.lastFrame = null;
      }

      super.onChunkUnloaded();
   }

   @Nullable
   public Holder<Biome> getNearestBiome(BlockPos pos) {
      double nearest = Double.MAX_VALUE;
      if (this.biomeCache.containsKey(pos.atY(0))) {
         return this.biomeCache.get(pos.atY(0));
      } else {
         Holder<Biome> biome = null;

         for (RadarBlockEntity.BiomeData bData : this.biomeData) {
            double dist = (double)pos.distManhattan(bData.pos);
            if (dist < nearest) {
               nearest = dist;
               biome = bData.biome;
            }

            if (dist < 128.0) {
               break;
            }
         }

         if (biome != null) {
            this.biomeCache.put(pos.atY(0), biome);
         }

         return biome;
      }
   }

   public void clientInit(Level level, CompoundTag data) {
      if (!this.init) {
         PMWeather.LOGGER.debug("Radar data received");
         this.init = true;
         CompoundTag list = data.getCompound("data");

         for (String key : list.getAllKeys()) {
            CompoundTag element = list.getCompound(key);
            BlockPos blockPos = NbtUtils.readBlockPos(element.getCompound("blockPos"));
            Holder<Biome> biome = level.registryAccess()
               .registryOrThrow(Registries.BIOME)
               .getHolderOrThrow(ResourceKey.create(Registries.BIOME, new ResourceLocation(element.getString("biome"))));
            this.biomeData.add(new RadarBlockEntity.BiomeData(blockPos, biome));
         }
      }
   }

   public void sync(@Nullable Player player, BlockPos blockPos) {
      if (this.init) {
         CompoundTag data = new CompoundTag();
         data.putString("packetCommand", "Radar");
         data.putString("command", "syncBiomes");
         CompoundTag map = new CompoundTag();
         int i = 0;

         for (RadarBlockEntity.BiomeData bData : this.biomeData) {
            CompoundTag element = new CompoundTag();
            element.put("blockPos", NbtUtils.writeBlockPos(bData.pos()));
            element.putString("biome", bData.biome.getRegisteredName());
            map.put(String.valueOf(i), element);
            i++;
         }

         data.put("data", map);
         data.put("blockPos", NbtUtils.writeBlockPos(blockPos));
         if (player == null) {
            ModNetworking.serverSendToClientNear(data, blockPos.getCenter(), 128.0, this.level);
         } else {
            ModNetworking.serverSendToClientPlayer(data, player);
         }
      }
   }

   public static void playerRequestsSync(ServerPlayer player, BlockPos blockPos) {
      Level lvl = player.level();
      if (lvl.getBlockEntity(blockPos) instanceof RadarBlockEntity radarBlockEntity) {
         radarBlockEntity.sync(player, blockPos);
      }
   }

   public void tick(Level level, BlockPos blockPos, BlockState blockState) {
      this.tickCount++;
      if (level.isClientSide() && (level.getGameTime() % 100L == 0L || this.storms.isEmpty())) {
         WeatherHandlerClient weatherHandler = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
         if (weatherHandler == null) {
            return;
         }

         this.updateCount++;
         this.storms = weatherHandler.getStorms();
      }

      if (level.isClientSide() && level.getGameTime() % 10L == 0L) {
         boolean rangeUpgrade = false;

         for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  BlockPos pos = blockPos.offset(x, y, z);
                  BlockState state = level.getBlockState(pos);
                  if (state.is(ModBlocks.RANGE_UPGRADE_MODULE)) {
                     rangeUpgrade = true;
                  }
               }
            }
         }

         this.hasRangeUpgrade = rangeUpgrade;
      }

      if (!this.init) {
         if (!level.isClientSide()) {
            this.init = true;

            for (int x = -2048; x <= 2048; x += 64) {
               for (int zx = -2048; zx <= 2048; zx += 64) {
                  BlockPos pos = blockPos.offset(new Vec3i(x, 0, zx));
                  Holder<Biome> biome = level.getBiome(pos);
                  this.biomeData.add(new RadarBlockEntity.BiomeData(pos, biome));
               }
            }

            for (int x = -2048; x <= 2048; x += 128) {
               for (int zx = -2048; zx <= 2048; zx += 128) {
                  BlockPos pos = blockPos.offset(new Vec3i(x * 4, 0, zx * 4));
                  Holder<Biome> biome = level.getBiome(pos);
                  RadarBlockEntity.BiomeData data = new RadarBlockEntity.BiomeData(pos, biome);
                  if (!this.biomeData.contains(data)) {
                     this.biomeData.add(data);
                  }
               }
            }

            this.sync(null, blockPos);
         } else {
            this.ticksNoPacket++;
            if (this.ticksNoPacket > 40) {
               PMWeather.LOGGER.debug("Requesting data from server for radar at {}", blockPos);
               this.ticksNoPacket = 0;
               CompoundTag data = new CompoundTag();
               data.putString("packetCommand", "Radar");
               data.putString("command", "syncBiomes");
               data.put("blockPos", NbtUtils.writeBlockPos(blockPos));
               ModNetworking.clientSendToServer(data);
            }
         }
      } else {
         this.ticksNoPacket = 0;
      }
   }

   public static record BiomeData(BlockPos pos, Holder<Biome> biome) {
   }
}
