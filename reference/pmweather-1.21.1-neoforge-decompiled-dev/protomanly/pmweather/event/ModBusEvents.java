package dev.protomanly.pmweather.event;

import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.particle.ParticleRegistry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
   modid = "pmweather"
)
public class ModBusEvents {
   public ModBusEvents() {
   }

   @SubscribeEvent
   public static void registerPayload(RegisterPayloadHandlersEvent event) {
      ModNetworking.register(event.registrar("1"));
   }

   @SubscribeEvent
   public static void gatherData(GatherDataEvent event) {
      if (event.includeClient()) {
         DataGenerator gen = event.getGenerator();
         PackOutput packOutput = gen.getPackOutput();
         ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
         gen.addProvider(event.includeClient(), new ParticleRegistry(packOutput, event.getLookupProvider(), "pmweather", existingFileHelper));
      }
   }
}
