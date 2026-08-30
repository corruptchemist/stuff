package dev.protomanly.pmweather.level;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

@Mod.EventBusSubscriber(
   modid = "pmweather"
)
public class ChunkLoading {
   public static final TicketController CONTROLLER = new TicketController(PMWeather.getPath("storms"), ChunkLoading::validate);
   private static final HashMap<ServerLevel, HashMap<UUID, List<Long>>> LOADED_CHUNKS_MAP = new HashMap<>();
   private static final HashMap<ServerLevel, Boolean> locks = new HashMap<>();

   public ChunkLoading() {
   }

   @SubscribeEvent
   private static void registerTicketControllers(RegisterTicketControllersEvent event) {
      event.register(CONTROLLER);
   }

   public static long getForcedCount() {
      return LOADED_CHUNKS_MAP.values().stream().mapToLong(forced -> forced.values().stream().mapToLong(List::size).sum()).sum();
   }

   private static boolean isLocked(ServerLevel level) {
      return !locks.containsKey(level) ? false : locks.get(level);
   }

   private static void setLock(ServerLevel level, boolean locked) {
      locks.put(level, locked);
   }

   public static boolean force(Storm storm, ChunkPos chunkPos, boolean add) {
      if (storm.uuid == null) {
         return false;
      } else if (storm.level instanceof ServerLevel serverLevel) {
         if (isLocked(serverLevel)) {
            return false;
         } else {
            if (!LOADED_CHUNKS_MAP.containsKey(serverLevel)) {
               LOADED_CHUNKS_MAP.put(serverLevel, new HashMap<>());
            }

            if (LOADED_CHUNKS_MAP.get(serverLevel).containsKey(storm.uuid) && LOADED_CHUNKS_MAP.get(serverLevel).get(storm.uuid).contains(chunkPos.toLong())) {
               return false;
            } else {
               boolean success = CONTROLLER.forceChunk(serverLevel, storm.uuid, chunkPos.x, chunkPos.z, add, true);
               if (!success) {
                  return false;
               } else {
                  if (!LOADED_CHUNKS_MAP.get(serverLevel).containsKey(storm.uuid)) {
                     LOADED_CHUNKS_MAP.get(serverLevel).put(storm.uuid, new ArrayList<>());
                  }

                  if (add) {
                     LOADED_CHUNKS_MAP.get(serverLevel).get(storm.uuid).add(chunkPos.toLong());
                  } else {
                     LOADED_CHUNKS_MAP.get(serverLevel).get(storm.uuid).remove(chunkPos.toLong());
                  }

                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private static void assertLocked(ServerLevel level) {
      if (!isLocked(level)) {
         throw new RuntimeException("Modification on unlocked level forced chunk data. Make sure to use setLock()!");
      }
   }

   private static boolean unloadAll(ServerLevel level, UUID id) {
      assertLocked(level);
      if (!LOADED_CHUNKS_MAP.get(level).containsKey(id)) {
         return false;
      } else {
         Iterator<Long> iterator = LOADED_CHUNKS_MAP.get(level).get(id).iterator();

         while (iterator.hasNext()) {
            long l = iterator.next();
            ChunkPos pos = new ChunkPos(l);
            if (CONTROLLER.forceChunk(level, id, pos.x, pos.z, false, true)) {
               iterator.remove();
            }
         }

         return LOADED_CHUNKS_MAP.get(level).get(id).isEmpty();
      }
   }

   private static boolean validate(ServerLevel level, Storm storm) {
      assertLocked(level);
      UUID id = storm.uuid;
      if (!LOADED_CHUNKS_MAP.get(level).containsKey(id)) {
         return false;
      } else {
         Iterator<Long> iterator = LOADED_CHUNKS_MAP.get(level).get(id).iterator();

         while (iterator.hasNext()) {
            long l = iterator.next();
            ChunkPos pos = new ChunkPos(l);
            if (!keepLoaded(storm, pos) && CONTROLLER.forceChunk(level, id, pos.x, pos.z, false, true)) {
               iterator.remove();
            }
         }

         return LOADED_CHUNKS_MAP.get(level).get(id).isEmpty();
      }
   }

   public static void update(ServerLevel level) {
      WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
      if (weatherHandler != null) {
         setLock(level, true);
         if (!LOADED_CHUNKS_MAP.containsKey(level)) {
            LOADED_CHUNKS_MAP.put(level, new HashMap<>());
         }

         Map<UUID, List<Long>> forced = LOADED_CHUNKS_MAP.get(level);
         Iterator<UUID> uuidIterator = forced.keySet().iterator();
         long cleaned = forced.values().stream().mapToLong(List::size).sum();
         if (cleaned <= 0L) {
            setLock(level, false);
         } else {
            while (uuidIterator.hasNext()) {
               UUID id = uuidIterator.next();
               Storm storm = weatherHandler.lookupStormByUUID.get(id);
               boolean removalRequested = storm == null || !storm.shouldLoadChunks();
               if (removalRequested && unloadAll(level, id)) {
                  uuidIterator.remove();
               } else if (storm != null && validate(level, storm)) {
                  uuidIterator.remove();
               }
            }

            setLock(level, false);
         }
      }
   }

   private static boolean keepLoaded(@Nullable Storm storm, ChunkPos chunkPos) {
      return storm != null && storm.shouldLoadChunks() ? storm.shouldLoadChunk(chunkPos, 1.1) : false;
   }

   public static void validate(ServerLevel level, TicketHelper helper) {
      setLock(level, true);
      if (!LOADED_CHUNKS_MAP.containsKey(level)) {
         LOADED_CHUNKS_MAP.put(level, new HashMap<>());
      }

      helper.getBlockTickets().forEach((pos, set) -> {
         if (!set.nonTicking().isEmpty() || !set.ticking().isEmpty()) {
            helper.removeAllTickets(pos);
         }
      });
      WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
      if (weatherHandler != null) {
         helper.getEntityTickets().forEach((id, set) -> {
            List<Long> loaded = new ArrayList<>();
            Storm storm = weatherHandler.lookupStormByUUID.get(id);
            if (storm != null && storm.shouldLoadChunks()) {
               set.nonTicking().forEach(l -> helper.removeTicket(id, l, false));
               set.ticking().forEach(l -> {
                  ChunkPos pos = new ChunkPos(l);
                  if (!keepLoaded(storm, pos)) {
                     helper.removeTicket(id, l, true);
                  } else {
                     loaded.add(l);
                  }
               });
               LOADED_CHUNKS_MAP.get(level).put(id, loaded);
            } else {
               helper.removeAllTickets(id);
            }
         });
         setLock(level, false);
      }
   }
}
