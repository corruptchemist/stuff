package dev.protomanly.pmweather.data;

import dev.protomanly.pmweather.block.MetarBlock;
import dev.protomanly.pmweather.interfaces.IWorldData;
import dev.protomanly.pmweather.networking.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ServerMetarData extends MetarData implements IWorldData {
   private final ServerLevel serverLevel;

   public ServerMetarData(ServerLevel serverLevel) {
      super(serverLevel);
      this.serverLevel = serverLevel;
   }

   public void validate(BlockPos pos) {
      if (this.serverLevel.isLoaded(pos)) {
         BlockState state = this.serverLevel.getBlockState(pos);
         if (!(state.getBlock() instanceof MetarBlock)) {
            this.remove(pos);
         }
      }
   }

   public void validateAll() {
      this.METARS.removeIf(state -> {
         if (!this.serverLevel.isLoaded(state.pos())) {
            return false;
         } else {
            BlockState blockState = this.serverLevel.getBlockState(state.pos());
            return blockState.isAir() ? true : !(blockState.getBlock() instanceof MetarBlock);
         }
      });
   }

   public void read() {
      LevelSavedData savedData = (LevelSavedData)this.serverLevel.getDataStorage().computeIfAbsent(LevelSavedData.factory(), "pmweather_metar_placement");
      savedData.setDataHandler(this);
      CompoundTag data = savedData.getData();
      this.read(data);
   }

   @Override
   public void setLevel(Level level) {
      throw new AssertionError();
   }

   public CompoundTag getPacket() {
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "SyncMetars");
      this.save(data);
      return data;
   }

   public void syncToPlayer(ServerPlayer player) {
      if (player.level() == this.serverLevel) {
         ModNetworking.serverSendToClientPlayer(this.getPacket(), player);
      }
   }

   public void syncToPlayers() {
      ModNetworking.serverSendToClientDimension(this.getPacket(), this.serverLevel);
   }

   @Override
   public CompoundTag save(CompoundTag data) {
      data.putInt("dataVer", 1);
      CompoundTag states = new CompoundTag();

      for (int i = 0; i < this.METARS.size(); i++) {
         MetarBlock.PlacementState state = this.METARS.get(i);
         CompoundTag metar = new CompoundTag();
         metar.put("position", NbtUtils.writeBlockPos(state.pos()));
         metar.putInt("terrainHeight", state.terrainHeight());
         metar.putString("biome", state.biome().getRegisteredName());
         states.put(String.valueOf(i), metar);
      }

      data.put("metars", states);
      return data;
   }
}
