package dev.protomanly.pmweather.shaders;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.post.PostPipeline.Context;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

public class PMWPostShader {
   public static Map<String, PMWPostShader> SHADERS = new HashMap<>();
   private final ResourceLocation location;
   protected Minecraft minecraft;
   protected LocalPlayer player;
   protected WeatherHandlerClient weatherHandler;
   protected ClientLevel level;
   protected float partialTicks = 0.0F;

   public static PMWPostShader getShader(ResourceLocation location) {
      return !location.getNamespace().equals("pmweather") ? null : SHADERS.getOrDefault(location.getPath(), null);
   }

   public PMWPostShader(String location) {
      this.location = PMWeather.getPath(location);
      SHADERS.putIfAbsent(location, this);
   }

   public void pre(PostPipeline pipeline, Context context) {
      this.minecraft = Minecraft.getInstance();
      this.player = this.minecraft.player;
      this.weatherHandler = (WeatherHandlerClient)GameBusClientEvents.weatherHandler;
      this.level = this.minecraft.level;
      if (this.isValidFrame()) {
         this.partialTicks = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);
      }

      pipeline.getUniformSafe("shouldRender").setInt(this.shouldRender() ? 1 : 0);
   }

   public void init() {
      PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
      boolean dirty = post.add(this.getLocation());
      PMWeather.LOGGER.info("Shader {} marked {}", this.getLocation().toString(), dirty ? "dirty" : "not dirty");
   }

   public void clear() {
      PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
      boolean dirty = post.remove(this.getLocation());
      PMWeather.LOGGER.info(dirty ? "Shader {} disabled" : "Shader {} not running for disable", this.getLocation().toString());
   }

   public void tick() {
   }

   protected boolean shouldRender() {
      return this.isValidDimension();
   }

   protected boolean isValidFrame() {
      return this.minecraft != null && this.player != null && this.level != null && this.weatherHandler != null;
   }

   protected boolean isValidDimension() {
      return this.isValidFrame() && ServerConfig.validDimensions != null && ServerConfig.validDimensions.contains(this.level.dimension());
   }

   public ResourceLocation getLocation() {
      return this.location;
   }

   public void onCompile(ShaderProgram program, ResourceLocation id) {
   }
}
