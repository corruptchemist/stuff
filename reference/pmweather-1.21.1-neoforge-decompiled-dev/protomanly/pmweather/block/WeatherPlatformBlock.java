package dev.protomanly.pmweather.block;

import com.mojang.serialization.MapCodec;
import dev.protomanly.pmweather.block.entity.ModBlockEntities;
import dev.protomanly.pmweather.block.entity.WeatherPlatformBlockEntity;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.item.component.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class WeatherPlatformBlock extends BaseEntityBlock {
   public static final MapCodec<WeatherPlatformBlock> CODEC = simpleCodec(WeatherPlatformBlock::new);

   protected WeatherPlatformBlock(Properties properties) {
      super(properties);
   }

   protected ItemInteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      if (stack.is((Item)ModItems.CONNECTOR.get())) {
         if (!level.isClientSide()) {
            stack.set(ModComponents.WEATHER_BALLOON_PLATFORM, pos);
            player.sendSystemMessage(Component.literal(String.format("Connector is configured to %s, %s, %s", pos.getX(), pos.getY(), pos.getZ())));
         }

         return ItemInteractionResult.SUCCESS;
      } else {
         if (stack.is((Item)ModItems.WEATHER_BALLOON.get())) {
            if (level.getBlockEntity(pos) instanceof WeatherPlatformBlockEntity weatherPlatformBlockEntity
               && !weatherPlatformBlockEntity.active
               && level.canSeeSky(pos.above())) {
               if (!level.isClientSide()) {
                  stack.consume(1, player);
                  weatherPlatformBlockEntity.activate(level, pos, state);
               }

               return ItemInteractionResult.SUCCESS;
            }

            if (!level.canSeeSky(pos.above()) && !level.isClientSide()) {
               player.sendSystemMessage(Component.literal("Platform cannot see sky!"));
            }
         }

         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
      return new WeatherPlatformBlockEntity(blockPos, blockState);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
      return createTickerHelper(
         blockEntityType,
         ModBlockEntities.WEATHER_PLATFORM_BE.get(),
         (level1, blockPos, blockState, blockEntity) -> blockEntity.tick(level1, blockPos, blockState)
      );
   }
}
