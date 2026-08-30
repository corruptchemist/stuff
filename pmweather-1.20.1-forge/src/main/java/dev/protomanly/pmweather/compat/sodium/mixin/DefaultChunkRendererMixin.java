package dev.protomanly.pmweather.compat.sodium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.compat.sodium.ModTerrainRenderPasses;
import dev.protomanly.pmweather.compat.sodium.interfaces.IDefaultShaderInterface;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DefaultChunkRenderer.class})
public abstract class DefaultChunkRendererMixin {
   public DefaultChunkRendererMixin() {
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",
         shift = Shift.AFTER
      )}
   )
   private void redirectRender(
      ChunkRenderMatrices matrices,
      CommandList commandList,
      ChunkRenderListIterable renderLists,
      TerrainRenderPass renderPass,
      CameraTransform camera,
      boolean indexedRenderingEnabled,
      CallbackInfo ci,
      @Local ChunkShaderInterface shader
   ) {
      if (shader instanceof IDefaultShaderInterface dShader) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.cameraEntity == null) {
            return;
         }

         float time = (float)minecraft.cameraEntity.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(true);
         CameraMatrices cammatrices = VeilRenderSystem.renderer().getCameraMatrices();
         Vector3f camPos = cammatrices.getCameraPosition();
         Vec3 mojangCamPos = new Vec3(camPos);
         dShader.setPMWDefaults(mojangCamPos, time, renderPass == ModTerrainRenderPasses.SWAYING_CUTOUT ? 1 : 0);
      }
   }
}
