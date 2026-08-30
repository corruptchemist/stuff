package dev.protomanly.pmweather.compat.powergrid.mixin;

import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.interfaces.ConditionallyPulled;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import dev.protomanly.pmweather.weather.WindEngine;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.WireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HangingWireEntity.class})
public abstract class HangingWireEntityMixin extends WireEntity implements ConditionallyPulled {
   public HangingWireEntityMixin(EntityType<?> type, Level world) {
      super(type, world);
      throw new AssertionError();
   }

   @Override
   public boolean shouldPull(Storm actor) {
      return false;
   }

   @Override
   public boolean shouldPush() {
      return false;
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void injectTick(CallbackInfo ci) {
      if (ServerConfig.doDamage) {
         if (this.isAlive()) {
            if (!this.level().isClientSide()) {
               if (this.level().getGameTime() % 10L == 0L) {
                  Vec3 wind = WindEngine.getWind(this.position(), this.level(), false, false, false, false);
                  double windspeed = wind.length();
                  if (!(windspeed < 60.0)) {
                     windspeed -= 60.0;
                     double chanceBreak = -Math.cos(Mth.clamp(windspeed / 720.0, 0.0, 1.0) * Math.PI) / 2.0 + 0.5;
                     if ((double)this.random.nextFloat() < chanceBreak) {
                        this.itemCount = 0;
                        double voltage = this.pmwgetMaxVoltage(GlobalElectricNetworks.getLine(this));
                        voltage *= 1.5;
                        if (voltage > 50.0) {
                           ((WeatherHandlerServer)GameBusEvents.MANAGERS.get(this.level().dimension())).syncPowerFlashNew(this.position(), (float)voltage);
                        }

                        this.kill();
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private double pmwgetMaxVoltage(@Nullable TransmissionLine transmissionLine) {
      ElectricWire wire = this.getWire();
      double v1 = 0.0;
      double v2 = 0.0;
      if (transmissionLine == null) {
         if (wire.getNode1() != null) {
            v1 = wire.getNode1().getVoltage();
         }

         if (wire.getNode2() != null) {
            v2 = wire.getNode2().getVoltage();
         }
      } else {
         if (wire.getNode1() instanceof OwnedFloatingNode floatingNode) {
            v1 = transmissionLine.voltageFor(floatingNode);
         }

         if (wire.getNode2() instanceof OwnedFloatingNode floatingNode) {
            v2 = transmissionLine.voltageFor(floatingNode);
         }
      }

      return Math.max(Math.abs(v1), Math.abs(v2));
   }
}
