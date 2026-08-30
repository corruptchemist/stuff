package dev.protomanly.pmweather.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

public class SparkParticleProvider implements ParticleProvider<SimpleParticleType> {
   private final SpriteSet spriteSet;

   public SparkParticleProvider(SpriteSet spriteSet) {
      this.spriteSet = spriteSet;
   }

   @Nullable
   public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
      return new SparkParticle(level, x, y, z, ySpeed, this.spriteSet);
   }
}
