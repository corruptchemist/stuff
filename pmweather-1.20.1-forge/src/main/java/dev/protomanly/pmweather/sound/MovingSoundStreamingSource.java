package dev.protomanly.pmweather.sound;

import net.minecraft.util.Mth;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class MovingSoundStreamingSource extends AbstractTickableSoundInstance {
   public Storm storm;
   public Vec3 realSource;
   public float cutOffRange = 128.0F;
   public boolean lockToPlayer = false;
   private float extraVolumeAdjForDistScale = 1.0F;
   private Block block;
   private BlockPos blockPos;
   private Level level;
   private int mode = 0;

   public MovingSoundStreamingSource(Vec3 pos, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, boolean lockToPlayer) {
      super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
      this.looping = false;
      this.volume = 0.1F;
      this.extraVolumeAdjForDistScale = volume;
      this.pitch = pitch;
      this.realSource = pos;
      this.lockToPlayer = lockToPlayer;
      this.tick();
   }

   public MovingSoundStreamingSource(
      Level level, BlockState block, BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, float cutOffRange
   ) {
      super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
      this.looping = false;
      this.volume = 0.1F;
      this.extraVolumeAdjForDistScale = volume;
      this.pitch = pitch;
      this.cutOffRange = cutOffRange;
      this.realSource = blockPos.getCenter();
      this.block = block.getBlock();
      this.blockPos = blockPos;
      this.level = level;
      this.tick();
   }

   public MovingSoundStreamingSource(Vec3 pos, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, float cutOffRange) {
      super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
      this.looping = false;
      this.volume = 0.1F;
      this.extraVolumeAdjForDistScale = volume;
      this.pitch = pitch;
      this.cutOffRange = cutOffRange;
      this.realSource = pos;
      this.tick();
   }

   public MovingSoundStreamingSource(
      Storm storm, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, float cutOffRange, boolean looping, int mode
   ) {
      super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
      this.volume = 0.1F;
      this.extraVolumeAdjForDistScale = volume;
      this.pitch = pitch;
      this.cutOffRange = cutOffRange;
      this.storm = storm;
      this.looping = looping;
      this.mode = mode;
      this.tick();
   }

   public void stopPlaying() {
      this.stop();
   }

   public boolean canStartSilent() {
      return true;
   }

   public void tick() {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         this.x = (double)((float)player.getX());
         this.y = (double)((float)player.getY());
         this.z = (double)((float)player.getZ());
         this.volume = this.extraVolumeAdjForDistScale;
      }

      if (this.storm != null) {
         this.realSource = this.storm.position;
         if (this.mode == 0) {
            this.cutOffRange = (float)ServerConfig.stormSize / 1.5F;
            if (this.storm.stage == 2) {
               this.extraVolumeAdjForDistScale = (float)this.storm.energy / 100.0F * 2.0F;
            } else {
               this.extraVolumeAdjForDistScale = 2.0F;
            }
         } else if (this.mode == 1) {
            this.cutOffRange = Math.max(this.storm.width, 45.0F);
            this.extraVolumeAdjForDistScale = (float)this.storm.windspeed / 60.0F;
         } else if (this.mode == 2) {
            this.cutOffRange = (float)this.storm.maxWidth * 0.35F;
            if (player != null) {
               float wind = (float)WindEngine.getWind(player.position(), player.level(), false, true, false, true).length();
               this.extraVolumeAdjForDistScale = Math.max((wind - 90.0F) / 35.0F, 0.0F);
               int worldHeight = player.level().getHeightmapPos(Types.MOTION_BLOCKING, player.blockPosition()).getY();
               float heightPerc = ((float)player.position().y - (float)worldHeight) / -30.0F;
               this.extraVolumeAdjForDistScale = this.extraVolumeAdjForDistScale * (1.0F - Mth.clamp(heightPerc, 0.0F, 1.0F));
            }
         } else if (this.mode == 3) {
            if (this.storm.is(StormTypes.CYCLONE)) {
               float wind = (float)WindEngine.getWind(player.position(), player.level(), false, true, false, true).length();
               this.extraVolumeAdjForDistScale = Math.max((wind - 75.0F) / 60.0F, 0.0F);
               int worldHeight = player.level().getHeightmapPos(Types.MOTION_BLOCKING, player.blockPosition()).getY();
               float heightPerc = ((float)player.position().y - (float)worldHeight) / -30.0F;
               float heightPerc2 = ((float)player.position().y - ((float)worldHeight - 50.0F)) / -30.0F;
               this.extraVolumeAdjForDistScale = this.extraVolumeAdjForDistScale * Mth.clamp(heightPerc, 0.0F, 1.0F);
               this.extraVolumeAdjForDistScale = this.extraVolumeAdjForDistScale * (1.0F - Mth.clamp(heightPerc2, 0.0F, 1.0F));
            } else {
               this.extraVolumeAdjForDistScale = 0.0F;
            }
         } else if (this.mode == 4) {
            this.cutOffRange = Math.max(this.storm.width, 45.0F) * 0.85F;
            this.extraVolumeAdjForDistScale = Math.max(((float)this.storm.windspeed - 100.0F) / 100.0F, 0.0F);
            this.extraVolumeAdjForDistScale = this.extraVolumeAdjForDistScale * Mth.clamp((float)this.storm.listParticleDebris.size() / 100.0F, 0.0F, 1.0F);
         }
      }

      if (this.storm != null && this.storm.dead) {
         this.stop();
      }

      if (this.block != null && this.blockPos != null && this.level != null && !this.level.getBlockState(this.blockPos).is(this.block)) {
         this.stop();
      }

      if (!this.lockToPlayer && player != null) {
         double dist = this.realSource.distanceTo(player.position());
         if (dist > (double)this.cutOffRange) {
            this.volume = 0.0F;
         } else {
            this.volume = (float)(1.0 - dist / (double)this.cutOffRange) * this.extraVolumeAdjForDistScale;
         }
      }
   }
}
