package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class LithicTags {

    public static final class Blocks {
        /** Cannot be broken at all without something in {@link Items#CUTTING_TOOLS}. */
        public static final TagKey<Block> REQUIRES_CUTTING_TOOL = block("requires_cutting_tool");
        /** Cannot be broken at all without something in {@link Items#STRIKING_TOOLS}. */
        public static final TagKey<Block> REQUIRES_STRIKING_TOOL = block("requires_striking_tool");
        /** Hard surfaces flint can be struck against. */
        public static final TagKey<Block> KNAPPING_SURFACE = block("knapping_surface");

        private static TagKey<Block> block(String path) {
            return TagKey.create(Registries.BLOCK, Lithic.id(path));
        }

        private Blocks() {}
    }

    public static final class Items {
        public static final TagKey<Item> CUTTING_TOOLS = item("cutting_tools");
        public static final TagKey<Item> STRIKING_TOOLS = item("striking_tools");

        private static TagKey<Item> item(String path) {
            return TagKey.create(Registries.ITEM, Lithic.id(path));
        }

        private Items() {}
    }

    private LithicTags() {}
}
