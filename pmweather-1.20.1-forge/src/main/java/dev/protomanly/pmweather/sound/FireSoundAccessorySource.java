package dev.protomanly.pmweather.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class FireSoundAccessorySource extends FireSoundStreamingSource {
   public final FireSoundStreamingSource parent;
   public final Vec3 offsetFromParent;
   public final float volumeOffset;
   public final float pitchShift;

   public FireSoundAccessorySource(FireSoundStreamingSource parent) {
      super(parent.soundEvent, parent.getSource(), parent.startAt, parent.peakAt);
      this.parent = parent;
      this.offsetFromParent = Vec3.ZERO.offsetRandom(this.random, 23.0F).multiply(1.0, 0.2, 1.0);
      this.volumeOffset = -this.random.nextFloat() * parent.volMultiplier * 0.4F;
      this.pitchShift = 0.6F + this.random.nextFloat() * 0.8F;
   }

   @Override
   public void start(Minecraft minecraft) {
      minecraft.getSoundManager().play(this);
   }

   public float getPitch() {
      return this.parent == null ? 1.0F : this.parent.getPitch() * this.pitchShift;
   }

   public float getVolume() {
      return this.parent == null ? 0.0F : Math.max(this.parent.getVolume() + this.volumeOffset, 0.0F);
   }

   @Override
   public boolean isRelative() {
      return true;
   }

   public boolean isLooping() {
      return this.parent == null || this.parent.isLooping();
   }

   public boolean isStopped() {
      return this.parent != null && this.parent.isStopped();
   }

   @Override
   public void cleanup() {
      if (!this.cleaned) {
         this.cleaned = true;
         this.stop();
      }
   }

   @Override
   public void tick() {
      if (this.parent != null) {
         this.x = this.parent.getX() + this.offsetFromParent.x;
         this.y = this.parent.getY() + this.offsetFromParent.y;
         this.z = this.parent.getZ() + this.offsetFromParent.z;
      }
   }
}
