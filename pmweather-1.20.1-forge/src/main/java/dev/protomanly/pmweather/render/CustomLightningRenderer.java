package dev.protomanly.pmweather.render;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.weather.effects.ClientLightning;
import java.awt.Color;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;

public class CustomLightningRenderer {
   public CustomLightningRenderer() {
   }

   public static void render(ClientLightning lightning, Camera camera, Color color) {
      if (Minecraft.getInstance().player != null) {
         Random rand = new Random(lightning.seed);
         Vec3 camPos = camera.getPosition();
         Vec3 offset = lightning.position.subtract(camPos);
         Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
         matrix4fStack.pushMatrix();
         matrix4fStack.translate((float)offset.x, (float)offset.y, (float)offset.z);
         RenderSystem.applyModelViewMatrix();
         RenderSystem.enableBlend();
         RenderSystem.depthMask(true);
         RenderSystem.enableDepthTest();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder bufferBuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         Vec3 lastPos = new Vec3(
            (double)rand.nextFloat(-256.0F, 256.0F), ServerConfig.layer0Height * 2.0 - lightning.position.y, (double)rand.nextFloat(-256.0F, 256.0F)
         );
         float r = 3.5F;
         float distance = (float)camera.getPosition().multiply(1.0, 0.0, 1.0).distanceTo(lightning.position.multiply(1.0, 0.0, 1.0));
         float rMult = Math.max(Mth.sqrt(distance / 64.0F) * 2.0F, 2.0F);

         while (lastPos.y > 0.0 && r > 0.1F) {
            Vec3 newPos = lastPos.add((double)rand.nextFloat(-6.0F, 6.0F), (double)rand.nextFloat(-20.0F, 0.0F), (double)rand.nextFloat(-6.0F, 6.0F));
            if (newPos.y <= 0.0) {
               newPos.multiply(1.0, 0.0, 1.0);
            }

            newPos = newPos.lerp(Vec3.ZERO, Mth.square(1.0 - Mth.clamp(newPos.y / 200.0, 0.0, 1.0)));
            box(
               bufferBuilder,
               color,
               newPos.add((double)(-rMult * r), 0.0, (double)(-rMult * r)),
               newPos.add((double)(-rMult * r), 0.0, (double)(rMult * r)),
               newPos.add((double)(rMult * r), 0.0, (double)(rMult * r)),
               newPos.add((double)(rMult * r), 0.0, (double)(-rMult * r)),
               lastPos.add((double)(-rMult * r), 0.0, (double)(-rMult * r)),
               lastPos.add((double)(-rMult * r), 0.0, (double)(rMult * r)),
               lastPos.add((double)(rMult * r), 0.0, (double)(rMult * r)),
               lastPos.add((double)(rMult * r), 0.0, (double)(-rMult * r))
            );
            lastPos = newPos;
            if (rand.nextInt(8) == 0) {
               float r2 = r * 0.8F;
               if (r2 > 0.1F) {
                  r *= 0.6F;
                  Vec3 lP = newPos;
                  int n = rand.nextInt(3, 10);

                  for (int i = 0; i < n; i++) {
                     Vec3 nP = lP.add(
                        (double)(rand.nextFloat(-50.0F, 50.0F) / ((float)i + 1.0F)),
                        (double)rand.nextFloat(-10.0F, 5.0F),
                        (double)(rand.nextFloat(-50.0F, 50.0F) / ((float)i + 1.0F))
                     );
                     box(
                        bufferBuilder,
                        color,
                        nP.add((double)(-rMult * r2), 0.0, (double)(-rMult * r2)),
                        nP.add((double)(-rMult * r2), 0.0, (double)(rMult * r2)),
                        nP.add((double)(rMult * r2), 0.0, (double)(rMult * r2)),
                        nP.add((double)(rMult * r2), 0.0, (double)(-rMult * r2)),
                        lP.add((double)(-rMult * r2), 0.0, (double)(-rMult * r2)),
                        lP.add((double)(-rMult * r2), 0.0, (double)(rMult * r2)),
                        lP.add((double)(rMult * r2), 0.0, (double)(rMult * r2)),
                        lP.add((double)(rMult * r2), 0.0, (double)(-rMult * r2))
                     );
                     lP = nP;
                  }
               }
            }

            if (r <= 0.1F && newPos.y > 0.0) {
               r = 0.15F;
            }
         }

         matrix4fStack.translate(-((float)offset.x), -((float)offset.y), -((float)offset.z));
         matrix4fStack.popMatrix();
         MeshData meshData = bufferBuilder.build();
         if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
         }

         RenderSystem.applyModelViewMatrix();
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
      }
   }

   public static void box(BufferBuilder bufferBuilder, Color color, Vec3 btl, Vec3 bbl, Vec3 bbr, Vec3 btr, Vec3 ttl, Vec3 tbl, Vec3 tbr, Vec3 ttr) {
      quad(bufferBuilder, color, btl, bbl, bbr, btr, true);
      quad(bufferBuilder, color, ttl, tbl, tbr, ttr, false);
      quad(bufferBuilder, color, ttl, btl, btr, ttr, false);
      quad(bufferBuilder, color, ttl, btl, bbl, tbl, false);
      quad(bufferBuilder, color, tbr, bbr, btr, ttr, false);
      quad(bufferBuilder, color, tbr, bbr, bbl, tbl, false);
   }

   public static void quad(BufferBuilder bufferBuilder, Color color, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, boolean clockwise) {
      float r = (float)color.getRed() / 255.0F;
      float g = (float)color.getGreen() / 255.0F;
      float b = (float)color.getBlue() / 255.0F;
      float a = (float)color.getAlpha() / 255.0F;
      if (clockwise) {
         bufferBuilder.addVertex(tr.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(br.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(bl.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(tl.toVector3f()).setColor(r, g, b, a);
      } else {
         bufferBuilder.addVertex(tl.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(bl.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(br.toVector3f()).setColor(r, g, b, a);
         bufferBuilder.addVertex(tr.toVector3f()).setColor(r, g, b, a);
      }
   }
}
