package dev.protomanly.pmweather.networking;

import dev.protomanly.pmweather.PMWeather;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.20.1 networking.
 *
 * <p>Port note: the 1.21.1 build used NeoForge's {@code CustomPacketPayload} +
 * {@code StreamCodec} + {@code PayloadRegistrar} API, none of which exists on
 * 1.20.1. Forge 1.20.1 uses a {@link SimpleChannel} with explicit
 * encoder/decoder/handler triples and an integer discriminator per message, so
 * the three packets are registered here instead of in a payload-registrar event.
 *
 * <p>The wire format is unchanged (a single NBT compound, or a single string for
 * the version packet), so the packets carry exactly the same data as before.
 */
public class ModNetworking {

   private static final String PROTOCOL_VERSION = "0.17.14";

   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(PMWeather.MOD_ID, "main"),
      () -> PROTOCOL_VERSION,
      PROTOCOL_VERSION::equals,
      PROTOCOL_VERSION::equals
   );

   private static int packetId = 0;

   private static int nextId() {
      return packetId++;
   }

   /**
    * Called once during mod construction (FMLCommonSetupEvent is also fine).
    * The varargs parameter is kept so existing call sites compile unchanged; it
    * is ignored on Forge.
    */
   public static void register(Object... args) {
      CHANNEL.messageBuilder(PacketNBTFromServer.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
             .encoder(PacketNBTFromServer::write)
             .decoder(PacketNBTFromServer::new)
             .consumerMainThread(PacketNBTFromServer::handle)
             .add();

      CHANNEL.messageBuilder(PacketNBTFromClient.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
             .encoder(PacketNBTFromClient::write)
             .decoder(PacketNBTFromClient::new)
             .consumerMainThread(PacketNBTFromClient::handle)
             .add();

      CHANNEL.messageBuilder(PacketVersionFromClient.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
             .encoder(PacketVersionFromClient::write)
             .decoder(PacketVersionFromClient::new)
             .consumerMainThread(PacketVersionFromClient::handle)
             .add();
   }

   public static void clientSendToServer(CompoundTag data) {
      CHANNEL.sendToServer(new PacketNBTFromClient(data));
   }

   public static void clientSendVersionToServer(String version) {
      CHANNEL.sendToServer(new PacketVersionFromClient(version));
   }

   public static void serverSendToClientAll(CompoundTag data) {
      CHANNEL.send(PacketDistributor.ALL.noArg(), new PacketNBTFromServer(data));
   }

   public static void serverSendToClientPlayer(CompoundTag data, net.minecraft.world.entity.player.Player player) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new PacketNBTFromServer(data));
   }

   public static void serverSendToClientNear(CompoundTag data, Vec3 position, double distance, Level level) {
      CHANNEL.send(
         PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
            position.x, position.y, position.z, distance, level.dimension())),
         new PacketNBTFromServer(data)
      );
   }

   public static void serverSendToClientDimension(CompoundTag data, Level level) {
      CHANNEL.send(PacketDistributor.DIMENSION.with(((ServerLevel) level)::dimension), new PacketNBTFromServer(data));
   }
}
