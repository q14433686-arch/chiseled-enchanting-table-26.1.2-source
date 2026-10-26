package chiseled_enchanting_table.research;

import java.util.HashMap;
import java.util.Map;

/** One scan's distinct knowledge, independent of book stacks, wear, generation and lock metadata. */
public final class ResearchKnowledge {
    private final Map<String,Integer> best = new HashMap<>();
    public void include(String id, int level) {
        ResearchCatalog.key(id);
        if (level < 1 || level > 255) throw new IllegalArgumentException("Invalid knowledge level");
        if (!best.containsKey(id) && best.size() >= 4096) throw new IllegalStateException("Research knowledge limit exceeded");
        best.merge(id,level,Math::max);
    }
    public Map<String,Integer> snapshot() { return Map.copyOf(best); }
}
