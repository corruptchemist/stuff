package dev.protomanly.pmweather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.BlockDamageEvent;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.level.ChunkLoading;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.seasons.MoistureHandler;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Sounding;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormType;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import java.util.Locale;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.common.NeoForge;

public class WeatherCommands {
   public WeatherCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("report").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("generate").executes(this::generateReport))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("atmosphere").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("sample").then(Commands.literal("column").executes(this::sampleColumn)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("tornado")
                        .then(
                           Commands.argument("windspeed", IntegerArgumentType.integer(0, 400))
                              .then(Commands.argument("width", IntegerArgumentType.integer(5, 1200)).executes(this::newTornado))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("firewhirl")
                        .then(
                           Commands.argument("windspeed", IntegerArgumentType.integer(0, 100))
                              .then(Commands.argument("width", IntegerArgumentType.integer(1, 10)).executes(this::newFireWhirl))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("cyclone")
                        .then(
                           Commands.argument("windspeed", IntegerArgumentType.integer(0, 250))
                              .then(Commands.argument("width", IntegerArgumentType.integer(3000, 16000)).executes(this::newCyclone))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("cyclone").then(Commands.literal("natural").executes(this::naturalCyclone)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("tornado")
                        .then(
                           Commands.literal("buildto")
                              .then(
                                 Commands.argument("fromStage", IntegerArgumentType.integer(0, 2))
                                    .then(
                                       Commands.argument("fromEnergy", IntegerArgumentType.integer(0, 99))
                                          .then(
                                             Commands.argument("windspeed", IntegerArgumentType.integer(0, 400))
                                                .then(Commands.argument("width", IntegerArgumentType.integer(5, 1200)).executes(this::buildTornado))
                                          )
                                    )
                              )
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("effect").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("strike")
                        .then(
                           Commands.argument("position", Vec3Argument.vec3(true))
                              .then(Commands.argument("power", FloatArgumentType.floatArg(1.0F, 20.0F)).executes(this::strike))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("effect").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("powerflash")
                        .then(
                           Commands.argument("position", Vec3Argument.vec3(true))
                              .then(Commands.argument("voltage", FloatArgumentType.floatArg(0.0F)).executes(this::powerflash))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("supercell")
                        .then(
                           Commands.argument("stage", IntegerArgumentType.integer(0, 2))
                              .then(Commands.argument("energy", IntegerArgumentType.integer(1, 99)).executes(this::newStorm))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("supercell")
                        .then(
                           Commands.literal("buildto")
                              .then(
                                 Commands.argument("stage", IntegerArgumentType.integer(0, 2))
                                    .then(Commands.argument("energy", IntegerArgumentType.integer(1, 99)).executes(this::buildStorm))
                              )
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("squall")
                        .then(
                           Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                              .then(Commands.argument("energy", IntegerArgumentType.integer(1, 99)).executes(this::newSquall))
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("squall")
                        .then(
                           Commands.literal("buildto")
                              .then(
                                 Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                                    .then(Commands.argument("energy", IntegerArgumentType.integer(1, 99)).executes(this::buildSquall))
                              )
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("supercell").then(Commands.literal("natural").executes(this::naturalStorm)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("spawn").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("squall").then(Commands.literal("natural").executes(this::naturalSquall)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("clear").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.argument("type", StringArgumentType.greedyString()).suggests((cntxt, builder) -> {
                     builder.suggest("all");
                     StormTypes.getStormTypes().forEach(builder::suggest);
                     return builder.buildFuture();
                  }).executes(this::clearStorms))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("aimtoplayer").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.argument("type", StringArgumentType.greedyString()).suggests((cntxt, builder) -> {
                     builder.suggest("all");
                     StormTypes.getStormTypes().forEach(builder::suggest);
                     return builder.buildFuture();
                  }).executes(this::aimToPlayer))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               Commands.literal("block")
                  .then(Commands.literal("getstrength").then(Commands.argument("block", ItemArgument.item(context)).executes(this::blockStrength)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(Commands.literal("block").then(Commands.literal("getstrength").executes(this::blockStrengthLooking)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(Commands.literal("block").then(Commands.literal("isreinforced").executes(this::isReinforced)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               Commands.literal("block")
                  .then(((LiteralArgumentBuilder)Commands.literal("isplayerplaced").requires(plr -> plr.hasPermission(2))).executes(this::isPlayerPlaced))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               Commands.literal("block")
                  .then(((LiteralArgumentBuilder)Commands.literal("getsaplingtype").requires(plr -> plr.hasPermission(2))).executes(this::getSaplingType))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("moisture").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("set").then(Commands.argument("percent", FloatArgumentType.floatArg(0.0F, 100.0F)).executes(this::setChunkMoisture)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("moisture").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("get").executes(this::getChunkMoisture))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("debug").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("chunkfireintensity").then(Commands.literal("get").executes(this::getFireIntensity)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("calendar").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("getdate").executes(this::getDate))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("debug").requires(plr -> plr.hasPermission(2)))
                  .then(
                     Commands.literal("particle")
                        .then(
                           Commands.literal("smoke")
                              .then(
                                 Commands.argument("position", Vec3Argument.vec3(true))
                                    .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.0F)).executes(this::spawnSmoke))
                              )
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("debug").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("damage").executes(this::damageBlock))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweather")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("debug").requires(plr -> plr.hasPermission(2)))
                  .then(Commands.literal("forcedchunks").then(Commands.literal("getcount").executes(this::getForcedChunkCount)))
            )
      );
   }

   private int getForcedChunkCount(CommandContext<CommandSourceStack> context) {
      long count = ChunkLoading.getForcedCount();
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(count + " chunks force loaded across all dimensions"), true);
      return 1;
   }

   private int spawnSmoke(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      Vec3 position = Vec3Argument.getVec3(context, "position");
      float intensity = FloatArgumentType.getFloat(context, "intensity");
      CompoundTag data = new CompoundTag();
      data.putString("packetCommand", "SpawnParticle");
      data.putString("command", "smoke");
      data.putFloat("posX", (float)position.x);
      data.putFloat("posY", (float)position.y);
      data.putFloat("posZ", (float)position.z);
      data.putFloat("intensity", intensity);
      ModNetworking.serverSendToClientNear(data, position, 256.0, level);
      return 1;
   }

   private int damageBlock(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         Vec3 pos = player.getEyePosition();
         BlockHitResult blockHitResult = level.clip(new ClipContext(pos, pos.add(player.getLookAngle().scale(4.5)), Block.OUTLINE, Fluid.NONE, player));
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState state = level.getBlockState(blockPos);
            NeoForge.EVENT_BUS.post(new BlockDamageEvent(level, blockPos, state));
            level.removeBlock(blockPos, false);
            return 1;
         }

         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Not looking at any block"));
      }

      return -1;
   }

   private int blockStrengthLooking(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (player != null && reinforcementManager != null) {
         Vec3 pos = player.getEyePosition();
         BlockHitResult blockHitResult = level.clip(new ClipContext(pos, pos.add(player.getLookAngle().scale(4.5)), Block.OUTLINE, Fluid.NONE, player));
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            net.minecraft.world.level.block.Block block = level.getBlockState(blockPos).getBlock();
            float strength;
            if (ServerConfig.blockStrengths.containsKey(block)) {
               strength = ServerConfig.blockStrengths.get(block);
               if (reinforcementManager.isReinforced(blockPos)) {
                  strength *= 1.3F;
               }
            } else {
               strength = Storm.getBlockStrength(block, ((CommandSourceStack)context.getSource()).getLevel(), blockPos);
            }

            if (((CommandSourceStack)context.getSource()).isPlayer()) {
               ((CommandSourceStack)context.getSource())
                  .getPlayer()
                  .sendSystemMessage(
                     Component.literal(String.format("%s Strength: Damaged at %s MPH", block.getName().getString(), Math.floor((double)strength)))
                  );
            }

            return 1;
         }

         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Not looking at any block"));
      }

      return -1;
   }

   private int getSaplingType(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (player != null && reinforcementManager != null) {
         Vec3 pos = player.getEyePosition();
         BlockHitResult blockHitResult = level.clip(new ClipContext(pos, pos.add(player.getLookAngle().scale(4.5)), Block.OUTLINE, Fluid.NONE, player));
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            net.minecraft.world.level.block.Block saplingType = reinforcementManager.getSaplingType(blockPos);
            player.sendSystemMessage(
               Component.literal(saplingType == null ? "No sapling data" : "Block has sapling data: " + saplingType.getName().getString())
            );
            return 1;
         }

         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Not looking at any block"));
      }

      return -1;
   }

   private int isReinforced(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (player != null && reinforcementManager != null) {
         Vec3 pos = player.getEyePosition();
         BlockHitResult blockHitResult = level.clip(new ClipContext(pos, pos.add(player.getLookAngle().scale(4.5)), Block.OUTLINE, Fluid.NONE, player));
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            player.sendSystemMessage(Component.literal(reinforcementManager.isReinforced(blockPos) ? "Block is reinforced" : "Block is not reinforced"));
            return 1;
         }

         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Not looking at any block"));
      }

      return -1;
   }

   private int isPlayerPlaced(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
      if (player != null && reinforcementManager != null) {
         Vec3 pos = player.getEyePosition();
         BlockHitResult blockHitResult = level.clip(new ClipContext(pos, pos.add(player.getLookAngle().scale(4.5)), Block.OUTLINE, Fluid.NONE, player));
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            player.sendSystemMessage(Component.literal(reinforcementManager.isPlayerPlaced(blockPos) ? "Block was placed by player" : "Block is natural"));
            return 1;
         }

         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Not looking at any block"));
      }

      return -1;
   }

   private int getFireIntensity(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         long chunkLong = ChunkPos.asLong(player.blockPosition());
         LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkLong), ChunkPos.getZ(chunkLong));
         player.sendSystemMessage(
            Component.literal("Chunk Fire Intensity: ")
               .append(String.valueOf(Math.floor((double)((Float)chunk.getData(DataAttachments.FIRE_INTENSITY) * 10.0F)) / 10.0))
         );
         return 1;
      } else {
         return -1;
      }
   }

   private int getDate(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         int day = SeasonHandler.getDayInMonth(level);
         int month = SeasonHandler.getMonth(level);
         player.sendSystemMessage(Component.literal(SeasonHandler.getMonthName(month)).append(" ").append(String.valueOf(day)));
         return 1;
      } else {
         return -1;
      }
   }

   private int setChunkMoisture(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         long chunkLong = ChunkPos.asLong(player.blockPosition());
         LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkLong), ChunkPos.getZ(chunkLong));
         MoistureHandler.setMoistureOnChunk(level, chunk, FloatArgumentType.getFloat(context, "percent"));
         if (chunk.hasData(DataAttachments.MOISTURE)) {
            player.sendSystemMessage(
               Component.literal("Moisture set to: ")
                  .append(String.valueOf(Math.floor((double)((Float)chunk.getData(DataAttachments.MOISTURE)).floatValue())))
                  .append("%")
            );
            return 1;
         } else {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Moisture value does not exist in chunk, this should never happen!"));
            return -1;
         }
      } else {
         return -1;
      }
   }

   private int getChunkMoisture(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         long chunkLong = ChunkPos.asLong(player.blockPosition());
         LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkLong), ChunkPos.getZ(chunkLong));
         if (chunk.hasData(DataAttachments.MOISTURE)) {
            player.sendSystemMessage(
               Component.literal("Moisture: ")
                  .append(String.valueOf(Math.floor((double)((Float)chunk.getData(DataAttachments.MOISTURE)).floatValue())))
                  .append("%")
            );
            return 1;
         } else {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Moisture value does not exist in chunk, this should never happen!"));
            return -1;
         }
      } else {
         return -1;
      }
   }

   private int generateReport(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         StringBuilder str = new StringBuilder();

         for (Storm storm : weatherHandlerServer.getStorms()) {
            str.append("@").append("(X: ").append(Math.floor(storm.position.x * 10.0) / 10.0);
            str.append(", Y: ").append(Math.floor(storm.position.y * 10.0) / 10.0);
            str.append(", Z: ").append(Math.floor(storm.position.z * 10.0) / 10.0);
            str.append(")\n");
            str.append("Type: ").append(storm.stormType.getId()).append("\n");
            if (storm.is(StormTypes.CYCLONE)) {
               str.append("Windspeed: ").append(storm.windspeed).append("\n");
               str.append("Width: ").append(storm.maxWidth);
            }

            if (storm.is(StormTypes.SUPERCELL)) {
               str.append("Windspeed: ").append(storm.windspeed).append("/").append(storm.maxWindspeed).append("\n");
               str.append("Width: ").append(Mth.ceil(storm.width)).append("/").append(storm.maxWidth).append("\n");
               str.append("Stage: ").append(storm.stage).append("/").append(storm.maxStage).append("\n");
               str.append("Energy: ").append(storm.energy);
            }

            if (storm.is(StormTypes.SQUALL)) {
               str.append("Stage: ").append(storm.stage).append("/").append(storm.maxStage).append("\n");
               str.append("Energy: ").append(storm.energy);
            }

            str.append("\n");
         }

         player.sendSystemMessage(Component.literal(str.toString()));
         return 1;
      } else {
         return -1;
      }
   }

   private int sampleColumn(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         Sounding sounding = new Sounding(weatherHandlerServer, player.position(), level, 1000, 16000);
         player.sendSystemMessage(Component.literal(sounding.toString()));
         return 1;
      } else {
         return -1;
      }
   }

   private int blockStrength(CommandContext<CommandSourceStack> context) {
      if (ItemArgument.getItem(context, "block").getItem() instanceof BlockItem blockItem) {
         net.minecraft.world.level.block.Block block = blockItem.getBlock();
         float strength;
         if (ServerConfig.blockStrengths.containsKey(block)) {
            strength = ServerConfig.blockStrengths.get(block);
         } else {
            strength = Storm.getBlockStrength(block, ((CommandSourceStack)context.getSource()).getLevel(), null);
         }

         if (((CommandSourceStack)context.getSource()).isPlayer()) {
            ((CommandSourceStack)context.getSource())
               .getPlayer()
               .sendSystemMessage(Component.literal(String.format("%s Strength: Damaged at %s MPH", block.getName().getString(), Math.floor((double)strength))));
         }

         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to get block from item, is item not a block?"));
         return -1;
      }
   }

   private int aimToPlayer(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      String type = StringArgumentType.getString(context, "type");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      if (type.equalsIgnoreCase("all")) {
         weatherHandlerServer.getStorms().forEach(Storm::aimAtPlayer);

         for (Storm storm : weatherHandlerServer.getStorms()) {
            storm.aimAtPlayer();
         }
      } else {
         StormType stormType = StormTypes.getFromID(type.toLowerCase(Locale.ROOT));
         weatherHandlerServer.getStorms().forEach(stormx -> {
            if (stormx.is(stormType)) {
               stormx.aimAtPlayer();
            }
         });
      }

      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully aimed storms at players"), true);
      return 1;
   }

   private int clearStorms(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      String type = StringArgumentType.getString(context, "type");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      if (type.equalsIgnoreCase("all")) {
         weatherHandlerServer.clearAllStorms();
      } else {
         StormType stormType = StormTypes.getFromID(type.toLowerCase(Locale.ROOT));
         weatherHandlerServer.getStorms().forEach(storm -> {
            if (storm.is(stormType)) {
               storm.remove();
               weatherHandlerServer.syncStormRemove(storm);
            }
         });
      }

      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully cleared storms"), true);
      return 1;
   }

   private int buildTornado(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int windspeed = IntegerArgumentType.getInteger(context, "windspeed");
      int width = IntegerArgumentType.getInteger(context, "width");
      int fromStage = IntegerArgumentType.getInteger(context, "fromStage");
      int fromEnergy = IntegerArgumentType.getInteger(context, "fromEnergy");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Storm storm = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
      storm.width = 15.0F;
      storm.windspeed = 0;
      storm.stage = fromStage;
      storm.velocity = Vec3.ZERO;
      storm.energy = fromEnergy;
      storm.initFirstTime();
      storm.maxStage = 3;
      storm.maxProgress = 100;
      storm.maxWindspeed = windspeed;
      storm.maxWidth = width;
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned storm"), true);
      return 1;
   }

   private int buildStorm(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int fromStage = IntegerArgumentType.getInteger(context, "stage");
      int fromEnergy = IntegerArgumentType.getInteger(context, "energy");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Vec3 pos = new Vec3(player.getX(), (double)level.getMaxBuildHeight(), player.getZ())
         .add(
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1),
            0.0,
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1)
         );
      Storm storm = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandlerServer, level, pos, null));
      storm.width = 0.0F;
      storm.windspeed = 0;
      storm.stage = 0;
      Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
      float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 4.0F;
      pos = pos.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
      storm.velocity = Vec3.ZERO;
      storm.energy = 0;
      storm.maxStage = fromStage;
      storm.maxProgress = fromEnergy;
      storm.initFirstTime();
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(
            () -> Component.literal(
                  "Successfully spawned storm:\nMax Stage: "
                     + storm.maxStage
                     + " Max Energy: "
                     + storm.maxProgress
                     + " Max Windspeed: "
                     + storm.maxWindspeed
                     + " Max Width: "
                     + storm.maxWidth
               ),
            true
         );
      return 1;
   }

   private int buildSquall(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int fromStage = IntegerArgumentType.getInteger(context, "stage");
      int fromEnergy = IntegerArgumentType.getInteger(context, "energy");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Vec3 pos = new Vec3(player.getX(), (double)level.getMaxBuildHeight(), player.getZ())
         .add(
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1),
            0.0,
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1)
         );
      Storm storm = StormTypes.SQUALL.create(new StormSpawnProperties(weatherHandlerServer, level, pos, null));
      storm.width = 0.0F;
      storm.windspeed = 0;
      storm.stage = 0;
      Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
      if (wind.length() < 10.0) {
         wind = wind.normalize().multiply(10.0, 0.0, 10.0);
      }

      float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 6.0F;
      pos = pos.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
      storm.position = pos;
      double fac = 0.85;
      Vec3 offset = new Vec3(1.0 + PMWeather.RANDOM.nextDouble() * fac, 0.0, 1.0 + PMWeather.RANDOM.nextDouble() * fac)
         .subtract(new Vec3(fac / 2.0, 0.0, fac / 2.0));
      storm.velocity = wind.multiply(0.15, 0.0, 0.15).multiply(offset);
      storm.energy = 0;
      storm.maxStage = fromStage;
      storm.maxProgress = fromEnergy;
      storm.initFirstTime();
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.literal("Successfully spawned storm:\nMax Stage: " + storm.maxStage + " Max Energy: " + storm.maxProgress), true);
      return 1;
   }

   private int naturalCyclone(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Vec3 pos = Util.getValidTropicalSystemSpawn(weatherHandlerServer, player.position(), 4000.0F);
      if (pos == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to find valid spawn for tropical cyclone"));
         return -1;
      } else {
         Storm storm = StormTypes.CYCLONE.create(new StormSpawnProperties(weatherHandlerServer, level, pos, null));
         storm.width = PMWeather.RANDOM.nextFloat(5000.0F, 16000.0F);
         storm.windspeed = 0;
         storm.velocity = Vec3.ZERO;
         storm.initFirstTime();
         storm.maxWindspeed = 0;
         storm.maxWidth = Math.round(storm.width);
         storm.calcRain();
         weatherHandlerServer.addStorm(storm);
         weatherHandlerServer.syncStormNew(storm);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned cyclone"), true);
         return 1;
      }
   }

   private int newCyclone(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int windspeed = IntegerArgumentType.getInteger(context, "windspeed");
      int width = IntegerArgumentType.getInteger(context, "width");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Storm storm = StormTypes.CYCLONE.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
      storm.width = (float)width;
      storm.windspeed = windspeed;
      storm.cycloneWindspeed = (float)windspeed;
      storm.velocity = Vec3.ZERO;
      storm.initFirstTime();
      storm.maxStage = Math.max(storm.maxStage, 3);
      storm.maxProgress = Math.max(storm.maxProgress, 100);
      storm.maxWindspeed = windspeed;
      storm.maxWidth = width;
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned cyclone"), true);
      return 1;
   }

   private int newTornado(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int windspeed = IntegerArgumentType.getInteger(context, "windspeed");
      int width = IntegerArgumentType.getInteger(context, "width");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Storm storm = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
      storm.width = (float)width;
      storm.windspeed = windspeed;
      storm.stage = 3;
      storm.velocity = Vec3.ZERO;
      storm.energy = 0;
      storm.initFirstTime();
      storm.maxStage = Math.max(storm.maxStage, 3);
      storm.maxProgress = Math.max(storm.maxProgress, 100);
      storm.maxWindspeed = windspeed;
      storm.maxWidth = width;
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned storm"), true);
      return 1;
   }

   private int newFireWhirl(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int windspeed = IntegerArgumentType.getInteger(context, "windspeed");
      int width = IntegerArgumentType.getInteger(context, "width");
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Storm storm = StormTypes.FIRE_WHIRL.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
      storm.width = (float)width;
      storm.windspeed = windspeed;
      storm.velocity = Vec3.ZERO;
      storm.energy = 0;
      storm.initFirstTime();
      storm.maxWindspeed = windspeed;
      storm.maxWidth = width;
      storm.calcRain();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned fire whirl"), true);
      return 1;
   }

   private int newStorm(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int stage = IntegerArgumentType.getInteger(context, "stage");
      int energy = IntegerArgumentType.getInteger(context, "energy");
      if (stage < 0 || stage >= 3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("stage must be within range 0-2"));
         return -1;
      } else if (energy >= 0 && energy <= 100) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
         Storm storm = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
         storm.width = 0.0F;
         storm.windspeed = 0;
         storm.stage = stage;
         storm.velocity = Vec3.ZERO;
         storm.energy = energy;
         storm.initFirstTime();
         storm.calcRain();
         storm.maxStage = Math.max(storm.maxStage, stage);
         storm.maxProgress = Math.max(storm.maxProgress, energy);
         weatherHandlerServer.addStorm(storm);
         weatherHandlerServer.syncStormNew(storm);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned storm"), true);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("energy must be within range 0-100"));
         return -1;
      }
   }

   private int newSquall(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      int stage = IntegerArgumentType.getInteger(context, "stage");
      int energy = IntegerArgumentType.getInteger(context, "energy");
      if (stage < 0 || stage > 3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("stage must be within range 0-3"));
         return -1;
      } else if (energy >= 0 && energy <= 100) {
         WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
         Storm storm = StormTypes.SQUALL.create(new StormSpawnProperties(weatherHandlerServer, level, player.position(), null));
         Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
         if (wind.length() < 10.0) {
            wind = wind.normalize().multiply(10.0, 0.0, 10.0);
         }

         storm.width = 0.0F;
         storm.windspeed = 0;
         storm.stage = stage;
         storm.position = player.position();
         double fac = 0.85;
         Vec3 offset = new Vec3(1.0 + PMWeather.RANDOM.nextDouble() * fac, 0.0, 1.0 + PMWeather.RANDOM.nextDouble() * fac)
            .subtract(new Vec3(fac / 2.0, 0.0, fac / 2.0));
         storm.velocity = wind.multiply(0.15, 0.0, 0.15).multiply(offset);
         storm.energy = energy;
         storm.initFirstTime();
         storm.maxStage = Math.max(storm.maxStage, stage);
         storm.maxProgress = Math.max(storm.maxProgress, energy);
         storm.coldEnergy = stage * 100 + energy;
         storm.calcRain();
         weatherHandlerServer.addStorm(storm);
         weatherHandlerServer.syncStormNew(storm);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Successfully spawned storm"), true);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("energy must be within range 0-100"));
         return -1;
      }
   }

   private int naturalStorm(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Vec3 pos = new Vec3(player.getX(), (double)level.getMaxBuildHeight(), player.getZ())
         .add(
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1),
            0.0,
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1)
         );
      Vec3 sfcPos = weatherHandlerServer.getWorld().getHeightmapPos(Types.WORLD_SURFACE_WG, player.blockPosition()).getCenter();
      Sounding sounding = new Sounding(weatherHandlerServer, sfcPos, level, 250, 16000, 5400);
      float riskV = sounding.getRisk(5400);
      Storm storm = StormTypes.SUPERCELL.create(new StormSpawnProperties(weatherHandlerServer, level, sfcPos, riskV));
      storm.width = 0.0F;
      storm.windspeed = 0;
      storm.stage = 0;
      Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
      float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 4.0F;
      pos = pos.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
      storm.position = pos;
      if (ServerConfig.environmentSystem) {
         if (PMWeather.RANDOM.nextFloat() <= riskV * 2.5F) {
            storm.maxStage = Math.max(storm.maxStage, 1);
         }

         if (PMWeather.RANDOM.nextFloat() <= riskV * 2.0F) {
            storm.maxStage = Math.max(storm.maxStage, 2);
         }

         if (PMWeather.RANDOM.nextFloat() <= riskV * 1.5F) {
            storm.maxStage = Math.max(storm.maxStage, 3);
         }

         storm.recalc(riskV);
      }

      storm.velocity = Vec3.ZERO;
      storm.energy = 0;
      storm.initFirstTime();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(
            () -> Component.literal(
                  "Successfully spawned storm:\nMax Stage: "
                     + storm.maxStage
                     + " Max Energy: "
                     + storm.maxProgress
                     + " Max Windspeed: "
                     + storm.maxWindspeed
                     + " Max Width: "
                     + storm.maxWidth
               ),
            true
         );
      return 1;
   }

   private int naturalSquall(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      WeatherHandlerServer weatherHandlerServer = (WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension());
      Vec3 pos = new Vec3(player.getX(), (double)level.getMaxBuildHeight(), player.getZ())
         .add(
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1),
            0.0,
            (double)PMWeather.RANDOM.nextInt(-ServerConfig.spawnRange, ServerConfig.spawnRange + 1)
         );
      Vec3 sfcPos = weatherHandlerServer.getWorld().getHeightmapPos(Types.WORLD_SURFACE_WG, player.blockPosition()).getCenter();
      Sounding sounding = new Sounding(weatherHandlerServer, sfcPos, level, 250, 16000, 5400);
      ThermodynamicEngine.AtmosphericDataPoint sfc = ThermodynamicEngine.samplePoint(weatherHandlerServer, sfcPos, level, null, 5400);
      float riskV = sounding.getRisk(5400);
      if (sfc.temperature() < 3.0F) {
         riskV += Math.clamp((sfc.temperature() - 3.0F) / -6.0F, 0.0F, 1.0F) * 0.25F;
      }

      Storm storm = StormTypes.SQUALL.create(new StormSpawnProperties(weatherHandlerServer, level, sfcPos, riskV));
      storm.width = 0.0F;
      storm.windspeed = 0;
      storm.stage = 0;
      Vec3 wind = WindEngine.getWind(new Vec3(player.getX(), (double)(level.getMaxBuildHeight() + 1), player.getZ()), level, true, true, false);
      if (wind.length() < 10.0) {
         wind = wind.normalize().multiply(10.0, 0.0, 10.0);
      }

      float dist = PMWeather.RANDOM.nextFloat(350.0F, 900.0F) * 6.0F;
      pos = pos.add(wind.normalize().multiply((double)(-dist), 0.0, (double)(-dist)));
      storm.position = pos;
      if (ServerConfig.environmentSystem) {
         if (PMWeather.RANDOM.nextFloat() <= riskV * 2.5F) {
            storm.maxStage = Math.max(storm.maxStage, 1);
         }

         if (PMWeather.RANDOM.nextFloat() <= riskV * 2.0F) {
            storm.maxStage = Math.max(storm.maxStage, 2);
         }

         if (PMWeather.RANDOM.nextFloat() <= riskV * 1.5F) {
            storm.maxStage = Math.max(storm.maxStage, 3);
         }

         storm.recalc(riskV);
      }

      double fac = 0.85;
      Vec3 offset = new Vec3(1.0 + PMWeather.RANDOM.nextDouble() * fac, 0.0, 1.0 + PMWeather.RANDOM.nextDouble() * fac)
         .subtract(new Vec3(fac / 2.0, 0.0, fac / 2.0));
      storm.velocity = wind.multiply(0.15, 0.0, 0.15).multiply(offset);
      storm.energy = 0;
      storm.initFirstTime();
      weatherHandlerServer.addStorm(storm);
      weatherHandlerServer.syncStormNew(storm);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.literal("Successfully spawned storm:\nMax Stage: " + storm.maxStage + " Max Energy: " + storm.maxProgress), true);
      return 1;
   }

   private int strike(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      Vec3 lPos = Vec3Argument.getVec3(context, "position").multiply(1.0, 0.0, 1.0);
      int height = level.getHeightmapPos(Types.MOTION_BLOCKING, new BlockPos((int)lPos.x, (int)lPos.y, (int)lPos.z)).getY();
      float power = FloatArgumentType.getFloat(context, "power");
      ((WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension())).syncLightningNew(new Vec3(lPos.x, (double)height, lPos.z), power);
      return 1;
   }

   private int powerflash(CommandContext<CommandSourceStack> context) {
      ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
      Vec3 pos = Vec3Argument.getVec3(context, "position");
      float voltage = FloatArgumentType.getFloat(context, "voltage");
      ((WeatherHandlerServer)GameBusEvents.MANAGERS.get(level.dimension())).syncPowerFlashNew(pos, voltage);
      return 1;
   }
}
