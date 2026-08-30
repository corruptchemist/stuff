package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.block.ChoppingBlockBlock;
import com.corruptchemist.lithic.block.DryingRackBlock;
import com.corruptchemist.lithic.block.FirePitBlock;
import com.corruptchemist.lithic.block.KnappingSiteBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Lithic.MOD_ID);

    /** The first station, and deliberately craftable without any wood. */
    public static final DeferredBlock<KnappingSiteBlock> KNAPPING_SITE = BLOCKS.registerBlock(
            "knapping_site",
            KnappingSiteBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5F)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<ChoppingBlockBlock> CHOPPING_BLOCK = BLOCKS.registerBlock(
            "chopping_block",
            ChoppingBlockBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<FirePitBlock> FIRE_PIT = BLOCKS.registerBlock(
            "fire_pit",
            FirePitBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> state.getValue(FirePitBlock.LIT) ? 12 : 0)
                    .noOcclusion());

    public static final DeferredBlock<DryingRackBlock> DRYING_RACK = BLOCKS.registerBlock(
            "drying_rack",
            DryingRackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    private LithicBlocks() {}
}
