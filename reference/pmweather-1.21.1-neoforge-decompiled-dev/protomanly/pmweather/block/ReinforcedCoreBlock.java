package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.tags.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ReinforcedCoreBlock extends IronBarsBlock {
   public ReinforcedCoreBlock(Properties properties) {
      super(properties);
   }

   protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
      return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
   }

   protected ItemInteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
         BlockState defaultBlockState = blockItem.getBlock().defaultBlockState();
         ReinforcementManager reinforcementManager = null;
         if (!level.isClientSide()) {
            reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
         }

         if (defaultBlockState.is(ModTags.Blocks.REINFORCEABLE)) {
            if (!level.isClientSide() && reinforcementManager != null) {
               BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hitResult);
               BlockState forPlacing = blockItem.getBlock().getStateForPlacement(context);
               if (forPlacing == null || blockItem.getBlock() instanceof WallBlock || blockItem.getBlock() instanceof CrossCollisionBlock) {
                  forPlacing = defaultBlockState;
               }

               if (forPlacing.getBlock() instanceof SlabBlock) {
                  SlabType type = SlabType.BOTTOM;
                  Vec3 rel = context.getClickLocation().subtract(context.getClickedPos().getCenter());
                  if (rel.y > 0.0) {
                     type = SlabType.TOP;
                  }

                  forPlacing = (BlockState)forPlacing.setValue(SlabBlock.TYPE, type);
               }

               level.setBlockAndUpdate(pos, forPlacing);
               level.updateNeighborsAt(pos, forPlacing.getBlock());
               level.scheduleTick(pos, forPlacing.getBlock(), 1);
               reinforcementManager.reinforceBlock(pos);
            }

            if (!player.isCreative()) {
               stack.shrink(1);
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide());
         }
      }

      return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
   }
}
