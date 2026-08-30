package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.PMWeather;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class IcicleBlock extends Block implements Fallable {
   public static final DirectionProperty DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
   public static final IntegerProperty SUPPORTING = IntegerProperty.create("supporting", 0, 3);
   public static final EnumProperty<IcicleBlock.IcicleType> ICICLE_TYPE = EnumProperty.create("icicle_type", IcicleBlock.IcicleType.class);
   public static final ResourceKey<DamageType> FALL_ON_ICICLE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, PMWeather.getPath("fall_on_icicle"));
   public static final ResourceKey<DamageType> FALLING_ICICLE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, PMWeather.getPath("falling_icicle"));
   private static final VoxelShape FULL = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   private static final VoxelShape BASE_TIP = Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0);
   private static final VoxelShape BASE_TIP_DOWN = Block.box(0.0, 5.0, 0.0, 16.0, 16.0, 16.0);
   private static final VoxelShape MIDDLE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
   private static final VoxelShape FRUSTUM = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
   private static final VoxelShape TIP = Block.box(6.0, 0.0, 6.0, 10.0, 11.0, 10.0);
   private static final VoxelShape TIP_DOWN = Block.box(6.0, 5.0, 6.0, 10.0, 16.0, 10.0);
   private static final VoxelShape MERGED = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

   public IcicleBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.defaultBlockState().setValue(DIRECTION, Direction.DOWN)).setValue(ICICLE_TYPE, IcicleBlock.IcicleType.BASE))
            .setValue(SUPPORTING, 0)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{DIRECTION, SUPPORTING, ICICLE_TYPE});
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      IcicleBlock.IcicleType type = (IcicleBlock.IcicleType)state.getValue(ICICLE_TYPE);
      Direction direction = (Direction)state.getValue(DIRECTION);
      int supports = (Integer)state.getValue(SUPPORTING);
      VoxelShape shape;
      if (type == IcicleBlock.IcicleType.BASE) {
         if (supports == 0) {
            shape = direction == Direction.DOWN ? BASE_TIP_DOWN : BASE_TIP;
         } else {
            shape = FULL;
         }
      } else if (type == IcicleBlock.IcicleType.MIDDLE) {
         shape = supports > 1 ? MIDDLE : FRUSTUM;
      } else if (type == IcicleBlock.IcicleType.TIP) {
         shape = direction == Direction.DOWN ? TIP_DOWN : TIP;
      } else if (type == IcicleBlock.IcicleType.MERGED) {
         shape = MERGED;
      } else {
         shape = FULL;
      }

      Vec3 offset = state.getOffset(level, pos);
      return shape.move(offset.x, 0.0, offset.z);
   }

   protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
      return false;
   }

   protected float getMaxHorizontalOffset() {
      return 0.125F;
   }

   public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
      if (!fallingBlock.isSilent()) {
         level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, level.random.nextFloat() * 0.1F + 0.9F);
      }
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      LevelAccessor levelaccessor = context.getLevel();
      BlockPos blockpos = context.getClickedPos();
      Direction direction = context.getNearestLookingVerticalDirection().getOpposite();
      Direction direction1 = calculateDirection(levelaccessor, blockpos, direction);
      return direction1 == null ? null : calculateIcicleState(levelaccessor, blockpos, (BlockState)this.defaultBlockState().setValue(DIRECTION, direction1));
   }

   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return isValidIciclePlacement(level, pos, (Direction)state.getValue(DIRECTION));
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      int chanceGrow = 20;
      float temp = this.getTemperature(level, pos);
      if (random.nextInt(chanceGrow * 3) == 0 && level.getBlockState(pos.below()).isEmpty() && this.canGrow(level, pos, state, temp)) {
         for (int y = 1; y < 6; y++) {
            BlockPos belowPos = pos.below(y);
            BlockState belowState = level.getBlockState(belowPos);
            if (!belowState.canBeReplaced() && !belowState.isAir()) {
               break;
            }

            if (this.canSurvive((BlockState)this.defaultBlockState().setValue(DIRECTION, Direction.UP), level, belowPos)) {
               level.setBlockAndUpdate(belowPos, calculateIcicleState(level, belowPos, (BlockState)this.defaultBlockState().setValue(DIRECTION, Direction.UP)));
               return;
            }
         }
      }

      if (random.nextInt(chanceGrow) == 0
         && level.getBlockState(pos.below()).isEmpty()
         && this.canSurvive((BlockState)this.defaultBlockState().setValue(DIRECTION, Direction.DOWN), level, pos.below())
         && this.canGrow(level, pos, state, temp)) {
         level.setBlockAndUpdate(
            pos.below(), calculateIcicleState(level, pos.below(), (BlockState)this.defaultBlockState().setValue(DIRECTION, Direction.DOWN))
         );
      } else if (!(temp <= 0.0F)) {
         int chance = 5;
         if (temp > 0.5F) {
            chance = 4;
         }

         if (temp > 2.5F) {
            chance = 2;
         }

         if (temp > 5.0F) {
            chance = 1;
         }

         if (random.nextInt(chance) == 0) {
            level.removeBlock(pos, false);
         }
      }
   }

   protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
      if (direction != Direction.UP && direction != Direction.DOWN) {
         return state;
      } else {
         Direction dir = (Direction)state.getValue(DIRECTION);
         if (dir == Direction.DOWN && level.getBlockTicks().hasScheduledTick(pos, this)) {
            return state;
         } else if (direction == dir.getOpposite() && !this.canSurvive(state, level, pos)) {
            if (direction == Direction.DOWN) {
               level.scheduleTick(pos, this, 2);
            } else {
               level.scheduleTick(pos, this, 1);
            }

            return state;
         } else {
            return calculateIcicleState(level, pos, state);
         }
      }
   }

   private static Direction calculateDirection(LevelReader level, BlockPos pos, Direction dir) {
      Direction direction;
      if (isValidIciclePlacement(level, pos, dir)) {
         direction = dir;
      } else {
         if (!isValidIciclePlacement(level, pos, dir.getOpposite())) {
            return null;
         }

         direction = dir.getOpposite();
      }

      return direction;
   }

   protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
      if (!level.isClientSide) {
         BlockPos pos = hit.getBlockPos();
         if (projectile.mayInteract(level, pos) && projectile.mayBreak(level) && projectile.getDeltaMovement().length() > 0.1) {
            level.destroyBlock(pos, false);
         }
      }
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      if (state.getValue(DIRECTION) == Direction.UP) {
         DamageSource damageSource = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALL_ON_ICICLE_DAMAGE));
         entity.causeFallDamage(fallDistance + 2.0F, 2.0F, damageSource);
      } else {
         super.fallOn(level, state, pos, entity, fallDistance);
      }
   }

   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      super.animateTick(state, level, pos, random);
      if (level.isClientSide && state.getValue(DIRECTION) != Direction.UP) {
         if (GameBusClientEvents.weatherHandler instanceof WeatherHandlerClient weatherHandlerClient) {
            float temp = weatherHandlerClient.cachedPlayerTemp;
            if (temp > 0.0F) {
               float chance = Mth.sqrt(Mth.clamp(temp / 10.0F, 0.0F, 1.0F));
               if (random.nextFloat() < chance) {
                  Vec3 v3pos = pos.getBottomCenter().add((double)((random.nextFloat() - 0.5F) * 0.2F), 0.0, (double)((random.nextFloat() - 0.5F) * 0.2F));
                  level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_WATER, v3pos.x, v3pos.y, v3pos.z, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (state.getValue(DIRECTION) == Direction.UP && !this.canSurvive(state, level, pos)) {
         level.destroyBlock(pos, false);
      } else {
         spawnFallingIcicle(state, level, pos);
      }
   }

   private static void spawnFallingIcicle(BlockState state, ServerLevel level, BlockPos pos) {
      MutableBlockPos mutableBlockPos = pos.mutable();
      BlockState blockState = state;

      while (blockState.is(ModBlocks.ICICLE) && blockState.getValue(DIRECTION) == Direction.DOWN) {
         FallingBlockEntity fallingBlockEntity = FallingBlockEntity.fall(level, mutableBlockPos, blockState);
         fallingBlockEntity.setHurtsEntities(6.0F, 40);
         fallingBlockEntity.disableDrop();
         mutableBlockPos.move(Direction.DOWN);
         blockState = level.getBlockState(mutableBlockPos);
      }
   }

   public DamageSource getFallDamageSource(Entity entity) {
      return new DamageSource(entity.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALLING_ICICLE_DAMAGE));
   }

   private float getTemperature(Level level, BlockPos pos) {
      return ThermodynamicEngine.samplePoint(GameBusEvents.MANAGERS.get(level.dimension()), pos.getCenter(), level, null, 0).temperature();
   }

   public boolean canGrow(Level level, BlockPos pos, BlockState state, @Nullable Float temp) {
      if (level.isClientSide()) {
         return false;
      } else if (state.getValue(DIRECTION) == Direction.UP) {
         return false;
      } else {
         if (temp == null) {
            temp = this.getTemperature(level, pos);
         }

         if (temp > 2.5F) {
            return false;
         } else {
            WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
            float precip = weatherHandler.getPrecipitation(pos.getCenter());
            return precip < 0.05F ? false : level.getBrightness(LightLayer.SKY, pos) >= 13;
         }
      }
   }

   private static BlockState calculateIcicleState(LevelAccessor level, BlockPos pos, BlockState state) {
      Direction direction = (Direction)state.getValue(DIRECTION);
      Direction opposite = direction.getOpposite();
      BlockState nextState = level.getBlockState(pos.relative(direction));
      BlockState lastState = level.getBlockState(pos.relative(opposite));
      IcicleBlock.IcicleType type = IcicleBlock.IcicleType.BASE;
      boolean betweenIcicles = true;
      if (lastState.is(ModBlocks.ICICLE)) {
         type = IcicleBlock.IcicleType.TIP;
      } else {
         betweenIcicles = false;
      }

      if (nextState.is(ModBlocks.ICICLE)) {
         if (type == IcicleBlock.IcicleType.TIP) {
            type = IcicleBlock.IcicleType.MIDDLE;
         }
      } else {
         betweenIcicles = false;
      }

      int supports = 0;

      for (int y = 1; y < 4; supports = y++) {
         BlockPos checkPos = pos.relative(direction, y);
         BlockState checkState = level.getBlockState(checkPos);
         if (!checkState.is(ModBlocks.ICICLE) || checkState.getValue(DIRECTION) != direction) {
            break;
         }
      }

      boolean var13 = betweenIcicles || direction == Direction.UP && type == IcicleBlock.IcicleType.BASE && supports == 0 && nextState.is(ModBlocks.ICICLE);
      if (var13 && nextState.getValue(DIRECTION) != direction) {
         type = IcicleBlock.IcicleType.MERGED;
      }

      return (BlockState)((BlockState)state.setValue(ICICLE_TYPE, type)).setValue(SUPPORTING, supports);
   }

   public static boolean isValidIciclePlacement(LevelReader level, BlockPos pos, Direction dir) {
      BlockPos supportedBy = pos.relative(dir.getOpposite());
      BlockState supportedByState = level.getBlockState(supportedBy);
      int supportCount = 0;

      for (int y = 1; y < 5; supportCount = y++) {
         BlockPos checkPos = pos.relative(dir.getOpposite(), y);
         BlockState checkState = level.getBlockState(checkPos);
         if (!checkState.is(ModBlocks.ICICLE)) {
            break;
         }
      }

      return supportCount >= 4
         ? false
         : supportedByState.isFaceSturdy(level, supportedBy, dir) || supportedByState.is(ModBlocks.ICICLE) && supportedByState.getValue(DIRECTION) == dir;
   }

   public static enum IcicleType implements StringRepresentable {
      BASE("base"),
      MIDDLE("middle"),
      TIP("tip"),
      MERGED("merged");

      private final String name;

      private IcicleType(String name) {
         this.name = name;
      }

      @Override
      public String toString() {
         return this.name;
      }

      public String getSerializedName() {
         return this.name;
      }
   }
}
