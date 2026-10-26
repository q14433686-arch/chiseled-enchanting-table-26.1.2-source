package chiseled_enchanting_table.research;

import java.util.*;
import static chiseled_enchanting_table.research.ResearchCatalog.*;

/** Pure state transitions. Adapters must supply server-observed library conditions and evidence. */
public final class ResearchEngine {
    private ResearchEngine() {}
    public enum Status { AVAILABLE, PREREQUISITES, BREADTH, SHELVES, DEPTH, COMPLETE, STALE, NO_ATTEMPT, IRRELEVANT, DUPLICATE, OBSERVED, EVIDENCE }
    public record Preview(Status status, Set<String> missingPrerequisites, Set<String> missingDepth,
                          int missingBreadth, int missingShelves) {
        public Preview { missingPrerequisites = Set.copyOf(missingPrerequisites); missingDepth = Set.copyOf(missingDepth); }
    }
    public record Transition(Status status, ResearchJournal journal) {}
    public static Preview preview(Topic t, ResearchJournal j, Library library) {
        Set<String> missing = new TreeSet<>(t.prerequisites()); missing.removeAll(j.conclusions());
        Set<String> depth = new TreeSet<>();
        t.depth().forEach((id,level) -> { if (library.knowledge().getOrDefault(id,0) < level) depth.add(id); });
        int b = Math.max(0,t.breadth()-library.knowledge().size()), s = Math.max(0,t.shelves()-library.shelves());
        Status status = complete(t,j) ? Status.COMPLETE : !missing.isEmpty() ? Status.PREREQUISITES
            : b > 0 ? Status.BREADTH : s > 0 ? Status.SHELVES : !depth.isEmpty() ? Status.DEPTH : Status.AVAILABLE;
        return new Preview(status,missing,depth,b,s);
    }
    /** Prerequisites name stable conclusions, not topic ids. */
    public static boolean complete(Topic t, ResearchJournal j) {
        return t.hypotheses().values().stream().anyMatch(h -> j.conclusions().contains(h.conclusion()));
    }
    public static Transition begin(Topic t, ResearchJournal j, Library library, String hypothesis) {
        if (!t.hypotheses().containsKey(hypothesis)) throw new IllegalArgumentException("Unknown hypothesis");
        Status status = preview(t,j,library).status();
        if (status != Status.AVAILABLE) return new Transition(status,j);
        var old = j.attempts().get(t.id());
        if (old != null && old.revision() == t.revision() && old.hypothesis().equals(hypothesis))
            return new Transition(Status.AVAILABLE,j); // retries cannot erase observations
        var attempts = new HashMap<>(j.attempts());
        attempts.put(t.id(),new ResearchJournal.Attempt(t.revision(),hypothesis,Set.of()));
        return new Transition(Status.AVAILABLE,new ResearchJournal(j.conclusions(),attempts));
    }
    /** Call only after an evidence provider has verified actual server-side context. */
    public static Transition observe(Topic t, ResearchJournal j, String evidence) {
        if (complete(t,j)) return new Transition(Status.COMPLETE,j);
        var a = j.attempts().get(t.id());
        if (a == null) return new Transition(Status.NO_ATTEMPT,j);
        if (a.revision() != t.revision() || !t.hypotheses().containsKey(a.hypothesis())) return new Transition(Status.STALE,j);
        var h = t.hypotheses().get(a.hypothesis());
        if (!h.evidence().contains(evidence)) return new Transition(Status.IRRELEVANT,j);
        if (a.observations().contains(evidence)) return new Transition(Status.DUPLICATE,j);
        var observations = new HashSet<>(a.observations()); observations.add(evidence);
        var attempts = new HashMap<>(j.attempts());
        attempts.put(t.id(),new ResearchJournal.Attempt(t.revision(),a.hypothesis(),observations));
        return new Transition(Status.OBSERVED,new ResearchJournal(j.conclusions(),attempts));
    }
    /** Field observations survive leaving the library; committing a conclusion rechecks the live workspace. */
    public static Transition conclude(Topic t, ResearchJournal j, Library library) {
        if (complete(t,j)) return new Transition(Status.COMPLETE,j);
        var a = j.attempts().get(t.id());
        if (a == null) return new Transition(Status.NO_ATTEMPT,j);
        if (a.revision() != t.revision() || !t.hypotheses().containsKey(a.hypothesis())) return new Transition(Status.STALE,j);
        Status gate = preview(t,j,library).status();
        if (gate != Status.AVAILABLE) return new Transition(gate,j);
        var h = t.hypotheses().get(a.hypothesis());
        if (!a.observations().containsAll(h.evidence())) return new Transition(Status.EVIDENCE,j);
        var conclusions = new HashSet<>(j.conclusions()); conclusions.add(h.conclusion());
        var attempts = new HashMap<>(j.attempts()); attempts.remove(t.id());
        return new Transition(Status.COMPLETE,new ResearchJournal(conclusions,attempts));
    }
    /** Cancelling abandons this experiment only; completed understanding is never revoked. */
    public static ResearchJournal cancel(ResearchJournal j, String topic) {
        var attempts = new HashMap<>(j.attempts()); attempts.remove(topic);
        return new ResearchJournal(j.conclusions(),attempts);
    }
}
