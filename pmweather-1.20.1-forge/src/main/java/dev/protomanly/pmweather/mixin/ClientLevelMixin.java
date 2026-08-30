package dev.protomanly.pmweather.mixin;

import net.minecraft.util.Mth;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientLevel.class})
public class ClientLevelMixin {
   @Shadow
   @Final
   private LevelRenderer levelRenderer;

   public ClientLevelMixin() {
   }

   @Shadow
   private Block getMarkerParticleTarget() {
      throw new AssertionError();
   }

   @Shadow
   private void trySpawnDripParticles(BlockPos blockPos, BlockState blockState, ParticleOptions particleData, boolean shapeDownSolid) {
   }

   @Inject(
      method = {"animateTick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editAnimateTick(int posX, int posY, int posZ, CallbackInfo callbackInfo) {
      int i = 32;
      RandomSource randomsource = RandomSource.create();
      Block block = this.getMarkerParticleTarget();
      MutableBlockPos blockpos$mutableblockpos = new MutableBlockPos();

      for (int j = 0; j < 3072; j++) {
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 32, randomsource, block, blockpos$mutableblockpos);
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 64, randomsource, block, blockpos$mutableblockpos);
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 64, randomsource, block, blockpos$mutableblockpos);
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 128, randomsource, block, blockpos$mutableblockpos);
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 128, randomsource, block, blockpos$mutableblockpos);
         ((ClientLevel)this).doAnimateTick(posX, posY, posZ, 256, randomsource, block, blockpos$mutableblockpos);
      }

      callbackInfo.cancel();
   }

   @Inject(
      method = {"doAnimateTick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void doAnimateTick(
      int posX, int posY, int posZ, int range, RandomSource random, @Nullable Block block, MutableBlockPos blockPos, CallbackInfo callbackInfo
   ) {
      ClientLevel self = (ClientLevel)this;
      int i = posX + self.random.nextInt(range) - self.random.nextInt(range);
      int j = posY + self.random.nextInt(range) - self.random.nextInt(range);
      int k = posZ + self.random.nextInt(range) - self.random.nextInt(range);
      blockPos.set(i, j, k);
      BlockState blockstate = self.getBlockState(blockPos);
      if (!blockstate.is(ModBlocks.FIRE) && self.random.nextInt(10) != 0) {
         callbackInfo.cancel();
      } else {
         blockstate.getBlock().animateTick(blockstate, self, blockPos, random);
         FluidState fluidstate = self.getFluidState(blockPos);
         if (!fluidstate.isEmpty()) {
            fluidstate.animateTick(self, blockPos, random);
            ParticleOptions particleoptions = fluidstate.getDripParticle();
            if (particleoptions != null && self.random.nextInt(10) == 0) {
               boolean flag = blockstate.isFaceSturdy(self, blockPos, Direction.DOWN);
               BlockPos blockpos = blockPos.below();
               this.trySpawnDripParticles(blockpos, self.getBlockState(blockpos), particleoptions, flag);
            }
         }

         if (block == blockstate.getBlock()) {
            self.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockstate), (double)i + 0.5, (double)j + 0.5, (double)k + 0.5, 0.0, 0.0, 0.0);
         }

         if (!blockstate.isCollisionShapeFullBlock(self, blockPos)) {
            ((Biome)self.getBiome(blockPos).value())
               .getAmbientParticle()
               .ifPresent(
                  p_264703_ -> {
                     if (p_264703_.canSpawn(self.random)) {
                        self.addParticle(
                           p_264703_.getOptions(),
                           (double)blockPos.getX() + self.random.nextDouble(),
                           (double)blockPos.getY() + self.random.nextDouble(),
                           (double)blockPos.getZ() + self.random.nextDouble(),
                           0.0,
                           0.0,
                           0.0
                        );
                     }
                  }
               );
         }

         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"getSkyDarken"},
      at = {@At("RETURN")},
      cancellable = true
   )
   public void editSkyDarken(float partialTick, CallbackInfoReturnable<Float> callbackInfoReturnable) {
      float darken = 0.0F;
      WeatherHandlerClient weatherHandler = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
      if (weatherHandler != null) {
         darken = Mth.clamp((float)Math.pow((double)weatherHandler.getPrecipitation(), 0.3333333333333333), 0.0F, 1.0F);
      }

      callbackInfoReturnable.setReturnValue((Float)callbackInfoReturnable.getReturnValue() * (1.0F - darken));
   }
}
