package dev.protomanly.pmweather.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraftforge.registries.DeferredRegister;

public class ModParticleTypes {
   public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, "pmweather");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SMOKE = PARTICLE_TYPES.register(
      "fire_smoke", () -> new SimpleParticleType(false)
   );
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLE_TYPES.register("ember", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME = PARTICLE_TYPES.register("flame", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOAM = PARTICLE_TYPES.register("foam", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = PARTICLE_TYPES.register("spark", () -> new SimpleParticleType(false));

   public ModParticleTypes() {
   }
}
