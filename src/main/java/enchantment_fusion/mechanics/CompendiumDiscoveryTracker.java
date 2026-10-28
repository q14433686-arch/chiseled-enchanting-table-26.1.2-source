package enchantment_fusion.mechanics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Three-stage knowledge progression engine for {@code 《融合秘典》 (Fusion Grimoire)}:
 * <ul>
 *   <li>{@code STATE_UNKNOWN (0)}: Masked {@code ???}</li>
 *   <li>{@code STATE_HEARD (1 / 初识)}: Formula & target gear clue unlocked.</li>
 *   <li>{@code STATE_MASTERED (2 / 通晓)}: Full title & mechanics unlocked.</li>
 * </ul>
 * Uses batched dirty-flagged atomic temp-file writes every 200 ticks + shutdown flush (DATA-03)
 * and incremental {@code CompendiumDeltaPayload} updates on single-entry unlocks (NET-05).
 */
public final class CompendiumDiscoveryTracker {
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_HEARD = 1;
    public static final int STATE_MASTERED = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Map<String, Integer>> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SCANNED_ENCHANTS_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean dirtySave = false;
    private static volatile Map<String, Integer> CLIENT_STATES = Map.of();

    private CompendiumDiscoveryTracker() {}

    public static String pairKey(String primaryId, String secondaryId) {
        return primaryId + ">" + secondaryId;
    }

    public static String pairKey(FusedPair pair) {
        return pairKey(pair.primaryId(), pair.secondaryId());
    }

    public static String sigKey(String sigId) {
        return "sig/" + sigId;
    }

    public static String transmuteKey(String variantId) {
        return "transmute/" + variantId;
    }

    public static String aberrantKey(String aberrationId) {
        return "aberrant/" + aberrationId;
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId != null) {
            SCANNED_ENCHANTS_CACHE.remove(playerId);
        }
    }

    public static void initServerHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompendiumDiscoveryTracker::loadWorldFile);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveWorldFileAtomically(server);
            dirtySave = false;
            SERVER_STATES.clear();
            SCANNED_ENCHANTS_CACHE.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            FusionNetworking.sendMatrixSync(player);
            scanPlayerInventory(player);
            FusionNetworking.sendCompendiumSync(player, encodeStates(player.getUUID()));
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    FusionNetworking.sendMatrixSync(player);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            if (tick % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    scanPlayerInventory(player);
                }
            }
            if (dirtySave && tick % 200 == 0) {
                dirtySave = false;
                saveWorldFileAtomically(server);
            }
        });
    }

    public static void scanPlayerInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        Set<String> seenEnchants = SCANNED_ENCHANTS_CACHE.computeIfAbsent(
            player.getUUID(), k -> ConcurrentHashMap.newKeySet()
        );
        boolean bulkChanged = false;

        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;

            if (st.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments stored = st.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (Holder<Enchantment> h : stored.keySet()) {
                    String eid = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                    if (!eid.isEmpty() && seenEnchants.add(eid)) {
                        bulkChanged |= unlockHeardForEnchantment(player.getUUID(), eid);
                    }
                }
            }

            Map<String, Integer> transmuted = st.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
            if (!transmuted.isEmpty()) {
                for (String eid : transmuted.keySet()) {
                    Optional<TransmuteVariant> tv = CrossDomainTransmutations.resolve(st, eid);
                    if (tv.isPresent()) {
                        bulkChanged |= advanceStateInternal(player.getUUID(), transmuteKey(tv.get().id()), STATE_MASTERED);
                    }
                }
            }

            FusedEnchantmentList fused = st.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (!fused.isEmpty()) {
                for (FusedPair pair : fused.pairs()) {
                    bulkChanged |= advanceStateInternal(player.getUUID(), pairKey(pair), STATE_MASTERED);
                    Optional<SignatureEntry> sig = SignaturePairs.resolve(pair);
                    if (sig.isPresent()) {
                        bulkChanged |= advanceStateInternal(player.getUUID(), sigKey(sig.get().id()), STATE_MASTERED);
                    }
                    if (pair.isAberrant()) {
                        bulkChanged |= advanceStateInternal(player.getUUID(), aberrantKey(pair.aberrationId()), STATE_MASTERED);
                    }
                }
            }
        }

        if (bulkChanged) {
            dirtySave = true;
            FusionNetworking.sendCompendiumSync(player, encodeStates(player.getUUID()));
        }
    }

    private static boolean unlockHeardForEnchantment(UUID playerId, String enchantId) {
        boolean changed = false;
        for (SignatureEntry sig : SignaturePairs.allOrderedEntries()) {
            if (sig.primaryId().equals(enchantId) || sig.secondaryId().equals(enchantId)) {
                changed |= advanceStateInternal(playerId, sigKey(sig.id()), STATE_HEARD);
                changed |= advanceStateInternal(playerId, pairKey(sig.primaryId(), sig.secondaryId()), STATE_HEARD);
            }
        }
        for (TransmuteVariant tv : CrossDomainTransmutations.allVariants()) {
            if (tv.sourceEnchantId().equals(enchantId)) {
                changed |= advanceStateInternal(playerId, transmuteKey(tv.id()), STATE_HEARD);
            }
        }
        return changed;
    }

    public static boolean advanceState(ServerPlayer player, String key, int targetState) {
        int clamped = Math.min(STATE_MASTERED, targetState);
        boolean changed = advanceStateInternal(player.getUUID(), key, clamped);
        if (changed) {
            dirtySave = true;
            FusionNetworking.sendCompendiumDelta(player, key, clamped);
        }
        return changed;
    }

    private static boolean advanceStateInternal(UUID playerId, String key, int targetState) {
        if (key == null || key.isBlank() || targetState <= STATE_UNKNOWN) {
            return false;
        }
        int clamped = Math.min(STATE_MASTERED, targetState);
        Map<String, Integer> map = SERVER_STATES.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        int prev = map.getOrDefault(key, STATE_UNKNOWN);
        if (clamped > prev) {
            map.put(key, clamped);
            dirtySave = true;
            return true;
        }
        return false;
    }

    public static boolean recordDiscovery(ServerPlayer player, String primaryId, String secondaryId) {
        String pk = pairKey(primaryId, secondaryId);
        boolean changed = advanceStateInternal(player.getUUID(), pk, STATE_MASTERED);
        if (changed) {
            FusionNetworking.sendCompendiumDelta(player, pk, STATE_MASTERED);
        }
        Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(FusedPair.clean(primaryId, 1, secondaryId, 1));
        if (sigOpt.isPresent()) {
            String sk = sigKey(sigOpt.get().id());
            if (advanceStateInternal(player.getUUID(), sk, STATE_MASTERED)) {
                FusionNetworking.sendCompendiumDelta(player, sk, STATE_MASTERED);
                changed = true;
            }
        }
        return changed;
    }

    public static void recordPairHeard(ServerPlayer player, String primaryId, String secondaryId) {
        String pk = pairKey(primaryId, secondaryId);
        if (advanceStateInternal(player.getUUID(), pk, STATE_HEARD)) {
            FusionNetworking.sendCompendiumDelta(player, pk, STATE_HEARD);
        }
        Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(FusedPair.clean(primaryId, 1, secondaryId, 1));
        if (sigOpt.isPresent()) {
            String sk = sigKey(sigOpt.get().id());
            if (advanceStateInternal(player.getUUID(), sk, STATE_HEARD)) {
                FusionNetworking.sendCompendiumDelta(player, sk, STATE_HEARD);
            }
        }
    }

    public static void recordTransmuteProgress(ServerPlayer player, String variantId, int state) {
        int clamped = Math.min(STATE_MASTERED, state);
        String tk = transmuteKey(variantId);
        if (advanceStateInternal(player.getUUID(), tk, clamped)) {
            FusionNetworking.sendCompendiumDelta(player, tk, clamped);
        }
    }

    public static void recordAberrantMastered(ServerPlayer player, String aberrationId) {
        String ak = aberrantKey(aberrationId);
        if (advanceStateInternal(player.getUUID(), ak, STATE_MASTERED)) {
            FusionNetworking.sendCompendiumDelta(player, ak, STATE_MASTERED);
        }
    }

    public static boolean consumeTatteredPage(ServerPlayer player, int pageTier) {
        UUID pid = player.getUUID();
        Map<String, Integer> states = SERVER_STATES.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
        RandomSource rng = player.getRandom();

        if (pageTier == 2) {
            List<String> unmasteredAberrant = new ArrayList<>();
            for (String abId : AberrantRegistry.allIds()) {
                if (states.getOrDefault(aberrantKey(abId), STATE_UNKNOWN) < STATE_MASTERED) {
                    unmasteredAberrant.add(abId);
                }
            }
            if (!unmasteredAberrant.isEmpty()) {
                String picked = unmasteredAberrant.get(rng.nextInt(unmasteredAberrant.size()));
                String ak = aberrantKey(picked);
                advanceStateInternal(pid, ak, STATE_MASTERED);
                FusionNetworking.sendCompendiumDelta(player, ak, STATE_MASTERED);
                Component title = Component.translatable(AberrantRegistry.titleKey(picked)).withStyle(ChatFormatting.LIGHT_PURPLE);
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.page.mastered", title), true);
                return true;
            }
        }

        record Candidate(String key, String pairMirrorKey, Component title, int curState) {}
        List<Candidate> unknownPool = new ArrayList<>();
        List<Candidate> heardPool = new ArrayList<>();

        for (SignatureEntry sig : SignaturePairs.allOrderedEntries()) {
            String k = sigKey(sig.id());
            int st = states.getOrDefault(k, STATE_UNKNOWN);
            Candidate c = new Candidate(k, pairKey(sig.primaryId(), sig.secondaryId()), Component.translatable(sig.titleKey()), st);
            if (st == STATE_UNKNOWN) unknownPool.add(c);
            else if (st == STATE_HEARD) heardPool.add(c);
        }
        for (TransmuteVariant tv : CrossDomainTransmutations.allVariants()) {
            String k = transmuteKey(tv.id());
            int st = states.getOrDefault(k, STATE_UNKNOWN);
            Candidate c = new Candidate(k, null, Component.translatable(tv.titleKey()), st);
            if (st == STATE_UNKNOWN) unknownPool.add(c);
            else if (st == STATE_HEARD) heardPool.add(c);
        }

        if (pageTier >= 1) {
            List<Candidate> pool = !heardPool.isEmpty() ? heardPool : unknownPool;
            if (pool.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.page.all_known"), true);
                return false;
            }
            Candidate pick = pool.get(rng.nextInt(pool.size()));
            unlockCandidateWithDelta(player, pick, STATE_MASTERED);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.mastered", pick.title().copy().withStyle(ChatFormatting.GOLD)),
                true
            );
            return true;
        }

        if (!unknownPool.isEmpty()) {
            Candidate pick = unknownPool.get(rng.nextInt(unknownPool.size()));
            unlockCandidateWithDelta(player, pick, STATE_HEARD);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.heard", pick.title().copy().withStyle(ChatFormatting.AQUA)),
                true
            );
            return true;
        }
        if (!heardPool.isEmpty()) {
            Candidate pick = heardPool.get(rng.nextInt(heardPool.size()));
            unlockCandidateWithDelta(player, pick, STATE_MASTERED);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.mastered", pick.title().copy().withStyle(ChatFormatting.GOLD)),
                true
            );
            return true;
        }

        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.page.all_known"), true);
        return false;
    }

    private static void unlockCandidateWithDelta(ServerPlayer player, Record candRec, int stage) {
        UUID pid = player.getUUID();
        if (candRec != null) {
            try {
                String k = (String) candRec.getClass().getDeclaredMethod("key").invoke(candRec);
                String mirror = (String) candRec.getClass().getDeclaredMethod("pairMirrorKey").invoke(candRec);
                if (advanceStateInternal(pid, k, stage)) {
                    FusionNetworking.sendCompendiumDelta(player, k, stage);
                }
                if (mirror != null && advanceStateInternal(pid, mirror, stage)) {
                    FusionNetworking.sendCompendiumDelta(player, mirror, stage);
                }
            } catch (ReflectiveOperationException ignored) {
                FusionNetworking.sendCompendiumSync(player, encodeStates(pid));
            }
        }
    }

    public static List<String> encodeStates(UUID playerUuid) {
        Map<String, Integer> map = SERVER_STATES.getOrDefault(playerUuid, Map.of());
        List<String> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() != null && e.getValue() > STATE_UNKNOWN) {
                out.add(e.getKey() + "=" + e.getValue());
            }
        }
        Collections.sort(out);
        return out;
    }

    public static Set<String> getUnlockedFor(UUID playerUuid) {
        return Collections.unmodifiableSet(
            SERVER_STATES.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).keySet()
        );
    }

    public static void applyClientSync(Iterable<String> encoded) {
        Map<String, Integer> next = new HashMap<>();
        for (String token : encoded) {
            if (token == null || token.isBlank()) continue;
            int eq = token.lastIndexOf('=');
            if (eq > 0 && eq < token.length() - 1) {
                try {
                    int st = Integer.parseInt(token.substring(eq + 1));
                    next.put(token.substring(0, eq), Math.clamp(st, STATE_UNKNOWN, STATE_MASTERED));
                } catch (NumberFormatException ignored) {
                    next.put(token, STATE_MASTERED);
                }
            } else {
                next.put(token, STATE_MASTERED);
            }
        }
        CLIENT_STATES = Collections.unmodifiableMap(next);
    }

    public static void applyClientDelta(String entryKey, int stage) {
        if (entryKey == null || entryKey.isBlank()) return;
        int clamped = Math.clamp(stage, STATE_UNKNOWN, STATE_MASTERED);
        Map<String, Integer> cur = CLIENT_STATES;
        if (clamped <= cur.getOrDefault(entryKey, STATE_UNKNOWN)) {
            return;
        }
        Map<String, Integer> next = new HashMap<>(cur);
        next.put(entryKey, clamped);
        CLIENT_STATES = Collections.unmodifiableMap(next);
    }

    public static int clientState(String key) {
        return CLIENT_STATES.getOrDefault(key, STATE_UNKNOWN);
    }

    public static int clientStateVersionHash() {
        return CLIENT_STATES.hashCode();
    }

    public static int clientMasteredCount() {
        int count = 0;
        for (int v : CLIENT_STATES.values()) {
            if (v >= STATE_MASTERED) {
                count++;
            }
        }
        return count;
    }

    public static boolean isClientDiscovered(String primaryId, String secondaryId) {
        return clientState(pairKey(primaryId, secondaryId)) >= STATE_MASTERED;
    }

    private static Path resolveSaveFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("enchantment_fusion_compendium.json");
    }

    private static void loadWorldFile(MinecraftServer server) {
        SERVER_STATES.clear();
        SCANNED_ENCHANTS_CACHE.clear();
        dirtySave = false;
        Path path = resolveSaveFile(server);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                Map<String, Integer> states = new ConcurrentHashMap<>();
                JsonElement val = entry.getValue();
                if (val.isJsonArray()) {
                    for (JsonElement elem : val.getAsJsonArray()) {
                        states.put(elem.getAsString(), STATE_MASTERED);
                    }
                } else if (val.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> kv : val.getAsJsonObject().entrySet()) {
                        states.put(kv.getKey(), Math.clamp(kv.getValue().getAsInt(), STATE_UNKNOWN, STATE_MASTERED));
                    }
                }
                SERVER_STATES.put(uuid, states);
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to read compendium file {}: {}", path, ex.toString());
        }
    }

    /** Atomic temp-file write + move so mid-save crashes never leave half-written JSON (DATA-03). */
    private static void saveWorldFileAtomically(MinecraftServer server) {
        Path path = resolveSaveFile(server);
        Path tmp = path.resolveSibling("enchantment_fusion_compendium.json.tmp");
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Map<String, Integer>> entry : SERVER_STATES.entrySet()) {
                JsonObject playerObj = new JsonObject();
                List<String> keys = new ArrayList<>(entry.getValue().keySet());
                Collections.sort(keys);
                for (String k : keys) {
                    int v = entry.getValue().getOrDefault(k, STATE_UNKNOWN);
                    if (v > STATE_UNKNOWN) {
                        playerObj.addProperty(k, v);
                    }
                }
                root.add(entry.getKey().toString(), playerObj);
            }
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException nonAtomic) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to save compendium file {}: {}", path, ex.toString());
        }
    }
}
