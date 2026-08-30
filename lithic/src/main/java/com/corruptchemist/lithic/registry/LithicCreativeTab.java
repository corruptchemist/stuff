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
            .icon(() -> new ItemStack(LithicItems.SHARP_FLINT.get()))
            .displayItems((params, output) -> {
                output.accept(LithicItems.PLANT_FIBER.get());
                output.accept(LithicItems.CORDAGE.get());
                output.accept(LithicItems.FLINT_SHARD.get());
                output.accept(LithicItems.SHARP_FLINT.get());
                output.accept(LithicItems.BONE_SPLINTER.get());
                output.accept(LithicItems.REED_BUNDLE.get());
                output.accept(LithicItems.DRIED_REED.get());
                output.accept(LithicItems.RAW_HIDE.get());
                output.accept(LithicItems.CURED_HIDE.get());
                output.accept(LithicItems.HIDE_STRIP.get());
                output.accept(LithicItems.TINDER.get());
                output.accept(LithicItems.FORAGED_ROOTS.get());
                output.accept(LithicItems.CLAY_CRUCIBLE.get());
                output.accept(LithicItems.CRUSHED_ORE.get());
                output.accept(LithicItems.WASHED_ORE.get());
                output.accept(LithicItems.IRON_BLOOM.get());
                output.accept(LithicItems.SLAG.get());
                output.accept(LithicItems.CHARCOAL_DUST.get());
                output.accept(LithicItems.ASH.get());
                output.accept(LithicItems.CRUDE_HATCHET.get());
                output.accept(LithicItems.CRUDE_PICK.get());
                output.accept(LithicItems.CRUDE_KNIFE.get());
                output.accept(LithicItems.TALLY_BONE.get());
                output.accept(LithicItems.CRUDE_WORKBENCH.get());
                output.accept(LithicItems.DRYING_RACK.get());
                output.accept(LithicItems.CONTEMPLATION_STONE.get());
                output.accept(LithicItems.FIRE_PIT.get());
            })
            .build());

    /** Nothing extra to inject into vanilla tabs yet; hook kept for pack authors. */
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {}

    private LithicCreativeTab() {}
}
