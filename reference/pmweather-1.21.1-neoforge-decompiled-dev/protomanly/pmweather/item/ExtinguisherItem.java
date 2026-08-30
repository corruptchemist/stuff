package dev.protomanly.pmweather.item;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.PMWFireBlock;
import dev.protomanly.pmweather.block.interfaces.BurningBlockInterface;
import dev.protomanly.pmweather.particle.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class ExtinguisherItem extends Item {
   public ExtinguisherItem(Properties properties) {
      super(properties);
   }

   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.BOW;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
      ItemStack stack = player.getItemInHand(usedHand);
      int damage = stack.getDamageValue();
      int maxDamage = stack.getMaxDamage();
      if (damage < maxDamage - 1) {
         player.startUsingItem(usedHand);
         return InteractionResultHolder.success(stack);
      } else {
         return InteractionResultHolder.fail(stack);
      }
   }

   public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
      if (livingEntity instanceof Player player) {
         player.awardStat(Stats.ITEM_USED.get(this));
      }
   }

   public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
      int damage = stack.getDamageValue();
      int maxDamage = stack.getMaxDamage();
      if (damage < maxDamage - 1) {
         float damagePerc = (float)damage / (float)maxDamage;
         Vec3 pos = livingEntity.getEyePosition();
         Vec3 fireDir = livingEntity.getLookAngle().scale((double)((1.0F - damagePerc) * 0.5F + 0.25F));
         Vec3 right = livingEntity.getLookAngle().yRot((float) (-Math.PI / 2));
         fireDir = fireDir.offsetRandom(level.random, 0.1F);
         pos = pos.add(right.scale(0.25)).add(0.0, -0.25, 0.0).add(fireDir.scale(0.5));
         level.addParticle((ParticleOptions)ModParticleTypes.FOAM.get(), pos.x(), pos.y(), pos.z(), fireDir.x(), fireDir.y(), fireDir.z());
         if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockHitResult hitResult = level.clip(
               new ClipContext(pos, pos.add(fireDir.scale(30.0)).add(0.0, -4.0, 0.0), Block.COLLIDER, Fluid.ANY, livingEntity)
            );
            if (hitResult.getType() == Type.BLOCK) {
               BlockPos center = hitResult.getBlockPos();

               for (int x = -3; x <= 3; x++) {
                  for (int y = -2; y <= 4; y++) {
                     for (int z = -3; z <= 3; z++) {
                        if (level.random.nextInt(20) == 0) {
                           BlockPos checkPos = center.offset(x, y, z);
                           BlockState state = level.getBlockState(checkPos);
                           if (state.is(Blocks.FIRE)) {
                              level.removeBlock(checkPos, false);
                           } else if (state.is(ModBlocks.FIRE)) {
                              int intensity = (Integer)state.getValue(PMWFireBlock.INTENSITY) - 2;
                              if (intensity <= 0) {
                                 level.removeBlock(checkPos, false);
                              } else {
                                 level.setBlockAndUpdate(checkPos, (BlockState)state.setValue(PMWFireBlock.INTENSITY, intensity));
                              }

                              level.playSound(null, checkPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F + level.random.nextFloat() * 0.4F);
                           } else if (state.getBlock() instanceof BurningBlockInterface burningBlockInterface) {
                              burningBlockInterface.burn(state, serverLevel, checkPos);
                           }
                        }
                     }
                  }
               }
            }
         }

         stack.setDamageValue(damage + 1);
      } else {
         livingEntity.stopUsingItem();
      }
   }

   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      int maxDamage = stack.getMaxDamage();
      return maxDamage + 1;
   }
}
