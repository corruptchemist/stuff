package dev.protomanly.pmweather.block;

import com.mojang.serialization.MapCodec;
import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.block.interfaces.BurningBlockInterface;
import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.data.ReinforcementManager;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import dev.protomanly.pmweather.sound.ModSounds;
import dev.protomanly.pmweather.tags.ModTags;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerServer;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.protomanly.pmweather.weather.storms.StormSpawnProperties;
import dev.protomanly.pmweather.weather.storms.StormTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.Tags.Blocks;
import org.jetbrains.annotations.Nullable;

public class PMWFireBlock extends Block implements BurningBlockInterface {
   public static final MapCodec<PMWFireBlock> CODEC = simpleCodec(PMWFireBlock::new);
   public static final IntegerProperty INTENSITY = IntegerProperty.create("intensity", 1, 10);
   public static final ResourceKey<DamageType> PUNCH_FIRE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, PMWeather.getPath("punch_fire"));
   public static final VoxelShape SHAPE = box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

   public PMWFireBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(INTENSITY, 1));
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   protected boolean isRandomlyTicking(BlockState state) {
      return true;
   }

   protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }

   protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
   }

   public static boolean canBurnOn(Level level, BlockState state, BlockPos pos, int intensity) {
      if ((state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.STRIPPED_WOODS) || state.is(Blocks.STRIPPED_LOGS)) && intensity < 4) {
         return false;
      } else if (state.is(ModTags.Blocks.BURNS_GENERICALLY)) {
         return true;
      } else {
         return !state.is(BlockTags.PLANKS)
               && !state.is(Blocks.BOOKSHELVES)
               && !state.is(BlockTags.LOGS)
               && !state.is(Blocks.STRIPPED_LOGS)
               && !state.is(Blocks.STRIPPED_WOODS)
            ? state.isFlammable(level, pos, Direction.UP) || isGroundSuitable(level, state, pos)
            : true;
      }
   }

   public static boolean isGroundSuitable(Level level, BlockState state, BlockPos pos) {
      return state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
         || state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
         || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
         || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH);
   }

   private void scorch(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, float fireIntensity) {
      int intensity = (Integer)state.getValue(INTENSITY);
      if (intensity >= 6) {
         int range = 1 + (intensity - 6);

         for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
               for (int y = -1; y <= range * 2; y++) {
                  BlockPos checkPos = pos.offset(x, y, z);
                  if (!checkPos.equals(pos) && random.nextInt(11 - intensity + Math.max(y, 0)) == 0 && random.nextFloat() < fireIntensity / 25.0F) {
                     BlockState check = level.getBlockState(checkPos);
                     this.doScorching(check, checkPos, level);
                  }
               }
            }
         }
      }
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
      float percChange = 3.0F / (float)randomTickSpeed;
      if (!ServerConfig.doWildfires) {
         level.removeBlock(pos, false);
      } else {
         boolean intensityChanged = false;
         int intensity = (Integer)state.getValue(INTENSITY);
         BlockPos belowPos = pos.below();
         BlockState below = level.getBlockState(belowPos);
         boolean flammable = false;
         if (below.isAir()) {
            intensityChanged = true;
            intensity = 0;
         } else {
            flammable = canBurnOn(level, below, belowPos, intensity);
         }

         if (flammable) {
            int chance = level.random.nextInt(0, 13 - intensity);
            if (below.is(BlockTags.LEAVES) || below.is(net.minecraft.world.level.block.Blocks.BAMBOO) || below.is(BlockTags.WOOL)) {
               chance = level.random.nextInt(0, 2);
            }

            if (chance == 0) {
               if (isGroundSuitable(level, below, belowPos)) {
                  if (intensity < 4) {
                     this.setBurning((Block)ModBlocks.SMOLDERING_DIRT_SLIGHT.get(), below, belowPos, level);
                  } else {
                     this.setBurning((Block)ModBlocks.SMOLDERING_DIRT.get(), below, belowPos, level);
                  }
               } else if (below.is(ModTags.Blocks.BURNS_GENERICALLY)) {
                  this.setBurning((Block)ModBlocks.SMOLDERING_BLOCK.get(), below, belowPos, level);
               } else if (below.is(BlockTags.LOGS) || below.is(Blocks.STRIPPED_WOODS) || below.is(Blocks.STRIPPED_LOGS)) {
                  this.setBurning((Block)ModBlocks.SMOLDERING_LOG.get(), below, belowPos, level);
               } else if (below.is(BlockTags.PLANKS) || below.is(Blocks.BOOKSHELVES) || below.is(Blocks.CHESTS_WOODEN) || below.is(Blocks.BARRELS_WOODEN)) {
                  this.setBurning((Block)ModBlocks.SMOLDERING_PLANKS.get(), below, belowPos, level);
               } else if (below.is(BlockTags.WOODEN_STAIRS)) {
                  this.setBurning((Block)ModBlocks.SMOLDERING_STAIRS.get(), below, belowPos, level);
               } else if (below.is(BlockTags.WOODEN_SLABS)) {
                  this.setBurning((Block)ModBlocks.SMOLDERING_SLAB.get(), below, belowPos, level);
               } else if (below.is(BlockTags.LEAVES)) {
                  ReinforcementManager reinforcementManager = GameBusEvents.REINFORCEMENTMANAGERS.get(level.dimension());
                  reinforcementManager.setLeafData(belowPos, below.getBlock());
                  level.removeBlock(belowPos, false);

                  for (int x = -1; x <= 1; x++) {
                     for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                           BlockPos bPos = pos.offset(new Vec3i(x, y, z)).below();
                           BlockState bState = level.getBlockState(bPos);
                           if (canBurnOn(level, bState, bPos, intensity) && level.getBlockState(bPos.above()).isAir() && level.random.nextInt(0, 2) == 0) {
                              if (!bState.is(BlockTags.LOGS) && !bState.is(Blocks.STRIPPED_WOODS) && !bState.is(Blocks.STRIPPED_LOGS)) {
                                 level.setBlockAndUpdate(
                                    bPos.above(), (BlockState)((Block)ModBlocks.FIRE.get()).defaultBlockState().setValue(INTENSITY, intensity)
                                 );
                              } else {
                                 this.setFireToLog(level, bState, bPos);
                              }
                           }
                        }
                     }
                  }

                  intensityChanged = true;
                  intensity = 0;
               } else {
                  level.removeBlock(belowPos, false);
                  BlockPos belowBelowPos = belowPos.below();
                  BlockState belowBelow = level.getBlockState(belowBelowPos);
                  if (canBurnOn(level, belowBelow, belowBelowPos, intensity)) {
                     level.setBlockAndUpdate(belowPos, (BlockState)((Block)ModBlocks.FIRE.get()).defaultBlockState().setValue(INTENSITY, intensity));
                  } else {
                     level.setBlockAndUpdate(belowPos, ((Block)ModBlocks.ASH_BLOCK.get()).defaultBlockState());
                  }

                  intensityChanged = true;
                  intensity = 0;
               }
            } else {
               intensityChanged = true;
               intensity += level.random.nextInt(1, Math.clamp((long)(intensity + 1), 2, 11));
            }
         } else if (intensity > 0) {
            intensityChanged = true;
            intensity--;
         }

         int maxIntensity = 2;
         Vec3 wind = WindEngine.getWind(pos.getCenter(), level, false, true, false, true);
         maxIntensity += (int)Math.pow(wind.length() / 7.0, 2.0);
         ChunkAccess chunk = level.getChunk(pos);
         WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, pos.getCenter(), level, null, 0, null, false);
         float roughHumidity = (float)ThermodynamicEngine.getHumidity(dataPoint.temperature(), dataPoint.dewpoint());
         float precip = weatherHandler.getPrecipitation(pos.getCenter());
         precip *= 12.0F;
         int dropIntensity = Mth.floor(precip);
         if (dropIntensity > 0 && precip > 0.1F) {
            intensity -= dropIntensity;
            intensityChanged = true;
         }

         roughHumidity -= 0.85F;
         roughHumidity *= 2.0F;
         roughHumidity = Mth.clamp(roughHumidity, 0.0F, 0.25F);
         roughHumidity *= 100.0F;
         float moisture = (Float)chunk.getData(DataAttachments.MOISTURE);
         moisture = Math.max(roughHumidity, moisture);
         float fireIntensity = (Float)chunk.getData(DataAttachments.FIRE_INTENSITY);
         fireIntensity += (float)intensity * percChange / 500.0F;
         chunk.setData(DataAttachments.FIRE_INTENSITY, fireIntensity);
         if (intensity >= 8
            && fireIntensity > 6.0F
            && random.nextInt(1000 - intensity * 20) == 0
            && weatherHandler instanceof WeatherHandlerServer weatherHandlerServer) {
            boolean valid = true;
            int whirlCount = 0;

            for (Storm storm : weatherHandler.getStorms()) {
               if (storm.is(StormTypes.FIRE_WHIRL)) {
                  whirlCount++;
                  double dist = storm.position.multiply(1.0, 0.0, 1.0).distanceTo(pos.getCenter().multiply(1.0, 0.0, 1.0));
                  if (dist < 100.0 || whirlCount > 2) {
                     valid = false;
                     break;
                  }
               }
            }

            if (valid) {
               Storm stormx = StormTypes.FIRE_WHIRL.create(new StormSpawnProperties(weatherHandlerServer, level, pos.getCenter(), null));
               stormx.windspeed = 0;
               stormx.velocity = Vec3.ZERO;
               stormx.initFirstTime();
               weatherHandlerServer.addStorm(stormx);
               weatherHandlerServer.syncStormNew(stormx);
            }
         }

         maxIntensity += (int)(Math.pow((double)((100.0F - moisture) / 100.0F), 1.15) * 2.0);
         maxIntensity = (int)Math.clamp((double)maxIntensity * Math.pow((double)((100.0F - moisture) / 100.0F), 0.85), 1.0, 10.0);
         int max = below.is(net.minecraft.world.level.block.Blocks.FARMLAND) ? 6 : 10;
         if (maxIntensity > max) {
            maxIntensity = max;
         }

         Vec3 fwdPos = pos.getCenter().add(wind.normalize().multiply(6.0, 0.0, 6.0));
         BlockPos fwdTopPos = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int)Math.round(fwdPos.x()), 65, (int)Math.round(fwdPos.z())));
         BlockState fwdBlockState = level.getBlockState(fwdTopPos.below());
         if (!canBurnOn(level, fwdBlockState, fwdTopPos.below(), 10) && !below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES)) {
            maxIntensity = 4;
         }

         if (intensity > maxIntensity) {
            intensity = maxIntensity;
         }

         int massCap = 4;
         float d = 1.5F / (1.0F + (100.0F - moisture) / 35.0F);
         massCap += Mth.floor(fireIntensity / d);
         if (intensity > massCap) {
            intensity = massCap;
         }

         boolean spread = below.is(net.minecraft.world.level.block.Blocks.FARMLAND) || level.random.nextInt(0, 6 - Math.min(intensity, 5)) == 0;
         if (intensity > 2 && spread) {
            int runTimes = 1;
            if (below.is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
               runTimes = 4;
            }

            for (int i = 0; i < runTimes; i++) {
               this.trySpreadFireBlock(state, pos, level);
            }
         }

         if (intensity > 6 && fireIntensity > 1.0F) {
            this.scorch(state, level, pos, random, fireIntensity);
         }

         if (intensityChanged) {
            if (intensity <= 0) {
               level.removeBlock(pos, false);
               return;
            }

            level.setBlockAndUpdate(pos, (BlockState)state.setValue(INTENSITY, intensity));
         }
      }
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.tick(state, level, pos, random);
      BlockPos belowPos = pos.below();
      BlockState below = level.getBlockState(belowPos);
      if (below.isAir()) {
         level.removeBlock(pos, false);
      } else {
         if (canBurnOn(level, below, belowPos, 10) && !Util.hasCollision(below, belowPos, level) && below.getFluidState().isEmpty()) {
            this.propagateDown(state, pos, level);
         }
      }
   }

   protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      level.scheduleTick(pos, this, 1);
   }

   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
      level.scheduleTick(pos, this, 1);
   }

   public void propagateDown(BlockState state, BlockPos pos, ServerLevel level) {
      BlockPos belowPos = pos.below();
      BlockState below = level.getBlockState(belowPos);
      if (!below.isAir()) {
         level.setBlockAndUpdate(belowPos, state);
         level.removeBlock(pos, false);
      }
   }

   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      super.animateTick(state, level, pos, random);
      if (level.isClientSide()) {
         ClientLevel clientLevel = (ClientLevel)level;
         float fireIntensity = (Float)clientLevel.getChunk(pos).getData(DataAttachments.FIRE_INTENSITY);
         int intensity = (Integer)state.getValue(INTENSITY);
         float intenseMod = (float)intensity / 8.0F;
         intenseMod += fireIntensity / 10.0F;
         if (intensity > 2 && random.nextInt((int)(fireIntensity / 4.0F) + 1) == 0) {
            for (int i = 0; i < 3 * intensity; i++) {
               if (random.nextDouble() <= ClientConfig.fireParticleDensity) {
                  double x = pos.getCenter().x() + (random.nextDouble() - 0.5) * (double)(intenseMod * 1.25F + 0.25F);
                  double y = pos.getCenter().y() - 0.55;
                  double z = pos.getCenter().z() + (random.nextDouble() - 0.5) * (double)(intenseMod * 1.25F + 0.25F);
                  double flameIntensity = random.nextDouble() * Math.pow((double)intenseMod, 1.25) * 0.45 + 0.1;
                  clientLevel.addAlwaysVisibleParticle(
                     (ParticleOptions)ModParticleTypes.FLAME.get(),
                     true,
                     x,
                     y,
                     z,
                     random.nextDouble() * (double)intenseMod * 0.05,
                     flameIntensity,
                     random.nextDouble() * (double)intenseMod * 0.05
                  );
               }
            }
         }

         if (random.nextInt(3) == 0 && random.nextInt((int)(fireIntensity / 4.0F) + 1) == 0) {
            if (Minecraft.getInstance().cameraEntity != null) {
               double dist = Minecraft.getInstance().cameraEntity.position().distanceTo(pos.getCenter());
               boolean pop = true;
               if (intensity <= 6) {
                  pop = random.nextInt((7 - intensity) * 2) == 0;
               }

               if (dist <= 64.0 && pop) {
                  for (int ix = 0; ix < intensity / 5 + 1; ix++) {
                     if (random.nextDouble() <= ClientConfig.fireParticleDensity) {
                        double x = pos.getCenter().x() + (random.nextDouble() - 0.5);
                        double y = pos.getCenter().y() + random.nextDouble();
                        double z = pos.getCenter().z() + (random.nextDouble() - 0.5);
                        clientLevel.addAlwaysVisibleParticle(
                           (ParticleOptions)ModParticleTypes.EMBER.get(), false, x, y, z, 0.0, (random.nextDouble() - 0.5) * (double)intenseMod * 3.0, 0.0
                        );
                     }
                  }
               }

               if (pop && random.nextInt(1 + Mth.floor(fireIntensity / 5.0F)) == 0) {
                  float vol = 0.9F + random.nextFloat() * 0.2F;
                  vol *= 0.2F;
                  clientLevel.playLocalSound(pos, ModSounds.FIRE_CRACKLE.get(), SoundSource.BLOCKS, vol, 0.8F + random.nextFloat() * 0.4F, false);
               }
            }

            if (level.random.nextFloat() <= intenseMod && random.nextDouble() <= ClientConfig.fireParticleDensity) {
               double x = pos.getCenter().x() + (random.nextDouble() - 0.5);
               double y = pos.getCenter().y() - 0.35;
               double z = pos.getCenter().z() + (random.nextDouble() - 0.5);
               clientLevel.addAlwaysVisibleParticle(
                  (ParticleOptions)ModParticleTypes.FIRE_SMOKE.get(), true, x, y, z, 0.0, random.nextDouble() * (double)intenseMod, 0.0
               );
            }
         }
      }
   }

   protected MapCodec<? extends PMWFireBlock> codec() {
      return CODEC;
   }

   public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
      int intensity = (Integer)state.getValue(INTENSITY);
      if (--intensity <= 0) {
         return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
      } else {
         level.setBlockAndUpdate(pos, (BlockState)state.setValue(INTENSITY, intensity));
         if (player.getMainHandItem().isEmpty()) {
            DamageSource damageSource = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PUNCH_FIRE_DAMAGE));
            player.hurt(damageSource, 1.0F);
         }

         return false;
      }
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(INTENSITY, 1);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{INTENSITY});
   }

   protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
      int intensity = (Integer)state.getValue(INTENSITY);
      if (!entity.fireImmune() && intensity > 2) {
         entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
         if (entity.getRemainingFireTicks() == 0) {
            entity.igniteForSeconds((float)intensity);
         }
      }

      entity.hurt(level.damageSources().inFire(), (float)intensity / 5.0F + 0.5F);
      super.entityInside(state, level, pos, entity);
   }

   @Override
   public int getBurnChance() {
      return 0;
   }

   @Override
   public int getSpreadChance() {
      return 0;
   }

   @Override
   public Block getBurnsInto() {
      return net.minecraft.world.level.block.Blocks.AIR;
   }
}
