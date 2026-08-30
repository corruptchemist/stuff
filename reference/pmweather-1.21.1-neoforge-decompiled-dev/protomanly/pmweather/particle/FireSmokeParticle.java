package dev.protomanly.pmweather.particle;

import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import org.jetbrains.annotations.NotNull;

public class FireSmokeParticle extends TextureSheetParticle {
   private final SpriteSet spriteSet;
   private float rolld = 0.0F;

   protected FireSmokeParticle(ClientLevel level, double x, double y, double z, double yd, SpriteSet spriteSet) {
      super(level, x, y, z);
      this.yd = yd;
      this.spriteSet = spriteSet;
      this.scale(30.0F);
      this.scale((float)this.yd);
      this.lifetime = level.random.nextInt(50, 160);
      float rRoll = level.random.nextFloat() * (float) Math.PI * 2.0F;
      this.oRoll = rRoll;
      this.roll = rRoll;
      this.hasPhysics = false;
      this.rolld = (level.random.nextFloat() - 0.5F) * 0.02F;
      this.setSpriteFromAge(spriteSet);
   }

   public float getQuadSize(float scaleFactor) {
      if (Minecraft.getInstance().cameraEntity == null) {
         return this.quadSize;
      } else {
         float distance = (float)Minecraft.getInstance().cameraEntity.position().distanceTo(this.getPos());
         float mult = 1.0F + distance / 32.0F;
         return this.quadSize * mult;
      }
   }

   public void tick() {
      this.oRoll = this.roll;
      this.setSpriteFromAge(this.spriteSet);
      if (this.yd < 0.75) {
         this.yd += 0.015F;
      }

      this.roll = this.roll + this.rolld;
      if (this.level.isClientSide()) {
         WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
         double closestWhirl = Double.MAX_VALUE;
         if (weatherHandler != null) {
            for (Storm storm : weatherHandler.getStorms()) {
               if (storm.is(StormTypes.FIRE_WHIRL)) {
                  double dist = storm.position.multiply(1.0, 0.0, 1.0).distanceTo(this.getPos().multiply(1.0, 0.0, 1.0));
                  if (dist < closestWhirl) {
                     closestWhirl = dist;
                  }
               }
            }
         }

         if (closestWhirl < 20.0 + (double)this.quadSize / 2.0) {
            this.age += 8;
         }
      }

      super.tick();
   }

   @NotNull
   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }
}
