package dev.protomanly.pmweather.block.interfaces;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.PMWFireBlock;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.tags.ModTags;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags.Blocks;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Quartet;

public interface BurningBlockInterface {
   int getBurnChance();

   int getSpreadChance();

   Block getBurnsInto();

   default BlockState getBlockStateForBurning(BlockState state) {
      BlockState newState = this.getBurnsInto().defaultBlockState();

      for (Property<?> property : state.getProperties()) {
         newState = this.copyProperty(state, newState, property);
      }

      return newState;
   }

   default void burn(BlockState state, ServerLevel level, BlockPos pos) {
      if (level.random.nextInt(this.getKeepChance()) != 0) {
         level.removeBlock(pos, false);
      } else {
         level.setBlockAndUpdate(pos, this.getBlockStateForBurning(state));
      }
   }

   default int getKeepChance() {
      return 3;
   }

   default void setBurning(Block block, BlockState to, BlockPos toPos, Level level) {
      if (to.is(Blocks.CHESTS) && to.getBlock() instanceof ChestBlock && to.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
         Direction dir = ChestBlock.getConnectedDirection(to);
         BlockPos nextPos = toPos.relative(dir);
         BlockState next = level.getBlockState(nextPos);
         if (next.is(Blocks.CHESTS)) {
            this.setBurningBase(block, to, toPos, level);
         }
      }

      this.setBurningBase(block, to, toPos, level);
   }

   default void setBurningBase(Block block, BlockState to, BlockPos toPos, Level level) {
      BlockState newState = block.defaultBlockState();

      for (Property<?> property : to.getProperties()) {
         newState = this.copyProperty(to, newState, property);
      }

      if (to.is(BlockTags.LOGS_THAT_BURN) && to.getOptionalValue(RotatedPillarBlock.AXIS).isPresent()) {
         ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
         Block sapling = Util.tryGuessSapling(to.getBlock(), net.minecraft.world.level.block.Blocks.OAK_SAPLING);
         if (to.getValue(RotatedPillarBlock.AXIS) == Axis.Y && level.getBlockState(toPos.below()).is(BlockTags.DIRT) && reinforcementManager != null) {
            reinforcementManager.setSaplingData(toPos, sapling);
         }
      }

      level.setBlockAndUpdate(toPos, newState);
   }

   default void spread(@Nullable BlockState actor, @Nullable BlockPos actorPos, BlockState to, BlockPos toPos, Level level) {
      if (!to.isAir()) {
         if (to.is(ModTags.Blocks.BURNS_GENERICALLY)) {
            this.setBurning((Block)ModBlocks.SMOLDERING_BLOCK.get(), to, toPos, level);
         } else if (to.is(BlockTags.PLANKS) || to.is(Blocks.BOOKSHELVES) || to.is(Blocks.CHESTS_WOODEN) || to.is(Blocks.BARRELS_WOODEN)) {
            this.setBurning((Block)ModBlocks.SMOLDERING_PLANKS.get(), to, toPos, level);
         } else if (to.is(BlockTags.LOGS) || to.is(Blocks.STRIPPED_LOGS) || to.is(Blocks.STRIPPED_WOODS)) {
            this.setBurning((Block)ModBlocks.SMOLDERING_LOG.get(), to, toPos, level);
         } else if (to.is(BlockTags.WOODEN_SLABS)) {
            this.setBurning((Block)ModBlocks.SMOLDERING_SLAB.get(), to, toPos, level);
         } else if (to.is(BlockTags.WOODEN_STAIRS)) {
            this.setBurning((Block)ModBlocks.SMOLDERING_STAIRS.get(), to, toPos, level);
         } else if (to.is(BlockTags.WOODEN_DOORS)) {
            BlockPos altPos = toPos.above();
            if (to.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
               altPos = toPos.below();
            }

            this.setBurning((Block)ModBlocks.SMOLDERING_DOOR.get(), to, toPos, level);
            BlockState altState = level.getBlockState(altPos);
            if (altState.is(BlockTags.WOODEN_DOORS)) {
               this.setBurning((Block)ModBlocks.SMOLDERING_DOOR.get(), altState, altPos, level);
            }
         } else if (to.is(Blocks.GLASS_BLOCKS) || to.is(Blocks.GLASS_PANES)) {
            level.destroyBlock(toPos, false);
         } else if (!this.doIncineration(to)) {
            BlockPos below = toPos.below();
            BlockState belowState = level.getBlockState(below);
            if (PMWFireBlock.canBurnOn(level, belowState, below, 6) && !Util.hasCollision(to, toPos, level)) {
               level.setBlockAndUpdate(toPos, (BlockState)((Block)ModBlocks.FIRE.get()).defaultBlockState().setValue(PMWFireBlock.INTENSITY, 4));
            }
         } else if (this.doFlash(to)) {
            this.executeFlash(level, to, toPos, null, null);
         } else {
            level.removeBlock(toPos, false);
         }
      }
   }

   default boolean doIncineration(BlockState state) {
      return ServerConfig.incinerateBlocks.contains(state.getBlock()) || state.is(ModTags.Blocks.INCINERATES);
   }

   default boolean doThermalShock(BlockState state) {
      return state.is(ModTags.Blocks.THERMAL_SHOCKS);
   }

   default boolean doFlash(BlockState state) {
      return state.is(ModTags.Blocks.FLASHES);
   }

   default Quartet<Integer, Boolean, ArrayList<BlockPos>, ArrayList<BlockPos>> executeFlash(
      Level level, BlockState state, BlockPos pos, @Nullable ArrayList<BlockPos> checked, @Nullable ArrayList<BlockPos> explosives
   ) {
      boolean original = false;
      if (checked == null) {
         original = true;
         checked = new ArrayList<>();
         checked.add(pos);
      }

      if (explosives == null) {
         explosives = new ArrayList<>();
         explosives.add(pos);
      }

      int mass = 0;
      boolean foundAir = false;

      label71:
      for (int x = -1; x <= 1; x++) {
         for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
               if (x != y && y != z && z != x) {
                  BlockPos checkPos = pos.offset(x, y, z);
                  if (!checked.contains(checkPos)) {
                     checked.add(checkPos);
                     BlockState check = level.getBlockState(checkPos);
                     if (check.isAir() || check.getBlock() instanceof BurningBlockInterface) {
                        foundAir = true;
                        break label71;
                     }

                     if (this.doFlash(check)) {
                        explosives.add(checkPos);
                        mass++;
                        Quartet<Integer, Boolean, ArrayList<BlockPos>, ArrayList<BlockPos>> rtrn = this.executeFlash(
                           level, check, checkPos, checked, explosives
                        );
                        checked = (ArrayList<BlockPos>)rtrn.getC();
                        explosives = (ArrayList<BlockPos>)rtrn.getD();
                        mass += rtrn.getA();
                        if ((Boolean)rtrn.getB()) {
                           foundAir = true;
                           break label71;
                        }
                     }
                  }
               }
            }
         }
      }

      if (original) {
         if (foundAir) {
            BlockPos lastExplosivePos = explosives.getLast();
            BlockState lastExplosive = level.getBlockState(lastExplosivePos);
            this.setBurning((Block)ModBlocks.SMOLDERING_BLOCK.get(), lastExplosive, lastExplosivePos, level);
         } else {
            for (BlockPos blockPos : explosives) {
               float explosiveAdd = (float)mass / 40.0F;
               level.explode(null, (double)blockPos.getX(), (double)blockPos.getY(), (double)blockPos.getZ(), 4.0F + explosiveAdd, ExplosionInteraction.BLOCK);
               level.removeBlock(blockPos, false);
            }
         }
      }

      return new Quartet(mass, foundAir, checked, explosives);
   }

   default int getFireIntensity() {
      return 4;
   }

   default boolean doExtinguishLogic(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
      float precip = weatherHandler.getPrecipitation(pos.getCenter());
      precip -= 0.1F;
      if (random.nextFloat() < precip * 2.5F) {
         this.burn(state, level, pos);
      }

      return precip > 0.0F;
   }

   default void doRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (!this.doExtinguishLogic(state, level, pos, random)) {
         int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
         float percChange = 3.0F / (float)randomTickSpeed;
         ChunkAccess chunk = level.getChunk(pos);
         float fireIntensity = (Float)chunk.getData(DataAttachments.FIRE_INTENSITY);
         fireIntensity += (float)this.getFireIntensity() * 15.0F * percChange / 500.0F;
         chunk.setData(DataAttachments.FIRE_INTENSITY, fireIntensity);

         for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  if (random.nextInt(this.getSpreadChance()) == 0) {
                     BlockPos toPos = pos.offset(x, y, z);
                     if (!toPos.equals(pos)) {
                        this.spread(state, pos, level.getBlockState(toPos), toPos, level);
                     }
                  }
               }
            }
         }

         if (random.nextInt(this.getBurnChance()) == 0) {
            this.burn(state, level, pos);
            this.trySpreadFireBlock(state, pos, level);
         }
      }
   }

   default void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
      if (level.isClientSide()) {
         ClientLevel clientLevel = (ClientLevel)level;
         int intensity = 4;
         float intenseMod = (float)intensity / 8.0F;
         if (Minecraft.getInstance().cameraEntity != null && !state.is(ModBlocks.SMOLDERING_DIRT) && !state.is(ModBlocks.SMOLDERING_DIRT_SLIGHT)) {
            double dist = Minecraft.getInstance().cameraEntity.position().distanceTo(pos.getCenter());
            if (dist <= 64.0) {
               for (int i = 0; i < intensity + 1; i++) {
                  if (random.nextDouble() <= ClientConfig.fireParticleDensity) {
                     double x = pos.getCenter().x() + (random.nextDouble() - 0.5) * 2.0;
                     double y = pos.getCenter().y() + random.nextDouble() * 1.5;
                     double z = pos.getCenter().z() + (random.nextDouble() - 0.5) * 2.0;
                     clientLevel.addAlwaysVisibleParticle(
                        (ParticleOptions)ModParticleTypes.EMBER.get(), false, x, y, z, 0.0, (random.nextDouble() - 0.7) * 0.65, 0.0
                     );
                  }
               }
            }

            float flameIntenseMod = intenseMod * 2.5F;

            for (int ix = 0; ix < 3 * intensity; ix++) {
               if (random.nextDouble() <= ClientConfig.fireParticleDensity * 0.75) {
                  double x = pos.getCenter().x() + (random.nextDouble() - 0.5) * (double)(flameIntenseMod * 1.25F + 0.25F);
                  double y = pos.getCenter().y() + random.nextDouble() * 0.75;
                  double z = pos.getCenter().z() + (random.nextDouble() - 0.5) * (double)(flameIntenseMod * 1.25F + 0.25F);
                  double flameIntensity = random.nextDouble() * Math.pow((double)flameIntenseMod, 1.25) * 0.45 + 0.1;
                  clientLevel.addAlwaysVisibleParticle(
                     (ParticleOptions)ModParticleTypes.FLAME.get(),
                     true,
                     x,
                     y,
                     z,
                     random.nextDouble() * (double)flameIntenseMod * 0.05,
                     flameIntensity * 1.5,
                     random.nextDouble() * (double)flameIntenseMod * 0.05
                  );
               }
            }
         }

         if (level.random.nextFloat() <= intenseMod && random.nextDouble() <= ClientConfig.fireParticleDensity) {
            double x = pos.getCenter().x() + (level.random.nextDouble() - 0.5) * 2.0;
            double y = pos.getCenter().y() + 0.6F;
            double z = pos.getCenter().z() + (level.random.nextDouble() - 0.5) * 2.0;
            clientLevel.addAlwaysVisibleParticle(
               (ParticleOptions)ModParticleTypes.FIRE_SMOKE.get(), true, x, y, z, 0.0, level.random.nextDouble() * (double)intenseMod, 0.0
            );
         }
      }
   }

   default void doScorching(BlockState state, BlockPos pos, Level level) {
      if (state.is(ModTags.Blocks.SCORCHABLE_COBBLESTONE)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_COBBLESTONE_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_COBBLESTONE_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_COBBLESTONE.get(), state, pos, level);
         }
      } else if (state.is(ModTags.Blocks.SCORCHABLE_STONE)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_STONE_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_STONE_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_STONE.get(), state, pos, level);
         }
      } else if (state.is(ModTags.Blocks.SCORCHABLE_DEEPSLATE)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_DEEPSLATE_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_DEEPSLATE_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_DEEPSLATE.get(), state, pos, level);
         }
      } else if (state.is(ModTags.Blocks.SCORCHABLE_BRICKS)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_BRICK_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_BRICK_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_BRICKS.get(), state, pos, level);
         }
      } else if (state.is(ModTags.Blocks.SCORCHABLE_STONE_BRICKS)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_STONE_BRICK_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_STONE_BRICK_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_STONE_BRICKS.get(), state, pos, level);
         }
      } else if (state.is(ModTags.Blocks.SCORCHABLE_CONCRETE)) {
         if (state.is(BlockTags.STAIRS)) {
            this.setBurning((Block)ModBlocks.SCALDED_CONCRETE_STAIRS.get(), state, pos, level);
         } else if (state.is(BlockTags.SLABS)) {
            this.setBurning((Block)ModBlocks.SCALDED_CONCRETE_SLAB.get(), state, pos, level);
         } else {
            this.setBurning((Block)ModBlocks.SCALDED_CONCRETE.get(), state, pos, level);
         }
      } else {
         if (state.is(ModTags.Blocks.SCORCHABLE_METAL)) {
            this.setBurning((Block)ModBlocks.SCALDED_METAL.get(), state, pos, level);
         }
      }
   }

   default void trySpreadFireBlock(BlockState actor, BlockPos actorPos, Level level) {
      int intensity = 4;
      if (actor.is(ModBlocks.FIRE)) {
         intensity = (Integer)actor.getValue(PMWFireBlock.INTENSITY);
      }

      Vec3 wind = WindEngine.getWind(actorPos.getCenter(), level, false, true, false, true);
      Vec3 weightedPos;
      if (intensity < 5) {
         weightedPos = actorPos.getCenter().add(wind.multiply(0.05, 0.0, 0.05));
         weightedPos = weightedPos.add((level.random.nextDouble() - 0.5) * 1.5, 0.0, (level.random.nextDouble() - 0.5) * 1.5);
      } else {
         weightedPos = actorPos.getCenter().add(wind.multiply(0.15, 0.0, 0.15));
         weightedPos = weightedPos.add((level.random.nextDouble() - 0.5) * 4.0, 0.0, (level.random.nextDouble() - 0.5) * 4.0);
      }

      BlockPos topPos;
      if (intensity > 6 && level.random.nextInt(2) == 0) {
         topPos = level.getHeightmapPos(Types.MOTION_BLOCKING, new BlockPos((int)Math.round(weightedPos.x()), 65, (int)Math.round(weightedPos.z())));
      } else {
         topPos = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int)Math.round(weightedPos.x()), 65, (int)Math.round(weightedPos.z())));
      }

      int newIntensity = level.random.nextInt(1, Math.clamp((long)intensity, 1, 6));
      BlockPos topBelow = topPos.below();
      BlockState topBelowState = level.getBlockState(topBelow);
      if (PMWFireBlock.canBurnOn(level, topBelowState, topBelow, newIntensity)) {
         int heightDifference = topBelow.getY() - actorPos.getY();
         if (heightDifference < 0) {
            heightDifference *= -2;
         }

         int chance = heightDifference / 4;
         if (level.random.nextInt(++chance) == 0 && heightDifference < 15) {
            BlockState topState = level.getBlockState(topPos);
            if (topState.isAir() || !Util.hasCollision(topState, topPos, level) && topState.getFluidState().isEmpty()) {
               if (!topBelowState.is(BlockTags.LOGS) && !topBelowState.is(Blocks.STRIPPED_WOODS) && !topBelowState.is(Blocks.STRIPPED_LOGS)) {
                  level.setBlockAndUpdate(topPos, (BlockState)((Block)ModBlocks.FIRE.get()).defaultBlockState().setValue(PMWFireBlock.INTENSITY, newIntensity));
               } else {
                  this.setFireToLog(level, topBelowState, topBelow);
               }
            }
         } else {
            for (int y = 1; y <= 8; y++) {
               if (level.random.nextInt(4) == 0) {
                  BlockPos colPos = topBelow.below(y);
                  BlockState colState = level.getBlockState(colPos);
                  if (PMWFireBlock.canBurnOn(level, colState, colPos, newIntensity)) {
                     if (colState.is(ModTags.Blocks.BURNS_GENERICALLY)) {
                        this.setBurning((Block)ModBlocks.SMOLDERING_BLOCK.get(), colState, colPos, level);
                     } else if (colState.is(BlockTags.LOGS) || colState.is(Blocks.STRIPPED_WOODS) || colState.is(Blocks.STRIPPED_LOGS)) {
                        this.setBurning((Block)ModBlocks.SMOLDERING_LOG.get(), colState, colPos, level);
                     } else if (colState.is(BlockTags.PLANKS)
                        || colState.is(Blocks.BOOKSHELVES)
                        || colState.is(Blocks.BARRELS_WOODEN)
                        || colState.is(Blocks.CHESTS_WOODEN)) {
                        this.setBurning((Block)ModBlocks.SMOLDERING_PLANKS.get(), colState, colPos, level);
                     } else if (colState.is(BlockTags.WOODEN_STAIRS)) {
                        this.setBurning((Block)ModBlocks.SMOLDERING_STAIRS.get(), colState, colPos, level);
                     } else if (colState.is(BlockTags.WOODEN_SLABS)) {
                        this.setBurning((Block)ModBlocks.SMOLDERING_SLAB.get(), colState, colPos, level);
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   default void setFireToLog(Level level, BlockState state, BlockPos pos) {
      BlockState topBelowState = state;
      BlockPos topBelow = pos;

      for (int count = 0; count < 26; count++) {
         if (!topBelowState.is(BlockTags.LOGS) || topBelowState.is(Blocks.STRIPPED_WOODS) || topBelowState.is(Blocks.STRIPPED_LOGS)) {
            topBelow = topBelow.above();
            topBelowState = level.getBlockState(topBelow);
            break;
         }

         topBelow = topBelow.below();
         topBelowState = level.getBlockState(topBelow);
      }

      if (topBelowState.is(BlockTags.LOGS) || topBelowState.is(Blocks.STRIPPED_WOODS) || topBelowState.is(Blocks.STRIPPED_LOGS)) {
         this.setBurning((Block)ModBlocks.SMOLDERING_LOG.get(), topBelowState, topBelow, level);
      }
   }

   default <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
      return (BlockState)to.trySetValue(property, from.getValue(property));
   }
}
