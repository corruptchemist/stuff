package dev.protomanly.pmweather.weather.effects;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.util.ColorTables;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import java.awt.Color;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PowerFlash {
   public long seed;
   public Vec3 position;
   public float size;
   public float voltage;
   public float airResistance;
   public float current;
   public float strength;
   public Level level;
   public boolean dead;
   public int ticks = 0;
   public int lifetime;
   public LightRenderHandle<PointLightData> lightHandle = null;

   public PowerFlash(Vec3 position, Level level, float voltage) {
      this.voltage = Math.abs(voltage);
      this.strength = Math.min(this.voltage / 10000.0F, 1.0F);
      this.strength = Mth.sqrt(Mth.sin(this.strength * (float) (Math.PI / 2)));
      this.position = position;
      this.level = level;
      this.seed = PMWeather.RANDOM.nextLong();
      this.dead = false;
      this.lifetime = PMWeather.RANDOM.nextInt(15, Mth.ceil(50.0F * this.strength) + 20);
      this.size = Mth.sqrt(this.strength) * 250.0F;
      this.airResistance = 0.5F;
      this.airResistance = this.airResistance + (PMWeather.RANDOM.nextFloat() - 0.4F) * this.airResistance * 0.5F;
      this.current = this.voltage / this.airResistance;
      this.level
         .playLocalSound(
            this.position.x,
            this.position.y,
            this.position.z,
            ModSounds.POWERFLASH.get(),
            SoundSource.WEATHER,
            this.strength * 4.0F,
            0.9F + PMWeather.RANDOM.nextFloat() * 0.1F,
            false
         );
   }

   public float getTime(float partialTick) {
      return Math.min(((float)this.ticks + partialTick) / (float)this.lifetime, 1.0F);
   }

   public float getMod(float partialTick) {
      float time = this.getTime(partialTick);
      float lengthPeriod = (float)this.lifetime / 50.0F;
      lengthPeriod += this.strength * 0.75F;
      return (1.0F - Mth.square(2.0F * time - 1.0F))
         * (Math.abs(Mth.sin(4.0F * lengthPeriod * (float) Math.PI * (float)Math.pow((double)time, 0.3333333333333333))) + 0.3F);
   }

   public float getCurrent(float partialTick) {
      return this.current * Math.max(this.getMod(partialTick) - 0.2F, 0.0F);
   }

   public Vec3 getColor(float partialTick) {
      float current = this.getCurrent(partialTick);
      Color color = new Color(4128672);
      color = ColorTables.lerp((current - 2000.0F) / 4000.0F, color, new Color(8645887));
      color = ColorTables.lerp((current - 8500.0F) / 3000.0F, color, new Color(12831736));
      float intensity = Math.min((float)Math.pow((double)current / 2000.0, 0.2) * 14.0F, 18.0F);
      return new Vec3((double)((float)color.getRed() / 255.0F), (double)((float)color.getGreen() / 255.0F), (double)((float)color.getBlue() / 255.0F))
         .scale((double)(this.getMod(partialTick) * intensity));
   }

   public float getSize(float partialTick) {
      return this.size * Mth.lerp(this.getMod(partialTick), 0.1F, 1.0F);
   }

   public void tick() {
      if (!this.dead) {
         this.ticks++;
         if (this.ticks > this.lifetime) {
            this.dead = true;
            this.onDeath();
         }
      }
   }

   public void onDeath() {
      if (this.lightHandle != null) {
         this.lightHandle.free();
      }
   }
}
