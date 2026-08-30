package dev.protomanly.pmweather.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.interfaces.BufferBuilderExtension;
import dev.protomanly.pmweather.render.ModRenderTypes;
import dev.protomanly.pmweather.render.ModVertexFormats;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionCompiler.Results;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SectionCompiler.class})
public class SectionCompilerMixin {
   public SectionCompilerMixin() {
   }

   @Inject(
      method = {"compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
         shift = Shift.BEFORE
      )}
   )
   private void pmwcompileBeginBlock(
      SectionPos sectionPos,
      RenderChunkRegion region,
      VertexSorting vertexSorting,
      SectionBufferBuilderPack sectionBufferBuilderPack,
      List<AdditionalSectionRenderer> additionalRenderers,
      CallbackInfoReturnable<Results> cir,
      @Local BufferBuilder bufferbuilder1,
      @Local BlockState blockstate,
      @Local(ordinal = 2) BlockPos blockpos2
   ) {
      if (ClientConfig.swayingGrass && bufferbuilder1 instanceof BufferBuilderExtension bufferBuilderExtension) {
         bufferBuilderExtension.beginBlock(
            ModRenderTypes.getBlockTypeForRender(blockstate, blockpos2, region), blockpos2.getX(), blockpos2.getY(), blockpos2.getZ()
         );
      }
   }

   @Inject(
      method = {"compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
         shift = Shift.BEFORE
      )}
   )
   private void pmwcompileEndBlock(
      SectionPos sectionPos,
      RenderChunkRegion region,
      VertexSorting vertexSorting,
      SectionBufferBuilderPack sectionBufferBuilderPack,
      List<AdditionalSectionRenderer> additionalRenderers,
      CallbackInfoReturnable<Results> cir,
      @Local BufferBuilder bufferbuilder1
   ) {
      if (bufferbuilder1 instanceof BufferBuilderExtension bufferBuilderExtension) {
         bufferBuilderExtension.endBlock();
      }
   }

   @Inject(
      method = {"getOrBeginLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pmwgetOrBeginLayer(
      Map<RenderType, BufferBuilder> bufferLayers,
      SectionBufferBuilderPack sectionBufferBuilderPack,
      RenderType renderType,
      CallbackInfoReturnable<BufferBuilder> cir
   ) {
      if (renderType.format == ModVertexFormats.WAVING_BLOCK) {
         BufferBuilder bufferbuilder = bufferLayers.get(renderType);
         if (bufferbuilder == null) {
            ByteBufferBuilder bytebufferbuilder = sectionBufferBuilderPack.buffer(renderType);
            bufferbuilder = new BufferBuilder(bytebufferbuilder, Mode.QUADS, ModVertexFormats.WAVING_BLOCK);
            bufferLayers.put(renderType, bufferbuilder);
         }

         cir.setReturnValue(bufferbuilder);
      }
   }
}
