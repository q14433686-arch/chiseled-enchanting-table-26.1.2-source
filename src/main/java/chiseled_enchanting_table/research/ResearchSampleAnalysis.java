package chiseled_enchanting_table.research;

import java.util.*;

/** Non-consuming analysis route. Samples supply evidence, not automatic learning or higher registry caps. */
public final class ResearchSampleAnalysis {
    public record Rule(String topic, String hypothesis, String evidence, String enchantment, int minimumLevel) {
        public Rule {
            topic = ResearchCatalog.key(topic); hypothesis = ResearchCatalog.key(hypothesis);
            evidence = ResearchCatalog.key(evidence); enchantment = ResearchCatalog.key(enchantment);
            if (minimumLevel < 1 || minimumLevel > 255) throw new IllegalArgumentException("Invalid sample quality");
        }
    }
    private final Map<String,Rule> rules;
    public ResearchSampleAnalysis(Collection<Rule> definitions, ResearchCatalog catalog) {
        if (definitions.size() > 4096) throw new IllegalArgumentException("Too many sample analysis rules");
        Map<String,Rule> next = new HashMap<>();
        for (Rule rule : definitions) {
            var topic = catalog.topic(rule.topic()); var hypothesis = topic.hypotheses().get(rule.hypothesis());
            if (hypothesis == null || !hypothesis.evidence().contains(rule.evidence()))
                throw new IllegalArgumentException("Analysis must produce evidence for its defined hypothesis");
            if (next.putIfAbsent(rule.topic()+"|"+rule.hypothesis(),rule) != null)
                throw new IllegalArgumentException("Duplicate sample analysis route");
        }
        rules = Map.copyOf(next);
    }
    public Collection<Rule> rules() { return rules.values(); }
    public ResearchEngine.Transition analyze(ResearchCatalog.Topic topic, ResearchJournal journal, ResearchCatalog.Library library) {
        var attempt = journal.attempts().get(topic.id());
        if (attempt == null) return new ResearchEngine.Transition(ResearchEngine.Status.NO_ATTEMPT,journal);
        var rule = rules.get(topic.id()+"|"+attempt.hypothesis());
        if (rule == null || library.knowledge().getOrDefault(rule.enchantment(),0) < rule.minimumLevel())
            return new ResearchEngine.Transition(ResearchEngine.Status.EVIDENCE,journal);
        var gate = ResearchEngine.preview(topic,journal,library).status();
        if (gate != ResearchEngine.Status.AVAILABLE) return new ResearchEngine.Transition(gate,journal);
        return ResearchEngine.observe(topic,journal,rule.evidence());
    }
}
