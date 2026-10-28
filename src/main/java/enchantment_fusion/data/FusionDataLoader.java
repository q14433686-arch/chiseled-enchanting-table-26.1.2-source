package enchantment_fusion.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reloads {@code matrix_profiles.json}, {@code repulsive_pairs.json}, and {@code economy.json}
 * atomically on server startup and {@code /reload}.
 */
public final class FusionDataLoader implements SimpleSynchronousResourceReloadListener {
    private static final Identifier RELOAD_ID = EnchantmentFusion.id("matrix_data");
    private static final Identifier MATRIX_PROFILES_PATH =
        EnchantmentFusion.id("enchantment_fusion/matrix_profiles.json");
    private static final Identifier REPULSIVE_PAIRS_PATH =
        EnchantmentFusion.id("enchantment_fusion/repulsive_pairs.json");
    private static final Identifier ECONOMY_PATH =
        EnchantmentFusion.id("enchantment_fusion/economy.json");

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
            JsonObject matrixObj = readJson(manager, MATRIX_PROFILES_PATH);
            JsonObject repulsiveObj = readJson(manager, REPULSIVE_PAIRS_PATH);
            JsonObject economyObj = readJson(manager, ECONOMY_PATH);
            FusionMatrixRegistry.Snapshot staged = FusionMatrixRegistry.parseFromJson(matrixObj, repulsiveObj, economyObj);
            FusionMatrixRegistry.publish(staged);
            EnchantmentFusion.LOGGER.info(
                "Loaded Enchantment Fusion matrix ({} profiles, {} exclusive groups)",
                staged.profiles().size(),
                staged.exclusiveGroups().size()
            );
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Failed to reload Enchantment Fusion datapack tables; keeping previous snapshot", ex);
        }
    }

    private static JsonObject readJson(ResourceManager manager, Identifier path) throws Exception {
        Optional<Resource> res = manager.getResource(path);
        if (res.isEmpty()) {
            return null;
        }
        try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
