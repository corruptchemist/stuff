package com.corruptchemist.lithic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every hostile rule Lithic imposes is switchable, because "hardest mod in the
 * world" should still be something a pack author can dial back one axis at a time.
 */
public final class LithicConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue REQUIRE_CUTTING_TOOL;
    public static final ModConfigSpec.BooleanValue REQUIRE_STRIKING_TOOL;
    public static final ModConfigSpec.BooleanValue BARE_HAND_HURTS;
    public static final ModConfigSpec.DoubleValue BARE_HAND_DAMAGE;
    public static final ModConfigSpec.BooleanValue DISABLE_NATURAL_REGEN;
    public static final ModConfigSpec.BooleanValue RESEARCH_GATING;
    public static final ModConfigSpec.IntValue KNAPPING_SUCCESS_PERCENT;
    public static final ModConfigSpec.DoubleValue LABOUR_THIRST_EXHAUSTION;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_DISCOVERIES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("world_rules");
        REQUIRE_CUTTING_TOOL = b
                .comment("Blocks tagged lithic:requires_cutting_tool cannot be broken without an item tagged lithic:cutting_tools.")
                .define("requireCuttingTool", true);
        REQUIRE_STRIKING_TOOL = b
                .comment("Blocks tagged lithic:requires_striking_tool cannot be broken without an item tagged lithic:striking_tools.")
                .define("requireStrikingTool", true);
        BARE_HAND_HURTS = b
                .comment("Punching a gated block bare-handed hurts you.")
                .define("bareHandHurts", true);
        BARE_HAND_DAMAGE = b
                .comment("Damage dealt by punching a gated block bare-handed.")
                .defineInRange("bareHandDamage", 1.0D, 0.0D, 20.0D);
        DISABLE_NATURAL_REGEN = b
                .comment("Turn off the naturalRegeneration gamerule when a world loads. Food stops healing you; you need bandaging.")
                .define("disableNaturalRegen", true);
        b.pop();

        b.push("knowledge");
        RESEARCH_GATING = b
                .comment("Master switch for research-gated recipes. Off = every Lithic recipe is craftable immediately.")
                .define("researchGating", true);
        ANNOUNCE_DISCOVERIES = b
                .comment("Send a chat message when a research node is discovered or learned.")
                .define("announceDiscoveries", true);
        b.pop();

        b.push("primitive");
        KNAPPING_SUCCESS_PERCENT = b
                .comment("Percent chance that striking flint against stone yields a shard rather than shattering it.")
                .defineInRange("knappingSuccessPercent", 55, 1, 100);
        LABOUR_THIRST_EXHAUSTION = b
                .comment("Tough As Nails thirst exhaustion added per heavy-labour action (knapping, gated mining).")
                .defineInRange("labourThirstExhaustion", 0.75D, 0.0D, 40.0D);
        b.pop();

        SPEC = b.build();
    }

    private LithicConfig() {}
}
