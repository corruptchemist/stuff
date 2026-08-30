package dev.protomanly.pmweather.compat.create.mixin;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import dev.protomanly.pmweather.compat.sable.SableMod;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WindmillBearingBlockEntity.class})
public class WindmillBearingBlockEntityMixin {
   public WindmillBearingBlockEntityMixin() {
   }

   @Inject(
      method = {"getGeneratedSpeed"},
      at = {@At("RETURN")},
      cancellable = true
   )
   public void editGeneratedSpeed(CallbackInfoReturnable<Float> callbackInfoReturnable) {
      if (ServerConfig.createWindmillSpeed) {
         WindmillBearingBlockEntity blockEntity = (WindmillBearingBlockEntity)this;
         if (blockEntity.isRunning()) {
            if (blockEntity.getMovedContraption() != null) {
               Level level = blockEntity.getLevel();
               if (level != null) {
                  Vec3 pos = SableMod.getPos(level, blockEntity.getBlockPosition());
                  Vec3 wind = WindEngine.getWind(pos.add(0.0, 1.0, 0.0), level);
                  float current = (Float)callbackInfoReturnable.getReturnValue();
                  double windspeed = wind.multiply(1.0, 0.0, 1.0).length();
                  if (SableMod.exists()) {
                     windspeed = SableMod.getHandler().getRelativeWind(blockEntity, wind, pos).getSignedWindspeed();
                  }

                  callbackInfoReturnable.setReturnValue(current * ((float)windspeed / 15.0F));
               }
            }
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   public void editTick(CallbackInfo callbackInfo) {
      if (ServerConfig.createWindmillSpeed) {
         WindmillBearingBlockEntity blockEntity = (WindmillBearingBlockEntity)this;
         if (blockEntity.isRunning()) {
            Level level = blockEntity.getLevel();
            if (level != null && level.getGameTime() % 20L == 0L) {
               blockEntity.updateGeneratedRotation();
            }
         }
      }
   }
}
