package dev.protomanly.pmweather.debug;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.jarjar.nio.util.Lazy;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

public class DebugKeybinds {
   public static final Lazy<KeyMapping> RERENDER_CHUNKS = Lazy.of(
      () -> new KeyMapping(
            "key.pmweather.debug.rerender_chunks", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Type.KEYSYM, 76, "key.categories.pmweather.debug"
         )
   );
   public static final Lazy<KeyMapping> TOGGLE_RADAR_DEBUG = Lazy.of(
      () -> new KeyMapping(
            "key.pmweather.debug.toggle_radar_debug", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Type.KEYSYM, 80, "key.categories.pmweather.debug"
         )
   );

   public DebugKeybinds() {
   }
}
