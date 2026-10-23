package chiseled_enchanting_table.mechanics.resonance;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, server-thread-confined per-world-session scheduler. The server adapter must collect ALL eligible covering tables
 * for a window, then settle once. No item timestamp is trusted and no world/ItemStack is mutated here.
 * K must include world/session, shelf position and slot; bookIdentity is compared by reference.
 */
public final class RegenerationScheduler<K> {
    public static final int WINDOW_TICKS = 20;
    public static final int MAX_OBSERVATIONS = 65_536;

    public record Observation<K>(K slot, Object bookIdentity, int wear, int intervalTicks) {
        public Observation {
            Objects.requireNonNull(slot);
            Objects.requireNonNull(bookIdentity);
            if (wear <= 0 || intervalTicks < WINDOW_TICKS) {
                throw new IllegalArgumentException("Only worn books at valid intervals can be scheduled");
            }
        }
    }
    public record Recovery(Object expectedIdentity, int points, int remainingWear) {}
    private record Progress(Object identity, int expectedWear, int interval, long ticks) {}
    private Map<K, Progress> previous = Map.of();
    private long lastWindow = -1;

    /**
     * A missing window, changed book/wear/rate, or conflicting observations starts a new residency.
     * A full first observation window earns no credit: insertion time within it is not known.
     * Overlap uses the fastest eligible rate, never sums rates. Unobserved slots are evicted.
     */
    public Map<K, Recovery> settle(long window, Collection<Observation<K>> observations) {
        if (window < 0 || window <= lastWindow) throw new IllegalArgumentException("Non-increasing window");
        if (observations.size() > MAX_OBSERVATIONS) throw new IllegalArgumentException("Too many observations");
        Map<K, Observation<K>> selected = new HashMap<>();
        Set<K> conflicts = new HashSet<>();
        for (Observation<K> observation : observations) {
            if (conflicts.contains(observation.slot())) continue;
            Observation<K> existing = selected.get(observation.slot());
            if (existing != null && (existing.bookIdentity() != observation.bookIdentity()
                || existing.wear() != observation.wear())) {
                selected.remove(observation.slot());
                conflicts.add(observation.slot());
            } else if (existing == null || observation.intervalTicks() < existing.intervalTicks()) {
                selected.put(observation.slot(), observation);
            }
        }
        Map<K, Progress> next = new HashMap<>();
        Map<K, Recovery> recoveries = new HashMap<>();
        boolean continuous = lastWindow >= 0 && window - lastWindow == 1;
        selected.forEach((key, observation) -> {
            Progress old = previous.get(key);
            boolean sameResidency = continuous && old != null && old.identity() == observation.bookIdentity()
                && old.expectedWear() == observation.wear() && old.interval() == observation.intervalTicks();
            long ticks = sameResidency ? old.ticks() + WINDOW_TICKS : 0;
            int points = (int) Math.min(observation.wear(), ticks / observation.intervalTicks());
            int remaining = observation.wear() - points;
            if (points > 0) recoveries.put(key, new Recovery(observation.bookIdentity(), points, remaining));
            if (remaining > 0) {
                next.put(key, new Progress(observation.bookIdentity(), remaining, observation.intervalTicks(),
                    ticks % observation.intervalTicks()));
            }
        });
        previous = next;
        lastWindow = window;
        return Map.copyOf(recoveries);
    }

    /** Partial recovery only: fully restored slots are already evicted and need no acknowledgement. */
    public void acknowledgeReplacement(K slot, Object expectedIdentity, Object replacementIdentity, int remainingWear) {
        Objects.requireNonNull(replacementIdentity);
        Progress state = previous.get(slot);
        if (state == null || state.identity() != expectedIdentity || state.expectedWear() != remainingWear) {
            throw new IllegalStateException("Stale regeneration acknowledgement");
        }
        // State is private and thread-confined; cloning the entire map per recovered book would
        // turn a large simultaneous recovery batch into quadratic work.
        previous.put(slot, new Progress(replacementIdentity, remainingWear, state.interval(), state.ticks()));
    }

    public int trackedSlots() { return previous.size(); }
    public void clear() { previous = Map.of(); lastWindow = -1; }
}
