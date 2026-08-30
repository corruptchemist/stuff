package com.corruptchemist.lithic.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A tool that is <em>used</em> by a recipe rather than consumed by it: crafting with
 * one returns it, one point of durability poorer, until it finally breaks.
 *
 * <p>This is what stops a knife-in-every-recipe design from becoming a knife-per-craft
 * grind, while still making the knife a real, finite cost.
 */
public class ToolIngredientItem extends Item {

    public ToolIngredientItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack returned = stack.copyWithCount(1);
        if (!returned.isDamageableItem()) {
            return returned;
        }

        int damage = returned.getDamageValue() + 1;
        if (damage >= returned.getMaxDamage()) {
            return ItemStack.EMPTY;
        }
        returned.setDamageValue(damage);
        return returned;
    }
}
