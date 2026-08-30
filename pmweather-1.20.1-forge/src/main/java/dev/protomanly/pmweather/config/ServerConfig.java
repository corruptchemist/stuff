package dev.protomanly.pmweather.config;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent.Unloading;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

@Mod.EventBusSubscriber(
   modid = "pmweather"
)
public class ServerConfig {
   private static final Builder BUILDER = new Builder();
   private static final BooleanValue DODAMAGE = BUILDER.push("modules").comment("Whether damage should be done by weather").define("dodamage", true);
   public static boolean doDamage;
   private static final BooleanValue USESEASONALPLANTS = BUILDER.comment("Whether the mod's custom plants are enabled").define("useseasonalplants", true);
   public static boolean useSeasonalPlants;
   private static final BooleanValue DOGRASSGENERATION = BUILDER.comment("Whether the mod will generate any grass").define("dograssgeneration", true);
   public static boolean doGrassGeneration;
   private static final BooleanValue DOSEASONALLEVELUPDATER = BUILDER.comment(
         "Whether the mod will update chunks in the world that stayed unloaded for an amount of time for seasons (will cause some performance loss with chunk loading)"
      )
      .define("doseasonallevelupdater", true);
   public static boolean doSeasonalLevelUpdater;
   private static final BooleanValue DOROTTING = BUILDER.comment("Whether logs will rot whenever trees lose their leaves").define("dorotting", true);
   public static boolean doRotting;
   private static final BooleanValue DOWILDFIRES = BUILDER.comment("Whether the mod's custom fire system is used").define("dowildfires", true);
   public static boolean doWildfires;
   private static final BooleanValue DOTORNADOES = BUILDER.comment("Whether tornadoes should be possible").define("dotornadoes", true);
   public static boolean doTornadoes;
   private static final BooleanValue DOCYCLONES = BUILDER.comment("Whether cyclones should be possible").define("docyclones", true);
   public static boolean doCyclones;
   private static final BooleanValue DOSQUALLS = BUILDER.comment("Whether squalls should be possible").define("dosqualls", true);
   public static boolean doSqualls;
   private static final BooleanValue DO_DEBARKING = BUILDER.comment("Whether debarking will be applied to logs").define("dodebarking", true);
   public static boolean doDebarking;
   private static final BooleanValue DO_SEASONS = BUILDER.comment("Whether seasons exist").define("doseasons", true);
   public static boolean doSeasons;
   private static final BooleanValue SEASONAL_CROPS = BUILDER.comment("Whether temperatures affect crop growth and death").define("seasonalcrops", true);
   public static boolean seasonalCrops;
   private static final BooleanValue DOICICLES = BUILDER.comment("Whether PMWeather will spawn icicles").define("doicicles", true);
   public static boolean doIcicles;
   private static final IntValue SPAWN_RANGE = BUILDER.pop()
      .push("spawning")
      .comment("Range within which clouds and storms will spawn from players")
      .defineInRange("spawnrange", 384, 256, 2048);
   public static int spawnRange;
   private static final IntValue CHANCE_IN_ONE_SQUALL = BUILDER.comment("1 in x chance that a spawning storm will spawn as a squall")
      .defineInRange("chanceinonesquall", 10, 1, 100);
   public static int chanceInOneSquall;
   private static final IntValue CHANCE_IN_ONE_CYCLONE = BUILDER.comment("1 in x chance that a spawning storm will spawn as a cyclone")
      .defineInRange("chanceinonecyclone", 64, 1, 100);
   public static int chanceInOneCyclone;
   private static final IntValue CHANCE_IN_ONE_STAGE_1 = BUILDER.comment("1 in x chance that a storm will progress to stage 1")
      .defineInRange("chanceinonestage1", 2, 1, 100);
   public static int chanceInOneStage1;
   private static final IntValue CHANCE_IN_ONE_STAGE_2 = BUILDER.comment("1 in x chance that a storm will progress to stage 2")
      .defineInRange("chanceinonestage2", 3, 1, 100);
   public static int chanceInOneStage2;
   private static final IntValue CHANCE_IN_ONE_STAGE_3 = BUILDER.comment("1 in x chance that a storm will progress to stage 3 / tornado")
      .defineInRange("chanceinonestage3", 5, 1, 100);
   public static int chanceInOneStage3;
   private static final DoubleValue STORM_SPAWN_CHANCE_PER_MINUTE = BUILDER.comment("Chance a storm will spawn each minute")
      .defineInRange("stormspawnchanceperminute", 0.2, 0.0, 1.0);
   public static double stormSpawnChancePerMinute;
   private static final BooleanValue ENVIRONMENT_SYSTEM = BUILDER.comment(
         "Whether chance to spawn storms and chance for storms to progress is affected by the game environment"
      )
      .define("environmentsystem", true);
   public static boolean environmentSystem;
   private static final DoubleValue RISK_CURVE = BUILDER.comment("Risk curve, higher is rarer").defineInRange("riskcurve", 1.0, 0.5, 2.0);
   public static double riskCurve;
   private static final IntValue MAX_STORMS = BUILDER.comment("Maximum number of active storms allowed to spawn").defineInRange("maxstorms", 3, 1, 10);
   public static int maxStorms;
   private static final DoubleValue SQUALL_STRENGTH_MULTIPLIER = BUILDER.pop()
      .push("storms")
      .comment("Multiplier of squall windspeeds")
      .defineInRange("squallstrengthmultiplier", 1.25, 0.0, 2.0);
   public static double squallStrengthMultiplier;
   private static final IntValue SNOW_ACCUMULATION_HEIGHT = BUILDER.comment("Maximum precipitation accumulation, 0 = off")
      .defineInRange("snowaccumulationheight", 6, 0, 8);
   public static int snowAccumulationHeight;
   public static int maxClouds = 0;
   private static final ConfigValue<List<? extends String>> BLOCK_STENGTHS = BUILDER.comment(
         "List of blocks and respective windspeed at which they get damaged"
      )
      .defineListAllowEmpty(
         "blockstrengths",
         () -> new ArrayList<String>() {
               {
                  this.add("pmweather:reinforced_glass=140");
                  this.add("pmweather:reinforced_glass_pane=130");
                  this.add("minecraft:acacia_leaves=55");
                  this.add("minecraft:azalea_leaves=55");
                  this.add("minecraft:birch_leaves=50");
                  this.add("minecraft:dark_oak_leaves=55");
                  this.add("minecraft:cherry_leaves=55");
                  this.add("minecraft:flowering_azalea_leaves=55");
                  this.add("minecraft:mangrove_leaves=65");
                  this.add("minecraft:oak_leaves=55");
                  this.add("minecraft:jungle_leaves=55");
                  this.add("minecraft:chain=75");
                  this.add("minecraft:lantern=75");
                  this.add("minecraft:soul_lantern=75");
                  this.add("minecraft:cobweb=65");
                  this.add("minecraft:white_wool=60");
                  this.add("minecraft:orange_wool=60");
                  this.add("minecraft:magenta_wool=60");
                  this.add("minecraft:light_blue_wool=60");
                  this.add("minecraft:yellow_wool=60");
                  this.add("minecraft:lime_wool=60");
                  this.add("minecraft:pink_wool=60");
                  this.add("minecraft:gray_wool=60");
                  this.add("minecraft:light_gray_wool=60");
                  this.add("minecraft:cyan_wool=60");
                  this.add("minecraft:purple_wool=60");
                  this.add("minecraft:blue_wool=60");
                  this.add("minecraft:brown_wool=60");
                  this.add("minecraft:green_wool=60");
                  this.add("minecraft:red_wool=60");
                  this.add("minecraft:black_wool=60");
                  this.add("minecraft:chest=120");
               }
            },
         () -> "pmweather",
         e -> {
            if (e instanceof String string
               && string.contains("=")
               && string.split("=").length == 2
               && Objects.nonNull(ResourceLocation.tryParse(string.split("=")[0]))
               && BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(string.split("=")[0]))
               && Util.isInteger(string.split("=")[1])) {
               return true;
            }

            return false;
         }
      );
   public static Map<Block, Float> blockStrengths;
   private static final ConfigValue<List<? extends String>> BLACKLISTED_BLOCKS = BUILDER.comment("List of blocks not allowed to be damaged")
      .defineListAllowEmpty(
         "blacklistedblocks",
         () -> new ArrayList<String>() {
               {
                  this.add("minecraft:gravel");
                  this.add("minecraft:farmland");
                  this.add("minecraft:dirt_path");
                  this.add("minecraft:snow_block");
               }
            },
         () -> "pmweather",
         e -> {
            if (e instanceof String string
               && Objects.nonNull(ResourceLocation.tryParse(string))
               && BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(string))) {
               return true;
            }

            return false;
         }
      );
   public static List<Block> blacklistedBlocks;
   private static final ConfigValue<List<? extends String>> BLACKLISTED_BLOCKTAGS = BUILDER.comment("List of blocktags not allowed to be damaged")
      .defineListAllowEmpty("blacklistedblocktags", () -> new ArrayList<String>() {
            {
               this.add("minecraft:dirt");
               this.add("minecraft:base_stone_overworld");
               this.add("minecraft:terracotta");
               this.add("minecraft:badlands_terracotta");
               this.add("minecraft:ice");
               this.add("minecraft:sand");
            }
         }, () -> "pmweather", e -> {
         if (e instanceof String string) {
            ResourceLocation path = ResourceLocation.tryParse(string);
            if (Objects.isNull(path)) {
               return false;
            } else {
               TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, path);
               BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);
               return BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).iterator().hasNext();
            }
         } else {
            return false;
         }
      });
   public static List<TagKey<Block>> blacklistedBlockTags;
   private static final ConfigValue<List<? extends String>> PLAYER_PLACED_WHITELIST = BUILDER.comment(
         "List of blocktags allowed to be damaged when blacklisted if placed by a player"
      )
      .defineListAllowEmpty("playerplacedwhitelist", () -> new ArrayList<String>() {
            {
               this.add("minecraft:base_stone_overworld");
               this.add("minecraft:terracotta");
               this.add("minecraft:badlands_terracotta");
            }
         }, () -> "pmweather", e -> {
         if (e instanceof String string) {
            ResourceLocation path = ResourceLocation.tryParse(string);
            if (Objects.isNull(path)) {
               return false;
            } else {
               TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, path);
               BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);
               return BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).iterator().hasNext();
            }
         } else {
            return false;
         }
      });
   public static List<TagKey<Block>> playerPlacedWhitelist;
   private static final DoubleValue STORM_SIZE = BUILDER.comment("Size of storms").defineInRange("stormsize", 456.0, 128.0, 512.0);
   public static double stormSize;
   private static final IntValue MAX_TORNADO_WIDTH = BUILDER.comment("Maximum width of tornadoes").defineInRange("maxtornadowidth", 900, 100, 1200);
   public static double maxTornadoWidth;
   private static final BooleanValue AIM_AT_PLAYER = BUILDER.comment("Whether storms will aim at the player whenever strengthening into a tornado")
      .define("aimatplayer", false);
   public static boolean aimAtPlayer;
   private static final DoubleValue AIM_AT_PLAYER_OFFSET = BUILDER.comment("Random range of blocks that storms will aim at around players")
      .defineInRange("aimatplayeroffset", 248.0, 0.0, 1024.0);
   public static double aimAtPlayerOffset;
   private static final IntValue MAX_CHUNKS_LOADED_PER_5_TICKS = BUILDER.comment("Maximum number of chunks that can be loaded at once every 5 ticks")
      .defineInRange("maxchunksloadedper5ticks", 5, 1, 15);
   public static int maxChunksLoadedPer5Ticks;
   private static final IntValue MAX_BLOCKS_DAMAGED_PER_TICK = BUILDER.comment("Maximum number of blocks allowed to be damaged by a tornado per tick")
      .defineInRange("maxblocksdamagedpertick", 12500, 5000, 40000);
   public static int maxBlocksDamagedPerTick;
   private static final BooleanValue DAMAGE_EVERY_5TH_TICK = BUILDER.comment("Whether damage should run every tick (off) or every 5th tick (on)")
      .define("damageevery5thtick", true);
   public static boolean damageEvery5thTick;
   private static final DoubleValue FLYING_BLOCK_ENTITY_CHANCE = BUILDER.comment(
         "Chance modifier that a block damaged by a tornado uses flying block entities (higher values may cause TPS lag during damage)"
      )
      .defineInRange("flyingblockentitychance", 1.0, 0.0, 1.0);
   public static double flyingBlockEntityChance;
   private static final DoubleValue OVERCAST_PERCENT = BUILDER.pop()
      .push("global")
      .comment("Overcast Modifier")
      .defineInRange("overcastpercent", 1.0, 0.0, 1.4);
   public static double overcastPercent;
   private static final DoubleValue RAIN_STRENGTH = BUILDER.comment("Rain Modifier").defineInRange("rainstrength", 0.8, 0.0, 1.25);
   public static double rainStrength;
   private static final DoubleValue LAYER_0_HEIGHT = BUILDER.comment("Height of first cloud layer").defineInRange("layer0height", 315.0, 150.0, 350.0);
   public static double layer0Height;
   private static final DoubleValue LAYER_C_HEIGHT = BUILDER.comment("Height of cirrus cloud layer").defineInRange("layerCheight", 2000.0, 1000.0, 3000.0);
   public static double layerCHeight;
   private static final DoubleValue BACKGROUND_WIND_MULTIPLIER = BUILDER.comment("Multiplier of base windspeeds")
      .defineInRange("backgroundwindmultiplier", 1.0, 0.0, 4.0);
   public static double backgroundWindMultiplier;
   private static final ConfigValue<List<? extends String>> VALID_DIMENSIONS = BUILDER.comment("List of valid dimensions for spawning weather")
      .defineListAllowEmpty("validdimensions", () -> new ArrayList<String>() {
            {
               this.add("minecraft:overworld");
            }
         }, () -> "pmweather", e -> e instanceof String);
   public static List<ResourceKey<Level>> validDimensions;
   private static final IntValue MONTH_LENGTH = BUILDER.comment("Length of months for seasons (in days)").defineInRange("monthlength", 8, 1, 31);
   public static int monthLength;
   private static final ConfigValue<List<? extends String>> INCINERATE_BLOCKS = BUILDER.pop()
      .push("fire")
      .comment("List of blocks to be incinerated in high temperatures from fires")
      .defineListAllowEmpty(
         "incinerateblocks",
         () -> new ArrayList<String>() {
               {
                  this.add("minecraft:torch");
                  this.add("minecraft:wall_torch");
                  this.add("minecraft:redstone_torch");
                  this.add("minecraft:redstone_wall_torch");
                  this.add("minecraft:soul_torch");
                  this.add("minecraft:soul_wall_torch");
                  this.add("minecraft:moss_carpet");
                  this.add("minecraft:lantern");
                  this.add("minecraft:soul_lantern");
                  this.add("pmweather:sleet_layer");
                  this.add("pmweather:ice_layer");
                  this.add("minecraft:snow");
                  this.add("minecraft:snow_block");
                  this.add("minecraft:ice");
                  this.add("minecraft:blue_ice");
                  this.add("minecraft:packed_ice");
                  this.add("minecraft:frosted_ice");
                  this.add("minecraft:cobweb");
                  this.add("minecraft:ladder");
               }
            },
         () -> "pmweather",
         e -> {
            if (e instanceof String string
               && Objects.nonNull(ResourceLocation.tryParse(string))
               && BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(string))) {
               return true;
            }

            return false;
         }
      );
   public static List<Block> incinerateBlocks;
   private static final BooleanValue ALLOW_NON_LIGHTNING_FIRE_SOURCES = BUILDER.comment("Whether sources other than lightning can start PMWeather fires")
      .define("allownonlightningfiresources", true);
   public static boolean allowNonLightningFireSources;
   private static final BooleanValue REQUIRE_WSR88D = BUILDER.pop()
      .push("etc")
      .comment("Whether radar blocks require a completed WSR-88D to be nearby for display")
      .define("requirewsr88d", false);
   public static boolean requireWSR88D;
   private static final BooleanValue LARGE_DATA_CONVERSION = BUILDER.comment(
         "Whether the server will perform data conversion for large data sets to new data systems"
      )
      .define("largedataconversion", true);
   public static boolean largeDataConversion;
   private static final BooleanValue CREATE_WINDMILL_SPEED = BUILDER.pop()
      .push("compat")
      .push("create")
      .comment("Whether windmill speed reacts to local windspeed (will cause visual jitter)")
      .define("createwindmillspeed", false);
   public static boolean createWindmillSpeed;
   private static final DoubleValue SABLE_WIND_MULT = BUILDER.pop()
      .push("sable")
      .comment("Wind multiplier for Sable physics")
      .defineInRange("sablewindmult", 1.0, 0.0, 10.0);
   public static double sableWindMult;
   private static final DoubleValue SABLE_INERTIA_MOD = BUILDER.comment("Wind Inertia multiplier for Sable physics")
      .defineInRange("sableinertiamod", 1.0, 0.0, 1.0);
   public static double sableInertiaMod;
   public static final ForgeConfigSpec SPEC = BUILDER.build();

   public ServerConfig() {
   }

   @SubscribeEvent
   private static void onLoad(ModConfigEvent event) {
      if (event.getConfig().getSpec() == SPEC && !(event instanceof Unloading)) {
         PMWeather.LOGGER.info("Loading Server PMWeather Configs");
         createWindmillSpeed = (Boolean)CREATE_WINDMILL_SPEED.get();
         sableWindMult = (Double)SABLE_WIND_MULT.get();
         sableInertiaMod = (Double)SABLE_INERTIA_MOD.get();
         requireWSR88D = (Boolean)REQUIRE_WSR88D.get();
         chanceInOneSquall = (Integer)CHANCE_IN_ONE_SQUALL.get();
         chanceInOneCyclone = (Integer)CHANCE_IN_ONE_CYCLONE.get();
         chanceInOneStage1 = (Integer)CHANCE_IN_ONE_STAGE_1.get();
         chanceInOneStage2 = (Integer)CHANCE_IN_ONE_STAGE_2.get();
         chanceInOneStage3 = (Integer)CHANCE_IN_ONE_STAGE_3.get();
         environmentSystem = (Boolean)ENVIRONMENT_SYSTEM.get();
         aimAtPlayer = (Boolean)AIM_AT_PLAYER.get();
         aimAtPlayerOffset = (Double)AIM_AT_PLAYER_OFFSET.get();
         spawnRange = (Integer)SPAWN_RANGE.get();
         stormSpawnChancePerMinute = (Double)STORM_SPAWN_CHANCE_PER_MINUTE.get();
         maxStorms = (Integer)MAX_STORMS.get();
         overcastPercent = (Double)OVERCAST_PERCENT.get();
         rainStrength = (Double)RAIN_STRENGTH.get();
         riskCurve = (Double)RISK_CURVE.get();
         squallStrengthMultiplier = (Double)SQUALL_STRENGTH_MULTIPLIER.get();
         maxTornadoWidth = (double)((Integer)MAX_TORNADO_WIDTH.get()).intValue();
         maxBlocksDamagedPerTick = (Integer)MAX_BLOCKS_DAMAGED_PER_TICK.get();
         damageEvery5thTick = (Boolean)DAMAGE_EVERY_5TH_TICK.get();
         doDebarking = (Boolean)DO_DEBARKING.get();
         doSeasons = (Boolean)DO_SEASONS.get();
         seasonalCrops = (Boolean)SEASONAL_CROPS.get();
         monthLength = (Integer)MONTH_LENGTH.get();
         backgroundWindMultiplier = (Double)BACKGROUND_WIND_MULTIPLIER.get();
         blockStrengths = new HashMap<>();

         for (String string : (List)BLOCK_STENGTHS.get()) {
            String[] args = string.split("=");
            if (args.length == 2) {
               ResourceLocation resourceLocation = new ResourceLocation(args[0]);
               if (BuiltInRegistries.BLOCK.containsKey(resourceLocation) && Util.isInteger(args[1])) {
                  Block block = (Block)BuiltInRegistries.BLOCK.get(resourceLocation);
                  int strength = Integer.parseInt(args[1]);
                  PMWeather.LOGGER.debug("Setup Block {} with strength {} mph", block, strength);
                  blockStrengths.put(block, (float)strength);
               } else {
                  PMWeather.LOGGER.warn("Invalid blockstrengths config: {}", string);
               }
            } else {
               PMWeather.LOGGER.warn("Invalid blockstrengths config: {}", string);
            }
         }

         blacklistedBlocks = new ArrayList<>();

         for (String stringx : (List)BLACKLISTED_BLOCKS.get()) {
            ResourceLocation resourceLocation = new ResourceLocation(stringx);
            if (BuiltInRegistries.BLOCK.containsKey(resourceLocation)) {
               Block block = (Block)BuiltInRegistries.BLOCK.get(resourceLocation);
               PMWeather.LOGGER.debug("Inserted Block {} -> blacklist", block);
               blacklistedBlocks.add(block);
            } else {
               PMWeather.LOGGER.warn("Invalid block within config blacklistedblocks: {}", stringx);
            }
         }

         incinerateBlocks = new ArrayList<>();

         for (String stringxx : (List)INCINERATE_BLOCKS.get()) {
            ResourceLocation resourceLocation = new ResourceLocation(stringxx);
            if (BuiltInRegistries.BLOCK.containsKey(resourceLocation)) {
               Block block = (Block)BuiltInRegistries.BLOCK.get(resourceLocation);
               PMWeather.LOGGER.debug("Inserted Block {} -> incinerateblocks", block);
               incinerateBlocks.add(block);
            } else {
               PMWeather.LOGGER.warn("Invalid block within config incinerateblocks: {}", stringxx);
            }
         }

         blacklistedBlockTags = new ArrayList<>();

         for (String stringxxx : (List)BLACKLISTED_BLOCKTAGS.get()) {
            ResourceLocation resourceLocation = new ResourceLocation(stringxxx);
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, resourceLocation);
            PMWeather.LOGGER.debug("Inserted BlockTag {} -> blacklist", tagKey);
            blacklistedBlockTags.add(tagKey);
         }

         playerPlacedWhitelist = new ArrayList<>();

         for (String stringxxx : (List)PLAYER_PLACED_WHITELIST.get()) {
            ResourceLocation resourceLocation = new ResourceLocation(stringxxx);
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, resourceLocation);
            PMWeather.LOGGER.debug("Inserted BlockTag {} -> player placed whitelist", tagKey);
            playerPlacedWhitelist.add(tagKey);
         }

         validDimensions = new ArrayList<>();

         for (String stringxxx : (List)VALID_DIMENSIONS.get()) {
            ResourceLocation resourceLocation = new ResourceLocation(stringxxx);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, resourceLocation);
            validDimensions.add(dimension);
         }

         stormSize = (Double)STORM_SIZE.get();
         layer0Height = (Double)LAYER_0_HEIGHT.get();
         layerCHeight = (Double)LAYER_C_HEIGHT.get();
         snowAccumulationHeight = (Integer)SNOW_ACCUMULATION_HEIGHT.get();
         doGrassGeneration = (Boolean)DOGRASSGENERATION.get();
         doTornadoes = (Boolean)DOTORNADOES.get();
         doCyclones = (Boolean)DOCYCLONES.get();
         doSqualls = (Boolean)DOSQUALLS.get();
         doDamage = (Boolean)DODAMAGE.get();
         useSeasonalPlants = (Boolean)USESEASONALPLANTS.get();
         doSeasonalLevelUpdater = (Boolean)DOSEASONALLEVELUPDATER.get();
         doRotting = (Boolean)DOROTTING.get();
         doWildfires = (Boolean)DOWILDFIRES.get();
         allowNonLightningFireSources = (Boolean)ALLOW_NON_LIGHTNING_FIRE_SOURCES.get();
         largeDataConversion = (Boolean)LARGE_DATA_CONVERSION.get();
         maxChunksLoadedPer5Ticks = (Integer)MAX_CHUNKS_LOADED_PER_5_TICKS.get();
         flyingBlockEntityChance = (Double)FLYING_BLOCK_ENTITY_CHANCE.get();
         doIcicles = (Boolean)DOICICLES.get();
      }
   }
}
