package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.interfaces.ISnowLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SnowLayerBlock.class})
public class SnowLayerBlockMixin implements ISnowLayerBlock {
   public SnowLayerBlockMixin() {
   }

   @Shadow
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      throw new AssertionError();
   }

   @Inject(
      method = {"randomTick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callbackInfo) {
      callbackInfo.cancel();
   }

   @Override
   public boolean survives(BlockState state, LevelReader level, BlockPos pos) {
      return this.canSurvive(state, level, pos);
   }
}
