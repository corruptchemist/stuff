package dev.protomanly.pmweather.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {
   public ModTags() {
   }

   public static class Blocks {
      public static final TagKey<Block> INCINERATES = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "incinerates"));
      public static final TagKey<Block> THERMAL_SHOCKS = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "thermal_shocks"));
      public static final TagKey<Block> FLASHES = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "flashes"));
      public static final TagKey<Block> BURNS_GENERICALLY = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "burns_generically")
      );
      public static final TagKey<Block> REINFORCEABLE = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "reinforceable"));
      public static final TagKey<Block> POWERFLASHES = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "powerflashes"));
      public static final TagKey<Block> KEEPS_TREES_ALIVE = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "keeps_trees_alive")
      );
      public static final TagKey<Block> SCORCHABLE_STONE = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/stone")
      );
      public static final TagKey<Block> SCORCHABLE_COBBLESTONE = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/cobblestone")
      );
      public static final TagKey<Block> SCORCHABLE_DEEPSLATE = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/deepslate")
      );
      public static final TagKey<Block> SCORCHABLE_METAL = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/metal")
      );
      public static final TagKey<Block> SCORCHABLE_BRICKS = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/bricks")
      );
      public static final TagKey<Block> SCORCHABLE_STONE_BRICKS = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/stone_bricks")
      );
      public static final TagKey<Block> SCORCHABLE_CONCRETE = TagKey.create(
         Registries.BLOCK, new ResourceLocation("pmweather", "scorchables/concrete")
      );
      public static final TagKey<Block> CONDUCTIVE = TagKey.create(Registries.BLOCK, new ResourceLocation("pmweather", "conductive"));

      public Blocks() {
      }
   }
}
