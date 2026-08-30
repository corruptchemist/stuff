package dev.protomanly.pmweather.render;

import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.shaders.PMWShaderProgramShard;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import dev.protomanly.pmweather.weather.WeatherHandler;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ModRenderTypes {
   private static final RenderType SWAYING_CUTOUT = RenderType.create(
      "pmweather:swaying_cutout",
      ModVertexFormats.WAVING_BLOCK,
      Mode.QUADS,
      786432,
      true,
      false,
      CompositeState.builder()
         .setLightmapState(RenderType.LIGHTMAP)
         .setShaderState(new PMWShaderProgramShard(PMWeather.getPath("swaying_cutout"), ModRenderTypes::setupSwayingCutout))
         .setTextureState(RenderType.BLOCK_SHEET)
         .createCompositeState(true)
   );

   public ModRenderTypes() {
   }

   public static int getBlockTypeForRender(BlockState blockState, BlockPos blockPos, BlockAndTintGetter level) {
      DoubleBlockHalf half = (DoubleBlockHalf)blockState.getOptionalValue(DoublePlantBlock.HALF).orElse(null);
      if (half != null) {
         return switch (half) {
            case LOWER -> 0;
            case UPPER -> 1;
            default -> throw new MatchException(null, null);
         };
      } else if (!blockState.is(Blocks.SUGAR_CANE)) {
         return -1;
      } else {
         int height = 0;

         for (int i = 0; i < 15; i++) {
            BlockPos check = blockPos.below(i + 1);
            BlockState checkState = level.getBlockState(check);
            if (!checkState.is(Blocks.SUGAR_CANE)) {
               break;
            }

            height++;
         }

         return height;
      }
   }

   public static RenderType swayingCutout() {
      return SWAYING_CUTOUT;
   }

   private static void setupSwayingCutout(ShaderProgram shader) {
      Minecraft minecraft = Minecraft.getInstance();
      ClientLevel level = minecraft.level;
      WeatherHandler weatherHandler = GameBusClientEvents.weatherHandler;
      if (weatherHandler != null && level != null && minecraft.cameraEntity != null) {
         float time = (float)minecraft.cameraEntity.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(true);
         shader.getUniformSafe("time").setFloat(time);
         shader.getUniformSafe("doesSway").setInt(ClientConfig.swayingGrass ? 1 : 0);
         CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();
         Vector3f camPos = matrices.getCameraPosition();
         Vec3 mojangCamPos = new Vec3(camPos);
         Vec3 offset = mojangCamPos.subtract(FBOManager.LAST_POSITION);
         shader.getUniformSafe("fboOffset").setVector(offset.toVector3f());
         Vec3 prevOffset = mojangCamPos.subtract(FBOManager.PREV_LAST_POSITION);
         shader.getUniformSafe("prevFboOffset").setVector(prevOffset.toVector3f());
         shader.getUniformSafe("fboTimeDelta").setFloat(FBOManager.getTimeDelta(time));
      }
   }
}
