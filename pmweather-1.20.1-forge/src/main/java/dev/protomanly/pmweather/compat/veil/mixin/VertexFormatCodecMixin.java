package dev.protomanly.pmweather.compat.veil.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.protomanly.pmweather.render.ModVertexFormats;
import foundry.veil.api.client.util.VertexFormatCodec;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VertexFormatCodec.class})
public class VertexFormatCodecMixin {
   public VertexFormatCodecMixin() {
   }

   @Inject(
      method = {"getDefaultFormats"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void editDefaultFormats(CallbackInfoReturnable<Map<String, VertexFormat>> cir) {
      Map<String, VertexFormat> rtrn = new HashMap<>((Map<? extends String, ? extends VertexFormat>)cir.getReturnValue());
      rtrn.put("WAVING_BLOCK", ModVertexFormats.WAVING_BLOCK);
      cir.setReturnValue(rtrn);
   }
}
