package dev.protomanly.pmweather.block.entity;

import dev.protomanly.pmweather.block.ModBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;

public class ModBlockEntities {
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "pmweather");
   public static final Supplier<BlockEntityType<AnemometerBlockEntity>> ANEMOMETER_BE = BLOCK_ENTITIES.register(
      "anemometer_be", () -> Builder.of(AnemometerBlockEntity::new, new Block[]{(Block)ModBlocks.ANEMOMETER.get()}).build(null)
   );
   public static final Supplier<BlockEntityType<RadarBlockEntity>> RADAR_BE = BLOCK_ENTITIES.register(
      "radar_be", () -> Builder.of(RadarBlockEntity::new, new Block[]{(Block)ModBlocks.RADAR.get()}).build(null)
   );
   public static final Supplier<BlockEntityType<TornadoSensorBlockEntity>> TORNADO_SENSOR_BE = BLOCK_ENTITIES.register(
      "tornado_sensor_be", () -> Builder.of(TornadoSensorBlockEntity::new, new Block[]{(Block)ModBlocks.TORNADO_SENSOR.get()}).build(null)
   );
   public static final Supplier<BlockEntityType<TornadoSirenBlockEntity>> TORNADO_SIREN_BE = BLOCK_ENTITIES.register(
      "tornado_siren_be", () -> Builder.of(TornadoSirenBlockEntity::new, new Block[]{(Block)ModBlocks.TORNADO_SIREN.get()}).build(null)
   );
   public static final Supplier<BlockEntityType<WeatherPlatformBlockEntity>> WEATHER_PLATFORM_BE = BLOCK_ENTITIES.register(
      "weather_platform_be", () -> Builder.of(WeatherPlatformBlockEntity::new, new Block[]{(Block)ModBlocks.WEATHER_PLATFORM.get()}).build(null)
   );
   public static final Supplier<BlockEntityType<SoundingViewerBlockEntity>> SOUNDING_VIEWER_BE = BLOCK_ENTITIES.register(
      "sounding_viewer_be", () -> Builder.of(SoundingViewerBlockEntity::new, new Block[]{(Block)ModBlocks.SOUNDING_VIEWER.get()}).build(null)
   );

   public ModBlockEntities() {
   }
}
