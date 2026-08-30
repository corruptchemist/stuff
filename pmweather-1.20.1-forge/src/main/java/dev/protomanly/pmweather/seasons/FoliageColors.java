package dev.protomanly.pmweather.seasons;

import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.util.ColorTables;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class FoliageColors {
   private static ColorResolver originalGrassResolver;
   private static ColorResolver originalFoliageResolver;
   private static Color dryGrassColor = new Color(11901019);
   private static Color dryFoliageColor = new Color(7362599);
   private static Color springGrassColor = new Color(2125328);
   private static Color springFoliageColor = new Color(2125328);
   private static Color summerGrassColor = new Color(9088532);
   private static Color summerFoliageColor = new Color(9088532);
   private static Color fallGrassColor = new Color(10129430);
   private static Color fallFoliageColor = new Color(10372882);
   private static Color winterGrassColor = new Color(8416065);
   private static Color winterFoliageColor = new Color(5718324);
   private static Color frostColor = new Color(16777215);

   public FoliageColors() {
   }

   public static void register() {
      originalGrassResolver = BiomeColors.GRASS_COLOR_RESOLVER;
      originalFoliageResolver = BiomeColors.FOLIAGE_COLOR_RESOLVER;
      BiomeColors.GRASS_COLOR_RESOLVER = (biome, x, z) -> resolveColors("grass", biome, x, z);
      BiomeColors.FOLIAGE_COLOR_RESOLVER = (biome, x, z) -> resolveColors("foliage", biome, x, z);
   }

   public static int getColor(String type, Biome biome, double x, double z, float moisture) {
      byte level = -1;
      switch (type.hashCode()) {
         case 98615734:
            if (type.equals("grass")) {
               level = 0;
            }
         default:
            int originalColor = switch (level) {
               case 0 -> originalGrassResolver.getColor(biome, x, z);
               default -> originalFoliageResolver.getColor(biome, x, z);
            };
            Minecraft minecraft = Minecraft.getInstance();
            Level levelx = minecraft.level;
            if (levelx == null) {
               return originalColor;
            } else {
               Registry<Biome> biomeRegistry = levelx.registryAccess().registryOrThrow(Registries.BIOME);
               Holder<Biome> biomeHolder = biomeRegistry.getResourceKey(biome).<Holder<Biome>>flatMap(biomeRegistry::getHolder).orElse(null);
               if (biomeHolder != null && ServerConfig.doSeasons) {
                  Color color = new Color(originalColor);
                  float springAmount = (float)Math.pow((double)((SeasonHandler.getSeasonEffectSine(levelx, -3.5F) + 1.0F) / 2.0F), 4.0);
                  float summerAmount = (float)Math.pow((double)((SeasonHandler.getSeasonEffectSine(levelx, 0.0F) + 1.0F) / 2.0F), 4.0);
                  float fallAmount = (float)Math.pow((double)((SeasonHandler.getSeasonEffectSine(levelx, 3.5F) + 1.0F) / 2.0F), 4.0);
                  float winterAmount = (float)Math.pow((double)((SeasonHandler.getSeasonEffectSine(levelx, 6.0F) + 1.0F) / 2.0F), 4.0);
                  if (type.equals("grass")) {
                     color = ColorTables.lerp(springAmount, color, springGrassColor);
                     color = ColorTables.lerp(summerAmount, color, summerGrassColor);
                     color = ColorTables.lerp(fallAmount, color, fallGrassColor);
                     color = ColorTables.lerp(winterAmount, color, winterGrassColor);
                  } else {
                     color = ColorTables.lerp(springAmount, color, springFoliageColor);
                     color = ColorTables.lerp(summerAmount, color, summerFoliageColor);
                     color = ColorTables.lerp(fallAmount, color, fallFoliageColor);
                     color = ColorTables.lerp(winterAmount, color, winterFoliageColor);
                  }

                  int red = color.getRed();
                  int green = color.getGreen();
                  int blue = color.getBlue();
                  if (type.equals("foliage")) {
                     moisture = (float)Math.pow((double)(moisture / 100.0F), 1.3) * 100.0F;
                  }

                  Color drynessColor = new Color(red, green, blue);
                  if (type.equals("grass")) {
                     drynessColor = ColorTables.lerp(moisture / 100.0F, dryGrassColor, drynessColor);
                  } else {
                     drynessColor = ColorTables.lerp(moisture / 100.0F, dryFoliageColor, drynessColor);
                  }

                  WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
                  if (weatherHandler != null) {
                     int y = levelx.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)x, (int)z);
                     ThermodynamicEngine.AtmosphericDataPoint sample = ThermodynamicEngine.samplePoint(
                        weatherHandler, new Vec3(x, (double)y, z), levelx, null, 0, y, false, false
                     );
                     float freezePerc = Mth.clamp(sample.temperature() / -1.0F, 0.0F, 1.0F);
                     freezePerc *= Math.max(
                        (float)ThermodynamicEngine.getHumidity(sample.temperature(), sample.dewpoint()), Mth.clamp(moisture / 75.0F, 0.0F, 1.0F)
                     );
                     drynessColor = ColorTables.lerp(freezePerc, drynessColor, frostColor);
                  }

                  red = drynessColor.getRed();
                  green = drynessColor.getGreen();
                  blue = drynessColor.getBlue();
                  float rf = (float)red / 255.0F;
                  float gf = (float)green / 255.0F;
                  float bf = (float)blue / 255.0F;
                  red = Math.clamp((long)((int)(rf * 255.0F)), 0, 255);
                  green = Math.clamp((long)((int)(gf * 255.0F)), 0, 255);
                  blue = Math.clamp((long)((int)(bf * 255.0F)), 0, 255);
                  return new Color(red, green, blue).getRGB();
               } else {
                  return originalColor;
               }
            }
      }
   }

   private static int resolveColors(String type, Biome biome, double x, double z) {
      byte level = -1;
      switch (type.hashCode()) {
         case 98615734:
            if (type.equals("grass")) {
               level = 0;
            }
         default:
            int originalColor = switch (level) {
               case 0 -> originalGrassResolver.getColor(biome, x, z);
               default -> originalFoliageResolver.getColor(biome, x, z);
            };
            Minecraft minecraft = Minecraft.getInstance();
            Level levelx = minecraft.level;
            if (levelx == null) {
               return originalColor;
            } else {
               long chunkLong = ChunkPos.asLong(new BlockPos((int)x, 75, (int)z));
               LevelChunk chunk = levelx.getChunk(ChunkPos.getX(chunkLong), ChunkPos.getZ(chunkLong));
               float moisture;
               if (chunk.hasData(DataAttachments.MOISTURE)) {
                  moisture = (Float)chunk.getData(DataAttachments.MOISTURE);
               } else {
                  moisture = 75.0F;
               }

               return getColor(type, biome, x, z, moisture);
            }
      }
   }
}
