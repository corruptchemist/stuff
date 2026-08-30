package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.event.GameBusEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Serverbound NBT packet. See {@link PacketNBTFromServer} for the port note. */
public record PacketNBTFromClient(CompoundTag compoundTag) {

   public PacketNBTFromClient(FriendlyByteBuf buf) {
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
               RadarBlockEntity.playerRequestsSync(serverPlayer, NbtUtils.readBlockPos(this.compoundTag.getCompound("blockPos")));
            }
         }
      } catch (Exception var5) {
         PMWeather.LOGGER.error(var5.getMessage(), var5);
      }
   }


   /** SimpleChannel entry point. Runs on the server main thread. */
   public static void handle(PacketNBTFromClient msg, Supplier<NetworkEvent.Context> ctx) {
      ServerPlayer sender = ctx.get().getSender();
      if (sender != null) {
         msg.handle(sender);
      }
   }
}
