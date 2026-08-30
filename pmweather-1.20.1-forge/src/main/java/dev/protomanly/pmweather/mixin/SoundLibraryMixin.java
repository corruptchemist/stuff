package dev.protomanly.pmweather.mixin;

import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({Library.class})
public class SoundLibraryMixin {
   public SoundLibraryMixin() {
   }

   @ModifyConstant(
      method = {"init"},
      constant = {@Constant(
         intValue = 2
      )}
   )
   private int modifyStreamingSoundsMin(int value) {
      return Math.max(value, 16);
   }

   @ModifyConstant(
      method = {"init"},
      constant = {@Constant(
         intValue = 8
      )}
   )
   private int modifyStreamingSoundsMax(int value) {
      return Math.max(value, 32);
   }

   @ModifyConstant(
      method = {"init"},
      constant = {@Constant(
         intValue = 255
      )}
   )
   private int modifyStaticSoundsMax(int value) {
      return Math.max(value, 512);
   }
}
