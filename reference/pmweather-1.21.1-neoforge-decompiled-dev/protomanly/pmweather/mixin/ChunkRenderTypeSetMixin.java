package dev.protomanly.pmweather.mixin;

import com.google.common.collect.ImmutableList;
import dev.protomanly.pmweather.render.ModRenderTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ChunkRenderTypeSet.class})
public class ChunkRenderTypeSetMixin {
   @Shadow
   @Mutable
   @Final
   public static List<RenderType> CHUNK_RENDER_TYPES_LIST;
   @Shadow
   @Mutable
   @Final
   public static RenderType[] CHUNK_RENDER_TYPES;

   public ChunkRenderTypeSetMixin() {
   }

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   )
   private static void addRenderTypes(CallbackInfo ci) {
      List<RenderType> mutableList = new ArrayList<>(CHUNK_RENDER_TYPES_LIST);
      mutableList.add(ModRenderTypes.swayingCutout());
      CHUNK_RENDER_TYPES_LIST = ImmutableList.copyOf(mutableList);
      CHUNK_RENDER_TYPES = CHUNK_RENDER_TYPES_LIST.toArray(new RenderType[0]);
   }
}
