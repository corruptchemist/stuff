package com.corruptchemist.lithic.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Sits below wood: few uses, barely faster than bare hands, and unable to
 * harvest anything a wooden tool could not. It exists to make the stone age
 * long, not to make it comfortable.
 */
public final class LithicTiers {
    public static final Tier CRUDE = new SimpleTier(
            BlockTags.INCORRECT_FOR_WOODEN_TOOL,
            32,     // uses
            1.5F,   // speed; bare hand is 1.0, wood is 2.0
            0.0F,   // no attack bonus - these are not weapons
            1,      // enchantment value
            () -> Ingredient.of(LithicItems.FLINT_BLADE.get()));

    private LithicTiers() {}
}
