package dev.protomanly.pmweather.data;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.interfaces.IWorldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import oshi.util.tuples.Pair;

public class ReinforcementManager implements IWorldData {
   public static final int Version = 5;
   public List<BlockPos> reinforcedBlocks = new ArrayList<>();
   public List<BlockPos> placedBlocks = new ArrayList<>();
   public Map<BlockPos, String> saplingReplacements = new HashMap<>();
   public Map<BlockPos, Pair<String, Integer>> deadLeaves = new HashMap<>();
   private final ServerLevel serverLevel;

   public ReinforcementManager(ServerLevel serverLevel) {
      this.serverLevel = serverLevel;
   }

   public void update() {
   }

   public BlockDataHandler getBlockData(ChunkAccess chunk) {
      return (BlockDataHandler)chunk.getData(DataAttachments.BLOCK_DATA);
   }

   private ChunkAccess getEarlyChunk(BlockPos pos) {
      ChunkPos chunkPos = new ChunkPos(pos);
      return this.serverLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
   }

   public Block getLeafType(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.getLeafType(blockPos);
   }

   public boolean hasLeafData(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.hasLeafData(blockPos);
   }

   public void setLeafData(BlockPos blockPos, Block leafBlock) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.setLeafData(blockPos, leafBlock);
   }

   public void removeLeafData(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.removeLeafData(blockPos);
   }

   public Block getSaplingType(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.getSaplingType(blockPos);
   }

   public boolean hasSaplingData(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.hasSaplingData(blockPos);
   }

   public void setSaplingData(BlockPos blockPos, Block sapling) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.setSaplingData(blockPos, sapling);
   }

   public boolean isPlayerPlaced(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.isPlayerPlaced(blockPos);
   }

   public void markPlayerPlaced(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.markPlayerPlaced(blockPos);
   }

   public void unmarkPlayerPlaced(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.unmarkPlayerPlaced(blockPos);
   }

   public boolean isReinforced(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      return handler.isReinforced(blockPos);
   }

   public void reinforceBlock(BlockPos blockPos) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.reinforceBlock(blockPos, this.serverLevel);
   }

   public void removeReinforcement(BlockPos blockPos, boolean drop) {
      BlockDataHandler handler = this.getBlockData(this.getEarlyChunk(blockPos));
      handler.removeReinforcement(blockPos, this.serverLevel, drop);
   }

   @Override
   public CompoundTag save(CompoundTag data) {
      PMWeather.LOGGER.debug("Legacy ReinforcementData Save");
      data.putInt("dataVer", 5);
      return null;
   }

   public void read() {
      LevelSavedData savedData = (LevelSavedData)this.serverLevel.getDataStorage().computeIfAbsent(LevelSavedData.factory(), "pmweather_reinforcement_data");
      savedData.setDataHandler(this);
      CompoundTag data = savedData.getData();
      int version = data.contains("dataVer") ? data.getInt("dataVer") : 1;
      if (version != 1 && version != 5) {
         if (version >= 2) {
            CompoundTag rBlockData = data.getCompound("reinforced");

            for (int i = 0; i < rBlockData.size(); i++) {
               CompoundTag block = rBlockData.getCompound("block_" + i);
               this.reinforcedBlocks.add(new BlockPos(block.getInt("x"), block.getInt("y"), block.getInt("z")));
            }

            CompoundTag pBlockData = data.getCompound("playerPlaced");

            for (int i = 0; i < pBlockData.size(); i++) {
               CompoundTag block = pBlockData.getCompound("block_" + i);
               this.placedBlocks.add(new BlockPos(block.getInt("x"), block.getInt("y"), block.getInt("z")));
            }
         }

         if (version >= 3) {
            CompoundTag saplingData = data.getCompound("saplingData");

            for (int i = 0; i < saplingData.size(); i++) {
               CompoundTag sapling = saplingData.getCompound("sapling_" + i);
               this.saplingReplacements.put(new BlockPos(sapling.getInt("x"), sapling.getInt("y"), sapling.getInt("z")), sapling.getString("id"));
            }
         }

         if (version >= 4) {
            CompoundTag saplingData = data.getCompound("leafData");

            for (int i = 0; i < saplingData.size(); i++) {
               CompoundTag sapling = saplingData.getCompound("leaf_" + i);
               this.deadLeaves
                  .put(
                     new BlockPos(sapling.getInt("x"), sapling.getInt("y"), sapling.getInt("z")),
                     new Pair(sapling.getString("id"), sapling.getInt("timeNoSupport"))
                  );
            }
         }

         if (ServerConfig.largeDataConversion) {
            PMWeather.LOGGER.info("Doing one-time data conversion from legacy system to new system");
            Map<ChunkPos, List<BlockPos>> rData = new HashMap<>();
            Map<ChunkPos, List<BlockPos>> pData = new HashMap<>();
            Map<ChunkPos, List<Pair<BlockPos, String>>> sData = new HashMap<>();
            Map<ChunkPos, List<Pair<BlockPos, Pair<String, Integer>>>> lData = new HashMap<>();
            int i = 0;

            for (BlockPos pos : this.reinforcedBlocks) {
               ChunkPos chunkPos = new ChunkPos(pos);
               if (!rData.containsKey(chunkPos)) {
                  rData.put(chunkPos, new ArrayList<>());
               }

               rData.get(chunkPos).add(pos);
               i++;
            }

            for (BlockPos pos : this.placedBlocks) {
               ChunkPos chunkPos = new ChunkPos(pos);
               if (!pData.containsKey(chunkPos)) {
                  pData.put(chunkPos, new ArrayList<>());
               }

               pData.get(chunkPos).add(pos);
               i++;
            }

            for (Entry<BlockPos, String> sapling : this.saplingReplacements.entrySet()) {
               BlockPos pos = sapling.getKey();
               ChunkPos chunkPos = new ChunkPos(pos);
               if (!sData.containsKey(chunkPos)) {
                  sData.put(chunkPos, new ArrayList<>());
               }

               sData.get(chunkPos).add(new Pair(pos, sapling.getValue()));
               i++;
            }

            for (Entry<BlockPos, Pair<String, Integer>> leaf : this.deadLeaves.entrySet()) {
               BlockPos pos = leaf.getKey();
               ChunkPos chunkPos = new ChunkPos(pos);
               if (!lData.containsKey(chunkPos)) {
                  lData.put(chunkPos, new ArrayList<>());
               }

               lData.get(chunkPos).add(new Pair(pos, leaf.getValue()));
               i++;
            }

            this.reinforcedBlocks.clear();
            this.placedBlocks.clear();
            this.saplingReplacements.clear();
            this.deadLeaves.clear();

            for (Entry<ChunkPos, List<BlockPos>> d : rData.entrySet()) {
               ChunkPos chunkPos = d.getKey();
               ChunkAccess chunkAccess = this.serverLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
               BlockDataHandler handler = (BlockDataHandler)chunkAccess.getData(DataAttachments.BLOCK_DATA);
               handler.reinforcedBlocks.addAll(d.getValue());
            }

            for (Entry<ChunkPos, List<BlockPos>> d : pData.entrySet()) {
               ChunkPos chunkPos = d.getKey();
               ChunkAccess chunkAccess = this.serverLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
               BlockDataHandler handler = (BlockDataHandler)chunkAccess.getData(DataAttachments.BLOCK_DATA);
               handler.placedBlocks.addAll(d.getValue());
            }

            for (Entry<ChunkPos, List<Pair<BlockPos, String>>> d : sData.entrySet()) {
               ChunkPos chunkPos = d.getKey();
               ChunkAccess chunkAccess = this.serverLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
               BlockDataHandler handler = (BlockDataHandler)chunkAccess.getData(DataAttachments.BLOCK_DATA);

               for (Pair<BlockPos, String> p : d.getValue()) {
                  handler.saplingReplacements.putIfAbsent((BlockPos)p.getA(), (String)p.getB());
               }
            }

            for (Entry<ChunkPos, List<Pair<BlockPos, Pair<String, Integer>>>> d : lData.entrySet()) {
               ChunkPos chunkPos = d.getKey();
               ChunkAccess chunkAccess = this.serverLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
               BlockDataHandler handler = (BlockDataHandler)chunkAccess.getData(DataAttachments.BLOCK_DATA);

               for (Pair<BlockPos, Pair<String, Integer>> p : d.getValue()) {
                  handler.deadLeaves.putIfAbsent((BlockPos)p.getA(), (Pair<String, Integer>)p.getB());
               }
            }

            System.gc();
            PMWeather.LOGGER.info("Finished converting {} data points", i);
         } else {
            PMWeather.LOGGER.warn("Large data conversion NOT performed. Some data may be lost on level save events");
         }
      }
   }
}
