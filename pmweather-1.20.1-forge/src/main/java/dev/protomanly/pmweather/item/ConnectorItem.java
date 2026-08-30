package dev.protomanly.pmweather.item;

import dev.protomanly.pmweather.block.entity.WeatherPlatformBlockEntity;
import dev.protomanly.pmweather.item.component.ModComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ConnectorItem extends Item {
   public ConnectorItem(Properties properties) {
      super(properties);
   }

   public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
      super.inventoryTick(stack, level, entity, slotId, isSelected);
      if (stack.has(ModComponents.WEATHER_BALLOON_PLATFORM) && !level.isClientSide()) {
         BlockPos pos = (BlockPos)stack.get(ModComponents.WEATHER_BALLOON_PLATFORM);
         BlockEntity blockEntity = level.getBlockEntity(pos);
         if (!(blockEntity instanceof WeatherPlatformBlockEntity)) {
            stack.remove(ModComponents.WEATHER_BALLOON_PLATFORM);
         }
      }
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
      tooltipComponents.add(Component.translatable("tooltip.pmweather.connector"));
      if (stack.has(ModComponents.WEATHER_BALLOON_PLATFORM)) {
         BlockPos pos = (BlockPos)stack.get(ModComponents.WEATHER_BALLOON_PLATFORM);
         tooltipComponents.add(Component.literal(String.format("Connected to: %s, %s, %s", pos.getX(), pos.getY(), pos.getZ())));
      } else {
         tooltipComponents.add(Component.literal("Unset"));
      }
   }
}
