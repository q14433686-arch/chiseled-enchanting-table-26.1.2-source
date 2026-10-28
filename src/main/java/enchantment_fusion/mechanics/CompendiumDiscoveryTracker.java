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
import java.util.ArrayList;
import java.util.Collections;
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
 * Three-stage knowledge progression engine for {@code 《融合秘典》 (Fusion Grimoire)},
 * modeled after Herbcraft's {@code KnowledgeAPI} + {@code KnowledgeInventoryScanner} + {@code TatteredPageItem}:
 * <ul>
 *   <li>{@code STATE_UNKNOWN (0)}: Masked {@code ???}</li>
 *   <li>{@code STATE_HEARD (1 / 初识)}: Unlocked when scanning related Enchanted Books in inventory,
 *       reading a Common Tattered Page, or attempting a failed synthesis — reveals formula & target gear clue.</li>
 *   <li>{@code STATE_MASTERED (2 / 通晓)}: Unlocked when crafting/transmuting at the Fusion Table,
 *       triggering the effect in live gameplay, or reading a Rare Ancient Page — reveals full title & mechanics.</li>
 * </ul>
 */
public final class CompendiumDiscoveryTracker {
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_HEARD = 1;
    public static final int STATE_MASTERED = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Map<String, Integer>> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SCANNED_ENCHANTS_CACHE = new ConcurrentHashMap<>();
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

    public static void initServerHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompendiumDiscoveryTracker::loadWorldFile);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveWorldFile(server);
            SERVER_STATES.clear();
            SCANNED_ENCHANTS_CACHE.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            scanPlayerInventory(player);
            FusionNetworking.sendCompendiumSync(player, encodeStates(player.getUUID()));
        });
        // Herbcraft-style 20-tick inventory scanner: acquiring an Enchanted Book unlocks Stage 1 clues!
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    scanPlayerInventory(player);
                }
            }
        });
    }

    public static void scanPlayerInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        Set<String> seenEnchants = SCANNED_ENCHANTS_CACHE.computeIfAbsent(
            player.getUUID(), k -> ConcurrentHashMap.newKeySet()
        );
        boolean dirty = false;

        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;

            if (st.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments stored = st.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (Holder<Enchantment> h : stored.keySet()) {
                    String eid = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                    if (!eid.isEmpty() && seenEnchants.add(eid)) {
                        dirty |= unlockHeardForEnchantment(player.getUUID(), eid);
                    }
                }
            }

            FusedEnchantmentList fused = st.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (!fused.isEmpty()) {
                for (FusedPair pair : fused.pairs()) {
                    dirty |= advanceStateInternal(player.getUUID(), pairKey(pair), STATE_MASTERED);
                    SignaturePairs.resolve(pair).ifPresent(sig ->
                        advanceStateInternal(player.getUUID(), sigKey(sig.id()), STATE_MASTERED)
                    );
                    if (pair.isAberrant()) {
                        dirty |= advanceStateInternal(player.getUUID(), aberrantKey(pair.aberrationId()), STATE_MASTERED);
                    }
                }
            }
        }

        if (dirty) {
            persistAndSync(player);
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
        boolean changed = advanceStateInternal(player.getUUID(), key, targetState);
        if (changed) {
            persistAndSync(player);
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
            return true;
        }
        return false;
    }

    public static boolean recordDiscovery(ServerPlayer player, String primaryId, String secondaryId) {
        boolean changed = advanceStateInternal(player.getUUID(), pairKey(primaryId, secondaryId), STATE_MASTERED);
        SignaturePairs.resolve(FusedPair.clean(primaryId, 1, secondaryId, 1)).ifPresent(sig ->
            advanceStateInternal(player.getUUID(), sigKey(sig.id()), STATE_MASTERED)
        );
        if (changed) {
            persistAndSync(player);
        }
        return changed;
    }

    public static void recordPairHeard(ServerPlayer player, String primaryId, String secondaryId) {
        boolean changed = advanceStateInternal(player.getUUID(), pairKey(primaryId, secondaryId), STATE_HEARD);
        Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(FusedPair.clean(primaryId, 1, secondaryId, 1));
        if (sigOpt.isPresent()) {
            changed |= advanceStateInternal(player.getUUID(), sigKey(sigOpt.get().id()), STATE_HEARD);
        }
        if (changed) {
            persistAndSync(player);
        }
    }

    public static void recordTransmuteProgress(ServerPlayer player, String variantId, int state) {
        if (advanceStateInternal(player.getUUID(), transmuteKey(variantId), state)) {
            persistAndSync(player);
        }
    }

    public static void recordAberrantMastered(ServerPlayer player, String aberrationId) {
        if (advanceStateInternal(player.getUUID(), aberrantKey(aberrationId), STATE_MASTERED)) {
            persistAndSync(player);
        }
    }

    /**
     * Consumes a tiered {@code 融合残页} (0 = Common -> HEARD, 1 = Rare -> MASTERED, 2 = Aberrant -> MASTERED).
     * Returns true if a new discovery was made; false if nothing left to learn for this tier.
     */
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
                advanceStateInternal(pid, aberrantKey(picked), STATE_MASTERED);
                persistAndSync(player);
                Component title = Component.translatable(AberrantRegistry.titleKey(picked)).withStyle(ChatFormatting.LIGHT_PURPLE);
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.page.mastered", title), true);
                return true;
            }
        }

        record Candidate(String key, String PairMirrorKey, Component title, int curState) {}
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
            advanceStateInternal(pid, pick.key(), STATE_MASTERED);
            if (pick.PairMirrorKey() != null) {
                advanceStateInternal(pid, pick.PairMirrorKey(), STATE_MASTERED);
            }
            persistAndSync(player);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.mastered", pick.title().copy().withStyle(ChatFormatting.GOLD)),
                true
            );
            return true;
        }

        // Common page: prefer UNKNOWN -> HEARD; fallback HEARD -> MASTERED if all recipes heard
        if (!unknownPool.isEmpty()) {
            Candidate pick = unknownPool.get(rng.nextInt(unknownPool.size()));
            advanceStateInternal(pid, pick.key(), STATE_HEARD);
            if (pick.PairMirrorKey() != null) {
                advanceStateInternal(pid, pick.PairMirrorKey(), STATE_HEARD);
            }
            persistAndSync(player);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.heard", pick.title().copy().withStyle(ChatFormatting.AQUA)),
                true
            );
            return true;
        }
        if (!heardPool.isEmpty()) {
            Candidate pick = heardPool.get(rng.nextInt(heardPool.size()));
            advanceStateInternal(pid, pick.key(), STATE_MASTERED);
            if (pick.PairMirrorKey() != null) {
                advanceStateInternal(pid, pick.PairMirrorKey(), STATE_MASTERED);
            }
            persistAndSync(player);
            player.sendSystemMessage(
                Component.translatable("message.enchantment_fusion.page.mastered", pick.title().copy().withStyle(ChatFormatting.GOLD)),
                true
            );
            return true;
        }

        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.page.all_known"), true);
        return false;
    }

    private static void persistAndSync(ServerPlayer player) {
        MinecraftServer srv = player.level().getServer();
        if (srv != null) {
            saveWorldFile(srv);
        }
        FusionNetworking.sendCompendiumSync(player, encodeStates(player.getUUID()));
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
        Map<String, Integer> next = new ConcurrentHashMap<>();
        for (String token : encoded) {
            if (token == null || token.isBlank()) continue;
            int eq = token.lastIndexOf('=');
            if (eq > 0 && eq < token.length() - 1) {
                try {
                    int st = Integer.parseInt(token.substring(eq + 1));
                    next.put(token.substring(0, eq), Math.max(STATE_UNKNOWN, Math.min(STATE_MASTERED, st)));
                } catch (NumberFormatException ignored) {
                    next.put(token, STATE_MASTERED);
                }
            } else {
                next.put(token, STATE_MASTERED);
            }
        }
        CLIENT_STATES = Collections.unmodifiableMap(next);
    }

    public static int clientState(String key) {
        return CLIENT_STATES.getOrDefault(key, STATE_UNKNOWN);
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
                    // Backward-compatibility with 0.1.0 - 0.3.1 saves!
                    for (JsonElement elem : val.getAsJsonArray()) {
                        states.put(elem.getAsString(), STATE_MASTERED);
                    }
                } else if (val.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> kv : val.getAsJsonObject().entrySet()) {
                        states.put(kv.getKey(), Math.max(STATE_UNKNOWN, Math.min(STATE_MASTERED, kv.getValue().getAsInt())));
                    }
                }
                SERVER_STATES.put(uuid, states);
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to read compendium file {}: {}", path, ex.toString());
        }
    }

    private static void saveWorldFile(MinecraftServer server) {
        Path path = resolveSaveFile(server);
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
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            EnchantmentFusion.LOGGER.warn("[EnchantmentFusion] Failed to save compendium file {}: {}", path, ex.toString());
        }
    }
}
