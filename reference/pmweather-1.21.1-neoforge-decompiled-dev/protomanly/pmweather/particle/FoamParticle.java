package dev.protomanly.pmweather.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class FoamParticle extends TextureSheetParticle {
   private final SpriteSet spriteSet;
   private float rolld = 0.0F;

   protected FoamParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet spriteSet) {
      super(level, x, y, z);
      this.xd = xd;
      this.yd = yd;
      this.zd = zd;
      this.spriteSet = spriteSet;
      this.scale(1.25F + level.random.nextFloat() * 0.25F);
      this.lifetime = level.random.nextInt(30, 50);
      this.gravity = 0.5F;
      float rRoll = level.random.nextFloat() * (float) Math.PI * 2.0F;
      this.oRoll = rRoll;
      this.roll = rRoll;
      this.rolld = (level.random.nextFloat() - 0.5F) * 0.02F;
      this.setSpriteFromAge(spriteSet);
   }

   public void setSpriteFromAge(SpriteSet sprite) {
      float p = (float)this.age / (float)this.lifetime;
      p = 4.0F * (float)Math.pow((double)(p - 0.5F), 3.0) + 0.5F;
      if (p < 0.5F) {
         p = (float)Math.pow((double)(p / 8.0F), 0.25);
      }

      this.setSprite(sprite.get(Mth.clamp((int)(p * (float)this.lifetime), 0, this.lifetime), this.lifetime));
   }

   public void tick() {
      this.oRoll = this.roll;
      this.setSpriteFromAge(this.spriteSet);
      this.roll = this.roll + this.rolld;
      if (this.age < 10) {
         float p = 1.0F - (float)this.age / 10.0F;
         this.scale(1.0F + p * 0.2F);
      }

      super.tick();
   }

   @NotNull
   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }
}
