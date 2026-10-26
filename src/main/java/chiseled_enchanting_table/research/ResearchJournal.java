package chiseled_enchanting_table.research;

import java.util.*;

/** Player understanding. Safe to copy/save; contains no inventory references or transient book locks. */
public record ResearchJournal(Set<String> conclusions, Map<String, Attempt> attempts) {
    public record Attempt(int revision, String hypothesis, Set<String> observations) {
        public Attempt {
            if (revision < 1) throw new IllegalArgumentException("Invalid revision");
            hypothesis = ResearchCatalog.key(hypothesis); observations = ResearchCatalog.keys(observations);
        }
    }
    public ResearchJournal {
        if (conclusions.size() > 8192 || attempts.size() > ResearchCatalog.MAX_TOPICS)
            throw new IllegalArgumentException("Research journal too large");
        conclusions.forEach(ResearchCatalog::key); attempts.keySet().forEach(ResearchCatalog::key);
        conclusions = Set.copyOf(conclusions); attempts = Map.copyOf(attempts);
    }
    public static ResearchJournal empty() { return new ResearchJournal(Set.of(), Map.of()); }
}
