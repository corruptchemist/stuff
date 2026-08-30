package dev.protomanly.pmweather.block;

import com.mojang.serialization.MapCodec;
import dev.protomanly.pmweather.block.entity.ModBlockEntities;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.ServerConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadarBlock extends BaseEntityBlock {
   public static EnumProperty<RadarBlock.Mode> RADAR_MODE = EnumProperty.create("mode", RadarBlock.Mode.class);
   public static BooleanProperty ON = BooleanProperty.create("on");
   public static final MapCodec<RadarBlock> CODEC = simpleCodec(RadarBlock::new);

   protected RadarBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.defaultBlockState().setValue(RADAR_MODE, RadarBlock.Mode.REFLECTIVITY)).setValue(ON, true));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{RADAR_MODE});
      builder.add(new Property[]{ON});
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
      return createTickerHelper(
         blockEntityType, ModBlockEntities.RADAR_BE.get(), (level1, blockPos, blockState, blockEntity) -> blockEntity.tick(level1, blockPos, blockState)
      );
   }

   @NotNull
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
      if (ServerConfig.requireWSR88D) {
         tooltipComponents.add(Component.literal("Requires a completed WSR-88D nearby to function"));
      }
   }

   private void nextMode(BlockState state, Level level, BlockPos pos) {
      RadarBlock.Mode newMode = (RadarBlock.Mode)state.getValue(RADAR_MODE);

      newMode = switch (newMode) {
         case REFLECTIVITY -> RadarBlock.Mode.VELOCITY;
         case VELOCITY -> RadarBlock.Mode.METAR_WIND;
         default -> RadarBlock.Mode.REFLECTIVITY;
      };
      level.setBlockAndUpdate(pos, (BlockState)state.setValue(RADAR_MODE, newMode));
   }

   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
      BlockPos offset = neighborPos.subtract(pos);
      Direction direction = Direction.fromDelta(offset.getX(), offset.getY(), offset.getZ());
      if (direction != null) {
         if (!level.isClientSide && level.hasSignal(neighborPos, direction)) {
            level.scheduleTick(pos, this, 4);
         }
      }
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.hasNeighborSignal(pos)) {
         this.nextMode(state, level, pos);
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (!level.isClientSide()) {
         if (player.isShiftKeyDown()) {
            level.setBlockAndUpdate(pos, (BlockState)state.setValue(ON, !(Boolean)state.getValue(ON)));
            return InteractionResult.SUCCESS_NO_ITEM_USED;
         }

         this.nextMode(state, level, pos);
      }

      return InteractionResult.SUCCESS_NO_ITEM_USED;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
      return new RadarBlockEntity(blockPos, blockState);
   }

   public static enum Mode implements StringRepresentable {
      REFLECTIVITY("reflectivity"),
      VELOCITY("velocity"),
      METAR_WIND("metar_wind");

      private final String mode;

      private Mode(String mode) {
         this.mode = mode;
      }

      @NotNull
      public String getSerializedName() {
         return this.mode;
      }
   }
}
