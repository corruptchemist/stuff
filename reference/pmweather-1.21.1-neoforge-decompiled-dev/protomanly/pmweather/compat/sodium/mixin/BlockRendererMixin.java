package dev.protomanly.pmweather.compat.sodium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.compat.sodium.interfaces.ChunkVertexExtension;
import dev.protomanly.pmweather.interfaces.BufferBuilderExtension;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlockRenderer.class})
public class BlockRendererMixin implements BufferBuilderExtension {
   @Unique
   private int pmwcurrentBlockID = -1;
   @Unique
   private int pmwcurrentLocalPosX;
   @Unique
   private int pmwcurrentLocalPosY;
   @Unique
   private int pmwcurrentLocalPosZ;

   public BlockRendererMixin() {
   }

   @Override
   public void beginBlock(int block, int localPosX, int localPosY, int localPosZ) {
      this.pmwcurrentBlockID = block;
      this.pmwcurrentLocalPosX = localPosX;
      this.pmwcurrentLocalPosY = localPosY;
      this.pmwcurrentLocalPosZ = localPosZ;
   }

   @Override
   public void endBlock() {
      this.pmwcurrentBlockID = -1;
   }

   @Inject(
      method = {"bufferQuad"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;x:F"
      )}
   )
   private void pmwWrite(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci, @Local Vertex vertex) {
      if (vertex instanceof ChunkVertexExtension chunkVertexExtension) {
         chunkVertexExtension.pmwsetData(this.pmwcurrentBlockID, this.pmwcurrentLocalPosX, this.pmwcurrentLocalPosY, this.pmwcurrentLocalPosZ);
      }
   }
}
