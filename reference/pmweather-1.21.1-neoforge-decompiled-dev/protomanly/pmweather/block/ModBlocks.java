package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.item.HintBlockItem;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.sound.ModSounds;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public class ModBlocks {
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("pmweather");
   public static final DeferredBlock<IcicleBlock> ICICLE = registerBlock(
      "icicle",
      () -> new IcicleBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE).randomTicks().sound(SoundType.GLASS).strength(0.25F)
         )
   );
   public static final DeferredBlock<Block> PLANTS = registerBlock(
      "plants", () -> new SeasonalPlantBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SHORT_GRASS).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> PLANTS_1 = registerBlock(
      "plants1", () -> new SeasonalPlantBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SHORT_GRASS).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> PLANTS_2 = registerBlock(
      "plants2", () -> new SeasonalPlantBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SHORT_GRASS).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> PLANTS_3 = registerBlock(
      "plants3", () -> new SeasonalPlantBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SHORT_GRASS).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> PLANTS_4 = registerBlock(
      "plants4", () -> new SeasonalPlantBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SHORT_GRASS).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> ANEMOMETER = registerBlock(
      "anemometer",
      () -> new AnemometerBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.25F).noOcclusion().sound(SoundType.METAL))
   );
   public static final DeferredBlock<Block> RADAR = registerBlock(
      "radar", () -> new RadarBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL))
   );
   public static final DeferredBlock<Block> TORNADO_SENSOR = registerBlock(
      "tornado_sensor",
      () -> new TornadoSensorBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL))
   );
   public static final DeferredBlock<Block> TORNADO_SIREN = registerBlock(
      "tornado_siren",
      () -> new TornadoSirenBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL))
   );
   public static final DeferredBlock<Block> WEATHER_STATION = registerBlock(
      "metar", () -> new WeatherStationBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL))
   );
   public static final DeferredBlock<Block> METAR = registerBlock(
      "linked_metar",
      () -> new MetarBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL).noOcclusion()),
      Component.translatable("tooltip.pmweather.linked_metar")
   );
   public static final DeferredBlock<Block> RADOME = registerBlock(
      "radome", () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_WOOL).strength(0.5F).sound(SoundType.STONE))
   );
   public static final DeferredBlock<Block> SCOURED_GRASS = registerBlock(
      "scoured_grass", () -> new ScouredGrassBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK).randomTicks())
   );
   public static final DeferredBlock<Block> MEDIUM_SCOURING = registerBlock(
      "medium_scouring", () -> new MediumScourBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).randomTicks())
   );
   public static final DeferredBlock<Block> HEAVY_SCOURING = registerBlock(
      "heavy_scouring", () -> new HeavyScourBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).randomTicks())
   );
   public static final DeferredBlock<Block> WEATHER_PLATFORM = registerBlock(
      "balloon_platform",
      () -> new WeatherPlatformBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL).noOcclusion()
         ),
      Component.translatable("tooltip.pmweather.balloon_platform")
   );
   public static final DeferredBlock<Block> SOUNDING_VIEWER = registerBlock(
      "sounding_viewer",
      () -> new SoundingViewerBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL).noOcclusion()
         ),
      Component.translatable("tooltip.pmweather.sounding_viewer")
   );
   public static final DeferredBlock<Block> ICE_LAYER = registerBlock(
      "ice_layer",
      () -> new SnowLayerBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SNOW).sound(SoundType.GLASS).strength(0.15F).noOcclusion())
   );
   public static final DeferredBlock<Block> SLEET_LAYER = registerBlock(
      "sleet_layer", () -> new SnowLayerBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SNOW).sound(ModSounds.SLEET_BLOCK).noOcclusion())
   );
   public static final DeferredBlock<Block> REINFORCED_GLASS = registerBlock(
      "reinforced_glass",
      () -> new TransparentBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).strength(3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> REINFORCED_GLASS_PANE = registerBlock(
      "reinforced_glass_pane",
      () -> new IronBarsBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).strength(1.5F).sound(SoundType.STONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> STRIPPED_ROTTED_LOG = registerBlock(
      "stripped_rotted_log", () -> new RotatedPillarBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG).strength(0.3F))
   );
   public static final DeferredBlock<Block> ROTTED_LOG = registerBlock(
      "rotted_log",
      () -> new RottedLogBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LOG).strength(0.3F).randomTicks(), (RotatedPillarBlock)STRIPPED_ROTTED_LOG.get()
         )
   );
   public static final DeferredBlock<Block> STRIPPED_ROTTED_WOOD = registerBlock(
      "stripped_rotted_wood", () -> new RotatedPillarBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG).strength(0.3F))
   );
   public static final DeferredBlock<Block> ROTTED_WOOD = registerBlock(
      "rotted_wood",
      () -> new ModLogBlock(
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG).strength(0.3F), (RotatedPillarBlock)STRIPPED_ROTTED_WOOD.get()
         )
   );
   public static final DeferredBlock<Block> ROTTED_PLANKS = registerBlock(
      "rotted_planks", () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(0.15F))
   );
   public static final DeferredBlock<Block> ROTTED_STAIRS = registerBlock(
      "rotted_stairs",
      () -> new StairBlock(
            ((Block)ROTTED_PLANKS.get()).defaultBlockState(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(0.15F)
         )
   );
   public static final DeferredBlock<Block> ROTTED_SLAB = registerBlock(
      "rotted_slab", () -> new SlabBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(0.15F))
   );
   public static final DeferredBlock<Block> RANGE_UPGRADE_MODULE = registerBlock(
      "range_upgrade_module",
      () -> new DirectionalBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).strength(1.0F).sound(SoundType.METAL).noOcclusion()),
      Component.translatable("tooltip.pmweather.range_upgrade_module")
   );
   public static final DeferredBlock<Block> FIRE = registerBlock(
      "fire", () -> new PMWFireBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.FIRE).randomTicks().replaceable())
   );
   public static final DeferredBlock<Block> CHARRED_DIRT = registerBlock(
      "charred_dirt",
      () -> new TransformingBlock(
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
            175,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK).sound(SoundType.GRAVEL).randomTicks()
         )
   );
   public static final DeferredBlock<Block> SMOLDERING_DIRT = registerBlock(
      "smoldering_dirt",
      () -> new BurningBlockNoSpread(
            (Block)CHARRED_DIRT.get(),
            1,
            1000,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK).sound(SoundType.GRAVEL).lightLevel(state -> 8).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_DIRT_SLIGHT = registerBlock(
      "charred_dirt_slight",
      () -> new TransformingBlock(
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
            75,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK).sound(SoundType.GRAVEL).randomTicks()
         )
   );
   public static final DeferredBlock<Block> SMOLDERING_DIRT_SLIGHT = registerBlock(
      "smoldering_dirt_slight",
      () -> new BurningBlockNoSpread(
            (Block)CHARRED_DIRT_SLIGHT.get(),
            1,
            1000,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK).sound(SoundType.GRAVEL).lightLevel(state -> 5).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_LOG = registerBlock(
      "charred_log", () -> new CharredLogBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LOG).sound(SoundType.BASALT).randomTicks())
   );
   public static final DeferredBlock<Block> SMOLDERING_LOG = registerBlock(
      "smoldering_log",
      () -> new BurningLogBlock(
            (Block)CHARRED_LOG.get(),
            1,
            1,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_PLANKS = registerBlock(
      "charred_planks",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(2368548), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT)
         )
   );
   public static final DeferredBlock<Block> SMOLDERING_PLANKS = registerBlock(
      "smoldering_planks",
      () -> new BurningBlock(
            (Block)CHARRED_PLANKS.get(),
            2,
            1,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_STAIRS = registerBlock(
      "charred_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(2368548),
            ((Block)CHARRED_PLANKS.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT)
         )
   );
   public static final DeferredBlock<Block> SMOLDERING_STAIRS = registerBlock(
      "smoldering_stairs",
      () -> new BurningStairBlock(
            (Block)CHARRED_STAIRS.get(),
            2,
            1,
            ((Block)SMOLDERING_PLANKS.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_SLAB = registerBlock(
      "charred_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(2368548), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT)
         )
   );
   public static final DeferredBlock<Block> SMOLDERING_SLAB = registerBlock(
      "smoldering_slab",
      () -> new BurningSlabBlock(
            (Block)CHARRED_SLAB.get(),
            2,
            1,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> CHARRED_DOOR = registerBlock(
      "charred_door",
      () -> new CharredDoorBlock(ModBlockSetType.CHARRED, Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR).sound(SoundType.BASALT))
   );
   public static final DeferredBlock<Block> SMOLDERING_DOOR = registerBlock(
      "smoldering_door",
      () -> new BurningDoorBlock(
            (Block)CHARRED_DOOR.get(),
            2,
            1,
            ModBlockSetType.CHARRED,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> ASH_BLOCK = registerBlock(
      "ash_block", () -> new ColoredFallingCharredBlock(new ColorRGBA(2368548), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRAVEL))
   );
   public static final DeferredBlock<Block> SMOLDERING_BLOCK = registerBlock(
      "smoldering_block",
      () -> new BurningBlock(
            (Block)ASH_BLOCK.get(),
            2,
            1,
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).sound(SoundType.BASALT).lightLevel(state -> 10).randomTicks()
         )
   );
   public static final DeferredBlock<Block> IRON_REINFORCEMENT_CORE = registerBlock(
      "iron_reinforcement_core",
      () -> new ReinforcedCoreBlock(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BARS).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> SCALDED_CONCRETE = registerBlock(
      "scalded_concrete",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_CONCRETE_STAIRS = registerBlock(
      "scalded_concrete_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_CONCRETE.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_CONCRETE_SLAB = registerBlock(
      "scalded_concrete_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE_BRICKS = registerBlock(
      "scalded_stone_bricks",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE_BRICK_STAIRS = registerBlock(
      "scalded_stone_brick_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_STONE_BRICKS.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE_BRICK_SLAB = registerBlock(
      "scalded_stone_brick_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_BRICKS = registerBlock(
      "scalded_bricks",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_BRICK_STAIRS = registerBlock(
      "scalded_brick_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_BRICKS.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_BRICK_SLAB = registerBlock(
      "scalded_brick_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE = registerBlock(
      "scalded_stone",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE_STAIRS = registerBlock(
      "scalded_stone_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_STONE.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_STONE_SLAB = registerBlock(
      "scalded_stone_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_COBBLESTONE = registerBlock(
      "scalded_cobblestone",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COBBLESTONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_COBBLESTONE_STAIRS = registerBlock(
      "scalded_cobblestone_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_COBBLESTONE.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COBBLESTONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_COBBLESTONE_SLAB = registerBlock(
      "scalded_cobblestone_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COBBLESTONE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_DEEPSLATE = registerBlock(
      "scalded_deepslate",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_DEEPSLATE_STAIRS = registerBlock(
      "scalded_deepslate_stairs",
      () -> new ColoredFallingStairBlock(
            new ColorRGBA(6381921),
            ((Block)SCALDED_DEEPSLATE.get()).defaultBlockState(),
            Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_DEEPSLATE_SLAB = registerBlock(
      "scalded_deepslate_slab",
      () -> new ColoredFallingSlabBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> SCALDED_METAL = registerBlock(
      "scalded_metal",
      () -> new ColoredFallingCharredBlock(
            new ColorRGBA(6381921), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).requiresCorrectToolForDrops()
         )
   );
   public static final DeferredBlock<Block> REINFORCED_WHITE_CONCRETE = registerBlock(
      "reinforced_white_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_WHITE_CONCRETE_POWDER = registerBlock(
      "reinforced_white_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_WHITE_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_ORANGE_CONCRETE = registerBlock(
      "reinforced_orange_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_ORANGE_CONCRETE_POWDER = registerBlock(
      "reinforced_orange_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_ORANGE_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_MAGENTA_CONCRETE = registerBlock(
      "reinforced_magenta_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_MAGENTA_CONCRETE_POWDER = registerBlock(
      "reinforced_magenta_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_MAGENTA_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_LIGHT_BLUE_CONCRETE = registerBlock(
      "reinforced_light_blue_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_LIGHT_BLUE_CONCRETE_POWDER = registerBlock(
      "reinforced_light_blue_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_LIGHT_BLUE_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_YELLOW_CONCRETE = registerBlock(
      "reinforced_yellow_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.YELLOW_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_YELLOW_CONCRETE_POWDER = registerBlock(
      "reinforced_yellow_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_YELLOW_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.YELLOW_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_LIME_CONCRETE = registerBlock(
      "reinforced_lime_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIME_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_LIME_CONCRETE_POWDER = registerBlock(
      "reinforced_lime_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_LIME_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIME_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_PINK_CONCRETE = registerBlock(
      "reinforced_pink_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.PINK_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_PINK_CONCRETE_POWDER = registerBlock(
      "reinforced_pink_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_PINK_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.PINK_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_GRAY_CONCRETE = registerBlock(
      "reinforced_gray_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRAY_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_GRAY_CONCRETE_POWDER = registerBlock(
      "reinforced_gray_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_GRAY_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRAY_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_LIGHT_GRAY_CONCRETE = registerBlock(
      "reinforced_light_gray_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_LIGHT_GRAY_CONCRETE_POWDER = registerBlock(
      "reinforced_light_gray_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_LIGHT_GRAY_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_CYAN_CONCRETE = registerBlock(
      "reinforced_cyan_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.CYAN_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_CYAN_CONCRETE_POWDER = registerBlock(
      "reinforced_cyan_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_CYAN_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.CYAN_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_PURPLE_CONCRETE = registerBlock(
      "reinforced_purple_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.PURPLE_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_PURPLE_CONCRETE_POWDER = registerBlock(
      "reinforced_purple_concrete_powder",
      () -> new ConcretePowderBlock(
            (Block)REINFORCED_PURPLE_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.PURPLE_CONCRETE_POWDER)
         )
   );
   public static final DeferredBlock<Block> REINFORCED_BLUE_CONCRETE = registerBlock(
      "reinforced_blue_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BLUE_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_BLUE_CONCRETE_POWDER = registerBlock(
      "reinforced_blue_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_BLUE_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BLUE_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_BROWN_CONCRETE = registerBlock(
      "reinforced_brown_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BROWN_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_BROWN_CONCRETE_POWDER = registerBlock(
      "reinforced_brown_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_BROWN_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BROWN_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_GREEN_CONCRETE = registerBlock(
      "reinforced_green_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GREEN_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_GREEN_CONCRETE_POWDER = registerBlock(
      "reinforced_green_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_GREEN_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GREEN_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_RED_CONCRETE = registerBlock(
      "reinforced_red_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.RED_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_RED_CONCRETE_POWDER = registerBlock(
      "reinforced_red_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_RED_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.RED_CONCRETE_POWDER))
   );
   public static final DeferredBlock<Block> REINFORCED_BLACK_CONCRETE = registerBlock(
      "reinforced_black_concrete",
      () -> new Block(Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BLACK_CONCRETE).strength(3.5F).requiresCorrectToolForDrops())
   );
   public static final DeferredBlock<Block> REINFORCED_BLACK_CONCRETE_POWDER = registerBlock(
      "reinforced_black_concrete_powder",
      () -> new ConcretePowderBlock((Block)REINFORCED_BLACK_CONCRETE.get(), Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BLACK_CONCRETE_POWDER))
   );

   public ModBlocks() {
   }

   private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
      DeferredBlock<T> returnBlock = BLOCKS.register(name, block);
      registerBlockItem(name, returnBlock);
      return returnBlock;
   }

   private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block, Component hint) {
      DeferredBlock<T> returnBlock = BLOCKS.register(name, block);
      registerBlockItem(name, returnBlock, hint);
      return returnBlock;
   }

   private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
      ModItems.ITEMS.register(name, () -> new BlockItem((Block)block.get(), new net.minecraft.world.item.Item.Properties()));
   }

   private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block, Component hint) {
      ModItems.ITEMS.register(name, () -> new HintBlockItem(hint, (Block)block.get(), new net.minecraft.world.item.Item.Properties()));
   }
}
