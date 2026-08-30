package dev.protomanly.pmweather.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.DeferredRegister.Items;

public class ModItems {
   public static final Items ITEMS = DeferredRegister.createItems("pmweather");
   public static final DeferredItem<Item> FIRE_EXTINGUISHER = ITEMS.register(
      "fire_extinguisher", () -> new ExtinguisherItem(new Properties().stacksTo(1).durability(400))
   );
   public static final DeferredItem<Item> ASH = ITEMS.register("ash", () -> new BoneMealItem(new Properties().fireResistant()));
   public static final DeferredItem<Item> CONNECTOR = ITEMS.register("connector", () -> new ConnectorItem(new Properties().stacksTo(1)));
   public static final DeferredItem<Item> WEATHER_BALLOON = ITEMS.register(
      "weather_balloon", () -> new HintItem(Component.translatable("tooltip.pmweather.weather_balloon"), new Properties().stacksTo(8))
   );
   public static final DeferredItem<Item> CALENDAR = ITEMS.register("calendar", () -> new CalendarItem(new Properties().stacksTo(1)));
   public static final DeferredItem<Item> APPLE = ITEMS.register(
      "chip", () -> new HintItem(Component.translatable("tooltip.pmweather.orange"), new Properties().stacksTo(63))
   );

   public ModItems() {
   }
}
