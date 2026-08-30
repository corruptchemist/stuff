package dev.protomanly.pmweather.item.component;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.DeferredRegister.DataComponents;

public class ModComponents {
   public static final DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "pmweather");
   public static final Supplier<DataComponentType<BlockPos>> WEATHER_BALLOON_PLATFORM = COMPONENTS.registerComponentType(
      "weather_balloon_platform", builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC)
   );

   public ModComponents() {
   }
}
