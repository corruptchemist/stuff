package dev.protomanly.pmweather.data;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.tags.ModTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.INBTSerializable;
import oshi.util.tuples.Pair;

public class BlockDataHandler implements INBTSerializable<CompoundTag> {
   public static final int Version = 1;
   public List<BlockPos> reinforcedBlocks = new ArrayList<>();
   public List<BlockPos> placedBlocks = new ArrayList<>();
   public Map<BlockPos, String> saplingReplacements = new HashMap<>();
   public Map<BlockPos, Pair<String, Integer>> deadLeaves = new HashMap<>();

   public BlockDataHandler() {
   }

   public void update(Level level, ChunkAccess chunk) {
      Iterator<BlockPos> iterator = this.reinforcedBlocks.iterator();

      while (iterator.hasNext()) {
         BlockPos block = iterator.next();
         BlockState state = chunk.getBlockState(block);
         if (!state.is(ModTags.Blocks.REINFORCEABLE)) {
            iterator.remove();
         }
      }

      iterator = this.placedBlocks.iterator();

      while (iterator.hasNext()) {
         BlockPos block = iterator.next();
         BlockState state = chunk.getBlockState(block);
         if (state.isAir()) {
            iterator.remove();
         }
      }

      iterator = this.saplingReplacements.keySet().iterator();

      while (iterator.hasNext()) {
         BlockPos block = iterator.next();
         BlockState state = chunk.getBlockState(block);
         if (!state.is(ModBlocks.ROTTED_LOG) && !state.is(ModBlocks.SMOLDERING_LOG) && !state.is(ModBlocks.CHARRED_LOG)) {
            iterator.remove();
         }
      }

      iterator = this.deadLeaves.keySet().iterator();

      while (iterator.hasNext()) {
         BlockPos block = iterator.next();
         BlockState state = chunk.getBlockState(block);
         if (!state.isAir()) {
            iterator.remove();
         } else {
            Pair<String, Integer> data = this.deadLeaves.get(block);
            boolean canGrow = false;
            int lowestSupportTime = Integer.MAX_VALUE;

            label118:
            for (int x = -1; x <= 1; x++) {
               for (int y = -1; y <= 1; y++) {
                  for (int z = -1; z <= 1; z++) {
                     BlockPos checkAt = block.offset(x, y, z);
                     if (!checkAt.equals(block)) {
                        if (this.hasLeafData(checkAt)) {
                           int checkTime = (Integer)this.deadLeaves.get(checkAt).getB();
                           if (checkTime < lowestSupportTime) {
                              lowestSupportTime = checkTime;
                           }
                        } else {
                           BlockState checkState = chunk.getBlockState(checkAt);
                           if (checkState.is(BlockTags.LOGS)) {
                              lowestSupportTime = 0;
                              canGrow = true;
                           } else if (checkState.is(BlockTags.LEAVES)) {
                              int dist = (Integer)checkState.getValue(LeavesBlock.DISTANCE);
                              if (dist < lowestSupportTime) {
                                 lowestSupportTime = dist;
                              }

                              canGrow = dist < 7;
                           }
                        }
                     }

                     if (lowestSupportTime <= 0) {
                        break label118;
                     }
                  }
               }
            }

            int time = lowestSupportTime + 1;
            if (time >= 8) {
               iterator.remove();
            } else {
               boolean regrow = false;

               for (int i = 0; i < level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING); i++) {
                  regrow = level.random.nextInt(1500 * Math.max(time, 1)) == 0;
                  if (regrow) {
                     break;
                  }
               }

               if (canGrow && regrow) {
                  level.setBlock(block, (BlockState)this.getLeafType(block).defaultBlockState().setValue(LeavesBlock.DISTANCE, Mth.clamp(time, 1, 7)), 2);
                  iterator.remove();
               } else if (time != (Integer)data.getB()) {
                  this.deadLeaves.replace(block, new Pair((String)data.getA(), time));
               }
            }
         }
      }
   }

   public boolean hasLeafData(BlockPos blockPos) {
      return this.deadLeaves.containsKey(blockPos);
   }

   public Block getLeafType(BlockPos blockPos) {
      if (this.hasLeafData(blockPos)) {
         Optional<Block> leafBlock = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation((String)this.deadLeaves.get(blockPos).getA()));
         if (leafBlock.isPresent()) {
            return leafBlock.get();
         }

         this.deadLeaves.remove(blockPos);
      }

      return null;
   }

   public void setLeafData(BlockPos blockPos, Block leafBlock) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKeyOrNull(leafBlock);
      if (id != null) {
         this.deadLeaves.put(blockPos, new Pair(id.toString(), 0));
      }
   }

   public void removeLeafData(BlockPos blockPos) {
      if (this.hasLeafData(blockPos)) {
         this.deadLeaves.remove(blockPos);
      }
   }

   public Block getSaplingType(BlockPos blockPos) {
      if (this.hasSaplingData(blockPos)) {
         Optional<Block> sapling = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(this.saplingReplacements.get(blockPos)));
         if (sapling.isPresent()) {
            return sapling.get();
         }

         this.saplingReplacements.remove(blockPos);
      }

      return null;
   }

   public boolean hasSaplingData(BlockPos blockPos) {
      return this.saplingReplacements.containsKey(blockPos);
   }

   public void setSaplingData(BlockPos blockPos, Block sapling) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKeyOrNull(sapling);
      if (id != null) {
         this.saplingReplacements.put(blockPos, id.toString());
      }
   }

   public boolean isPlayerPlaced(BlockPos blockPos) {
      return this.placedBlocks.contains(blockPos);
   }

   public void markPlayerPlaced(BlockPos blockPos) {
      if (!this.isPlayerPlaced(blockPos)) {
         this.placedBlocks.add(blockPos);
      }
   }

   public void unmarkPlayerPlaced(BlockPos blockPos) {
      if (this.isPlayerPlaced(blockPos)) {
         this.placedBlocks.remove(blockPos);
      }
   }

   public boolean isReinforced(BlockPos blockPos) {
      return this.reinforcedBlocks.contains(blockPos);
   }

   public void reinforceBlock(BlockPos blockPos, Level level) {
      if (!this.isReinforced(blockPos)) {
         this.reinforcedBlocks.add(blockPos);
         level.playSound(null, blockPos, SoundEvents.METAL_STEP, SoundSource.BLOCKS, 2.0F, 1.25F);
      }
   }

   public void removeReinforcement(BlockPos blockPos, Level level, boolean drop) {
      if (this.isReinforced(blockPos)) {
         this.reinforcedBlocks.remove(blockPos);
         if (drop) {
            level.playSound(null, blockPos, SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 1.0F, 2.0F);
            Vec3 center = blockPos.getCenter();
            ItemStack stack = new ItemStack(ModBlocks.IRON_REINFORCEMENT_CORE);
            stack.setCount(1);
            ItemEntity itemEntity = new ItemEntity(level, center.x, center.y, center.z, stack);
            level.addFreshEntity(itemEntity);
         }
      }
   }

   public CompoundTag serializeNBT(Provider provider) {
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("dataVer", 1);
      CompoundTag rData = new CompoundTag();
      int i = 0;

      for (BlockPos blockPos : this.reinforcedBlocks) {
         rData.put(String.valueOf(i), NbtUtils.writeBlockPos(blockPos));
         i++;
      }

      nbt.put("reinforced", rData);
      CompoundTag pData = new CompoundTag();
      i = 0;

      for (BlockPos blockPos : this.placedBlocks) {
         pData.put(String.valueOf(i), NbtUtils.writeBlockPos(blockPos));
         i++;
      }

      nbt.put("playerPlaced", pData);
      CompoundTag sData = new CompoundTag();
      i = 0;

      for (Entry<BlockPos, String> entry : this.saplingReplacements.entrySet()) {
         CompoundTag data = new CompoundTag();
         data.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
         data.putString("id", entry.getValue());
         sData.put(String.valueOf(i), data);
         i++;
      }

      nbt.put("saplings", sData);
      CompoundTag lData = new CompoundTag();
      i = 0;

      for (Entry<BlockPos, Pair<String, Integer>> entry : this.deadLeaves.entrySet()) {
         CompoundTag data = new CompoundTag();
         data.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
         data.putString("id", (String)entry.getValue().getA());
         data.putInt("timeNoSupport", (Integer)entry.getValue().getB());
         lData.put(String.valueOf(i), data);
         i++;
      }

      nbt.put("leaves", lData);
      return nbt;
   }

   public void deserializeNBT(Provider provider, CompoundTag compoundTag) {
      int version = compoundTag.getInt("dataVer");
      if (version != 0) {
         CompoundTag rData = compoundTag.getCompound("reinforced");

         for (int i = 0; i < rData.size(); i++) {
            this.reinforcedBlocks.add(NbtUtils.readBlockPos(rData.getCompound(String.valueOf(i))));
         }

         CompoundTag pData = compoundTag.getCompound("playerPlaced");

         for (int i = 0; i < pData.size(); i++) {
            this.placedBlocks.add(NbtUtils.readBlockPos(pData.getCompound(String.valueOf(i))));
         }

         CompoundTag sData = compoundTag.getCompound("saplings");

         for (int i = 0; i < sData.size(); i++) {
            CompoundTag sapling = sData.getCompound(String.valueOf(i));
            this.saplingReplacements.put(NbtUtils.readBlockPos(sapling.getCompound("pos")), sapling.getString("id"));
         }

         CompoundTag lData = compoundTag.getCompound("leaves");

         for (int i = 0; i < lData.size(); i++) {
            CompoundTag leaf = lData.getCompound(String.valueOf(i));
            this.deadLeaves.put(NbtUtils.readBlockPos(leaf.getCompound("pos")), new Pair(leaf.getString("id"), leaf.getInt("timeNoSupport")));
         }
      }
   }
}
