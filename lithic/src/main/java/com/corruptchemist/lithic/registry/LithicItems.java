package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.item.ToolIngredientItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ages 0-4 only. Every item here exists to make the road to a plank long.
 */
public final class LithicItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Lithic.MOD_ID);

    // --- Age 0: what bare hands can gather -----------------------------------
    public static final DeferredItem<Item> PLANT_FIBRE = ITEMS.registerSimpleItem("plant_fibre");

    // --- Age 1: flint --------------------------------------------------------
    public static final DeferredItem<Item> FLINT_SHARD = ITEMS.registerSimpleItem("flint_shard");
    public static final DeferredItem<Item> FLINT_BLADE = ITEMS.registerSimpleItem("flint_blade");

    // --- Age 2: cordage and hafting ------------------------------------------
    public static final DeferredItem<Item> CORDAGE = ITEMS.registerSimpleItem("cordage");

    /** The first tool. Being an AxeItem is what lets it satisfy the log gate. */
    public static final DeferredItem<AxeItem> HAND_AXE = ITEMS.registerItem("hand_axe",
            props -> new AxeItem(LithicTiers.CRUDE, props));

    /** A striking tool: the first thing that can shift stone at all. */
    public static final DeferredItem<PickaxeItem> DIGGING_STICK = ITEMS.registerItem("digging_stick",
            props -> new PickaxeItem(LithicTiers.CRUDE, props));

    /** Used by recipes rather than eaten by them; comes back one point blunter. */
    public static final DeferredItem<ToolIngredientItem> CRUDE_KNIFE = ITEMS.registerItem("crude_knife",
            ToolIngredientItem::new, new Item.Properties().durability(48));

    // --- Age 3: wood, in stages ----------------------------------------------
    public static final DeferredItem<Item> BARK = ITEMS.registerSimpleItem("bark");
    public static final DeferredItem<Item> ROUGH_LOG = ITEMS.registerSimpleItem("rough_log");
    public static final DeferredItem<Item> SPLIT_WOOD = ITEMS.registerSimpleItem("split_wood");

    // --- Age 4: fire ---------------------------------------------------------
    public static final DeferredItem<Item> TINDER = ITEMS.registerSimpleItem("tinder");

    /** Right-click a Fire Pit with this to try to light it. Can fail. */
    public static final DeferredItem<Item> FIRE_DRILL = ITEMS.registerSimpleItem("fire_drill",
            new Item.Properties().durability(24));

    // --- block items ---------------------------------------------------------
    public static final DeferredItem<BlockItem> KNAPPING_SITE =
            ITEMS.registerSimpleBlockItem("knapping_site", LithicBlocks.KNAPPING_SITE);
    public static final DeferredItem<BlockItem> CHOPPING_BLOCK =
            ITEMS.registerSimpleBlockItem("chopping_block", LithicBlocks.CHOPPING_BLOCK);
    public static final DeferredItem<BlockItem> FIRE_PIT =
            ITEMS.registerSimpleBlockItem("fire_pit", LithicBlocks.FIRE_PIT);
    public static final DeferredItem<BlockItem> DRYING_RACK =
            ITEMS.registerSimpleBlockItem("drying_rack", LithicBlocks.DRYING_RACK);

    private LithicItems() {}
}
