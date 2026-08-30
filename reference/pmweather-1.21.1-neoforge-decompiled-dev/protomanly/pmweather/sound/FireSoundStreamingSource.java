package dev.protomanly.pmweather.sound;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class FireSoundStreamingSource extends AbstractTickableSoundInstance {
   public final float startAt;
   public final float peakAt;
   public final SoundEvent soundEvent;
   public float volMultiplier = 1.0F;
   public float fireIntensity = 0.0F;
   public boolean cleaned = false;
   public Vec3 position = Vec3.ZERO;
   public boolean instantPos = false;
   private boolean caughtUp = false;
   private float smoothIntensity = 0.0F;
   private List<FireSoundAccessorySource> accessories;

   public FireSoundStreamingSource(SoundEvent soundEvent, SoundSource soundSource, float startAt, float peakAt) {
      super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
      this.looping = true;
      this.volume = 0.1F;
      this.pitch = 1.0F;
      this.startAt = startAt;
      this.peakAt = peakAt;
      this.attenuation = Attenuation.NONE;
      this.soundEvent = soundEvent;
      if (Minecraft.getInstance().cameraEntity != null) {
         this.position = Minecraft.getInstance().cameraEntity.position();
      }

      this.tick();
   }

   public void start(Minecraft minecraft) {
      minecraft.getSoundManager().play(this);
      this.accessories = new ArrayList<>();

      for (int i = 0; i <= this.random.nextInt(2); i++) {
         FireSoundAccessorySource accessory = new FireSoundAccessorySource(this);
         this.accessories.add(accessory);
         accessory.start(minecraft);
      }
   }

   public boolean isRelative() {
      Entity camera = Minecraft.getInstance().cameraEntity;
      return camera == null ? false : camera.position().distanceTo(new Vec3(this.x, this.y, this.z)) > 6.0;
   }

   public boolean canStartSilent() {
      return true;
   }

   public void cleanup() {
      if (!this.cleaned) {
         for (FireSoundAccessorySource accessory : this.accessories) {
            accessory.cleanup();
         }

         this.cleaned = true;
         this.stop();
      }
   }

   public void tick() {
      this.smoothIntensity = Mth.lerp(0.05F, this.smoothIntensity, this.fireIntensity);
      this.volume = this.volMultiplier * Mth.clamp((this.smoothIntensity - this.startAt) / (this.peakAt - this.startAt), 0.0F, 1.0F);
      Entity camera = Minecraft.getInstance().cameraEntity;
      if (this.instantPos && camera != null) {
         if (!(this.smoothIntensity <= this.startAt) && !(camera.position().distanceTo(new Vec3(this.x, this.y, this.z)) < 1.0) && !this.caughtUp) {
            this.x = Mth.lerp(0.05F, this.x, this.position.x);
            this.y = Mth.lerp(0.05F, this.y, this.position.y);
            this.z = Mth.lerp(0.05F, this.z, this.position.z);
         } else {
            this.x = this.position.x;
            this.y = this.position.y;
            this.z = this.position.z;
            this.caughtUp = true;
         }
      } else {
         if (this.smoothIntensity <= this.startAt) {
            this.x = this.position.x;
            this.y = this.position.y;
            this.z = this.position.z;
         } else {
            this.x = Mth.lerp(0.05F, this.x, this.position.x);
            this.y = Mth.lerp(0.05F, this.y, this.position.y);
            this.z = Mth.lerp(0.05F, this.z, this.position.z);
         }

         this.caughtUp = false;
      }
   }
}
