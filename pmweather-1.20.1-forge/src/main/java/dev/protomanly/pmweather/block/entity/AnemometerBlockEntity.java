package dev.protomanly.pmweather.block.entity;

import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AnemometerBlockEntity extends BlockEntity {
   public float smoothAngle = 0.0F;
   public float prevSmoothAngle = 0.0F;
   public float smoothAngleRotationalVel = 0.0F;

   public AnemometerBlockEntity(BlockPos pos, BlockState blockState) {
      super(ModBlockEntities.ANEMOMETER_BE.get(), pos, blockState);
   }

   public void tick(Level level, BlockPos blockPos, BlockState blockState) {
      if (level.isClientSide()) {
         Vec3 wind = WindEngine.getWind(blockPos, level);
         double windspeed = wind.length();
         double rotMax = 200.0;
         double maxSpeed = windspeed / 150.0 * rotMax;
         if ((double)this.smoothAngleRotationalVel < maxSpeed) {
            this.smoothAngleRotationalVel += (float)windspeed / 100.0F;
         }

         if ((double)this.smoothAngleRotationalVel > rotMax) {
            this.smoothAngleRotationalVel = (float)rotMax;
         }

         if (this.smoothAngle >= 180.0F) {
            this.smoothAngle -= 360.0F;
         }

         this.prevSmoothAngle = this.smoothAngle;
         this.smoothAngle = this.smoothAngle + this.smoothAngleRotationalVel;
         this.smoothAngleRotationalVel -= 0.01F;
         this.smoothAngleRotationalVel *= 0.99F;
         if (this.smoothAngleRotationalVel <= 0.0F) {
            this.smoothAngleRotationalVel = 0.0F;
         }
      }
   }
}
