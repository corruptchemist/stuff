package dev.protomanly.pmweather.multiblock;

import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.item.ModItems;
import dev.protomanly.pmweather.multiblock.wsr88d.WSR88DCore;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.DeferredBlock;

public class MultiBlocks {
   public static final DeferredBlock<Block> WSR88D_CORE = registerBlock(
      "wsr88d_core", () -> new WSR88DCore(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion())
   );

   public MultiBlocks() {
   }

   private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
      DeferredBlock<T> returnBlock = ModBlocks.BLOCKS.register(name, block);
      registerBlockItem(name, returnBlock);
      return returnBlock;
   }

   private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
      ModItems.ITEMS.register(name, () -> new BlockItem((Block)block.get(), new net.minecraft.world.item.Item.Properties()));
   }

   public static void register() {
   }
}
