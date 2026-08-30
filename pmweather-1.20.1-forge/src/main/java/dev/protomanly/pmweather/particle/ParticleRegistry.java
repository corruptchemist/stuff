package dev.protomanly.pmweather.particle;

import dev.protomanly.pmweather.PMWeather;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SpriteSourceProvider;

public class ParticleRegistry extends SpriteSourceProvider {
   public static TextureAtlasSprite lightRain;
   public static TextureAtlasSprite rain;
   public static TextureAtlasSprite mist;
   public static TextureAtlasSprite splash;
   public static TextureAtlasSprite snow;
   public static TextureAtlasSprite snow1;
   public static TextureAtlasSprite snow2;
   public static TextureAtlasSprite snow3;
   public static TextureAtlasSprite sleet;

   public ParticleRegistry(PackOutput output, CompletableFuture<Provider> lookupProvider, String modId, ExistingFileHelper existingFileHelper) {
      super(output, lookupProvider, modId, existingFileHelper);
   }

   protected void gather() {
      this.addSprite(PMWeather.getPath("particle/light_rain"));
      this.addSprite(PMWeather.getPath("particle/rain"));
      this.addSprite(PMWeather.getPath("particle/mist"));
      this.addSprite(PMWeather.getPath("particle/splash"));
      this.addSprite(PMWeather.getPath("particle/snow"));
      this.addSprite(PMWeather.getPath("particle/snow1"));
      this.addSprite(PMWeather.getPath("particle/snow2"));
      this.addSprite(PMWeather.getPath("particle/snow3"));
      this.addSprite(PMWeather.getPath("particle/sleet"));
   }

   public void addSprite(ResourceLocation resourceLocation) {
      this.atlas(SpriteSourceProvider.PARTICLES_ATLAS).addSource(new SingleFile(resourceLocation, Optional.empty()));
   }

   @Mod.EventBusSubscriber(
      modid = "pmweather",
      value = {Dist.CLIENT}
   )
   public static class Events {
      public Events() {
      }

      @SubscribeEvent
      public static void getRegisteredParticles(TextureAtlasStitchedEvent event) {
         if (event.getAtlas().location().equals(TextureAtlas.LOCATION_PARTICLES)) {
            ParticleRegistry.lightRain = event.getAtlas().getSprite(PMWeather.getPath("particle/light_rain"));
            ParticleRegistry.rain = event.getAtlas().getSprite(PMWeather.getPath("particle/rain"));
            ParticleRegistry.mist = event.getAtlas().getSprite(PMWeather.getPath("particle/mist"));
            ParticleRegistry.snow = event.getAtlas().getSprite(PMWeather.getPath("particle/snow"));
            ParticleRegistry.snow1 = event.getAtlas().getSprite(PMWeather.getPath("particle/snow1"));
            ParticleRegistry.snow2 = event.getAtlas().getSprite(PMWeather.getPath("particle/snow2"));
            ParticleRegistry.snow3 = event.getAtlas().getSprite(PMWeather.getPath("particle/snow3"));
            ParticleRegistry.splash = event.getAtlas().getSprite(PMWeather.getPath("particle/splash"));
            ParticleRegistry.sleet = event.getAtlas().getSprite(PMWeather.getPath("particle/sleet"));
         }
      }
   }
}
