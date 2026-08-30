package dev.protomanly.pmweather.compat.sodium.mixin;

import dev.protomanly.pmweather.compat.sodium.PMWChunkVertex;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ChunkMeshFormats.class},
   priority = 5000
)
public class ChunkMeshFormatsMixin {
   @Shadow
   @Mutable
   @Final
   public static ChunkVertexType COMPACT;

   public ChunkMeshFormatsMixin() {
   }

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   )
   private static void modVertex(CallbackInfo ci) {
      COMPACT = new PMWChunkVertex();
   }
}
