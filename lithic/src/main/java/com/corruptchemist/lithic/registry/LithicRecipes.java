package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.recipe.DryingRecipe;
import com.corruptchemist.lithic.recipe.GatedCraftingRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Lithic.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Lithic.MOD_ID);

    public static final Supplier<RecipeType<GatedCraftingRecipe>> CRUDE_CRAFTING =
            RECIPE_TYPES.register("crude_crafting", () -> RecipeType.simple(Lithic.id("crude_crafting")));
    public static final Supplier<RecipeType<DryingRecipe>> DRYING =
            RECIPE_TYPES.register("drying", () -> RecipeType.simple(Lithic.id("drying")));

    public static final Supplier<RecipeSerializer<GatedCraftingRecipe>> CRUDE_CRAFTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("crude_crafting", GatedCraftingRecipe.Serializer::new);
    public static final Supplier<RecipeSerializer<DryingRecipe>> DRYING_SERIALIZER =
            RECIPE_SERIALIZERS.register("drying", DryingRecipe.Serializer::new);

    private LithicRecipes() {}
}
