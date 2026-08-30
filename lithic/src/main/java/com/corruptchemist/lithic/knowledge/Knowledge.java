package com.corruptchemist.lithic.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-player knowledge state. Attached to the player and carried through death,
 * because Lithic punishes ignorance, not bad luck.
 */
public class Knowledge {
    public static final Codec<Knowledge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("learned", List.of())
                    .forGetter(k -> List.copyOf(k.learned)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("discovered", List.of())
                    .forGetter(k -> List.copyOf(k.discovered)),
            Codec.INT.optionalFieldOf("insight", 0).forGetter(Knowledge::getInsight)
    ).apply(inst, Knowledge::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Knowledge> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private final Set<ResourceLocation> learned = new LinkedHashSet<>();
    private final Set<ResourceLocation> discovered = new LinkedHashSet<>();
    private int insight;

    public Knowledge() {}

    public Knowledge(List<ResourceLocation> learned, List<ResourceLocation> discovered, int insight) {
        this.learned.addAll(learned);
        this.discovered.addAll(discovered);
        this.insight = insight;
    }

    public boolean hasLearned(ResourceLocation id) {
        return this.learned.contains(id);
    }

    public boolean hasDiscovered(ResourceLocation id) {
        return this.discovered.contains(id);
    }

    /** @return true if this call actually changed anything. */
    public boolean discover(ResourceLocation id) {
        return this.discovered.add(id);
    }

    /** @return true if this call actually changed anything. */
    public boolean learn(ResourceLocation id) {
        this.discovered.add(id);
        return this.learned.add(id);
    }

    public boolean forget(ResourceLocation id) {
        return this.learned.remove(id);
    }

    public int getInsight() {
        return this.insight;
    }

    public void addInsight(int amount) {
        this.insight = Math.max(0, this.insight + amount);
    }

    public boolean spendInsight(int amount) {
        if (this.insight < amount) return false;
        this.insight -= amount;
        return true;
    }

    public Set<ResourceLocation> learnedView() {
        return Collections.unmodifiableSet(this.learned);
    }

    public Set<ResourceLocation> discoveredView() {
        return Collections.unmodifiableSet(this.discovered);
    }

    public Knowledge copy() {
        return new Knowledge(List.copyOf(this.learned), List.copyOf(this.discovered), this.insight);
    }
}
