package com.corruptchemist.lithic.block;

import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.recipe.DryingRecipe;
import com.corruptchemist.lithic.registry.LithicBlockEntities;
import com.corruptchemist.lithic.registry.LithicRecipes;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds a single item and slowly turns it into something else. There is no fuel and
 * no player interaction while it runs: the cost is time, which is the one currency
 * a player cannot grind for.
 */
public class DryingRackBlockEntity extends BlockEntity {

    private ItemStack held = ItemStack.EMPTY;
    private int progress;
    private int total;

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(LithicBlockEntities.DRYING_RACK.get(), pos, state);
    }

    public ItemStack getHeld() {
        return this.held;
    }

    public boolean isEmpty() {
        return this.held.isEmpty();
    }

    public boolean isFinished() {
        return !this.held.isEmpty() && this.total > 0 && this.progress >= this.total;
    }

    /**
     * Attempts to place one item on the rack.
     *
     * @return true if an item was consumed from {@code stack}
     */
    public boolean tryInsert(Player player, ItemStack stack) {
        if (this.level == null || !this.held.isEmpty() || stack.isEmpty()) {
            return false;
        }

        ItemStack single = stack.copyWithCount(1);
        Optional<RecipeHolder<DryingRecipe>> found = this.level.getRecipeManager()
                .getRecipeFor(LithicRecipes.DRYING.get(), new SingleRecipeInput(single), this.level);
        if (found.isEmpty()) {
            return false;
        }

        DryingRecipe recipe = found.get().value();
        Optional<ResourceLocation> required = recipe.research();
        if (required.isPresent() && !KnowledgeEvents.knows(player, required.get())) {
            player.displayClientMessage(Component.translatable("lithic.rack.locked")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        this.held = single;
        this.progress = 0;
        this.total = Math.max(1, recipe.time());
        stack.shrink(1);
        this.syncState();
        return true;
    }

    /**
     * Clears the rack without touching the level. Used while the block is being
     * removed, where writing a block state back would be unsafe.
     */
    public ItemStack takeForDrop() {
        ItemStack out = this.held;
        this.held = ItemStack.EMPTY;
        this.progress = 0;
        this.total = 0;
        return out;
    }

    /** Removes whatever is on the rack and hands it to the player. */
    public ItemStack extract() {
        ItemStack out = this.held;
        this.held = ItemStack.EMPTY;
        this.progress = 0;
        this.total = 0;
        this.syncState();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if (rack.held.isEmpty() || rack.total <= 0 || rack.progress >= rack.total) {
            return;
        }

        rack.progress++;
        if (rack.progress < rack.total) {
            rack.setChanged();
            return;
        }

        Optional<RecipeHolder<DryingRecipe>> found = level.getRecipeManager()
                .getRecipeFor(LithicRecipes.DRYING.get(), new SingleRecipeInput(rack.held), level);
        // The datapack can change under a running rack; if the recipe is gone, leave
        // the input alone rather than silently destroying it.
        found.ifPresent(holder -> rack.held = holder.value().assemble(new SingleRecipeInput(rack.held), level.registryAccess()));
        rack.syncState();
    }

    private void syncState() {
        this.setChanged();
        if (this.level == null) {
            return;
        }
        BlockState current = this.level.getBlockState(this.worldPosition);
        if (!(current.getBlock() instanceof DryingRackBlock)) {
            return;
        }
        BlockState updated = current
                .setValue(DryingRackBlock.OCCUPIED, !this.held.isEmpty())
                .setValue(DryingRackBlock.FINISHED, this.isFinished());
        if (updated != current) {
            this.level.setBlock(this.worldPosition, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.held = tag.contains("Held")
                ? ItemStack.parse(registries, tag.getCompound("Held")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        this.progress = tag.getInt("Progress");
        this.total = tag.getInt("Total");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.held.isEmpty()) {
            tag.put("Held", this.held.save(registries));
        }
        tag.putInt("Progress", this.progress);
        tag.putInt("Total", this.total);
    }
}
