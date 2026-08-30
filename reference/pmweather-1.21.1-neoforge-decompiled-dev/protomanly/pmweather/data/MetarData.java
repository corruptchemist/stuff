package dev.protomanly.pmweather.data;

import dev.protomanly.pmweather.block.MetarBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;

public class MetarData {
   public static final int Version = 1;
   public static final double MAX_DATA_DISTANCE = 1024.0;
   public final List<MetarBlock.PlacementState> METARS = new ArrayList<>();
   public Level level;

   public MetarData(Level level) {
      this.level = level;
   }

   public void setLevel(Level level) {
      this.level = level;
   }

   public void put(MetarBlock.PlacementState state) {
      this.METARS.add(state);
   }

   public void remove(BlockPos pos) {
      this.METARS.removeIf(state -> state.pos() == pos);
   }

   public List<MetarData.WeightedState> getWeightedStatesAt(BlockPos pos) {
      List<MetarData.WeightedState> rtrn = new ArrayList<>();

      for (MetarBlock.PlacementState state : this.METARS) {
         double dist = pos.atY(0).distSqr(state.pos().atY(0));
         if (!(dist > 1048576.0)) {
            dist = Math.sqrt(dist);
            rtrn.add(new MetarData.WeightedState(state, dist));
         }
      }

      return rtrn;
   }

   public void read(CompoundTag data) {
      int dataVer = data.getInt("dataVer");
      CompoundTag states = data.getCompound("metars");
      this.METARS.clear();

      for (String i : states.getAllKeys()) {
         CompoundTag metar = states.getCompound(i);
         MetarBlock.PlacementState state = MetarBlock.PlacementState.create(
            NbtUtils.readBlockPos(metar, "position").orElse(BlockPos.ZERO), metar.getInt("terrainHeight"), metar.getString("biome"), this.level
         );
         this.METARS.add(state);
      }
   }

   public static record WeightedState(MetarBlock.PlacementState state, double distance) {
   }
}
