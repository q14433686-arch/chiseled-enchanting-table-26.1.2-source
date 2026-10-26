package chiseled_enchanting_table.config;

import chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy;
import static chiseled_enchanting_table.config.ModRuntimeConfig.GRANULAR_RULE_ATOM_KEYS;
import static chiseled_enchanting_table.config.RuntimeConfigJson.boolOr;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure, strict policy JSON and legacy atom-key expansion, independent of runtime publication. */
final class RuntimePolicyJson {
    private RuntimePolicyJson() {}
    static JsonObject toJson(PackPolicy policy) {
        JsonObject obj = new JsonObject();
        obj.addProperty("enforce_datapack_priority", policy.enforceDatapackPriority());
        obj.addProperty("lock_in_game_config", policy.lockInGameConfig());
        com.google.gson.JsonArray dpArr = new com.google.gson.JsonArray();
        for (String k : policy.datapackOverriddenKeys()) dpArr.add(k);
        obj.add("datapack_overridden_keys", dpArr);
        com.google.gson.JsonArray lockArr = new com.google.gson.JsonArray();
        for (String k : policy.lockedKeys()) lockArr.add(k);
        obj.add("locked_keys", lockArr);
        return obj;
    }

    static PackPolicy fromJson(JsonObject root, Set<String> externalDatapackKeys) {
        if (root != null) {
            for (String key : Set.of("enforce_datapack_priority", "lock_in_game_config")) {
                if (root.has(key) && (!root.get(key).isJsonPrimitive()
                    || !root.getAsJsonPrimitive(key).isBoolean()))
                    throw new IllegalArgumentException("Policy flag must be boolean: " + key);
            }
            for (String key : Set.of("locked_keys", "datapack_overridden_keys")) {
                if (!root.has(key)) continue;
                if (!root.get(key).isJsonArray()) throw new IllegalArgumentException("Policy keys must be array: " + key);
                for (var element : root.getAsJsonArray(key)) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Policy key must be a string");
                    String k = element.getAsString();
                    if (!"classic_1_0_3_mode".equals(k)
                        && ConfigCatalog.ALL_OPTIONS.stream().noneMatch(option -> option.key().equals(k))) {
                        throw new IllegalArgumentException("Unknown policy key: " + k);
                    }
                }
            }
        }
        boolean enforceDp = root != null && boolOr(root, "enforce_datapack_priority", false);
        boolean lockAll = root != null && boolOr(root, "lock_in_game_config", false);
        Set<String> dpKeys = new LinkedHashSet<>();
        if (externalDatapackKeys != null) {
            dpKeys.addAll(externalDatapackKeys);
        }
        if (root != null && root.has("datapack_overridden_keys") && root.get("datapack_overridden_keys").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("datapack_overridden_keys")) {
                if (el.isJsonPrimitive()) {
                    String k = el.getAsString();
                    if ("classic_1_0_3_mode".equals(k)) {
                        dpKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
                    } else {
                        dpKeys.add(k);
                    }
                }
            }
        }
        Set<String> locked = new LinkedHashSet<>();
        if (root != null && root.has("locked_keys") && root.get("locked_keys").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("locked_keys")) {
                if (el.isJsonPrimitive()) {
                    String k = el.getAsString();
                    if ("classic_1_0_3_mode".equals(k)) {
                        locked.addAll(GRANULAR_RULE_ATOM_KEYS);
                    } else {
                        locked.add(k);
                    }
                }
            }
        }
        return new PackPolicy(enforceDp, lockAll, dpKeys, locked);
    }
}
