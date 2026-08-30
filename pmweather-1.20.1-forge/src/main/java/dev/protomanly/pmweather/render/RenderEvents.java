package dev.protomanly.pmweather.render;

import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.particle.ParticleManager;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import dev.protomanly.pmweather.weather.effects.ClientLightning;
import foundry.veil.api.client.render.light.data.LightData;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;

@Mod.EventBusSubscriber(
   modid = "pmweather",
   value = {Dist.CLIENT}
)
public class RenderEvents {
   public RenderEvents() {
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void renderParticles(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_WEATHER) {
         ParticleManager pm = GameBusClientEvents.particleManager;
         if (pm != null) {
            pm.render(
               event.getPoseStack(),
               null,
               Minecraft.getInstance().gameRenderer.lightTexture(),
               event.getCamera(),
               event.getPartialTick().getGameTimeDeltaPartialTick(false),
               event.getFrustum()
            );
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_SKY) {
         FBOManager.uploadFBOs();
      }

      if (event.getStage() == Stage.AFTER_WEATHER) {
         RadarRenderer.RenderedRadars = 0;
         float partialTicks = event.getPartialTick().getGameTimeDeltaTicks();
         WeatherHandlerClient weatherHandlerClient = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
         if (weatherHandlerClient != null) {
            List<ClientLightning> lightnings = weatherHandlerClient.lightnings;

            for (int i = 0; i < lightnings.size(); i++) {
               ClientLightning lightning = lightnings.get(i);
               if (lightning != null) {
                  Color color = lightning.color;
                  float p = Math.clamp(((float)lightning.ticks + partialTicks) / (float)lightning.lifetime, 0.0F, 1.0F);
                  float alpha = (float)Math.abs(Math.cos(Math.sqrt((double)p) * Math.PI * 3.0)) * (1.0F - p);
                  color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp((long)((int)(alpha * 255.0F)), 0, 255));
                  if (lightning.lightHandle != null) {
                     LightData lightData = lightning.lightHandle.getLightData();
                     lightData.setBrightness(alpha * (Mth.sqrt(lightning.strength) + 1.0F) * 2.0F);
                  }

                  CustomLightningRenderer.render(lightning, event.getCamera(), color);
               }
            }
         }
      }
   }
}
