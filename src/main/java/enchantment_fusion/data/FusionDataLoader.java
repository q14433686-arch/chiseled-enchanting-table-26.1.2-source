package enchantment_fusion.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Reloads {@code matrix_profiles.json}, {@code repulsive_pairs.json}, and {@code economy.json}
 * atomically on server startup and {@code /reload}, merging all datapack layers bottom-up via
 * {@link ResourceManager#getResourceStack(Identifier)} while honoring {@code "replace": true} (DATA-3).
 */
public final class FusionDataLoader implements SimpleSynchronousResourceReloadListener {
    private static final Identifier RELOAD_ID = EnchantmentFusion.id("matrix_data");
    private static final Identifier MATRIX_PROFILES_PATH =
        EnchantmentFusion.id("enchantment_fusion/matrix_profiles.json");
    private static final Identifier REPULSIVE_PAIRS_PATH =
        EnchantmentFusion.id("enchantment_fusion/repulsive_pairs.json");
    private static final Identifier ECONOMY_PATH =
        EnchantmentFusion.id("enchantment_fusion/economy.json");
    private static final Identifier SIGNATURES_PATH =
        EnchantmentFusion.id("enchantment_fusion/signatures.json");
    private static final Identifier TRANSMUTATIONS_PATH =
        EnchantmentFusion.id("enchantment_fusion/transmutations.json");

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FusionDataLoader());
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        try {
            JsonObject matrixObj = readMergedJsonStack(manager, MATRIX_PROFILES_PATH);
            JsonObject repulsiveObj = readMergedJsonStack(manager, REPULSIVE_PAIRS_PATH);
            JsonObject economyObj = readMergedJsonStack(manager, ECONOMY_PATH);
            JsonObject signaturesObj = readMergedJsonStack(manager, SIGNATURES_PATH);
            JsonObject transmutesObj = readMergedJsonStack(manager, TRANSMUTATIONS_PATH);
            JsonObject base = new JsonObject();
            if (matrixObj != null) base.add("matrix_profiles", matrixObj);
            if (repulsiveObj != null) base.add("repulsive_pairs", repulsiveObj);
            if (economyObj != null) base.add("economy", economyObj);
            if (signaturesObj != null) base.add("signatures", signaturesObj);
            if (transmutesObj != null) base.add("transmutations", transmutesObj);
            FusionConfigStore.reload(base);
            FusionMatrixRegistry.Snapshot staged = FusionMatrixRegistry.serverSnapshot();
            EnchantmentFusion.LOGGER.info(
                "Loaded Enchantment Fusion matrix ({} profiles, {} exclusive groups)",
                staged.profiles().size(),
                staged.exclusiveGroups().size()
            );
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Failed to reload Enchantment Fusion datapack tables; keeping previous snapshot", ex);
        }
    }

    /**
     * Merges a stack of JSON objects from lowest-priority pack to highest-priority pack (DATA-3).
     * If a higher pack explicitly sets {@code "replace": true}, earlier pack layers are discarded cleanly.
     */
    public static @Nullable JsonObject mergeJsonLayers(List<JsonObject> layers) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        JsonObject merged = new JsonObject();
        for (JsonObject layer : layers) {
            if (layer == null) {
                continue;
            }
            boolean replaceLayer = layer.has("replace")
                && layer.get("replace").isJsonPrimitive()
                && layer.get("replace").getAsJsonPrimitive().isBoolean()
                && layer.get("replace").getAsBoolean();
            if (replaceLayer) {
                merged = new JsonObject();
            }
            deepMergeInto(merged, layer, replaceLayer);
        }
        return merged;
    }

    private static void deepMergeInto(JsonObject target, JsonObject overlay, boolean replaceLayer) {
        for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
            String key = entry.getKey();
            JsonElement val = entry.getValue();
            if ("exclusive_groups".equals(key) && val.isJsonArray()) {
                JsonArray incoming = val.getAsJsonArray();
                if (replaceLayer || incoming.isEmpty() || !target.has(key) || !target.get(key).isJsonArray()) {
                    target.add(key, incoming.deepCopy());
                } else {
                    JsonArray combined = target.getAsJsonArray(key).deepCopy();
                    for (JsonElement item : incoming) {
                        combined.add(item.deepCopy());
                    }
                    target.add(key, combined);
                }
            } else if (val.isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
                deepMergeInto(target.getAsJsonObject(key), val.getAsJsonObject(), false);
            } else {
                target.add(key, val.deepCopy());
            }
        }
    }

    private static @Nullable JsonObject readMergedJsonStack(ResourceManager manager, Identifier path) throws Exception {
        List<Resource> stack = manager.getResourceStack(path);
        if (stack.isEmpty()) {
            return null;
        }
        java.util.ArrayList<JsonObject> parsed = new java.util.ArrayList<>(stack.size());
        for (Resource res : stack) {
            try (var reader = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    parsed.add(root.getAsJsonObject());
                }
            }
        }
        return mergeJsonLayers(parsed);
    }
}
