package dev.protomanly.pmweather.item;

import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class CalendarItem extends Item {
   public CalendarItem(Properties properties) {
      super(properties);
   }

   public boolean isBarVisible(ItemStack stack) {
      return ServerConfig.monthLength > 1;
   }

   public int getBarColor(ItemStack stack) {
      return 1585970944;
   }

   @OnlyIn(Dist.CLIENT)
   public int getBarWidth(ItemStack stack) {
      if (ServerConfig.monthLength <= 1) {
         return 0;
      } else {
         ClientLevel level = Minecraft.getInstance().level;
         Player player = Minecraft.getInstance().player;
         if (player != null && level != null) {
            if (!player.getInventory().contains(stack) && !ItemStack.matches(stack, player.containerMenu.getCarried())) {
               return GameBusClientEvents.RandomMonth;
            } else {
               int day = ServerConfig.monthLength - (SeasonHandler.getDayInMonth(level) - 1);
               return Math.round(13.0F - (float)day * 13.0F / (float)ServerConfig.monthLength);
            }
         } else {
            return 0;
         }
      }
   }

   @OnlyIn(Dist.CLIENT)
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
      ClientLevel level = Minecraft.getInstance().level;
      Player player = Minecraft.getInstance().player;
      if (player != null && level != null) {
         if (!player.getInventory().contains(stack)) {
            tooltipComponents.add(Component.literal("? ?, ?").withColor(this.getBarColor(stack)).withStyle(ChatFormatting.OBFUSCATED));
         } else {
            tooltipComponents.add(
               Component.literal(SeasonHandler.getMonthName(SeasonHandler.getMonth(level)))
                  .append(" ")
                  .append(String.valueOf(SeasonHandler.getDayInMonth(level)))
                  .append(", ")
                  .append(Component.translatable("calendar.pmweather.year", new Object[]{SeasonHandler.getYear(level)}))
                  .withColor(this.getBarColor(stack))
            );
         }
      }
   }
}
