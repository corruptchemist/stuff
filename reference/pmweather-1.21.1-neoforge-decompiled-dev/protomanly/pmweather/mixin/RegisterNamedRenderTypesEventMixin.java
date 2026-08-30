package dev.protomanly.pmweather.mixin;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.protomanly.pmweather.interfaces.IRegisterRenderTypesEvent;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({RegisterNamedRenderTypesEvent.class})
public abstract class RegisterNamedRenderTypesEventMixin implements IRegisterRenderTypesEvent {
   @Shadow
   @Final
   private Map<ResourceLocation, RenderTypeGroup> renderTypes;

   public RegisterNamedRenderTypesEventMixin() {
   }

   @Override
   public void pmwregister(ResourceLocation key, RenderType blockRenderType, RenderType entityRenderType) {
      boolean var10000 = !this.renderTypes.containsKey(key);
      String var10001 = String.valueOf(key);
      Preconditions.checkArgument(var10000, "Render type already registered: " + var10001);
      Preconditions.checkArgument(
         blockRenderType.getChunkLayerId() >= 0, "Only chunk render types can be used for block rendering. Query RenderType#chunkBufferLayers() for a list."
      );
      Preconditions.checkArgument(entityRenderType.format() == DefaultVertexFormat.NEW_ENTITY, "The entity render type must use the NEW_ENTITY vertex format.");
      Preconditions.checkArgument(
         entityRenderType.format() == DefaultVertexFormat.NEW_ENTITY, "The fabulous entity render type must use the NEW_ENTITY vertex format."
      );
      this.renderTypes.put(key, new RenderTypeGroup(blockRenderType, entityRenderType, entityRenderType));
   }
}
