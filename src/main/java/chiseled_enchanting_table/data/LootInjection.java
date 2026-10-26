package chiseled_enchanting_table.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import static chiseled_enchanting_table.data.DataJson.*;

/** LootInjection domain; independent of reload/publication orchestration. */
public record LootInjection(String tableSelector, int rolls, List<LootInjectionEntry> entries) {
    static LootInjection fromJson(JsonObject json) {
        String table = json.get("table").getAsString();
        int rolls = Math.max(1, intOr(json, "rolls", 1));
        List<LootInjectionEntry> loadedEntries = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("entries");
        if (array != null) {
            for (JsonElement element : array) {
                LootInjectionEntry entry = LootInjectionEntry.fromJson(element.getAsJsonObject());
                if (entry != null) {
                    loadedEntries.add(entry);
                }
            }
        }
        if (loadedEntries.isEmpty()) {
            return null;
        }
        return new LootInjection(table, rolls, List.copyOf(loadedEntries));
    }

    public boolean isTag() {
        return tableSelector != null && !tableSelector.isEmpty() && tableSelector.charAt(0) == '#';
    }

    public Identifier tableId() {
        return parseIdentifier(isTag() ? tableSelector.substring(1) : tableSelector);
    }
}
