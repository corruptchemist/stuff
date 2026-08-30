package dev.protomanly.pmweather.block;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockSetType.PressurePlateSensitivity;

public class ModBlockSetType {
   public static final BlockSetType CHARRED = BlockSetType.register(
      new BlockSetType(
         "charred",
         true,
         true,
         true,
         PressurePlateSensitivity.EVERYTHING,
         SoundType.BASALT,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK,
         SoundEvents.BASALT_BREAK
      )
   );

   public ModBlockSetType() {
   }

   public static void register() {
   }
}
