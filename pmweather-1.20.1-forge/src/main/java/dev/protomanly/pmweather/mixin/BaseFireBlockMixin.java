package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.config.ServerConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraftforge.common.Tags.Blocks;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BaseFireBlock.class})
public class BaseFireBlockMixin {
   public BaseFireBlockMixin() {
   }

   @Shadow
   private static boolean inPortalDimension(Level level) {
      throw new AssertionError();
   }

   @Inject(
      method = {"onPlace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void editOnPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving, CallbackInfo callbackInfo) {
      if (ServerConfig.doWildfires && !level.getBlockState(pos.below()).is(Blocks.NETHERRACKS) && ServerConfig.allowNonLightningFireSources) {
         if (!oldState.is(state.getBlock())) {
            if (inPortalDimension(level)) {
               Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(level, pos, Axis.X);
               optional = ForgeEventFactory.onTrySpawnPortal(level, pos, optional);
               if (optional.isPresent()) {
                  optional.get().createPortalBlocks();
                  callbackInfo.cancel();
                  return;
               }
            }

            if (!state.canSurvive(level, pos)) {
               level.removeBlock(pos, false);
               callbackInfo.cancel();
               return;
            }
         }

         if (!state.is(net.minecraft.world.level.block.Blocks.FIRE)) {
            callbackInfo.cancel();
            return;
         }

         level.setBlockAndUpdate(pos, ((Block)ModBlocks.FIRE.get()).defaultBlockState());
         callbackInfo.cancel();
      }
   }
}
