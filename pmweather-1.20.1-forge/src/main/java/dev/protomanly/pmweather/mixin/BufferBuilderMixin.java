package dev.protomanly.pmweather.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.protomanly.pmweather.interfaces.BufferBuilderExtension;
import dev.protomanly.pmweather.render.ModVertexFormats;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BufferBuilder.class})
public abstract class BufferBuilderMixin implements BufferBuilderExtension {
   @Shadow
   private int elementsToFill;
   @Shadow
   @Final
   private VertexFormat format;
   @Shadow
   @Mutable
   @Final
   private boolean fastFormat;
   @Unique
   private int pmwcurrentBlockID = -1;
   @Unique
   private int pmwcurrentLocalPosX;
   @Unique
   private int pmwcurrentLocalPosY;
   @Unique
   private int pmwcurrentLocalPosZ;
   @Unique
   private boolean setData = false;
   @Unique
   private float vertexX = 0.0F;
   @Unique
   private float vertexY = 0.0F;
   @Unique
   private float vertexZ = 0.0F;

   public BufferBuilderMixin() {
   }

   @Shadow
   protected abstract long beginElement(VertexFormatElement var1);

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void injectInit(CallbackInfo callbackInfo) {
      if (this.format == ModVertexFormats.WAVING_BLOCK) {
         this.fastFormat = false;
      }
   }

   @Unique
   private byte[] packMidBlock(float ox, float oy, float oz) {
      float x = (float)this.pmwcurrentLocalPosX + 0.5F - ox;
      float y = (float)this.pmwcurrentLocalPosY + 0.5F - oy;
      float z = (float)this.pmwcurrentLocalPosZ + 0.5F - oz;
      return new byte[]{(byte)((int)(x * 64.0F) & 0xFF), (byte)((int)(y * 64.0F) & 0xFF), (byte)((int)(z * 64.0F) & 0xFF)};
   }

   @Inject(
      method = {"endLastVertex"},
      at = {@At("HEAD")}
   )
   private void injectEndLastVertex(CallbackInfo ci) {
      if (!this.setData) {
         this.pmwSetBlockID((short)-10);
         this.pmwSetMidBlock(this.vertexX, this.vertexY, this.vertexZ);
      }
   }

   @Inject(
      method = {"addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"},
      at = {@At("HEAD")}
   )
   private void injectAddVertex(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
      this.vertexX = x;
      this.vertexY = y;
      this.vertexZ = z;
   }

   @Inject(
      method = {"addVertex(FFFIFFIIFFF)V"},
      at = {@At("RETURN")}
   )
   private void injectVertexData(
      float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ, CallbackInfo ci
   ) {
      this.vertexX = x;
      this.vertexY = y;
      this.vertexZ = z;
      this.setData = true;
      this.pmwSetBlockID((short)this.pmwcurrentBlockID);
      this.pmwSetMidBlock(x, y, z);
   }

   @Unique
   private void pmwSetBlockID(short val) {
      long offset = this.beginElement(ModVertexFormats.BLOCK_ID);
      if (offset != -1L) {
         MemoryUtil.memPutShort(offset, val);
      }
   }

   @Unique
   private void pmwSetMidBlock(float x, float y, float z) {
      long offset = this.beginElement(ModVertexFormats.MID_BLOCK);
      if (offset != -1L) {
         byte[] data = this.packMidBlock(x, y, z);
         MemoryUtil.memPutByte(offset, data[0]);
         MemoryUtil.memPutByte(offset + 1L, data[1]);
         MemoryUtil.memPutByte(offset + 2L, data[2]);
      }
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
}
