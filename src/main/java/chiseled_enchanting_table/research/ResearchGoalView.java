package chiseled_enchanting_table.research;

import java.util.*;

/** Target-first page model: choose enchantment/branch/level, then inspect routes and the next action. */
public final class ResearchGoalView {
    public record Goal(String enchantment, String branch, int level) {
        public Goal {
            enchantment = ResearchCatalog.key(enchantment); branch = ResearchCatalog.key(branch);
            if (level < 1 || level > 255) throw new IllegalArgumentException("Invalid research goal");
        }
    }
    public enum Action { LEARNED, REQUIREMENTS, CHOOSE_ROUTE, CONTINUE_FIELDWORK, ANALYZE_SAMPLE, FIND_SAMPLE, CONCLUDE, RESTART_REVISION }
    public record Route(String topic, String hypothesis, ResearchEngine.Preview requirements,
                        Set<String> missingEvidence, boolean selected, Action next,
                        String sampleEnchantment, int sampleLevel) {
        public Route { missingEvidence = Set.copyOf(missingEvidence); }
    }
    public record View(Goal goal, int learnedLimit, List<Route> routes) {
        public View { routes = List.copyOf(routes); }
    }
    private ResearchGoalView() {}
    /** Includes only routes for the lowest defined grant meeting the requested level, never another branch. */
    public static View build(ResearchCatalog catalog, ResearchJournal journal, ResearchCatalog.Library library, Goal goal) {
        int learned = catalog.outcomes().manufacturingLimit(journal,goal.enchantment(),goal.branch());
        var candidates = catalog.outcomes().grants().stream().filter(g -> g.enchantment().equals(goal.enchantment())
            && g.branch().equals(goal.branch()) && g.maxLevel() >= goal.level()).toList();
        int closest = candidates.stream().mapToInt(ResearchOutcomes.Grant::maxLevel).min().orElse(0);
        Set<String> conclusions = new HashSet<>();
        candidates.stream().filter(g -> g.maxLevel() == closest).forEach(g -> conclusions.add(g.conclusion()));
        List<Route> routes = new ArrayList<>();
        var analyses = catalog.analyses().rules();
        for (var topic : new TreeMap<>(catalog.topics()).values()) {
            var requirements = ResearchEngine.preview(topic,journal,library);
            var attempt = journal.attempts().get(topic.id());
            for (var hypothesis : new TreeMap<>(topic.hypotheses()).values()) {
                if (!conclusions.contains(hypothesis.conclusion())) continue;
                boolean current = attempt != null && attempt.revision() == topic.revision();
                boolean selected = current && attempt.hypothesis().equals(hypothesis.id());
                Set<String> missing = new TreeSet<>(hypothesis.evidence());
                if (current) missing.removeAll(attempt.observations());
                if (learned >= goal.level()) missing.clear();
                var analysis = analyses.stream().filter(a -> a.topic().equals(topic.id()) && a.hypothesis().equals(hypothesis.id())).findFirst();
                String sample = analysis.map(ResearchSampleAnalysis.Rule::enchantment).orElse(null);
                int sampleLevel = analysis.map(ResearchSampleAnalysis.Rule::minimumLevel).orElse(0);
                Action action;
                if (learned >= goal.level()) action = Action.LEARNED;
                else if (requirements.status() != ResearchEngine.Status.AVAILABLE) action = Action.REQUIREMENTS;
                else if (attempt != null && !current) action = Action.RESTART_REVISION;
                else if (!selected) action = Action.CHOOSE_ROUTE;
                else if (missing.isEmpty()) action = Action.CONCLUDE;
                else if (analysis.isPresent() && missing.contains(analysis.get().evidence()))
                    action = library.knowledge().getOrDefault(sample,0) >= sampleLevel ? Action.ANALYZE_SAMPLE : Action.FIND_SAMPLE;
                else action = Action.CONTINUE_FIELDWORK;
                routes.add(new Route(topic.id(),hypothesis.id(),requirements,missing,selected,action,sample,sampleLevel));
            }
        }
        return new View(goal,learned,routes);
    }
}
