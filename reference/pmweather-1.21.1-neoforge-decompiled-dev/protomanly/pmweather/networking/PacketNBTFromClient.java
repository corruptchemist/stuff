package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.event.GameBusEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record PacketNBTFromClient(CompoundTag compoundTag) implements CustomPacketPayload {
   public static final Type<PacketNBTFromClient> TYPE = new Type(PMWeather.getPath("nbt_server"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PacketNBTFromClient> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.COMPOUND_TAG, PacketNBTFromClient::compoundTag, PacketNBTFromClient::new
   );

   public PacketNBTFromClient(RegistryFriendlyByteBuf buf) {
      this(buf.readNbt());
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeNbt(this.compoundTag);
   }

   public void handle(Player player) {
      try {
         if (player instanceof ServerPlayer serverPlayer) {
            String packetCommand = this.compoundTag.getString("packetCommand");
            String command = this.compoundTag.getString("command");
            if (packetCommand.equals("WeatherData")) {
               if (command.equals("syncFull")) {
                  GameBusEvents.playerRequestsFullSync(serverPlayer);
               }
            } else if (packetCommand.equals("Radar") && command.equals("syncBiomes")) {
               RadarBlockEntity.playerRequestsSync(serverPlayer, NbtUtils.readBlockPos(this.compoundTag, "blockPos").orElse(BlockPos.ZERO));
            }
         }
      } catch (Exception var5) {
         PMWeather.LOGGER.error(var5.getMessage(), var5);
      }
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
