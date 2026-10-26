package chiseled_enchanting_table.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import static chiseled_enchanting_table.data.DataJson.*;

/** LootInjectionEntry domain; independent of reload/publication orchestration. */
public record LootInjectionEntry(Identifier enchantmentId, int level, int weight, boolean empty, String special) {
    static LootInjectionEntry fromJson(JsonObject json) {
        int weight = Math.max(1, intOr(json, "weight", 1));
        // Empty filler entry (upstream's EmptyEntry.builder().weight(N)): keeps the pool total
        // roll-weight identical to upstream even when we don't want to drop a book.
        if (json.has("empty") && json.get("empty").getAsBoolean()) {
            return new LootInjectionEntry(null, 0, weight, true, null);
        }
        if (!json.has("enchantment")) return null;
        Identifier enchantmentId = parseIdentifier(json.get("enchantment").getAsString());
        int level = Math.max(1, intOr(json, "level", 1));
        String special = json.has("special") && !json.get("special").isJsonNull()
            ? json.get("special").getAsString() : null;
        return new LootInjectionEntry(enchantmentId, level, weight, false, special);
    }
}
