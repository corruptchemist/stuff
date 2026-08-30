package dev.protomanly.pmweather.interfaces;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public interface IRegisterRenderTypesEvent {
   void pmwregister(ResourceLocation var1, RenderType var2, RenderType var3);
}
