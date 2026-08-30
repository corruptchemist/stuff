package dev.protomanly.pmweather.particle;

import dev.protomanly.pmweather.util.ColorTables;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class FlameParticle extends TextureSheetParticle {
   private final SpriteSet spriteSet;

   protected FlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet spriteSet) {
      super(level, x, y, z);
      this.xd = xd;
      this.yd = yd;
      this.zd = zd;
      this.spriteSet = spriteSet;
      this.scale(1.25F);
      this.scale(Math.max((float)yd * 8.0F, 0.75F));
      this.gravity = -0.3F;
      this.friction = 0.96F - level.random.nextFloat() * 0.1F;
      this.lifetime = Mth.ceil((float)level.random.nextInt(20, 35) * Math.max((float)yd, 0.05F));
      this.hasPhysics = false;
      this.roll = (level.random.nextFloat() - 0.5F) * 0.025F;
      this.setSpriteFromAge(spriteSet);
      this.age = -level.random.nextInt(30);
   }

   public int getLightColor(float partialTick) {
      int i = super.getLightColor(partialTick);
      int k = i >> 16 & 0xFF;
      return 240 | k << 16;
   }

   public float getQuadSize(float scaleFactor) {
      if (this.age < 0) {
         return 0.0F;
      } else {
         float f = 1.0F - (float)this.age / (float)this.lifetime;
         f = (float)Math.pow((double)f, 1.5);
         f *= 0.5F;
         f += 0.5F;
         return this.quadSize * f;
      }
   }

   public void tick() {
      if (this.age >= 0) {
         this.setSpriteFromAge(this.spriteSet);
         float lifePerc = (float)this.age / (float)this.lifetime;
         Color colorStart = new Color(16777215);
         Color colorEnd = new Color(16754046);
         Color realColor = ColorTables.lerp(lifePerc, colorStart, colorEnd);
         this.setColor((float)realColor.getRed() / 255.0F, (float)realColor.getGreen() / 255.0F, (float)realColor.getBlue() / 255.0F);
      }

      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else if (this.age >= 0) {
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
   }

   @NotNull
   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
   }
}
