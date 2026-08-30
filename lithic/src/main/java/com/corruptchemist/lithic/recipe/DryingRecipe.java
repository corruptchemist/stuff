package com.corruptchemist.lithic.recipe;

import com.corruptchemist.lithic.registry.LithicRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Passive, slow conversion on a Drying Rack. Time is measured in ticks and is
 * meant to be long; the rack is a way to make the player wait, not a machine.
 */
public record DryingRecipe(
        Ingredient input,
        ItemStack result,
        int time,
        Optional<ResourceLocation> research) implements Recipe<SingleRecipeInput> {

    public static final MapCodec<DryingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC.fieldOf("input").forGetter(DryingRecipe::input),
            ItemStack.CODEC.fieldOf("result").forGetter(DryingRecipe::result),
            Codec.INT.optionalFieldOf("time", 2400).forGetter(DryingRecipe::time),
            ResourceLocation.CODEC.optionalFieldOf("required_research").forGetter(DryingRecipe::research)
    ).apply(inst, DryingRecipe::new));

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.createWithCapacity(1);
        out.add(this.input);
        return out;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LithicRecipes.DRYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return LithicRecipes.DRYING.get();
    }

    public static class Serializer implements RecipeSerializer<DryingRecipe> {
        private static final StreamCodec<RegistryFriendlyByteBuf, DryingRecipe> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

        @Override
        public MapCodec<DryingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DryingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
