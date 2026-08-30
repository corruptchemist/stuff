package dev.protomanly.pmweather.networking;

import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {
   public ModNetworking() {
   }

   public static void register(Object... args) {
      registerClientboundPacket(PacketNBTFromServer.TYPE, PacketNBTFromServer.STREAM_CODEC, PacketNBTFromServer::handle, args);
      registerServerboundPacket(PacketNBTFromClient.TYPE, PacketNBTFromClient.STREAM_CODEC, PacketNBTFromClient::handle, args);
      registerServerboundPacket(PacketVersionFromClient.TYPE, PacketVersionFromClient.STREAM_CODEC, PacketVersionFromClient::handle, args);
   }

   public static <T extends CustomPacketPayload, B extends FriendlyByteBuf> void registerServerboundPacket(
      Type<T> type, StreamCodec<B, T> codec, BiConsumer<T, Player> handler, Object... args
   ) {
      PayloadRegistrar registrar = (PayloadRegistrar)args[0];
      IPayloadHandler<T> serverHandler = (packet, context) -> context.enqueueWork(() -> handler.accept((T)packet, context.player()));
      registrar.playToServer(type, codec, serverHandler);
   }

   public static <T extends CustomPacketPayload, B extends FriendlyByteBuf> void registerClientboundPacket(
      Type<T> type, StreamCodec<B, T> codec, BiConsumer<T, Player> handler, Object... args
   ) {
      PayloadRegistrar registrar = (PayloadRegistrar)args[0];
      IPayloadHandler<T> clientHandler = (packet, context) -> context.enqueueWork(() -> handler.accept((T)packet, context.player()));
      registrar.playToClient(type, codec, clientHandler);
   }

   public static void clientSendToServer(CompoundTag data) {
      PacketDistributor.sendToServer(new PacketNBTFromClient(data), new CustomPacketPayload[0]);
   }

   public static void serverSendToClientAll(CompoundTag data) {
      PacketDistributor.sendToAllPlayers(new PacketNBTFromServer(data), new CustomPacketPayload[0]);
   }

   public static void serverSendToClientPlayer(CompoundTag data, Player player) {
      PacketDistributor.sendToPlayer((ServerPlayer)player, new PacketNBTFromServer(data), new CustomPacketPayload[0]);
   }

   public static void serverSendToClientNear(CompoundTag data, Vec3 position, double distance, Level level) {
      PacketDistributor.sendToPlayersNear(
         (ServerLevel)level, null, position.x, position.y, position.z, distance, new PacketNBTFromServer(data), new CustomPacketPayload[0]
      );
   }

   public static void serverSendToClientDimension(CompoundTag data, Level level) {
      PacketDistributor.sendToPlayersInDimension((ServerLevel)level, new PacketNBTFromServer(data), new CustomPacketPayload[0]);
   }
}
