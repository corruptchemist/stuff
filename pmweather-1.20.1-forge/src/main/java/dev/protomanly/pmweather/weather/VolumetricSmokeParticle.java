package dev.protomanly.pmweather.weather;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class VolumetricSmokeParticle {
   public Vec3 lastPos;
   public Vec3 position;
   public Vec3 velocity = Vec3.ZERO;
   public float lastBright;
   public float brightness;
   public float lastSize;
   public float size;
   public float lastOpacity = 0.0F;
   public float opacity = 0.0F;
   private final float maxSize;
   private float maxOpacity;
   private int lifetime;
   private int ticks = 0;
   private boolean dying = false;
   public boolean dead = false;
   private static final float StartSizeFactor = 3.5F;
   private static final float CoolingRate = 0.985F;
   private static final float DragCoefficient = 0.75F;
   public final Level level;

   public VolumetricSmokeParticle(Level level, Vec3 position, float maxSize, float maxOpacity, float brightness, int lifetime) {
      this.level = level;
      this.position = position.add(0.0, (double)((float)(-lifetime) / 300.0F), 0.0);
      this.lastPos = this.position;
      this.maxSize = maxSize;
      this.size = this.getMinSize();
      this.lastSize = this.size;
      this.brightness = brightness;
      this.lastBright = this.brightness;
      this.maxOpacity = maxOpacity;
      this.lifetime = Math.max(lifetime, 20);
   }

   public void startDeath() {
      if (!this.dying) {
         this.dying = true;
         this.maxOpacity = this.opacity;
         this.ticks = 5;
         this.lifetime = 20;
      }
   }

   public void kill() {
      this.ticks = this.lifetime;
      this.dead = true;
      this.opacity = 0.0F;
   }

   public float getMinSize() {
      return Math.max(this.size / 3.5F, Math.min(3.5F, this.size));
   }

   public float getUpwardSpeed() {
      return Math.max(this.maxSize / 5.0F + this.brightness * this.brightness * 3.0F, 20.0F);
   }

   public void tick() {
      if (!this.dead) {
         if (this.ticks < 0) {
            this.ticks = 0;
         }

         this.ticks++;
         this.lastPos = this.position;
         this.lastSize = this.size;
         this.lastBright = this.brightness;
         this.lastOpacity = this.opacity;
         this.brightness *= 0.985F;
         float lifeP = (float)this.ticks / (float)this.lifetime;
         float sizeP = Math.max(Mth.sqrt(1.0F - Mth.square(lifeP - 1.0F)), Mth.clamp((float)this.ticks / 300.0F, 0.0F, 1.0F) * 0.4F);
         if (!this.dying) {
            this.size = Mth.lerp(sizeP, this.getMinSize(), this.maxSize);
         }

         float v = 0.025F;
         float nLifeP = lifeP <= v ? Mth.sin(lifeP * (float) Math.PI / (2.0F * v)) : Mth.cos((lifeP - v) / (1.0F - v) * (float) (Math.PI / 2));
         this.opacity = this.maxOpacity * nLifeP;
         Vec3 wind = WindEngine.getWind(this.position, this.level, false, true, false, true, true);
         this.velocity = this.velocity.add(wind.scale(0.033333335F)).add(new Vec3(0.0, (double)(this.getUpwardSpeed() * 0.033333335F), 0.0));
         this.position = this.position.add(this.velocity.scale(0.05F));
         this.velocity = this.velocity.scale(0.75);
         if (this.ticks > this.lifetime) {
            this.kill();
         }
      }
   }
}
