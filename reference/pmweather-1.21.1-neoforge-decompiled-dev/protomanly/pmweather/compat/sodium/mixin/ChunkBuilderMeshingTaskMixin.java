package dev.protomanly.pmweather.compat.sodium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.interfaces.BufferBuilderExtension;
import dev.protomanly.pmweather.render.ModRenderTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChunkBuilderMeshingTask.class})
public class ChunkBuilderMeshingTaskMixin {
   public ChunkBuilderMeshingTaskMixin() {
   }

   @Inject(
      method = {"execute"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V",
         shift = Shift.BEFORE
      )}
   )
   private void pmwonRenderBlock(
      ChunkBuildContext buildContext,
      CancellationToken cancellationToken,
      CallbackInfoReturnable<ChunkBuildOutput> cir,
      @Local BlockState blockState,
      @Local(ordinal = 0) MutableBlockPos worldPos,
      @Local(ordinal = 1) MutableBlockPos blockPos,
      @Local LevelSlice slice,
      @Local BlockRenderer blockRenderer
   ) {
      if (ClientConfig.swayingGrass && blockRenderer instanceof BufferBuilderExtension bufferBuilderExtension) {
         bufferBuilderExtension.beginBlock(ModRenderTypes.getBlockTypeForRender(blockState, worldPos, slice), blockPos.getX(), blockPos.getY(), blockPos.getZ());
      }
   }
}
