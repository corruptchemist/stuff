package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MetarBlock extends Block {
   public static final VoxelShape SHAPE = box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

   public MetarBlock(Properties properties) {
      super(properties);
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }

   protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return super.getVisualShape(state, level, pos, context);
   }

   protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      super.onPlace(state, level, pos, oldState, movedByPiston);
      if (level instanceof ServerLevel serverLevel) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(serverLevel.dimension());
         int height = serverLevel.getHeight(Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
         weatherHandlerServer.metarData.put(new MetarBlock.PlacementState(pos, height, serverLevel.getBiome(pos)));
      }
   }

   public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
      super.playerDestroy(level, player, pos, state, blockEntity, tool);
      if (level instanceof ServerLevel serverLevel) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(serverLevel.dimension());
         weatherHandlerServer.metarData.remove(pos);
      }
   }

   public static record PlacementState(BlockPos pos, int terrainHeight, Holder<Biome> biome) {
      public static MetarBlock.PlacementState create(BlockPos pos, int terrainHeight, String biomeString, Level level) {
         return new MetarBlock.PlacementState(
            pos,
            terrainHeight,
            level.registryAccess()
               .registryOrThrow(Registries.BIOME)
               .getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeString)))
         );
      }
   }
}
