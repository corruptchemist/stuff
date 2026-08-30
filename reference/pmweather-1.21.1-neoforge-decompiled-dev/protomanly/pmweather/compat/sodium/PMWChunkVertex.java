package dev.protomanly.pmweather.compat.sodium;

import dev.protomanly.pmweather.compat.sodium.interfaces.ChunkVertexExtension;
import foundry.veil.ext.sodium.ChunkVertexEncoderVertexExtension;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.DefaultChunkMeshAttributes;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexFormatAttribute;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

public class PMWChunkVertex implements ChunkVertexType {
   public static final int STRIDE = 36;
   public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(36)
      .addElement(DefaultChunkMeshAttributes.POSITION, 0, 0)
      .addElement(DefaultChunkMeshAttributes.COLOR, 1, 8)
      .addElement(DefaultChunkMeshAttributes.TEXTURE, 2, 12)
      .addElement(DefaultChunkMeshAttributes.LIGHT_MATERIAL_INDEX, 3, 16)
      .addElement(new VertexFormatAttribute("NORMAL_INDEX", GlVertexAttributeFormat.BYTE, 4, false, true), 4, 20)
      .addElement(new VertexFormatAttribute("BLOCK_ID_INDEX", GlVertexAttributeFormat.FLOAT, 1, false, false), 5, 24)
      .addElement(new VertexFormatAttribute("MID_BLOCK_INDEX", GlVertexAttributeFormat.UNSIGNED_INT, 2, false, true), 6, 28)
      .build();
   private static final int POSITION_MAX_VALUE = 1048576;
   private static final int TEXTURE_MAX_VALUE = 32768;
   private static final float MODEL_ORIGIN = 8.0F;
   private static final float MODEL_RANGE = 32.0F;

   public PMWChunkVertex() {
   }

   public GlVertexFormat getVertexFormat() {
      return VERTEX_FORMAT;
   }

   public ChunkVertexEncoder getEncoder() {
      return (ptr, matBits, vertices, section) -> {
         float texCentroidU = 0.0F;
         float texCentroidV = 0.0F;

         for (Vertex vertex : vertices) {
            texCentroidU += vertex.u;
            texCentroidV += vertex.v;
         }

         texCentroidU *= 0.25F;
         texCentroidV *= 0.25F;

         for (int i = 0; i < 4; i++) {
            Vertex vertex = vertices[i];
            int x = quantizePosition(vertex.x);
            int y = quantizePosition(vertex.y);
            int z = quantizePosition(vertex.z);
            int u = encodeTexture(texCentroidU, vertex.u);
            int v = encodeTexture(texCentroidV, vertex.v);
            int light = encodeLight(vertex.light);
            MemoryUtil.memPutInt(ptr, packPositionHi(x, y, z));
            MemoryUtil.memPutInt(ptr + 4L, packPositionLo(x, y, z));
            MemoryUtil.memPutInt(ptr + 8L, ColorARGB.mulRGB(vertex.color, vertex.ao));
            MemoryUtil.memPutInt(ptr + 12L, packTexture(u, v));
            MemoryUtil.memPutInt(ptr + 16L, packLightAndData(light, matBits, section));
            ChunkVertexEncoderVertexExtension ext = (ChunkVertexEncoderVertexExtension)vertex;
            MemoryUtil.memPutInt(ptr + 20L, ext.veil$getPackedNormal());
            ChunkVertexExtension chunkVertexExtension = (ChunkVertexExtension)vertex;
            MemoryUtil.memPutFloat(ptr + 24L, (float)chunkVertexExtension.getID());
            int ox = quantizePosition(vertex.x + 0.5F - (float)chunkVertexExtension.getX());
            int oy = quantizePosition(vertex.y + 0.5F - (float)chunkVertexExtension.getY());
            int oz = quantizePosition(vertex.z + 0.5F - (float)chunkVertexExtension.getZ());
            MemoryUtil.memPutInt(ptr + 28L, packPositionHi(ox, oy, oz));
            MemoryUtil.memPutInt(ptr + 32L, packPositionLo(ox, oy, oz));
            ptr += 36L;
         }

         return ptr;
      };
   }

   private static int packPositionHi(int x, int y, int z) {
      return x >>> 10 & 1023 | (y >>> 10 & 1023) << 10 | (z >>> 10 & 1023) << 20;
   }

   private static int packPositionLo(int x, int y, int z) {
      return x & 1023 | (y & 1023) << 10 | (z & 1023) << 20;
   }

   private static int quantizePosition(float position) {
      return (int)(normalizePosition(position) * 1048576.0F) & 1048575;
   }

   private static float normalizePosition(float v) {
      return (8.0F + v) / 32.0F;
   }

   private static int packTexture(int u, int v) {
      return u & 65535 | (v & 65535) << 16;
   }

   private static int encodeTexture(float center, float x) {
      int bias = x < center ? 1 : -1;
      int quantized = floorInt(x * 32768.0F) + bias;
      return quantized & 32767 | sign(bias) << 15;
   }

   private static int encodeLight(int light) {
      int sky = Mth.clamp(light >>> 16 & 0xFF, 8, 248);
      int block = Mth.clamp(light & 0xFF, 8, 248);
      return block | sky << 8;
   }

   private static int packLightAndData(int light, int material, int section) {
      return light & 65535 | (material & 0xFF) << 16 | (section & 0xFF) << 24;
   }

   private static int sign(int x) {
      return x >>> 31;
   }

   private static int floorInt(float x) {
      return (int)Math.floor((double)x);
   }
}
