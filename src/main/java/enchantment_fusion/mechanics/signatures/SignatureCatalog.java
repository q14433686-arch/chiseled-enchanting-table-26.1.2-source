package enchantment_fusion.mechanics.signatures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Data-driven catalog for directional Signature Resonances loaded from
 * {@code data/enchantment_fusion/enchantment_fusion/signatures.json} (and reloadable via datapacks).
 */
public final class SignatureCatalog {
    public static final String CLASSPATH_RESOURCE =
        "/data/enchantment_fusion/enchantment_fusion/signatures.json";

    public record EquippedSignature(
        String sigId,
        FusedPair pair,
        EquipmentSlot slot,
        ItemStack stack
    ) {
        public int totalLevel() {
            return pair.primaryLevel() + pair.secondaryLevel();
        }
    }

    public static final EquipmentSlot[] ALL_EQUIP_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    public record Snapshot(
        Map<String, SignatureEntry> directMap,
        Map<String, SignatureEntry> wildcardSecondaryMap,
        List<SignatureEntry> uniqueSignatures,
        Map<String, String> domainById
    ) {}

    private static volatile Snapshot activeSnapshot = loadClasspathDefault();

    private SignatureCatalog() {}

    private static Snapshot loadClasspathDefault() {
        try (InputStream in = SignatureCatalog.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in != null) {
                try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    return parseFromJson(obj);
                }
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Failed to bootstrap classpath signatures.json", ex);
        }
        return new Snapshot(Map.of(), Map.of(), List.of(), Map.of());
    }

    public static Snapshot parseFromJson(@Nullable JsonObject root) {
        if (root == null || !root.has("signatures") || !root.get("signatures").isJsonObject()) {
            return activeSnapshot != null
                ? activeSnapshot
                : new Snapshot(Map.of(), Map.of(), List.of(), Map.of());
        }
        Map<String, SignatureEntry> direct = new LinkedHashMap<>();
        Map<String, SignatureEntry> wildcards = new LinkedHashMap<>();
        List<SignatureEntry> ordered = new ArrayList<>();
        Map<String, String> domains = new LinkedHashMap<>();

        JsonObject sigObj = root.getAsJsonObject("signatures");
        for (Map.Entry<String, JsonElement> e : sigObj.entrySet()) {
            String id = e.getKey();
            if (id == null || id.isBlank() || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject def = e.getValue().getAsJsonObject();
            String primary = def.has("primary") ? def.get("primary").getAsString() : "";
            String secondary = def.has("secondary") ? def.get("secondary").getAsString() : "";
            String domain = def.has("domain") ? def.get("domain").getAsString() : "melee";
            if (primary.isBlank() || secondary.isBlank()) {
                continue;
            }
            SignatureEntry entry = new SignatureEntry(id, primary, secondary);
            if ("*".equals(primary)) {
                wildcards.put(secondary, entry);
            } else {
                direct.put(primary + ">" + secondary, entry);
            }
            ordered.add(entry);
            domains.put(id, domain);
        }
        return new Snapshot(
            Collections.unmodifiableMap(direct),
            Collections.unmodifiableMap(wildcards),
            Collections.unmodifiableList(ordered),
            Collections.unmodifiableMap(domains)
        );
    }

    /** Publish a previously parsed snapshot, so configuration changes can be staged first. */
    public static void publish(Snapshot snapshot) {
        if (snapshot != null) activeSnapshot = snapshot;
    }

    public static void reloadFromJson(@Nullable JsonObject mergedRoot) {
        if (mergedRoot == null) {
            activeSnapshot = loadClasspathDefault();
            return;
        }
        Snapshot parsed = parseFromJson(mergedRoot);
        if (!parsed.uniqueSignatures().isEmpty()) {
            activeSnapshot = parsed;
        }
    }

    public static List<SignatureEntry> allUniqueSignatures() {
        return activeSnapshot.uniqueSignatures();
    }

    public static String domainOf(String sigId) {
        return activeSnapshot.domainById().getOrDefault(sigId, "melee");
    }

    public static Optional<SignatureEntry> resolve(String primaryId, String secondaryId) {
        if (primaryId == null || secondaryId == null || primaryId.equals(secondaryId)) {
            return Optional.empty();
        }
        Snapshot snap = activeSnapshot;
        SignatureEntry exact = snap.directMap().get(primaryId + ">" + secondaryId);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (!"*".equals(primaryId)) {
            SignatureEntry wild = snap.wildcardSecondaryMap().get(secondaryId);
            if (wild != null) {
                return Optional.of(wild);
            }
        }
        return Optional.empty();
    }

    public static Optional<SignatureEntry> resolve(FusedPair pair) {
        if (pair == null) {
            return Optional.empty();
        }
        return resolve(pair.primaryId(), pair.secondaryId());
    }

    public static boolean stackHasSignature(ItemStack stack, String signatureId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        FusedEnchantmentList list = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair p : list.pairs()) {
            Optional<SignatureEntry> entry = resolve(p);
            if (entry.isPresent() && entry.get().id().equals(signatureId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasEquippedSignature(ServerPlayer player, String signatureId) {
        for (EquipmentSlot slot : ALL_EQUIP_SLOTS) {
            if (stackHasSignature(player.getItemBySlot(slot), signatureId)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, EquippedSignature> collectUniqueEquippedSignatures(ServerPlayer player) {
        Map<String, EquippedSignature> bestById = new LinkedHashMap<>();
        for (EquipmentSlot slot : ALL_EQUIP_SLOTS) {
            ItemStack st = player.getItemBySlot(slot);
            if (st.isEmpty()) {
                continue;
            }
            FusedEnchantmentList list = st.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair p : list.pairs()) {
                Optional<SignatureEntry> entry = resolve(p);
                if (entry.isEmpty()) {
                    continue;
                }
                String sigId = entry.get().id();
                EquippedSignature cand = new EquippedSignature(sigId, p, slot, st);
                EquippedSignature prev = bestById.get(sigId);
                if (prev == null || cand.totalLevel() > prev.totalLevel()) {
                    bestById.put(sigId, cand);
                }
            }
        }
        return bestById;
    }
}
