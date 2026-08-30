package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.WeatherStationBlock;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.block.entity.WeatherPlatformBlockEntity;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.seasons.MoistureHandler;
import dev.protomanly.pmweather.weather.Sounding;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Clientbound NBT packet.
 *
 * <p>Port note: on 1.21.1/NeoForge this was a {@code CustomPacketPayload} with a
 * {@code StreamCodec}. Forge 1.20.1 has neither, so it is now a plain message with an
 * explicit {@link FriendlyByteBuf} encoder/decoder pair registered on a SimpleChannel.
 * The payload on the wire (one compound tag) is identical.
 */
public record PacketNBTFromServer(CompoundTag compoundTag) {

   public PacketNBTFromServer(FriendlyByteBuf buf) {
      this(buf.readNbt());
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeNbt(this.compoundTag);
   }

   public void handle(Player player) {
      try {
         String packetCommand = this.compoundTag.getString("packetCommand");
         String command = this.compoundTag.getString("command");
         GameBusClientEvents.getClientWeather();
         WeatherHandlerClient weatherHandler = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
         if (packetCommand.equals("WeatherData")) {
            weatherHandler.nbtSyncFromServer(this.compoundTag);
         } else if (packetCommand.equals("LevelData")) {
            if (command.equals("syncMisc")) {
               weatherHandler.seed = this.compoundTag.getLong("seed");
               WindEngine.init(weatherHandler);
               ThermodynamicEngine.noise = WindEngine.simplexNoise;
            }
         } else if (packetCommand.equals("Radar")) {
            if (command.equals("syncBiomes")) {
               BlockPos blockPos = NbtUtils.readBlockPos(this.compoundTag.getCompound("blockPos"));
               Level level = player.level();
               BlockState state = level.getBlockState(blockPos);
               if (state.is(ModBlocks.RADAR) && state.hasBlockEntity() && level.getBlockEntity(blockPos) instanceof RadarBlockEntity radarBlockEntity) {
                  radarBlockEntity.clientInit(level, this.compoundTag);
               }
            }
         } else if (packetCommand.equals("WeatherPlatform")) {
            if (command.equals("sync")) {
               CompoundTag data = this.compoundTag.getCompound("data");
               BlockPos blockPos = NbtUtils.readBlockPos(data.getCompound("blockPos"));
               Level level = player.level();
               BlockState state = level.getBlockState(blockPos);
               if (state.is(ModBlocks.WEATHER_PLATFORM)
                  && state.hasBlockEntity()
                  && level.getBlockEntity(blockPos) instanceof WeatherPlatformBlockEntity weatherPlatformBlockEntity) {
                  weatherPlatformBlockEntity.deserializeNBT(data);
               }
            } else if (command.equals("syncSounding")) {
               CompoundTag data = this.compoundTag.getCompound("data");
               BlockPos blockPos = NbtUtils.readBlockPos(this.compoundTag.getCompound("blockPos"));
               Level level = player.level();
               BlockState state = level.getBlockState(blockPos);
               if (state.is(ModBlocks.WEATHER_PLATFORM)
                  && state.hasBlockEntity()
                  && level.getBlockEntity(blockPos) instanceof WeatherPlatformBlockEntity weatherPlatformBlockEntity) {
                  weatherPlatformBlockEntity.sounding = new Sounding(GameBusClientEvents.weatherHandler, data, blockPos.getCenter());
               }
            }
         } else if (packetCommand.equals("Metar")) {
            if (command.equals("sendData")) {
               WeatherStationBlock.sendMessage(this.compoundTag);
            }
         } else if (packetCommand.equals("Chunk")) {
            if (command.equals("sendData")) {
               MoistureHandler.HandleChunkData(this.compoundTag);
            }
         } else if (packetCommand.equals("SpawnParticle")) {
            if (GameBusClientEvents.weatherHandler == null) {
               return;
            }

            WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
            if (command.equals("smoke")) {
               weatherHandlerClient.spawnSmoke(
                  new Vec3((double)this.compoundTag.getFloat("posX"), (double)this.compoundTag.getFloat("posY"), (double)this.compoundTag.getFloat("posZ")),
                  this.compoundTag.getFloat("intensity")
               );
            }
         } else if (packetCommand.equals("SyncMetars")) {
            if (GameBusClientEvents.weatherHandler == null) {
               return;
            }

            WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
            weatherHandlerClient.metarData.read(this.compoundTag);
         }
      } catch (Exception var11) {
         PMWeather.LOGGER.error(var11.getMessage(), var11);
      }
   }


   /** SimpleChannel entry point. Runs on the client main thread. */
   public static void handle(PacketNBTFromServer msg, Supplier<NetworkEvent.Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
         Player player = net.minecraft.client.Minecraft.getInstance().player;
         if (player != null) {
            msg.handle(player);
         }
      });
   }
}
