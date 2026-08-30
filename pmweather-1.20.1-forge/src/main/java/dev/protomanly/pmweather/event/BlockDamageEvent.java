package dev.protomanly.pmweather.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;

public class BlockDamageEvent extends Event {
   private final Level level;
   private final BlockPos pos;
   private final BlockState state;

   public BlockDamageEvent(Level level, BlockPos pos, BlockState state) {
      this.level = level;
      this.pos = pos;
      this.state = state;
   }

   public Level getLevel() {
      return this.level;
   }

   public BlockPos getPos() {
      return this.pos;
   }

   public BlockState getState() {
      return this.state;
   }
}
