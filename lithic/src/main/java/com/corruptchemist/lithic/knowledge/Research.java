package com.corruptchemist.lithic.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A single node of the research tree, loaded from {@code data/<ns>/lithic/research/*.json}.
 *
 * <p>A node moves through two states. It is <em>discovered</em> when any of its
 * {@link #triggers} fire, which is what puts it in front of the player at all. It is
 * <em>learned</em> once every id in {@link #parents} is learned and the player spends
 * {@link #insightCost} insight on it. Recipes check only the learned state.
 *
 * @param parents     research that must be learned first
 * @param insightCost insight spent to learn this node
 * @param triggers    the ways this node can be discovered
 * @param hidden      if true, the node is not listed until it has been discovered
 */
public record Research(
        List<ResourceLocation> parents,
        int insightCost,
        List<ResearchTrigger> triggers,
        boolean hidden) {

    public static final Codec<Research> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Research::parents),
            Codec.INT.optionalFieldOf("insight_cost", 1).forGetter(Research::insightCost),
            ResearchTrigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(Research::triggers),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Research::hidden)
    ).apply(inst, Research::new));

    /** Translation key for this node's display name. */
    public static String titleKey(ResourceLocation id) {
        return "research." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** Translation key for this node's flavour/description text. */
    public static String descriptionKey(ResourceLocation id) {
        return titleKey(id) + ".desc";
    }
}
