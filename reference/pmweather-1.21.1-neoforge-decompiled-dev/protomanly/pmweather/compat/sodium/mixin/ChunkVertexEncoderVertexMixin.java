package dev.protomanly.pmweather.compat.sodium.mixin;

import dev.protomanly.pmweather.compat.sodium.interfaces.ChunkVertexExtension;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Vertex.class})
public class ChunkVertexEncoderVertexMixin implements ChunkVertexExtension {
   private int blockID = -1;
   private int localX;
   private int localY;
   private int localZ;

   public ChunkVertexEncoderVertexMixin() {
   }

   @Override
   public void pmwsetData(int id, int x, int y, int z) {
      this.blockID = id;
      this.localX = x;
      this.localY = y;
      this.localZ = z;
   }

   @Override
   public int getID() {
      return this.blockID;
   }

   @Override
   public int getX() {
      return this.localX;
   }

   @Override
   public int getY() {
      return this.localY;
   }

   @Override
   public int getZ() {
      return this.localZ;
   }

   @Override
   public void copyData(ChunkVertexExtension dst) {
      dst.pmwsetData(this.blockID, this.localX, this.localY, this.localZ);
   }

   @Inject(
      method = {"copyVertexTo"},
      at = {@At("HEAD")}
   )
   private static void copyVertex(Vertex from, Vertex to, CallbackInfo ci) {
      if (from instanceof ChunkVertexExtension _f && to instanceof ChunkVertexExtension _t) {
         _f.copyData(_t);
      }
   }
}
