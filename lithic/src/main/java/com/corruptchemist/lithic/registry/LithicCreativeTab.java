package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Lithic.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithic"))
            .icon(() -> new ItemStack(LithicItems.HAND_AXE.get()))
            .displayItems((params, output) -> {
                // roughly progression order, so the tab reads as the tech tree
                output.accept(LithicItems.PLANT_FIBRE.get());
                output.accept(LithicItems.CORDAGE.get());
                output.accept(LithicItems.FLINT_SHARD.get());
                output.accept(LithicItems.FLINT_BLADE.get());
                output.accept(LithicItems.HAND_AXE.get());
                output.accept(LithicItems.CRUDE_KNIFE.get());
                output.accept(LithicItems.DIGGING_STICK.get());
                output.accept(LithicItems.BARK.get());
                output.accept(LithicItems.ROUGH_LOG.get());
                output.accept(LithicItems.SPLIT_WOOD.get());
                output.accept(LithicItems.TINDER.get());
                output.accept(LithicItems.FIRE_DRILL.get());
                output.accept(LithicItems.KNAPPING_SITE.get());
                output.accept(LithicItems.CHOPPING_BLOCK.get());
                output.accept(LithicItems.DRYING_RACK.get());
                output.accept(LithicItems.FIRE_PIT.get());
            })
            .build());

    /** Hook kept for pack authors; nothing injected into vanilla tabs yet. */
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {}

    private LithicCreativeTab() {}
}
