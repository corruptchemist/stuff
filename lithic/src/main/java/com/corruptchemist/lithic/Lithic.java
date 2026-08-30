package com.corruptchemist.lithic;

import com.corruptchemist.lithic.command.LithicCommands;
import com.corruptchemist.lithic.compat.ToughAsNailsCompat;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.ResearchManager;
import com.corruptchemist.lithic.registry.LithicAttachments;
import com.corruptchemist.lithic.registry.LithicBlockEntities;
import com.corruptchemist.lithic.registry.LithicBlocks;
import com.corruptchemist.lithic.registry.LithicCreativeTab;
import com.corruptchemist.lithic.registry.LithicItems;
import com.corruptchemist.lithic.registry.LithicMenus;
import com.corruptchemist.lithic.registry.LithicRecipes;
import com.corruptchemist.lithic.world.KnappingHandler;
import com.corruptchemist.lithic.world.ToolGatingHandler;
import com.corruptchemist.lithic.world.WorldRuleHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Lithic: a progression overhaul built on the idea that the player should have
 * to <em>understand</em> a thing before they are allowed to make it.
 *
 * <p>Three systems carry the difficulty:
 * <ul>
 *   <li>{@link com.corruptchemist.lithic.knowledge} &mdash; research gating. Recipes
 *       refuse to resolve until the player has learned the matching research node.</li>
 *   <li>{@link com.corruptchemist.lithic.world} &mdash; world rules. Bare hands take
 *       nothing from a tree; the wrong tool class takes nothing from stone.</li>
 *   <li>{@link com.corruptchemist.lithic.compat} &mdash; Tough As Nails integration.
 *       Thirst and temperature are load-bearing, not decoration.</li>
 * </ul>
 */
@Mod(Lithic.MOD_ID)
public class Lithic {
    public static final String MOD_ID = "lithic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Lithic(IEventBus modBus, ModContainer container) {
        LithicItems.ITEMS.register(modBus);
        LithicBlocks.BLOCKS.register(modBus);
        LithicBlockEntities.BLOCK_ENTITIES.register(modBus);
        LithicMenus.MENUS.register(modBus);
        LithicRecipes.RECIPE_TYPES.register(modBus);
        LithicRecipes.RECIPE_SERIALIZERS.register(modBus);
        LithicAttachments.ATTACHMENTS.register(modBus);
        LithicCreativeTab.TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(LithicCreativeTab::buildContents);

        NeoForge.EVENT_BUS.register(new KnowledgeEvents());
        NeoForge.EVENT_BUS.register(new ToolGatingHandler());
        NeoForge.EVENT_BUS.register(new KnappingHandler());
        NeoForge.EVENT_BUS.register(new LithicCommands());
        NeoForge.EVENT_BUS.register(new WorldRuleHandler());
        NeoForge.EVENT_BUS.register(new ResearchManager.Reloading());

        container.registerConfig(ModConfig.Type.COMMON, LithicConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Tough As Nails' helper singletons are populated during its own setup, so
        // touching them has to be deferred onto the synchronous work queue.
        event.enqueueWork(ToughAsNailsCompat::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
