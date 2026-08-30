package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.block.ContemplationStoneBlock;
import com.corruptchemist.lithic.block.CrudeWorkbenchBlock;
import com.corruptchemist.lithic.block.DryingRackBlock;
import com.corruptchemist.lithic.block.FirePitBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Lithic.MOD_ID);

    public static final DeferredBlock<CrudeWorkbenchBlock> CRUDE_WORKBENCH = BLOCKS.registerBlock(
            "crude_workbench",
            CrudeWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<DryingRackBlock> DRYING_RACK = BLOCKS.registerBlock(
            "drying_rack",
            DryingRackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    public static final DeferredBlock<ContemplationStoneBlock> CONTEMPLATION_STONE = BLOCKS.registerBlock(
            "contemplation_stone",
            ContemplationStoneBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<FirePitBlock> FIRE_PIT = BLOCKS.registerBlock(
            "fire_pit",
            FirePitBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> state.getValue(FirePitBlock.LIT) ? 12 : 0)
                    .noOcclusion());

    private LithicBlocks() {}
}
