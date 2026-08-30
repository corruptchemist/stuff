package dev.protomanly.pmweather.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.render.ModRenderTypes;
import java.util.function.Consumer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({PonderScene.class})
public class PonderSceneMixin {
   public PonderSceneMixin() {
   }

   @WrapOperation(
      method = {"renderScene"},
      at = {@At(
         value = "INVOKE",
         target = "forEachVisible"
      )}
   )
   private <T extends PonderSceneElement> void changeRendertype(
      PonderScene instance,
      Class<T> type,
      Consumer<T> function,
      Operation<Void> orig,
      @Local SuperRenderTypeBuffer buffer,
      @Local GuiGraphics graphics,
      @Local float pt,
      @Local RenderType renderType
   ) {
      if (renderType == ModRenderTypes.swayingCutout()) {
         Consumer<T> funcMod = e -> e.renderLayer(instance.getWorld(), buffer, RenderType.cutout(), graphics, pt);
         orig.call(new Object[]{instance, type, funcMod});
      } else {
         orig.call(new Object[]{instance, type, function});
      }
   }
}
