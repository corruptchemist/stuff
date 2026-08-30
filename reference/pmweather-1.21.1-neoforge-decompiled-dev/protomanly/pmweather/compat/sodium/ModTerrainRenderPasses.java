package dev.protomanly.pmweather.compat.sodium;

import dev.protomanly.pmweather.render.ModRenderTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

public class ModTerrainRenderPasses {
   public static final TerrainRenderPass SWAYING_CUTOUT = new TerrainRenderPass(ModRenderTypes.swayingCutout(), false, true);

   public ModTerrainRenderPasses() {
   }
}
