package dev.protomanly.pmweather.interfaces;

import net.minecraft.world.phys.Vec3;

public interface ParticleData {
   Vec3 getVelocity();

   float getFriction();

   void setFriction(float var1);

   void addVelocity(Vec3 var1);

   void setVelocity(Vec3 var1);
}
