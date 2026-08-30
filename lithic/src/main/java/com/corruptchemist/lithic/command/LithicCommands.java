package com.corruptchemist.lithic.command;

import com.corruptchemist.lithic.knowledge.Knowledge;
import com.corruptchemist.lithic.knowledge.KnowledgeEvents;
import com.corruptchemist.lithic.knowledge.Research;
import com.corruptchemist.lithic.knowledge.ResearchManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lithic} — player-facing introspection plus operator overrides for testing
 * a progression tree that is, by design, extremely slow to walk normally.
 */
public class LithicCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lithic")
                .then(Commands.literal("research")
                        .then(Commands.literal("list")
                                .executes(LithicCommands::listResearch))
                        .then(Commands.literal("learn")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(LithicCommands::learnResearch)))
                        .then(Commands.literal("grant")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(LithicCommands::grantResearch)))
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(LithicCommands::resetResearch)))
                .then(Commands.literal("insight")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(LithicCommands::addInsight))));
    }

    private static int listResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Knowledge knowledge = KnowledgeEvents.of(player);
        List<ResourceLocation> learnable = KnowledgeEvents.learnable(player);

        context.getSource().sendSuccess(() -> Component.translatable("lithic.tally.insight", knowledge.getInsight())
                .withStyle(ChatFormatting.AQUA), false);
        context.getSource().sendSuccess(() -> Component.translatable("lithic.tally.learned",
                knowledge.learnedView().size()).withStyle(ChatFormatting.DARK_GRAY), false);

        for (ResourceLocation id : learnable) {
            Research research = ResearchManager.get(id);
            int cost = research == null ? 0 : research.insightCost();
            context.getSource().sendSuccess(() -> Component.literal(" - " + id + " (" + cost + ")")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return learnable.size();
    }

    private static int learnResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        KnowledgeEvents.LearnResult result = KnowledgeEvents.tryLearn(player, id);

        context.getSource().sendSuccess(
                () -> Component.literal(result.name()).withStyle(
                        result == KnowledgeEvents.LearnResult.LEARNED ? ChatFormatting.GREEN : ChatFormatting.RED),
                false);
        return result == KnowledgeEvents.LearnResult.LEARNED ? 1 : 0;
    }

    /** Operator override: skips discovery, prerequisites and cost entirely. */
    private static int grantResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        if (!ResearchManager.exists(id)) {
            context.getSource().sendFailure(Component.literal("No such research: " + id));
            return 0;
        }

        Knowledge knowledge = KnowledgeEvents.of(player);
        knowledge.learn(id);
        KnowledgeEvents.commit(player, knowledge);
        context.getSource().sendSuccess(() -> Component.literal("Granted " + id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KnowledgeEvents.commit(player, new Knowledge());
        context.getSource().sendSuccess(() -> Component.literal("Knowledge wiped").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int addInsight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        Knowledge knowledge = KnowledgeEvents.of(player);
        knowledge.addInsight(amount);
        KnowledgeEvents.commit(player, knowledge);
        context.getSource().sendSuccess(
                () -> Component.literal("Insight is now " + knowledge.getInsight()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }
}
