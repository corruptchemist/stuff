package dev.protomanly.pmweather.weather.effects;

import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClientLightning extends Lightning {
   public LightRenderHandle<LightData> lightHandle = null;

   public ClientLightning(Vec3 position, Level level, float strength) {
      super(position, level, strength);
   }

   @Override
   public void onDeath() {
      super.onDeath();
      if (this.lightHandle != null) {
         this.lightHandle.free();
      }

      this.lightHandle = null;
   }
}
