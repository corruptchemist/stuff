package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serverbound version-handshake packet.
 *
 * <p>Port note: was a {@code CustomPacketPayload} with a {@code StreamCodec} on
 * 1.21.1/NeoForge; on Forge 1.20.1 it is a plain SimpleChannel message with an
 * explicit encoder/decoder. Wire format (one UTF-8 string) is unchanged.
 */
public record PacketVersionFromClient(String version) {

   public PacketVersionFromClient(FriendlyByteBuf buf) {
      this(buf.readUtf());
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeUtf(this.version);
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         String serverVersion = PMWeather.getModContainer().getModInfo().getVersion().toString();
         if (!serverVersion.equals(this.version)) {
            serverPlayer.connection.disconnect(
               Component.translatable("disconnect.pmweather.version_mismatch", serverVersion, this.version));
         }
      }
   }

   /** SimpleChannel entry point. Runs on the server main thread. */
   public static void handle(PacketVersionFromClient msg, Supplier<NetworkEvent.Context> ctx) {
      ServerPlayer sender = ctx.get().getSender();
      if (sender != null) {
         msg.handle(sender);
      }
   }
}
