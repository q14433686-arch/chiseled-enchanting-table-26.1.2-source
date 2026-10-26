package chiseled_enchanting_table.research;

import java.util.*;

/** Immutable, validated research graph. No prices, ticking, item wear or enchantment effects. */
public final class ResearchCatalog {
    public static final int MAX_TOPICS = 1024;
    public record Hypothesis(String id, Set<String> evidence, String conclusion) {
        public Hypothesis {
            id = key(id); conclusion = key(conclusion); evidence = keys(evidence);
            if (evidence.isEmpty()) throw new IllegalArgumentException("Hypothesis needs evidence");
        }
    }
    public record Topic(String id, int revision, String title, String question,
                        Set<String> prerequisites, int breadth, int shelves,
                        Map<String, Integer> depth, Map<String, Hypothesis> hypotheses) {
        public Topic {
            id = key(id); title = key(title); question = key(question);
            prerequisites = keys(prerequisites); depth = Map.copyOf(depth); hypotheses = Map.copyOf(hypotheses);
            if (revision < 1 || breadth < 0 || breadth > 1024 || shelves < 0 || shelves > 4096
                || depth.size() > 128 || hypotheses.isEmpty() || hypotheses.size() > 16)
                throw new IllegalArgumentException("Invalid topic bounds: " + id);
            depth.forEach((k,v) -> { key(k); if (v < 1 || v > 255) throw new IllegalArgumentException("Invalid depth"); });
            hypotheses.forEach((k,v) -> { if (!k.equals(v.id())) throw new IllegalArgumentException("Hypothesis key mismatch"); });
        }
    }
    /** Trusted server scan result: distinct knowledge ids and best quality, never raw book counts. */
    public record Library(int shelves, Map<String, Integer> knowledge) {
        public Library {
            if (shelves < 0 || shelves > 4096 || knowledge.size() > 4096) throw new IllegalArgumentException("Invalid library");
            knowledge = Map.copyOf(knowledge);
            knowledge.forEach((k,v) -> { key(k); if (v < 1 || v > 255) throw new IllegalArgumentException("Invalid knowledge"); });
        }
    }
    private final Map<String, Topic> topics;
    public ResearchCatalog(Collection<Topic> definitions) {
        if (definitions.size() > MAX_TOPICS) throw new IllegalArgumentException("Too many topics");
        Map<String, Topic> next = new TreeMap<>();
        for (Topic t : definitions) if (next.putIfAbsent(t.id(), t) != null) throw new IllegalArgumentException("Duplicate topic " + t.id());
        Map<String,String> owners = new HashMap<>();
        for (Topic t : next.values()) for (Hypothesis h : t.hypotheses().values()) {
            String previous = owners.putIfAbsent(h.conclusion(),t.id());
            if (previous != null && !previous.equals(t.id())) throw new IllegalArgumentException("Ambiguous conclusion " + h.conclusion());
        }
        for (Topic t : next.values()) for (String p : t.prerequisites())
            if (!owners.containsKey(p)) throw new IllegalArgumentException("Unknown prerequisite conclusion " + p);
        Set<String> done = new HashSet<>(), visiting = new HashSet<>();
        for (String id : next.keySet()) visit(id, next, owners, done, visiting);
        topics = Map.copyOf(next);
    }
    private static void visit(String id, Map<String, Topic> topics, Map<String,String> owners, Set<String> done, Set<String> visiting) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Research cycle at " + id);
        for (String p : topics.get(id).prerequisites()) visit(owners.get(p), topics, owners, done, visiting);
        visiting.remove(id); done.add(id);
    }
    public Map<String, Topic> topics() { return topics; }
    public Topic topic(String id) {
        Topic t = topics.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown research " + id);
        return t;
    }
    static String key(String s) {
        if (s == null || s.length() > 192 || !s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid research key: " + s);
        return s;
    }
    static Set<String> keys(Set<String> values) {
        if (values.size() > 128) throw new IllegalArgumentException("Too many keys");
        values.forEach(ResearchCatalog::key); return Set.copyOf(values);
    }
}
