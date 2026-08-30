package dev.protomanly.pmweather;

import com.mojang.logging.LogUtils;
import dev.protomanly.pmweather.block.ModBlockSetType;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.entity.ModBlockEntities;
import dev.protomanly.pmweather.compat.DistantHorizons;
import dev.protomanly.pmweather.compat.create.CreateMod;
import dev.protomanly.pmweather.compat.powergrid.PowerGridMod;
import dev.protomanly.pmweather.compat.sable.SableMod;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.creative.ModCreativeTabs;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.entity.ModEntities;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.item.component.ModComponents;
import dev.protomanly.pmweather.level.LevelUpdaters;
import dev.protomanly.pmweather.multiblock.MultiBlocks;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.render.ModRenderTypes;
import dev.protomanly.pmweather.seasons.FoliageColors;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.sound.ModSounds;
import foundry.veil.api.event.VeilRenderLevelStageEvent.Stage;
import foundry.veil.platform.VeilEventPlatform;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod("pmweather")
public class PMWeather {
   private static ModContainer modContainer;
   public static final String MOD_ID = "pmweather";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final Random RANDOM = new Random();

   public PMWeather(FMLModContainer container, IEventBus bus, Dist dist) {
      modContainer = container;
      ModComponents.COMPONENTS.register(bus);
      ModEntities.ENTITY_TYPES.register(bus);
      ModBlockEntities.BLOCK_ENTITIES.register(bus);
      MultiBlocks.register();
      ModSounds.SOUND_EVENTS.register(bus);
      ModBlockSetType.register();
      ModBlocks.BLOCKS.register(bus);
      ModItems.ITEMS.register(bus);
      ModCreativeTabs.CREATIVE_MODE_TABS.register(bus);
      DataAttachments.ATTACHMENT_TYPES.register(bus);
      ModParticleTypes.PARTICLE_TYPES.register(bus);
      if (dist.isClient()) {
         container.registerConfig(Type.CLIENT, ClientConfig.SPEC);
         FoliageColors.register();

         try {
            DistantHorizons.initialize();
         } catch (Exception var8) {
            LOGGER.info("Failed to initialize Distant Horizons compatibility: {}", var8.getMessage());
         }

         VeilEventPlatform.INSTANCE.preVeilPostProcessing(ModShadersVeil::PreVeilPostProcessing);
         VeilEventPlatform.INSTANCE.onVeilRendererAvailable(ModShadersVeil::VeilRendererAvailable);
         VeilEventPlatform.INSTANCE.onVeilShaderCompile(ModShadersVeil::VeilCompileShaders);
         VeilEventPlatform.INSTANCE.onVeilRegisterFixedBuffers(event -> event.registerFixedBuffer(Stage.AFTER_CUTOUT_BLOCKS, ModRenderTypes.swayingCutout()));
         VeilEventPlatform.INSTANCE.onVeilRegisterBlockLayers(event -> event.registerBlockLayer(ModRenderTypes.swayingCutout()));
      }

      try {
         CreateMod.initialize();
      } catch (Exception var7) {
         LOGGER.info("Failed to initialize Create compatibility: {}", var7.getMessage());
      }

      try {
         SableMod.initialize();
      } catch (Exception var6) {
         LOGGER.info("Failed to initialize Sable compatibility: {}", var6.getMessage());
      }

      try {
         PowerGridMod.initialize();
      } catch (Exception var5) {
         LOGGER.info("Failed to initialize Create Power Grid compatibility: {}", var5.getMessage());
      }

      container.registerConfig(Type.SERVER, ServerConfig.SPEC);
      if (dist.isClient()) {
         container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
      }

      LevelUpdaters.Init();
      LOGGER.info("Loaded PMWeather Version {}", container.getModInfo().getVersion().toString());
   }

   public static ModContainer getModContainer() {
      return modContainer;
   }

   public static ResourceLocation getPath(String path) {
      return new ResourceLocation("pmweather", path);
   }
}
