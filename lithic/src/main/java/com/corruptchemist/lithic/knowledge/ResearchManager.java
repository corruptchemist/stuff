package com.corruptchemist.lithic.knowledge;

import com.corruptchemist.lithic.Lithic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Loads the research tree from {@code data/<namespace>/lithic/research/*.json}.
 *
 * <p>Data-driven on purpose: the whole progression can be re-cut by a pack author
 * without touching Java, which is the only sane way to tune something this hostile.
 */
public class ResearchManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FOLDER = "lithic/research";

    /** Replaced wholesale on reload; read from many threads, so keep it volatile. */
    private static volatile Map<ResourceLocation, Research> RESEARCH = Map.of();

    public ResearchManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Research> parsed = new HashMap<>();
        entries.forEach((id, json) -> Research.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Lithic.LOGGER.error("Skipping malformed research '{}': {}", id, error))
                .ifPresent(research -> parsed.put(id, research)));

        RESEARCH = Map.copyOf(parsed);
        Lithic.LOGGER.info("Loaded {} Lithic research nodes", RESEARCH.size());

        // A node pointing at a parent that does not exist can never be learned, which
        // is a silent dead end for the player. Surface it loudly at load instead.
        RESEARCH.forEach((id, research) -> research.parents().stream()
                .filter(parent -> !RESEARCH.containsKey(parent))
                .forEach(parent -> Lithic.LOGGER.error(
                        "Research '{}' requires unknown parent '{}' and is therefore unreachable", id, parent)));
    }

    public static Map<ResourceLocation, Research> all() {
        return RESEARCH;
    }

    public static @Nullable Research get(ResourceLocation id) {
        return RESEARCH.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return RESEARCH.containsKey(id);
    }

    /** Registers a fresh listener instance on every datapack reload. */
    public static class Reloading {
        @SubscribeEvent
        public void onAddReloadListener(AddReloadListenerEvent event) {
            event.addListener(new ResearchManager());
        }
    }
}
