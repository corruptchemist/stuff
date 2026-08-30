package dev.protomanly.pmweather.event;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.SeasonalPlantBlock;
import dev.protomanly.pmweather.block.entity.ModBlockEntities;
import dev.protomanly.pmweather.debug.DebugKeybinds;
import dev.protomanly.pmweather.entity.ModEntities;
import dev.protomanly.pmweather.entity.client.MovingBlockRenderer;
import dev.protomanly.pmweather.interfaces.IRegisterRenderTypesEvent;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.item.component.ModComponents;
import dev.protomanly.pmweather.particle.EmberParticleProvider;
import dev.protomanly.pmweather.particle.FireSmokeParticleProvider;
import dev.protomanly.pmweather.particle.FlameParticleProvider;
import dev.protomanly.pmweather.particle.FoamParticleProvider;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.particle.SparkParticleProvider;
import dev.protomanly.pmweather.render.AnemometerModel;
import dev.protomanly.pmweather.render.AnemometerRenderer;
import dev.protomanly.pmweather.render.ModRenderTypes;
import dev.protomanly.pmweather.render.RadarRenderer;
import dev.protomanly.pmweather.render.SoundingViewerRenderer;
import dev.protomanly.pmweather.render.WeatherBalloonModel;
import dev.protomanly.pmweather.render.WeatherPlatformRenderer;
import dev.protomanly.pmweather.seasons.FoliageColors;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import dev.protomanly.pmweather.shaders.data.TextureManager3D;
import dev.protomanly.pmweather.util.ColorTables;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import java.awt.Color;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block;

@EventBusSubscriber(
   modid = "pmweather",
   value = {Dist.CLIENT}
)
public class ModBusClientEvents {
   public ModBusClientEvents() {
   }

   @SubscribeEvent
   public static void registerBlockColorHandlers(Block event) {
      event.register(
         (state, level, pos, tintIndex) -> 16777215, new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)ModBlocks.FIRE.get()}
      );
      event.register(
         (state, tintGetter, pos, tintIndex) -> {
            if (tintGetter == null || pos == null) {
               return GrassColor.getDefaultColor();
            } else if (state.getBlock() instanceof SeasonalPlantBlock && (Boolean)state.getValue(SeasonalPlantBlock.DEAD)) {
               Minecraft minecraft = Minecraft.getInstance();
               Level level = minecraft.level;
               return level == null
                  ? GrassColor.getDefaultColor()
                  : FoliageColors.getColor("grass", (Biome)level.getBiome(pos).value(), pos.getCenter().x, pos.getCenter().z, 0.0F);
            } else {
               return BiomeColors.getAverageGrassColor(tintGetter, pos);
            }
         },
         new net.minecraft.world.level.block.Block[]{
            (net.minecraft.world.level.block.Block)ModBlocks.PLANTS.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.PLANTS_1.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.PLANTS_2.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.PLANTS_3.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.PLANTS_4.get()
         }
      );
      event.register((state, tintGetter, pos, tintIndex) -> {
         if (tintGetter != null && pos != null) {
            Color color = new Color(BiomeColors.getAverageFoliageColor(tintGetter, pos));
            color = ColorTables.lerp(0.5F, color, new Color(9084033));
            return color.getRGB();
         } else {
            return FoliageColor.getBirchColor();
         }
      }, new net.minecraft.world.level.block.Block[]{Blocks.BIRCH_LEAVES});
   }

   @SubscribeEvent
   public static void registerBindings(RegisterKeyMappingsEvent event) {
      event.register((KeyMapping)DebugKeybinds.RERENDER_CHUNKS.get());
      event.register((KeyMapping)DebugKeybinds.TOGGLE_RADAR_DEBUG.get());
   }

   @SubscribeEvent
   public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet((ParticleType)ModParticleTypes.FIRE_SMOKE.get(), FireSmokeParticleProvider::new);
      event.registerSpriteSet((ParticleType)ModParticleTypes.EMBER.get(), EmberParticleProvider::new);
      event.registerSpriteSet((ParticleType)ModParticleTypes.FLAME.get(), FlameParticleProvider::new);
      event.registerSpriteSet((ParticleType)ModParticleTypes.FOAM.get(), FoamParticleProvider::new);
      event.registerSpriteSet((ParticleType)ModParticleTypes.SPARK.get(), SparkParticleProvider::new);
   }

   @SubscribeEvent
   public static void reloadListeners(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(new SimplePreparableReloadListener<Object>() {
         protected Object prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            return null;
         }

         protected void apply(Object o, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            ModShadersVeil.ClearShaders();
            WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
            if (weatherHandler instanceof WeatherHandlerClient weatherHandlerClient) {
               weatherHandlerClient.killFireAudios();

               for (Storm storm : weatherHandler.getStorms()) {
                  storm.cleanupAudios();
               }
            }

            FBOManager.reset();
            TextureManager3D.refresh();
            ModShadersVeil.InitShaders();
         }
      });
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      EntityRenderers.register(ModEntities.MOVING_BLOCK.get(), MovingBlockRenderer::new);
      registerItemProp(
         event,
         (Item)ModItems.CONNECTOR.get(),
         PMWeather.getPath("connected"),
         (itemStack, clientLevel, livingEntity, i) -> itemStack.has(ModComponents.WEATHER_BALLOON_PLATFORM) ? 1.0F : 0.0F
      );
      registerItemProp(
         event,
         (Item)ModItems.FIRE_EXTINGUISHER.get(),
         PMWeather.getPath("using"),
         (itemStack, clientLevel, livingEntity, i) -> livingEntity != null
                  && livingEntity.isUsingItem()
                  && livingEntity.isHolding((Item)ModItems.FIRE_EXTINGUISHER.get())
               ? 1.0F
               : 0.0F
      );
      registerItemProp(
         event,
         (Item)ModItems.CALENDAR.get(),
         PMWeather.getPath("month"),
         (itemStack, clientLevel, livingEntity, i) -> {
            if (clientLevel == null) {
               return 1.0F;
            } else {
               Entity representation = itemStack.getEntityRepresentation();
               if (!(representation instanceof ItemFrame) && !(representation instanceof ItemEntity)) {
                  if (livingEntity instanceof Player player
                     && (player.getInventory().contains(itemStack) || ItemStack.matches(itemStack, player.containerMenu.getCarried()))) {
                     return (float)SeasonHandler.getMonth(clientLevel);
                  }

                  return (float)GameBusClientEvents.RandomMonth;
               } else {
                  return (float)SeasonHandler.getMonth(clientLevel);
               }
            }
         }
      );
   }

   public static void registerItemProp(FMLClientSetupEvent event, Item item, ResourceLocation propertyID, ItemPropertyFunction function) {
      event.enqueueWork(() -> ItemProperties.register(item, propertyID, function));
   }

   @SubscribeEvent
   public static void registerLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(AnemometerModel.LAYER_LOCATION, AnemometerModel::createBodyLayer);
      event.registerLayerDefinition(WeatherBalloonModel.LAYER_LOCATION, WeatherBalloonModel::createBodyLayer);
   }

   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerBlockEntityRenderer(ModBlockEntities.ANEMOMETER_BE.get(), AnemometerRenderer::new);
      event.registerBlockEntityRenderer(ModBlockEntities.RADAR_BE.get(), RadarRenderer::new);
      event.registerBlockEntityRenderer(ModBlockEntities.WEATHER_PLATFORM_BE.get(), WeatherPlatformRenderer::new);
      event.registerBlockEntityRenderer(ModBlockEntities.SOUNDING_VIEWER_BE.get(), SoundingViewerRenderer::new);
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void registerNamedRenderTypes(RegisterNamedRenderTypesEvent event) {
      if (event instanceof IRegisterRenderTypesEvent ievent) {
         ievent.pmwregister(PMWeather.getPath("swaying_cutout"), ModRenderTypes.swayingCutout(), NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT.get());
      }
   }

   @SubscribeEvent
   public static void registerColorHandlerItem(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
      event.register(
         (itemStack, i) -> i == 0 ? 3434810 : 16777215,
         new ItemLike[]{
            (ItemLike)ModBlocks.PLANTS.get(),
            (ItemLike)ModBlocks.PLANTS_1.get(),
            (ItemLike)ModBlocks.PLANTS_2.get(),
            (ItemLike)ModBlocks.PLANTS_3.get(),
            (ItemLike)ModBlocks.PLANTS_4.get()
         }
      );
   }
}
