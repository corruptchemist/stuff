package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record PacketVersionFromClient(String version) implements CustomPacketPayload {
   public static final Type<PacketVersionFromClient> TYPE = new Type(PMWeather.getPath("version_client"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PacketVersionFromClient> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, PacketVersionFromClient::version, PacketVersionFromClient::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         String serverVersion = PMWeather.getModContainer().getModInfo().getVersion().toString();
         if (!serverVersion.equals(this.version)) {
            serverPlayer.connection.disconnect(Component.translatable("disconnect.pmweather.version_mismatch", new Object[]{serverVersion, this.version}));
         }
      }
   }
}
