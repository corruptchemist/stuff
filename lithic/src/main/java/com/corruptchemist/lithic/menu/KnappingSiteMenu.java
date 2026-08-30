package com.corruptchemist.lithic.menu;

import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.Research;
import com.corruptchemist.lithic.knowledge.ResearchTrigger;
import com.corruptchemist.lithic.recipe.GatedCraftingRecipe;
import com.corruptchemist.lithic.registry.LithicBlocks;
import com.corruptchemist.lithic.registry.LithicMenus;
import com.corruptchemist.lithic.registry.LithicRecipes;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A 3x3 crafting menu that only resolves {@link GatedCraftingRecipe}s, and only
 * those whose research the crafting player has actually learned.
 *
 * <p>The check lives here rather than in the recipe because this is the first place
 * in the chain where a {@link Player} exists. It runs on the logical server, so a
 * modified client cannot craft its way past a gate.
 */
public class KnappingSiteMenu extends AbstractContainerMenu {

    private static final int RESULT_SLOT = 0;
    private static final int CRAFT_START = 1;
    private static final int CRAFT_END = 10;      // exclusive
    private static final int INVENTORY_START = 10;
    private static final int INVENTORY_END = 37;  // exclusive
    private static final int HOTBAR_START = 37;
    private static final int HOTBAR_END = 46;     // exclusive

    private final CraftGrid craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final Slot resultSlot;

    /** Last gate we told the player about, so the hint is not repeated every tick. */
    private @Nullable ResourceLocation announcedGate;

    /**
     * Client-side factory, invoked through {@code IContainerFactory}.
     *
     * <p>Deliberately a static method rather than a third constructor: an overload
     * pair separated only by the type of the last parameter is an easy place to
     * introduce an accidental self-delegation, and the failure mode is a stack
     * overflow the moment a player opens the block.
     */
    public static KnappingSiteMenu forClient(int containerId, Inventory inventory, @Nullable RegistryFriendlyByteBuf data) {
        return new KnappingSiteMenu(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public KnappingSiteMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(LithicMenus.CRUDE_WORKBENCH.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        this.craftSlots = new CraftGrid(this);

        this.resultSlot = new Slot(this.resultSlots, 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                stack.onCraftedBy(taker.level(), taker, stack.getCount());
                KnowledgeEvents.fireItem(taker, ResearchTrigger.TriggerType.CRAFT_ITEM, stack);
                KnowledgeEvents.fireItem(taker, ResearchTrigger.TriggerType.OBTAIN_ITEM, stack);
                KnappingSiteMenu.this.consumeIngredients(taker);
                KnappingSiteMenu.this.slotsChanged(KnappingSiteMenu.this.craftSlots);
            }
        };
        this.addSlot(this.resultSlot);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    // ------------------------------------------------------------ crafting ----

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((level, pos) -> {
            if (container == this.craftSlots) {
                this.refreshResult(level);
            }
        });
        super.slotsChanged(container);
    }

    private void refreshResult(Level level) {
        if (level.isClientSide()) {
            return;
        }

        CraftingInput input = this.craftSlots.asCraftingInput();
        Optional<RecipeHolder<GatedCraftingRecipe>> found = level.getRecipeManager()
                .getRecipeFor(LithicRecipes.CRUDE_CRAFTING.get(), input, level);

        if (found.isEmpty()) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.announcedGate = null;
            return;
        }

        GatedCraftingRecipe recipe = found.get().value();
        Optional<ResourceLocation> required = recipe.research();

        if (required.isPresent() && !KnowledgeEvents.knows(this.player, required.get())) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.hintGate(required.get());
            return;
        }

        this.announcedGate = null;
        this.resultSlots.setItem(0, recipe.assemble(input, level.registryAccess()));
    }

    /**
     * Tells the player <em>that</em> they are missing knowledge, and which, without
     * telling them the recipe. Knowing a wall exists is the point; knowing what is
     * behind it is what the research tree is for.
     */
    private void hintGate(ResourceLocation research) {
        if (research.equals(this.announcedGate)) {
            return;
        }
        this.announcedGate = research;
        this.player.displayClientMessage(
                Component.translatable("lithic.workbench.locked",
                        Component.translatable(Research.titleKey(research)))
                        .withStyle(ChatFormatting.DARK_RED),
                true);
    }

    private void consumeIngredients(Player taker) {
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack current = this.craftSlots.getItem(i);
            if (current.isEmpty()) {
                continue;
            }

            ItemStack remainder = current.getCraftingRemainingItem();
            this.craftSlots.removeItem(i, 1);

            if (!remainder.isEmpty()) {
                if (this.craftSlots.getItem(i).isEmpty()) {
                    this.craftSlots.setItem(i, remainder);
                } else if (!taker.getInventory().add(remainder)) {
                    taker.drop(remainder, false);
                }
            }
        }
    }

    // -------------------------------------------------------------- menu ------

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, LithicBlocks.CRUDE_WORKBENCH.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot != this.resultSlot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();

        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(moving, original);
        } else if (index >= INVENTORY_START && index < HOTBAR_END) {
            if (!this.moveItemStackTo(moving, CRAFT_START, CRAFT_END, false)) {
                if (index < HOTBAR_START) {
                    if (!this.moveItemStackTo(moving, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(moving, INVENTORY_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!this.moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (moving.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, moving);
        return original;
    }

    /**
     * A 3x3 container that notifies its menu on change. {@code SimpleContainer} is
     * used rather than {@code TransientCraftingContainer} so that the shrink-to-fit
     * conversion into {@link CraftingInput} stays explicit and local.
     */
    public static class CraftGrid extends SimpleContainer {
        private final AbstractContainerMenu menu;

        public CraftGrid(AbstractContainerMenu menu) {
            super(9);
            this.menu = menu;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.menu.slotsChanged(this);
        }

        public CraftingInput asCraftingInput() {
            NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < 9; i++) {
                items.set(i, this.getItem(i));
            }
            return CraftingInput.of(3, 3, items);
        }
    }
}
