package dev.protomanly.pmweather.particle;

import dev.protomanly.pmweather.interfaces.ParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ParticleHail extends ParticleCube implements ParticleData {
   public ParticleHail(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, BlockState state) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed, state);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.onGround || this.collidingDownwards) {
         this.xd *= 0.95;
         this.zd *= 0.95;
      }
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
      if (!this.onGround && !this.collidingDownwards) {
         this.xd = this.xd + vec3.x;
         this.yd = this.yd + vec3.y;
         this.zd = this.zd + vec3.z;
      }
   }

   @Override
   public void setVelocity(Vec3 vec3) {
      this.xd = vec3.x;
      this.yd = vec3.y;
      this.zd = vec3.z;
   }
}
