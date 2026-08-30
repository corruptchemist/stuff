package dev.protomanly.pmweather.creative;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.multiblock.MultiBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.DeferredRegister;

public class ModCreativeTabs {
   public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "pmweather");
   public static final Supplier<CreativeModeTab> PMWEATHER_TAB = CREATIVE_MODE_TABS.register(
      "pmweather_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)ModBlocks.RADAR.get()))
            .title(Component.translatable("creativetab.pmweather.main"))
            .displayItems((itemDisplayParameters, output) -> {
               output.accept(ModBlocks.RADAR);
               output.accept(ModBlocks.RANGE_UPGRADE_MODULE);
               output.accept(ModBlocks.METAR);
               output.accept(MultiBlocks.WSR88D_CORE);
               output.accept(ModBlocks.TORNADO_SENSOR);
               output.accept(ModBlocks.TORNADO_SIREN);
               output.accept(ModBlocks.ANEMOMETER);
               output.accept(ModBlocks.WEATHER_STATION);
               output.accept(ModBlocks.SOUNDING_VIEWER);
               output.accept(ModBlocks.WEATHER_PLATFORM);
               output.accept(ModItems.CONNECTOR);
               output.accept(ModItems.WEATHER_BALLOON);
               output.accept(ModBlocks.ICE_LAYER);
               output.accept(ModBlocks.SLEET_LAYER);
               output.accept(ModBlocks.ICICLE);
               output.accept(ModItems.ASH);
               output.accept(ModItems.FIRE_EXTINGUISHER);
               output.accept(ModItems.CALENDAR);
            })
            .build()
   );
   public static final Supplier<CreativeModeTab> PMWEATHER_BUILDING_TAB = CREATIVE_MODE_TABS.register(
      "pmweather_building_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)ModBlocks.REINFORCED_GLASS.get()))
            .title(Component.translatable("creativetab.pmweather.building"))
            .withTabsBefore(new ResourceLocation[]{new ResourceLocation("pmweather", "pmweather_tab")})
            .displayItems((itemDisplayParameters, output) -> {
               output.accept(ModBlocks.RADOME);
               output.accept(ModBlocks.REINFORCED_GLASS);
               output.accept(ModBlocks.REINFORCED_GLASS_PANE);
               output.accept(ModBlocks.IRON_REINFORCEMENT_CORE);
               output.accept(ModBlocks.ROTTED_LOG);
               output.accept(ModBlocks.STRIPPED_ROTTED_LOG);
               output.accept(ModBlocks.ROTTED_WOOD);
               output.accept(ModBlocks.STRIPPED_ROTTED_WOOD);
               output.accept(ModBlocks.ROTTED_PLANKS);
               output.accept(ModBlocks.ROTTED_STAIRS);
               output.accept(ModBlocks.ROTTED_SLAB);
               output.accept(ModBlocks.MEDIUM_SCOURING);
               output.accept(ModBlocks.HEAVY_SCOURING);
               output.accept(ModBlocks.SCOURED_GRASS);
               output.accept(ModBlocks.SMOLDERING_DIRT_SLIGHT);
               output.accept(ModBlocks.SMOLDERING_DIRT);
               output.accept(ModBlocks.SMOLDERING_LOG);
               output.accept(ModBlocks.SMOLDERING_PLANKS);
               output.accept(ModBlocks.SMOLDERING_STAIRS);
               output.accept(ModBlocks.SMOLDERING_SLAB);
               output.accept(ModBlocks.SMOLDERING_DOOR);
               output.accept(ModBlocks.CHARRED_DIRT_SLIGHT);
               output.accept(ModBlocks.CHARRED_DIRT);
               output.accept(ModBlocks.CHARRED_LOG);
               output.accept(ModBlocks.CHARRED_PLANKS);
               output.accept(ModBlocks.CHARRED_STAIRS);
               output.accept(ModBlocks.CHARRED_SLAB);
               output.accept(ModBlocks.CHARRED_DOOR);
               output.accept(ModBlocks.ASH_BLOCK);
               output.accept(ModBlocks.SCALDED_STONE);
               output.accept(ModBlocks.SCALDED_STONE_STAIRS);
               output.accept(ModBlocks.SCALDED_STONE_SLAB);
               output.accept(ModBlocks.SCALDED_COBBLESTONE);
               output.accept(ModBlocks.SCALDED_COBBLESTONE_STAIRS);
               output.accept(ModBlocks.SCALDED_COBBLESTONE_SLAB);
               output.accept(ModBlocks.SCALDED_DEEPSLATE);
               output.accept(ModBlocks.SCALDED_DEEPSLATE_STAIRS);
               output.accept(ModBlocks.SCALDED_DEEPSLATE_SLAB);
               output.accept(ModBlocks.SCALDED_METAL);
               output.accept(ModBlocks.SCALDED_BRICKS);
               output.accept(ModBlocks.SCALDED_BRICK_STAIRS);
               output.accept(ModBlocks.SCALDED_BRICK_SLAB);
               output.accept(ModBlocks.SCALDED_STONE_BRICKS);
               output.accept(ModBlocks.SCALDED_STONE_BRICK_STAIRS);
               output.accept(ModBlocks.SCALDED_STONE_BRICK_SLAB);
               output.accept(ModBlocks.SCALDED_CONCRETE);
               output.accept(ModBlocks.SCALDED_CONCRETE_STAIRS);
               output.accept(ModBlocks.SCALDED_CONCRETE_SLAB);
               output.accept(ModBlocks.REINFORCED_WHITE_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_WHITE_CONCRETE);
               output.accept(ModBlocks.REINFORCED_ORANGE_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_ORANGE_CONCRETE);
               output.accept(ModBlocks.REINFORCED_MAGENTA_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_MAGENTA_CONCRETE);
               output.accept(ModBlocks.REINFORCED_LIGHT_BLUE_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_LIGHT_BLUE_CONCRETE);
               output.accept(ModBlocks.REINFORCED_YELLOW_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_YELLOW_CONCRETE);
               output.accept(ModBlocks.REINFORCED_LIME_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_LIME_CONCRETE);
               output.accept(ModBlocks.REINFORCED_PINK_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_PINK_CONCRETE);
               output.accept(ModBlocks.REINFORCED_GRAY_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_GRAY_CONCRETE);
               output.accept(ModBlocks.REINFORCED_LIGHT_GRAY_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_LIGHT_GRAY_CONCRETE);
               output.accept(ModBlocks.REINFORCED_CYAN_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_CYAN_CONCRETE);
               output.accept(ModBlocks.REINFORCED_PURPLE_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_PURPLE_CONCRETE);
               output.accept(ModBlocks.REINFORCED_BLUE_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_BLUE_CONCRETE);
               output.accept(ModBlocks.REINFORCED_BROWN_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_BROWN_CONCRETE);
               output.accept(ModBlocks.REINFORCED_GREEN_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_GREEN_CONCRETE);
               output.accept(ModBlocks.REINFORCED_RED_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_RED_CONCRETE);
               output.accept(ModBlocks.REINFORCED_BLACK_CONCRETE_POWDER);
               output.accept(ModBlocks.REINFORCED_BLACK_CONCRETE);
            })
            .build()
   );

   public ModCreativeTabs() {
   }
}
