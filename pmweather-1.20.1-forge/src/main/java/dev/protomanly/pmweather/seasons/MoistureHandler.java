package dev.protomanly.pmweather.seasons;

import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.networking.ModNetworking;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class MoistureHandler {
   public MoistureHandler() {
   }

   public static float getStartMoisture(Level level, ChunkAccess chunk) {
      float seasonEffect = SeasonHandler.getSeasonEffectSine(level, -2.5F);
      Holder<Biome> biome = level.getBiome(chunk.getPos().getMiddleBlockPosition(65));
      float humidity = Math.max(((Biome)biome.value()).getModifiedClimateSettings().downfall(), 0.0F);
      float moisture = 90.0F * Mth.sqrt(humidity);
      return moisture * Mth.clamp(Mth.sqrt((seasonEffect + 1.0F) / 2.0F), 0.15F, 1.0F);
   }

   public static void setMoistureOnChunk(Level level, ChunkAccess chunk, @Nullable Float moisture) {
      if (level.dimension() == Level.OVERWORLD) {
         if (moisture != null) {
            chunk.setData(DataAttachments.MOISTURE, moisture);
         } else if (!chunk.hasData(DataAttachments.MOISTURE)) {
            return;
         }

         CompoundTag data = new CompoundTag();
         data.putString("packetCommand", "Chunk");
         data.putString("command", "sendData");
         float fireIntensity = (Float)chunk.getData(DataAttachments.FIRE_INTENSITY);
         float stableFireIntensity = (Float)chunk.getData(DataAttachments.STABLE_FIRE_INTENSITY);
         float fireAftermath = (Float)chunk.getData(DataAttachments.FIRE_AFTERMATH);
         data.putInt("chunkX", chunk.getPos().x);
         data.putInt("chunkZ", chunk.getPos().z);
         float v = moisture == null ? (Float)chunk.getData(DataAttachments.MOISTURE) : moisture;
         if (Float.isNaN(v)) {
            v = 50.0F;
         }

         data.putFloat("moisture", v);
         data.putFloat("fireIntensity", fireIntensity);
         data.putFloat("stableFireIntensity", stableFireIntensity);
         data.putFloat("fireAftermath", fireAftermath);
         ModNetworking.serverSendToClientNear(
            data, chunk.getPos().getMiddleBlockPosition(65).getCenter(), (double)(level.getServer().getPlayerList().getViewDistance() * 19), level
         );
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static void HandleChunkData(CompoundTag tag) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Level level = player.level();
         if (level.dimension() == Level.OVERWORLD) {
            int chunkX = tag.getInt("chunkX");
            int chunkZ = tag.getInt("chunkZ");
            float moisture = tag.getFloat("moisture");
            float fireIntensity = tag.getFloat("fireIntensity");
            float stableFireIntensity = tag.getFloat("stableFireIntensity");
            float fireAftermath = tag.getFloat("fireAftermath");
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            chunk.setData(DataAttachments.MOISTURE, moisture);
            chunk.setData(DataAttachments.FIRE_INTENSITY, fireIntensity);
            chunk.setData(DataAttachments.STABLE_FIRE_INTENSITY, stableFireIntensity);
            chunk.setData(DataAttachments.FIRE_AFTERMATH, fireAftermath);
         }
      }
   }
}
