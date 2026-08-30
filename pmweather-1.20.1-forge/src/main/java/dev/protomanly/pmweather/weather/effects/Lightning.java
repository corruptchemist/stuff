package dev.protomanly.pmweather.weather.effects;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.PMWFireBlock;
import dev.protomanly.pmweather.compat.powergrid.PowerGridMod;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.util.Util;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class Lightning {
   public static List<Color> LIGHTNING_COLORS = new ArrayList<Color>() {
      {
         this.add(new Color(16777215));
         this.add(new Color(16775639));
         this.add(new Color(15204321));
         this.add(new Color(14799103));
         this.add(new Color(16769248));
         this.add(new Color(14090233));
      }
   };
   public static final List<Lightning.LightningFuture> LIGHTNING_FUTURES = new ArrayList<>();
   public static final List<Lightning.ClientLightningFuture> CLIENT_LIGHTNING_FUTURES = new ArrayList<>();
   public static final long TICK_DELAY = 60L;
   public Color color;
   public long seed;
   public Vec3 position;
   public float strength;
   public Level level;
   public boolean dead;
   public int ticks = 0;
   public int lifetime;

   public Lightning(Vec3 position, Level level, float strength) {
      this.position = position;
      this.level = level;
      this.seed = PMWeather.RANDOM.nextLong();
      this.dead = false;
      this.lifetime = PMWeather.RANDOM.nextInt(5, 20);
      this.strength = strength;
      this.color = LIGHTNING_COLORS.get(PMWeather.RANDOM.nextInt(LIGHTNING_COLORS.size()));
   }

   public void tick() {
      if (!this.dead) {
         this.ticks++;
         if (this.ticks > this.lifetime) {
            this.dead = true;
            this.onDeath();
         }
      }
   }

   public void onDeath() {
   }

   public static record ClientLightningFuture(Level level, Vec3 pos, float strength, long atTick) {
   }

   public static record LightningFuture(ServerLevel level, BlockPos pos, boolean rodHit, float strength, long atTick) {
      public void strike() {
         if (this.level.isLoaded(this.pos)) {
            boolean transferEnergy = !this.rodHit;
            if (this.rodHit) {
               BlockState rodState = this.level.getBlockState(this.pos.below());
               if (rodState.getBlock() instanceof LightningRodBlock rod) {
                  boolean broken = this.level.random.nextFloat() * 80.0F < this.strength / 20.0F;
                  if (broken) {
                     transferEnergy = true;
                     this.level.removeBlock(this.pos.below(), false);
                  } else {
                     rod.onLightningStrike(rodState, this.level, this.pos.below());
                  }
               }
            }

            PowerGridMod.lightning(this.level, this.pos.getCenter());
            if (transferEnergy) {
               if (ServerConfig.doWildfires) {
                  BlockPos strikePos = this.level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, this.pos);
                  BlockState state = this.level.getBlockState(strikePos);
                  BlockState belowState = this.level.getBlockState(strikePos.below());
                  if (!Util.hasCollision(state, strikePos, this.level)
                     && PMWFireBlock.canBurnOn(this.level, belowState, strikePos.below(), 4)
                     && this.level.random.nextInt(5) == 0) {
                     this.level.setBlockAndUpdate(strikePos, (BlockState)((Block)ModBlocks.FIRE.get()).defaultBlockState().setValue(PMWFireBlock.INTENSITY, 4));
                  }
               }

               double affectDist = Math.sqrt((double)this.strength) * 7.5;
               Vec3 lPos = this.pos.getCenter().multiply(1.0, 0.25, 1.0);
               List<? extends LivingEntity> struckEntities = this.level
                  .getEntities(
                     EntityTypeTest.forClass(LivingEntity.class),
                     entity -> !this.level.canSeeSky(entity.blockPosition()) ? false : entity.position().multiply(1.0, 0.25, 1.0).distanceTo(lPos) < affectDist
                  );
               struckEntities.forEach(entity -> {
                  double dist = entity.position().multiply(1.0, 0.25, 1.0).distanceTo(lPos);
                  float perc = 1.0F - (float)Mth.clamp(dist / affectDist, 0.0, 1.0);
                  entity.hurt(this.level.damageSources().lightningBolt(), (this.level.random.nextFloat() + 0.25F) * 30.0F * perc * Mth.sqrt(this.strength));
               });
            }
         }
      }
   }
}
