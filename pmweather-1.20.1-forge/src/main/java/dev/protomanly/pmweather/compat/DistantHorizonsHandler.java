package dev.protomanly.pmweather.compat;

import com.mojang.blaze3d.platform.Window;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.util.SmartDataUpdater;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector2i;

public class DistantHorizonsHandler {
   private boolean dhReady = false;
   private boolean enabled = false;
   private boolean dirty = true;
   private final SmartDataUpdater<Integer> dhDepthTextureId = new SmartDataUpdater<>(-1);
   private final SmartDataUpdater<Integer> dhColorTextureId = new SmartDataUpdater<>(-1);
   private final SmartDataUpdater<Vector2i> screenSize = new SmartDataUpdater<>(new Vector2i(0));
   private final Matrix4f dhProjectionMatrix = new Matrix4f();
   private float dhNearPlane = 0.05F;
   private float dhFarPlane = 1024.0F;
   private int dhRenderDistance = 256;
   private final ResourceLocation MAIN = PMWeather.getPath("distant_horizons_main");

   public DistantHorizonsHandler() {
   }

   public void initialize() {
      try {
         this.registerEventHandlers();
      } catch (Exception var2) {
         PMWeather.LOGGER.error("Failed to register DH event handlers", var2);
         throw var2;
      }
   }

   private void updateFBO() {
      this.dirty = true;
      Vector2i _screenSize = this.screenSize.get();
      VeilRenderer renderer = VeilRenderSystem.renderer();
      FramebufferManager framebufferManager = renderer.getFramebufferManager();
      AdvancedFbo fbo = AdvancedFbo.withSize(_screenSize.x, _screenSize.y)
         .addColorTextureWrapper(this.dhColorTextureId.get())
         .setDepthTextureWrapper(this.dhDepthTextureId.get())
         .build(true);

      try {
         framebufferManager.setFramebuffer(this.MAIN, fbo);
         this.dirty = false;
      } catch (Throwable var8) {
         if (fbo != null) {
            try {
               fbo.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (fbo != null) {
         fbo.close();
      }
   }

   private void registerEventHandlers() {
      DhApi.events.bind(DhApiAfterDhInitEvent.class, new DhApiAfterDhInitEvent() {
         public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
            PMWeather.LOGGER.info("Distant Horizons initialized");
            DistantHorizonsHandler.this.dhReady = true;

            try {
               DistantHorizonsHandler.this.dhRenderDistance = (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue();
            } catch (Exception var3) {
               PMWeather.LOGGER.warn("Failed to get DH render distance, using default", var3);
               DistantHorizonsHandler.this.dhRenderDistance = 256;
            }
         }
      });
      DhApi.events.bind(DhApiBeforeRenderPassEvent.class, new DhApiBeforeRenderPassEvent() {
         public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
            DistantHorizonsHandler.this.captureRenderState((DhApiRenderParam)event.value);
         }
      });
      DhApi.events.bind(DhApiBeforeRenderSetupEvent.class, new DhApiBeforeRenderSetupEvent() {
         public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
            DistantHorizonsHandler.this.captureRenderState((DhApiRenderParam)event.value);
         }
      });
   }

   private void captureRenderState(DhApiRenderParam param) {
      try {
         Window window = Minecraft.getInstance().getWindow();
         Vector2i _screenSize = new Vector2i(window.getWidth(), window.getHeight());
         boolean dirtyFbo = this.screenSize.update(_screenSize);
         DhApiResult<Integer> depthResult = Delayed.renderProxy.getDhDepthTextureId();
         if (depthResult.success && (Integer)depthResult.payload > 0) {
            dirtyFbo = dirtyFbo || this.dhDepthTextureId.update((Integer)depthResult.payload);
         }

         DhApiResult<Integer> colorResult = Delayed.renderProxy.getDhColorTextureId();
         if (colorResult.success && (Integer)colorResult.payload > 0) {
            dirtyFbo = dirtyFbo || this.dhColorTextureId.update((Integer)colorResult.payload);
         }

         if (dirtyFbo || this.dirty) {
            this.updateFBO();
         }

         this.enabled = (Boolean)Delayed.configs.graphics().renderingEnabled().getValue();
         float[] projValues = param.dhProjectionMatrix.getValuesAsArray();
         this.dhProjectionMatrix
            .set(
               projValues[0],
               projValues[4],
               projValues[8],
               projValues[12],
               projValues[1],
               projValues[5],
               projValues[9],
               projValues[13],
               projValues[2],
               projValues[6],
               projValues[10],
               projValues[14],
               projValues[3],
               projValues[7],
               projValues[11],
               projValues[15]
            );
         this.dhNearPlane = param.nearClipPlane;
         this.dhFarPlane = param.farClipPlane;

         try {
            this.dhRenderDistance = (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue();
         } catch (Exception var9) {
         }
      } catch (Exception var10) {
         PMWeather.LOGGER.error("Failed to capture DH render state", var10);
      }
   }

   public boolean isReady() {
      return this.dhReady && this.dhDepthTextureId.get() > 0;
   }

   public boolean isEnabled() {
      this.enabled = (Boolean)Delayed.configs.graphics().renderingEnabled().getValue();
      return this.enabled;
   }

   public Matrix4f getDhProjectionMatrix() {
      return new Matrix4f(this.dhProjectionMatrix);
   }

   public float getNearPlane() {
      return this.dhNearPlane;
   }

   public float getFarPlane() {
      return this.dhFarPlane;
   }

   public int getChunkRenderDistance() {
      return this.dhRenderDistance;
   }
}
