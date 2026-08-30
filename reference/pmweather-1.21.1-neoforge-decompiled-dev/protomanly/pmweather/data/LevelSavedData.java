package dev.protomanly.pmweather.data;

import dev.protomanly.pmweather.interfaces.IWorldData;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public class LevelSavedData extends SavedData {
   private CompoundTag compoundTag;
   private IWorldData dataHandler;

   public static Factory<LevelSavedData> factory() {
      return new Factory(LevelSavedData::new, LevelSavedData::load, null);
   }

   public LevelSavedData() {
      this.compoundTag = new CompoundTag();
   }

   public LevelSavedData(CompoundTag compoundTag) {
      this.compoundTag = compoundTag;
   }

   public void setDataHandler(IWorldData dataHandler) {
      this.dataHandler = dataHandler;
   }

   public static LevelSavedData load(CompoundTag compoundTag, Provider registries) {
      return new LevelSavedData(compoundTag);
   }

   public CompoundTag save(CompoundTag compoundTag, Provider provider) {
      this.dataHandler.save(compoundTag);
      return compoundTag;
   }

   public CompoundTag getData() {
      return this.compoundTag;
   }

   public boolean isDirty() {
      return true;
   }
}
