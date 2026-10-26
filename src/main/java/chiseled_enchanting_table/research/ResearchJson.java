package chiseled_enchanting_table.research;

import com.google.gson.*;
import java.util.*;
import static chiseled_enchanting_table.research.ResearchCatalog.*;

/** Explicit versioned format. Never deserialize mutable game objects or accept Gson record defaults. */
public final class ResearchJson {
    private ResearchJson() {}
    public static ResearchCatalog catalog(JsonObject root) {
        version(root);
        var definitions = new ArrayList<Topic>();
        JsonArray topics = root.getAsJsonArray("topics");
        if (topics.size() > MAX_TOPICS) throw new IllegalArgumentException("Too many topics");
        for (JsonElement element : topics) {
            JsonObject t = element.getAsJsonObject();
            Map<String,Integer> depth = new HashMap<>();
            t.getAsJsonObject("depth").entrySet().forEach(e -> depth.put(e.getKey(),integer(e.getValue())));
            Map<String,Hypothesis> hypotheses = new HashMap<>();
            for (JsonElement value : t.getAsJsonArray("hypotheses")) {
                JsonObject h = value.getAsJsonObject();
                var definition = new Hypothesis(h.get("id").getAsString(),strings(h.getAsJsonArray("evidence")),h.get("conclusion").getAsString());
                if (hypotheses.putIfAbsent(definition.id(),definition) != null) throw new IllegalArgumentException("Duplicate hypothesis");
            }
            definitions.add(new Topic(t.get("id").getAsString(),integer(t.get("revision")),t.get("title").getAsString(),
                t.get("question").getAsString(),strings(t.getAsJsonArray("prerequisites")),integer(t.get("breadth")),
                integer(t.get("shelves")),depth,hypotheses));
        }
        return new ResearchCatalog(definitions);
    }
    public static JsonObject journal(ResearchJournal journal) {
        JsonObject root = new JsonObject(); root.addProperty("schema",1);
        root.add("conclusions",array(journal.conclusions()));
        JsonObject attempts = new JsonObject();
        new TreeMap<>(journal.attempts()).forEach((id,a) -> {
            JsonObject value = new JsonObject(); value.addProperty("revision",a.revision());
            value.addProperty("hypothesis",a.hypothesis()); value.add("observations",array(a.observations())); attempts.add(id,value);
        });
        root.add("attempts",attempts); return root;
    }
    public static ResearchJournal journal(JsonObject root) {
        version(root);
        Map<String,ResearchJournal.Attempt> attempts = new HashMap<>();
        JsonObject values = root.getAsJsonObject("attempts");
        if (values.size() > MAX_TOPICS) throw new IllegalArgumentException("Too many attempts");
        values.entrySet().forEach(e -> {
            JsonObject a = e.getValue().getAsJsonObject();
            attempts.put(e.getKey(),new ResearchJournal.Attempt(integer(a.get("revision")),a.get("hypothesis").getAsString(),strings(a.getAsJsonArray("observations"))));
        });
        return new ResearchJournal(strings(root.getAsJsonArray("conclusions"),8192),attempts);
    }
    private static void version(JsonObject root) {
        if (integer(root.get("schema")) != 1) throw new IllegalArgumentException("Unsupported research schema; original file must be preserved");
    }
    private static int integer(JsonElement v) { return v.getAsBigDecimal().intValueExact(); }
    private static JsonArray array(Set<String> values) {
        JsonArray out = new JsonArray(); new TreeSet<>(values).forEach(out::add); return out;
    }
    private static Set<String> strings(JsonArray values) { return strings(values,128); }
    private static Set<String> strings(JsonArray values,int limit) {
        if (values.size() > limit) throw new IllegalArgumentException("Too many research keys");
        Set<String> out = new HashSet<>();
        for (JsonElement value : values) if (!out.add(key(value.getAsString()))) throw new IllegalArgumentException("Duplicate research key");
        return out;
    }
}
