package dev.protomanly.pmweather.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormatElement.Type;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;
import foundry.veil.api.client.render.vertex.VeilVertexFormat;

public class ModVertexFormats {
   public static final VertexFormatElement BLOCK_ID = VeilVertexFormat.register(0, Type.SHORT, Usage.GENERIC, 1);
   public static final VertexFormatElement MID_BLOCK = VeilVertexFormat.register(0, Type.BYTE, Usage.GENERIC, 3);
   public static final VertexFormat WAVING_BLOCK = VertexFormat.builder()
      .add("Position", VertexFormatElement.POSITION)
      .add("Color", VertexFormatElement.COLOR)
      .add("UV0", VertexFormatElement.UV0)
      .add("UV2", VertexFormatElement.UV2)
      .add("Normal", VertexFormatElement.NORMAL)
      .padding(1)
      .add("pmw_blockId", BLOCK_ID)
      .add("pmw_midBlock", MID_BLOCK)
      .padding(1)
      .build();

   public ModVertexFormats() {
   }
}
