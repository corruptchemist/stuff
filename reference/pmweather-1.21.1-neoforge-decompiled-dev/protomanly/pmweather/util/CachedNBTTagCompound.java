package dev.protomanly.pmweather.util;

import net.minecraft.nbt.CompoundTag;

public class CachedNBTTagCompound {
   private CompoundTag newData;
   private CompoundTag cachedData;
   private boolean forced = false;

   public CachedNBTTagCompound() {
      this.newData = new CompoundTag();
      this.cachedData = new CompoundTag();
   }

   public void setCachedNBT(CompoundTag cachedData) {
      if (cachedData == null) {
         cachedData = new CompoundTag();
      }

      this.cachedData = cachedData;
   }

   public CompoundTag getCachedNBT() {
      return this.cachedData;
   }

   public CompoundTag getNewNBT() {
      return this.newData;
   }

   public void setNewNBT(CompoundTag newData) {
      this.newData = newData;
   }

   public void setUpdateForced(boolean forced) {
      this.forced = forced;
   }

   public long getLong(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putLong(key, this.cachedData.getLong(key));
      }

      return this.newData.getLong(key);
   }

   public void putLong(String key, long value) {
      if (!this.cachedData.contains(key) || this.cachedData.getLong(key) != value || this.forced) {
         this.newData.putLong(key, value);
      }

      this.cachedData.putLong(key, value);
   }

   public int getInt(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putInt(key, this.cachedData.getInt(key));
      }

      return this.newData.getInt(key);
   }

   public void putInt(String key, int value) {
      if (!this.cachedData.contains(key) || this.cachedData.getInt(key) != value || this.forced) {
         this.newData.putInt(key, value);
      }

      this.cachedData.putInt(key, value);
   }

   public short getShort(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putShort(key, this.cachedData.getShort(key));
      }

      return this.newData.getShort(key);
   }

   public void putShort(String key, short value) {
      if (!this.cachedData.contains(key) || this.cachedData.getShort(key) != value || this.forced) {
         this.newData.putShort(key, value);
      }

      this.cachedData.putShort(key, value);
   }

   public String getString(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putString(key, this.cachedData.getString(key));
      }

      return this.newData.getString(key);
   }

   public void putString(String key, String value) {
      if (!this.cachedData.contains(key) || !this.cachedData.getString(key).equals(value) || this.forced) {
         this.newData.putString(key, value);
      }

      this.cachedData.putString(key, value);
   }

   public boolean getBoolean(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putBoolean(key, this.cachedData.getBoolean(key));
      }

      return this.newData.getBoolean(key);
   }

   public void putBoolean(String key, boolean value) {
      if (!this.cachedData.contains(key) || this.cachedData.getBoolean(key) != value || this.forced) {
         this.newData.putBoolean(key, value);
      }

      this.cachedData.putBoolean(key, value);
   }

   public float getFloat(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putFloat(key, this.cachedData.getFloat(key));
      }

      return this.newData.getFloat(key);
   }

   public void putFloat(String key, float value) {
      if (!this.cachedData.contains(key) || this.cachedData.getFloat(key) != value || this.forced) {
         this.newData.putFloat(key, value);
      }

      this.cachedData.putFloat(key, value);
   }

   public double getDouble(String key) {
      if (!this.newData.contains(key)) {
         this.newData.putDouble(key, this.cachedData.getDouble(key));
      }

      return this.newData.getDouble(key);
   }

   public void putDouble(String key, double value) {
      if (!this.cachedData.contains(key) || this.cachedData.getDouble(key) != value || this.forced) {
         this.newData.putDouble(key, value);
      }

      this.cachedData.putDouble(key, value);
   }

   public CompoundTag get(String key) {
      return this.newData.getCompound(key);
   }

   public void put(String key, CompoundTag tag) {
      this.newData.put(key, tag);
      this.cachedData.put(key, tag);
   }

   public boolean contains(String key) {
      return this.newData.contains(key);
   }

   public void updateCacheFromNew() {
      this.cachedData = this.newData;
   }
}
