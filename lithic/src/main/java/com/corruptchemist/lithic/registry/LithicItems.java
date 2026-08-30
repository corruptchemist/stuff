package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.item.TallyBoneItem;
import com.corruptchemist.lithic.item.ToolIngredientItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Lithic.MOD_ID);

    // --- gathered by hand -----------------------------------------------------
    public static final DeferredItem<Item> PLANT_FIBER = ITEMS.registerSimpleItem("plant_fiber");
    public static final DeferredItem<Item> FLINT_SHARD = ITEMS.registerSimpleItem("flint_shard");
    public static final DeferredItem<Item> BONE_SPLINTER = ITEMS.registerSimpleItem("bone_splinter");
    public static final DeferredItem<Item> REED_BUNDLE = ITEMS.registerSimpleItem("reed_bundle");
    public static final DeferredItem<Item> RAW_HIDE = ITEMS.registerSimpleItem("raw_hide");

    // --- first processing step ------------------------------------------------
    public static final DeferredItem<Item> CORDAGE = ITEMS.registerSimpleItem("cordage");
    public static final DeferredItem<Item> SHARP_FLINT = ITEMS.registerSimpleItem("sharp_flint");
    public static final DeferredItem<Item> DRIED_REED = ITEMS.registerSimpleItem("dried_reed");
    public static final DeferredItem<Item> CURED_HIDE = ITEMS.registerSimpleItem("cured_hide");
    public static final DeferredItem<Item> HIDE_STRIP = ITEMS.registerSimpleItem("hide_strip");
    public static final DeferredItem<Item> TINDER = ITEMS.registerSimpleItem("tinder");

    // --- the long road to metal ----------------------------------------------
    /** Survives a handful of blooms before it cracks; returned by the recipe, not eaten. */
    public static final DeferredItem<ToolIngredientItem> CLAY_CRUCIBLE = ITEMS.registerItem("clay_crucible",
            ToolIngredientItem::new, new Item.Properties().durability(8));
    public static final DeferredItem<Item> CRUSHED_ORE = ITEMS.registerSimpleItem("crushed_ore");
    public static final DeferredItem<Item> WASHED_ORE = ITEMS.registerSimpleItem("washed_ore");
    public static final DeferredItem<Item> IRON_BLOOM = ITEMS.registerSimpleItem("iron_bloom");
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("slag");
    public static final DeferredItem<Item> CHARCOAL_DUST = ITEMS.registerSimpleItem("charcoal_dust");
    public static final DeferredItem<Item> ASH = ITEMS.registerSimpleItem("ash");

    // --- edible, barely -------------------------------------------------------
    public static final DeferredItem<Item> FORAGED_ROOTS = ITEMS.registerSimpleItem("foraged_roots",
            new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationModifier(0.1F).build()));

    // --- tools ----------------------------------------------------------------
    public static final DeferredItem<AxeItem> CRUDE_HATCHET = ITEMS.registerItem("crude_hatchet",
            props -> new AxeItem(LithicTiers.CRUDE, props));

    public static final DeferredItem<PickaxeItem> CRUDE_PICK = ITEMS.registerItem("crude_pick",
            props -> new PickaxeItem(LithicTiers.CRUDE, props));

    /**
     * Not a digging tool: a cutting edge for hide, reed and fibre work. Recipes that
     * call for it get it back, blunter each time.
     */
    public static final DeferredItem<ToolIngredientItem> CRUDE_KNIFE = ITEMS.registerItem("crude_knife",
            ToolIngredientItem::new, new Item.Properties().durability(48));

    /** Right-click to read out what you know and what you could learn. */
    public static final DeferredItem<TallyBoneItem> TALLY_BONE = ITEMS.registerItem("tally_bone",
            TallyBoneItem::new, new Item.Properties().stacksTo(1));

    // --- block items ----------------------------------------------------------
    public static final DeferredItem<BlockItem> CRUDE_WORKBENCH =
            ITEMS.registerSimpleBlockItem("crude_workbench", LithicBlocks.CRUDE_WORKBENCH);
    public static final DeferredItem<BlockItem> DRYING_RACK =
            ITEMS.registerSimpleBlockItem("drying_rack", LithicBlocks.DRYING_RACK);
    public static final DeferredItem<BlockItem> CONTEMPLATION_STONE =
            ITEMS.registerSimpleBlockItem("contemplation_stone", LithicBlocks.CONTEMPLATION_STONE);
    public static final DeferredItem<BlockItem> FIRE_PIT =
            ITEMS.registerSimpleBlockItem("fire_pit", LithicBlocks.FIRE_PIT);

    private LithicItems() {}
}
