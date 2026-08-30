package dev.protomanly.pmweather.block;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;
import org.jetbrains.annotations.Nullable;

public class ModLogBlock extends RotatedPillarBlock {
   private final RotatedPillarBlock stripInto;

   public ModLogBlock(Properties properties, RotatedPillarBlock stripInto) {
      super(properties);
      this.stripInto = stripInto;
   }

   @Nullable
   public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction itemAbility, boolean simulate) {
      return itemAbility == ToolActions.AXE_STRIP && state.is(this)
         ? (BlockState)this.stripInto.defaultBlockState().setValue(AXIS, (Axis)state.getValue(AXIS))
         : super.getToolModifiedState(state, context, itemAbility, simulate);
   }
}
