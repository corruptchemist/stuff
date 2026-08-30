package dev.protomanly.pmweather.particle;

import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class SparkParticle extends TextureSheetParticle {
   private final SpriteSet spriteSet;
   private float rolld = 0.0F;

   protected SparkParticle(ClientLevel level, double x, double y, double z, double yd, SpriteSet spriteSet) {
      super(level, x, y, z);
      this.yd = yd;
      this.spriteSet = spriteSet;
      this.scale(0.5F);
      this.scale(Math.max((float)yd * 6.0F, 0.25F));
      this.gravity = 0.7F;
      this.friction = 0.999F;
      this.lifetime = level.random.nextInt(20, 60);
      float rRoll = level.random.nextFloat() * (float) Math.PI * 2.0F;
      this.oRoll = rRoll;
      this.roll = rRoll;
      this.setColor(3.0F, 3.0F, 3.0F);
      this.rolld = (level.random.nextFloat() - 0.5F) * 0.02F;
      this.setSpriteFromAge(spriteSet);
   }

   public int getLightColor(float partialTick) {
      int i = super.getLightColor(partialTick);
      int k = i >> 16 & 0xFF;
      return 240 | k << 16;
   }

   public float getQuadSize(float scaleFactor) {
      float f = ((float)this.age + scaleFactor) / (float)this.lifetime;
      return this.quadSize * (1.0F - f * f);
   }

   public void tick() {
      float f = (float)this.age / (float)this.lifetime;
      f = 1.0F - f * f;
      f *= 1.1F;
      f += 0.9F;
      this.setColor(f, f, f);
      this.oRoll = this.roll;
      this.roll = this.roll + this.rolld;
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         this.yd = this.yd - 0.04 * (double)this.gravity;
         this.move(this.xd, this.yd, this.zd);
         if (this.speedUpWhenYMotionIsBlocked && this.y == this.yo) {
            this.xd *= 1.1;
            this.zd *= 1.1;
         }

         this.xd = this.xd * (double)this.friction;
         this.yd = this.yd * (double)this.friction;
         this.zd = this.zd * (double)this.friction;
      }

      if (this.onGround) {
         this.yd *= 0.5;
      }
   }

   public void move(double x, double y, double z) {
      double d0 = x;
      double d1 = y;
      double d2 = z;
      if (this.hasPhysics && (x != 0.0 || y != 0.0 || z != 0.0) && x * x + y * y + z * z < Mth.square(100.0)) {
         Vec3 vec3 = Entity.collideBoundingBox(null, new Vec3(x, y, z), this.getBoundingBox(), this.level, List.of());
         x = vec3.x;
         y = vec3.y;
         z = vec3.z;
      }

      if (x != 0.0 || y != 0.0 || z != 0.0) {
         this.setBoundingBox(this.getBoundingBox().move(x, y, z));
         this.setLocationFromBoundingbox();
      }

      this.onGround = d1 != y && d1 < 0.0;
      if (d0 != x) {
         this.xd = 0.0;
      }

      if (d2 != z) {
         this.zd = 0.0;
      }
   }

   @NotNull
   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
   }
}
