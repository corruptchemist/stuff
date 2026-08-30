package dev.protomanly.pmweather.block.entity;

import dev.protomanly.pmweather.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SoundingViewerBlockEntity extends BlockEntity {
   public BlockPos connectedTo = BlockPos.ZERO;
   public boolean isConnected = false;

   public SoundingViewerBlockEntity(BlockPos pos, BlockState blockState) {
      super(ModBlockEntities.SOUNDING_VIEWER_BE.get(), pos, blockState);
   }

   public void connect(BlockPos to) {
      this.connectedTo = to;
      this.isConnected = true;
      this.setChanged();
   }

   public void tick(Level level, BlockPos blockPos, BlockState blockState) {
      if (!level.isClientSide() && blockState.is(ModBlocks.SOUNDING_VIEWER) && this.isConnected) {
         BlockState stateAt = level.getBlockState(this.connectedTo);
         if (!stateAt.is(ModBlocks.WEATHER_PLATFORM)) {
            this.isConnected = false;
            this.setChanged();
         }
      }
   }

   public void setChanged() {
      super.setChanged();
      this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      tag.put("connectedTo", NbtUtils.writeBlockPos(this.connectedTo));
      tag.putBoolean("isConnected", this.isConnected);
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      this.connectedTo = NbtUtils.readBlockPos(tag, "connectedTo").orElse(BlockPos.ZERO);
      this.isConnected = tag.getBoolean("isConnected");
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag data = super.getUpdateTag(registries);
      this.saveAdditional(data, registries);
      return data;
   }
}
