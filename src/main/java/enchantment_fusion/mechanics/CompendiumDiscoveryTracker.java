package enchantment_fusion.mechanics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.model.FusedPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Tracks discovered {@code [A ▷ B]} directional pairs per player (GDD §6.4: Clue Hints -> Full Unlock)
 * and persists them cleanly inside the world save folder.
 */
public final class CompendiumDiscoveryTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Set<String>> SERVER_UNLOCKED = new ConcurrentHashMap<>();
    private static volatile Set<String> CLIENT_UNLOCKED = Set.of();

    private CompendiumDiscoveryTracker() {}

    public static String pairKey(String primaryId, String secondaryId) {
        return primaryId + ">" + secondaryId;
    }

    public static String pairKey(FusedPair pair) {
        return pairKey(pair.primaryId(), pair.secondaryId());
    }

    public static void initServerHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompendiumDiscoveryTracker::loadWorldFile);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveWorldFile(server);
            SERVER_UNLOCKED.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            FusionNetworking.sendCompendiumSync(player, getUnlockedFor(player.getUUID()));
        });
    }

    public static Set<String> getUnlockedFor(UUID playerUuid) {
        return Collections.unmodifiableSet(
            SERVER_UNLOCKED.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet())
        );
    }

    public static boolean recordDiscovery(ServerPlayer player, String primaryId, String secondaryId) {
        Set<String> set = SERVER_UNLOCKED.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        boolean added = set.add(pairKey(primaryId, secondaryId));
        if (added) {
            if (player.level().getServer() != null) {
                saveWorldFile(player.level().getServer());
            }
            FusionNetworking.sendCompendiumSync(player, set);
        }
        return added;
    }

    public static void applyClientSync( Iterable<String> keys ) {
        Set<String> next = ConcurrentHashMap.newKeySet();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                next.add(key);
            }
        }
        CLIENT_UNLOCKED = Collections.unmodifiableSet(next);
    }

    public static boolean isClientDiscovered(String primaryId, String secondaryId) {
        return CLIENT_UNLOCKED.contains(pairKey(primaryId, secondaryId));
    }

    private static Path resolveSaveFile(net.minecraft.server.MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("enchantment_fusion_compendium.json");
    }

    private static void loadWorldFile(net.minecraft.server.MinecraftServer server) {
        SERVER_UNLOCKED.clear();
        Path path = resolveSaveFile(server);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                Set<String> set = ConcurrentHashMap.newKeySet();
                if (entry.getValue().isJsonArray()) {
                    for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                        set.add(elem.getAsString());
                    }
                }
                SERVER_UNLOCKED.put(uuid, set);
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to read compendium file {}: {}", path, ex.toString());
        }
    }

    private static void saveWorldFile(net.minecraft.server.MinecraftServer server) {
        Path path = resolveSaveFile(server);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Set<String>> entry : SERVER_UNLOCKED.entrySet()) {
                JsonArray arr = new JsonArray();
                List<String> sorted = new ArrayList<>(entry.getValue());
                Collections.sort(sorted);
                for (String k : sorted) {
                    arr.add(k);
                }
                root.add(entry.getKey().toString(), arr);
            }
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to save compendium file {}: {}", path, ex.toString());
        }
    }
}
