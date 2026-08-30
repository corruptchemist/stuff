package dev.protomanly.pmweather.mixin;

import dev.protomanly.pmweather.interfaces.ParticleData;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({Particle.class})
public class ParticleMixin implements ParticleData {
   @Shadow
   protected double xd;
   @Shadow
   protected double yd;
   @Shadow
   protected double zd;
   @Shadow
   protected float friction;

   public ParticleMixin() {
   }

   @Override
   public Vec3 getVelocity() {
      return new Vec3(this.xd, this.yd, this.zd);
   }

   @Override
   public float getFriction() {
      return this.friction;
   }

   @Override
   public void setFriction(float f) {
      this.friction = f;
   }

   @Override
   public void addVelocity(Vec3 vec3) {
      this.xd = this.xd + vec3.x;
      this.yd = this.yd + vec3.y;
      this.zd = this.zd + vec3.z;
   }

   @Override
   public void setVelocity(Vec3 vec3) {
      this.xd = vec3.x;
      this.yd = vec3.y;
      this.zd = vec3.z;
   }
}
