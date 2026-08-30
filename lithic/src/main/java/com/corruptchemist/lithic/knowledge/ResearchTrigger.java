package com.corruptchemist.lithic.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One way a player can stumble into a piece of knowledge.
 *
 * <p>The {@code value} is either a plain registry id ({@code minecraft:flint}) or a
 * tag reference ({@code #minecraft:logs}). Both forms are parsed once, at load, so
 * that the hot event handlers do no string work.
 */
public final class ResearchTrigger {
    public static final Codec<ResearchTrigger> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            TriggerType.CODEC.fieldOf("type").forGetter(ResearchTrigger::type),
            Codec.STRING.fieldOf("value").forGetter(ResearchTrigger::value),
            Codec.INT.optionalFieldOf("insight", 1).forGetter(ResearchTrigger::insight)
    ).apply(inst, ResearchTrigger::new));

    private final TriggerType type;
    private final String value;
    private final int insight;

    /** Exactly one of these is non-null once parsed. */
    private final @Nullable ResourceLocation directId;
    private final @Nullable ResourceLocation tagId;

    public ResearchTrigger(TriggerType type, String value, int insight) {
        this.type = type;
        this.value = value;
        this.insight = insight;
        if (value.startsWith("#")) {
            this.tagId = ResourceLocation.parse(value.substring(1));
            this.directId = null;
        } else {
            this.directId = ResourceLocation.parse(value);
            this.tagId = null;
        }
    }

    public TriggerType type() {
        return this.type;
    }

    public String value() {
        return this.value;
    }

    public int insight() {
        return this.insight;
    }

    public boolean matchesItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (this.tagId != null) {
            return stack.is(TagKey.create(Registries.ITEM, this.tagId));
        }
        return this.directId != null && this.directId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public boolean matchesBlock(BlockState state) {
        if (this.tagId != null) {
            return state.is(TagKey.create(Registries.BLOCK, this.tagId));
        }
        return this.directId != null && this.directId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public boolean matchesEntity(EntityType<?> entityType) {
        if (this.tagId != null) {
            return entityType.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, this.tagId));
        }
        return this.directId != null && this.directId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
    }

    /** Matches a bare identifier, used by {@link TriggerType#USE_STATION}. */
    public boolean matchesId(ResourceLocation id) {
        return this.directId != null && this.directId.equals(id);
    }

    public enum TriggerType implements StringRepresentable {
        OBTAIN_ITEM("obtain_item"),
        CRAFT_ITEM("craft_item"),
        BREAK_BLOCK("break_block"),
        KILL_ENTITY("kill_entity"),
        USE_STATION("use_station");

        public static final Codec<TriggerType> CODEC = StringRepresentable.fromEnum(TriggerType::values);

        private final String name;

        TriggerType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
