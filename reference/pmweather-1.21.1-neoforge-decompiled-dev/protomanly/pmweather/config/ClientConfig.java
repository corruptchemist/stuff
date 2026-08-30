package dev.protomanly.pmweather.config;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.config.ModConfigEvent.Unloading;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

@EventBusSubscriber(
   modid = "pmweather",
   value = {Dist.CLIENT}
)
public class ClientConfig {
   private static final Builder BUILDER = new Builder();
   private static final BooleanValue BASE_GAME_FOG = BUILDER.push("graphics")
      .comment("Whether the mod will disable Minecraft's base game fog system.")
      .define("basegamefog", true);
   public static boolean baseGameFog;
   private static final EnumValue<ModShadersVeil.Quality> VOLUMETRICS_QUALITY = BUILDER.comment("Quality of volumetric clouds")
      .defineEnum("volumetricsquality", ModShadersVeil.Quality.MEDIUM);
   public static ModShadersVeil.Quality volumetricsQuality;
   private static final DoubleValue VOLUMETRICS_DOWNSAMPLE = BUILDER.comment(
         "Render scale of volumetric clouds (Causes artifacting but greatly improves performance) (Value is curved at resolutions greater than 1080p)"
      )
      .defineInRange("volumetricsdownsample", 2.5, 1.0, 4.0);
   public static double volumetricsDownsample;
   private static final BooleanValue COMPUTATIONAL_NOISE = BUILDER.comment(
         "Whether the mod will forgo texture lookup FBM noise in favor of on the fly calculation"
      )
      .define("computationalnoise", false);
   public static boolean computationalNoise;
   private static final BooleanValue GLOW_FIX = BUILDER.comment("Whether the mod will attempt to fix bleeding when downsampled").define("glowfix", true);
   public static boolean glowFix;
   private static final BooleanValue SIMPLE_LIGHTING = BUILDER.comment(
         "Whether the sun will cast light on clouds, turning this off will improve performance at the cost of visuals"
      )
      .define("simplelighting", true);
   public static boolean simpleLighting;
   private static final BooleanValue VOLUMETRICS_BLUR = BUILDER.comment("Whether the mod will blur the output of the volumetrics shader")
      .define("volumetricsblur", true);
   public static boolean volumetricsBlur;
   private static final BooleanValue SWAYING_GRASS = BUILDER.comment("Whether the mod will render grass as swaying in the wind").define("swayinggrass", true);
   public static boolean swayingGrass;
   private static final DoubleValue FIRE_PARTICLE_DENSITY = BUILDER.comment("Fire particle density").defineInRange("fireparticledensity", 0.25, 0.0, 1.0);
   public static double fireParticleDensity;
   public static int stormParticleSpawnDelay = 2;
   public static int cloudParticleSpawnDelay = 3;
   private static final IntValue MAX_PARTICLE_SPAWN_DISTANCE_FROM_PLAYER = BUILDER.comment("Max distance particles will spawn from the player")
      .defineInRange("maxparticlespawndistancefromplayer", 1024, 256, 2048);
   public static int maxParticleSpawnDistanceFromPlayer;
   public static double tornadoParticleDensity = 0.35;
   private static final DoubleValue DEBRIS_PARTICLE_DENSITY = BUILDER.comment("Density of debris particles, lower values are more performant")
      .defineInRange("debrisparticledensity", 0.35, 0.0, 1.0);
   public static double debrisParticleDensity;
   public static boolean customParticles = true;
   private static final IntValue RAIN_PARTICLE_DENSITY = BUILDER.comment("Particle Density of Rain").defineInRange("rainparticledensity", 30, 5, 60);
   public static int rainParticleDensity;
   private static final IntValue RADAR_RESOLUTION = BUILDER.comment("Radar resolution").defineInRange("radarresolution", 100, 32, 200);
   public static int radarResolution;
   private static final DoubleValue LEAVES_VOLUME = BUILDER.pop()
      .push("sound")
      .comment("Volume of leaves in wind")
      .defineInRange("leavesvolume", 0.0, 0.0, 1.0);
   public static double leavesVolume;
   private static final DoubleValue SIREN_VOLUME = BUILDER.comment("Volume of tornado sirens").defineInRange("sirenvolume", 0.75, 0.0, 1.0);
   public static double sirenVolume;
   private static final BooleanValue RADAR_DEBUGGING = BUILDER.pop().push("debug").comment("Whether to use radar debugging").define("radardebugging", false);
   public static boolean radarDebugging;
   private static final EnumValue<ClientConfig.RadarMode> RADAR_MODE = BUILDER.comment("Radar mode when radar debugging is enabled")
      .defineEnum("radarmode", ClientConfig.RadarMode.TEMPERATURE);
   public static ClientConfig.RadarMode radarMode;
   private static final BooleanValue FUNENOMETERS = BUILDER.pop()
      .push("etc")
      .comment("Whether anemometers will shake at high speed")
      .define("funenometers", false);
   public static boolean funenometers;
   private static final BooleanValue METRIC = BUILDER.comment("Whether to use the metric system over imperial").define("metric", false);
   public static boolean metric;
   private static final BooleanValue _3X3RADAR = BUILDER.comment("Whether radars should be forced into 3x3 size, even when extended").define("3x3radar", false);
   public static boolean _3X3Radar;
   public static final ModConfigSpec SPEC = BUILDER.build();

   public ClientConfig() {
   }

   @SubscribeEvent
   private static void onLoad(ModConfigEvent event) {
      if (event.getConfig().getSpec() == SPEC && !(event instanceof Unloading)) {
         PMWeather.LOGGER.info("Loading Client PMWeather Configs");
         swayingGrass = (Boolean)SWAYING_GRASS.get();
         glowFix = (Boolean)GLOW_FIX.get();
         simpleLighting = (Boolean)SIMPLE_LIGHTING.get();
         volumetricsBlur = (Boolean)VOLUMETRICS_BLUR.get();
         volumetricsDownsample = (Double)VOLUMETRICS_DOWNSAMPLE.get();
         volumetricsQuality = (ModShadersVeil.Quality)VOLUMETRICS_QUALITY.get();
         metric = (Boolean)METRIC.get();
         radarDebugging = (Boolean)RADAR_DEBUGGING.get();
         radarMode = (ClientConfig.RadarMode)RADAR_MODE.get();
         radarResolution = (Integer)RADAR_RESOLUTION.get();
         leavesVolume = (Double)LEAVES_VOLUME.get();
         sirenVolume = (Double)SIREN_VOLUME.get();
         maxParticleSpawnDistanceFromPlayer = (Integer)MAX_PARTICLE_SPAWN_DISTANCE_FROM_PLAYER.get();
         debrisParticleDensity = (Double)DEBRIS_PARTICLE_DENSITY.get();
         baseGameFog = (Boolean)BASE_GAME_FOG.get();
         _3X3Radar = (Boolean)_3X3RADAR.get();
         rainParticleDensity = (Integer)RAIN_PARTICLE_DENSITY.get();
         funenometers = (Boolean)FUNENOMETERS.get();
         fireParticleDensity = (Double)FIRE_PARTICLE_DENSITY.get();
         computationalNoise = (Boolean)COMPUTATIONAL_NOISE.get();
      }
   }

   public static enum RadarMode {
      TEMPERATURE,
      SST,
      CLOUDS,
      WINDFIELDS,
      GLOBALWINDS,
      LOCALWINDS,
      CAPE,
      CAPE3KM,
      CINH,
      LAPSERATE03,
      LAPSERATE36,
      FIRE,
      PRECIPITATION;

      private RadarMode() {
      }
   }
}
