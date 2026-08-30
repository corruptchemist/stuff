package com.corruptchemist.lithic.recipe;

import com.corruptchemist.lithic.registry.LithicRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A crafting recipe for the Crude Workbench that may carry a research requirement.
 *
 * <p>Note that the requirement is <em>not</em> checked here: {@link Recipe#matches}
 * has no player to check it against. The recipe merely advertises what it needs via
 * {@link #research()}, and {@link com.corruptchemist.lithic.menu.KnappingSiteMenu}
 * enforces it at the point where a player is actually in scope. Doing it this way
 * keeps the check server-authoritative and avoids any mixin into vanilla crafting.
 *
 * <p>Supports both shaped ({@code pattern} + {@code key}) and shapeless
 * ({@code ingredients}) forms; the presence of {@code pattern} selects between them.
 */
public class GatedCraftingRecipe implements Recipe<CraftingInput> {

    private final Spec spec;

    /** Trimmed shaped grid, row-major, exactly {@code width * height} entries. */
    private final NonNullList<Ingredient> grid;
    private final List<Ingredient> loose;
    private final int width;
    private final int height;
    private final boolean shapeless;

    public GatedCraftingRecipe(Spec spec) {
        this.spec = spec;
        this.shapeless = spec.pattern().isEmpty();

        if (this.shapeless) {
            if (spec.ingredients().isEmpty()) {
                throw new IllegalArgumentException("A shapeless crude recipe needs a non-empty 'ingredients' list");
            }
            this.loose = List.copyOf(spec.ingredients());
            this.grid = NonNullList.create();
            this.width = 0;
            this.height = 0;
        } else {
            this.loose = List.of();
            Trimmed trimmed = trim(spec.pattern(), spec.key());
            this.grid = trimmed.grid();
            this.width = trimmed.width();
            this.height = trimmed.height();
        }
    }

    public Spec spec() {
        return this.spec;
    }

    /** The research a player must have learned before this recipe will resolve. */
    public Optional<ResourceLocation> research() {
        return this.spec.research();
    }

    // ------------------------------------------------------------ matching ----

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.shapeless ? this.matchesShapeless(input) : this.matchesShaped(input);
    }

    private boolean matchesShaped(CraftingInput input) {
        if (input.width() != this.width || input.height() != this.height) {
            return false;
        }
        if (this.matchesShapedAt(input, false)) {
            return true;
        }
        return this.spec.mirrored() && this.matchesShapedAt(input, true);
    }

    private boolean matchesShapedAt(CraftingInput input, boolean mirror) {
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int column = mirror ? this.width - 1 - x : x;
                Ingredient ingredient = this.grid.get(column + y * this.width);
                if (!ingredient.test(input.getItem(x + y * this.width))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(CraftingInput input) {
        List<ItemStack> present = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                present.add(stack);
            }
        }
        if (present.size() != this.loose.size()) {
            return false;
        }

        boolean[] consumed = new boolean[present.size()];
        for (Ingredient ingredient : this.loose) {
            boolean matched = false;
            for (int i = 0; i < present.size(); i++) {
                if (!consumed[i] && ingredient.test(present.get(i))) {
                    consumed[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- recipe -----

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.spec.result().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return this.shapeless
                ? this.loose.size() <= width * height
                : this.width <= width && this.height <= height;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.spec.result();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        if (!this.shapeless) {
            return this.grid;
        }
        NonNullList<Ingredient> out = NonNullList.createWithCapacity(this.loose.size());
        out.addAll(this.loose);
        return out;
    }

    @Override
    public String getGroup() {
        return this.spec.group();
    }

    /**
     * Keeps gated recipes out of the vanilla recipe book, which would otherwise
     * happily advertise things the player has not earned the right to make.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LithicRecipes.CRUDE_CRAFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return LithicRecipes.CRUDE_CRAFTING.get();
    }

    // ------------------------------------------------------------ trimming ----

    private record Trimmed(NonNullList<Ingredient> grid, int width, int height) {}

    /**
     * Reduces an authored pattern to its minimal bounding box, mirroring what
     * vanilla's shaped recipes do. This matters because {@link CraftingInput} is
     * itself delivered pre-shrunk, so an untrimmed pattern would never match.
     */
    private static Trimmed trim(List<String> pattern, Map<String, Ingredient> key) {
        int rows = pattern.size();
        int cols = pattern.stream().mapToInt(String::length).max().orElse(0);
        if (rows == 0 || cols == 0) {
            throw new IllegalArgumentException("Crude recipe pattern is empty");
        }

        int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int y = 0; y < rows; y++) {
            String row = pattern.get(y);
            for (int x = 0; x < cols; x++) {
                char c = x < row.length() ? row.charAt(x) : ' ';
                if (c == ' ') continue;
                minRow = Math.min(minRow, y);
                maxRow = Math.max(maxRow, y);
                minCol = Math.min(minCol, x);
                maxCol = Math.max(maxCol, x);
            }
        }
        if (maxRow < 0) {
            throw new IllegalArgumentException("Crude recipe pattern contains no ingredients");
        }

        int width = maxCol - minCol + 1;
        int height = maxRow - minRow + 1;
        NonNullList<Ingredient> grid = NonNullList.withSize(width * height, Ingredient.EMPTY);

        for (int y = 0; y < height; y++) {
            String row = pattern.get(minRow + y);
            for (int x = 0; x < width; x++) {
                int sourceCol = minCol + x;
                char c = sourceCol < row.length() ? row.charAt(sourceCol) : ' ';
                if (c == ' ') continue;

                Ingredient ingredient = key.get(String.valueOf(c));
                if (ingredient == null) {
                    throw new IllegalArgumentException("Crude recipe pattern uses key '" + c + "' with no definition");
                }
                grid.set(x + y * width, ingredient);
            }
        }
        return new Trimmed(grid, width, height);
    }

    // --------------------------------------------------------------- spec -----

    /**
     * @param research  research id the crafter must have learned, if any
     * @param pattern   shaped rows; empty selects the shapeless form
     * @param mirrored  whether the shaped form also matches left-right flipped
     */
    public record Spec(
            String group,
            Optional<ResourceLocation> research,
            List<String> pattern,
            Map<String, Ingredient> key,
            List<Ingredient> ingredients,
            ItemStack result,
            boolean mirrored) {

        public static final MapCodec<Spec> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(Spec::group),
                ResourceLocation.CODEC.optionalFieldOf("required_research").forGetter(Spec::research),
                Codec.STRING.listOf().optionalFieldOf("pattern", List.of()).forGetter(Spec::pattern),
                Codec.unboundedMap(Codec.STRING, Ingredient.CODEC).optionalFieldOf("key", Map.of()).forGetter(Spec::key),
                Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(Spec::ingredients),
                ItemStack.CODEC.fieldOf("result").forGetter(Spec::result),
                Codec.BOOL.optionalFieldOf("mirrored", true).forGetter(Spec::mirrored)
        ).apply(inst, Spec::new));
    }

    public static class Serializer implements RecipeSerializer<GatedCraftingRecipe> {
        private static final MapCodec<GatedCraftingRecipe> CODEC =
                Spec.CODEC.xmap(GatedCraftingRecipe::new, GatedCraftingRecipe::spec);
        private static final StreamCodec<RegistryFriendlyByteBuf, GatedCraftingRecipe> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

        @Override
        public MapCodec<GatedCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GatedCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
