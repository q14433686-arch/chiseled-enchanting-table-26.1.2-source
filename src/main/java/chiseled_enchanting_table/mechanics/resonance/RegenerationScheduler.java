package chiseled_enchanting_table.mechanics.resonance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure, server-thread-confined per-world-session scheduler. The server adapter must collect ALL eligible covering tables
 * for a window, then settle once. No item timestamp is trusted and no world/ItemStack is mutated here.
 * K must include world/session, shelf position and slot; bookIdentity is compared by reference unless explicitly rebound
 * across a verified chunk-unload or world-restart boundary.
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

    public record SavedEntry<K>(K slot, Object bookIdentity, int expectedWear, int intervalTicks, long progressTicks) {
        public SavedEntry {
            Objects.requireNonNull(slot);
            Objects.requireNonNull(bookIdentity);
            if (expectedWear <= 0 || intervalTicks < WINDOW_TICKS || progressTicks < 0) {
                throw new IllegalArgumentException("Invalid saved regeneration entry");
            }
            progressTicks = Math.min(intervalTicks - 1L, progressTicks);
        }
    }

    private record Progress(Object identity, int expectedWear, int interval, long ticks, boolean paused) {}

    private Map<K, Progress> previous = new HashMap<>();
    private long lastWindow = -1;

    /**
     * Standard settlement where any unobserved slot is evicted immediately.
     */
    public Map<K, Recovery> settle(long window, Collection<Observation<K>> observations) {
        return settle(window, observations, ignored -> false);
    }

    /**
     * Settles one 20-tick observation window.
     * <ul>
     *   <li>A missing unpaused window, changed book reference, unexpected wear jump, or conflicting observation starts a new residency.</li>
     *   <li>Rate changes preserve already-earned fractional progress without retroactively repricing past slow ticks at a faster rate.</li>
     *   <li>Slots whose chunk (or covering table chunk) is unloaded may be retained in {@code paused} state via
     *       {@code retainPausedWhenUnobserved}; paused slots earn {@code 0} ticks while unloaded and resume from their
     *       exact fractional progress once observed again.</li>
     * </ul>
     */
    public Map<K, Recovery> settle(
        long window,
        Collection<Observation<K>> observations,
        Predicate<K> retainPausedWhenUnobserved
    ) {
        if (window < 0 || window <= lastWindow) throw new IllegalArgumentException("Non-increasing window");
        if (observations.size() > MAX_OBSERVATIONS) throw new IllegalArgumentException("Too many observations");
        Objects.requireNonNull(retainPausedWhenUnobserved);

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
            int targetInterval = observation.intervalTicks();
            long ticks = 0L;
            if (old != null && old.identity() == observation.bookIdentity() && old.expectedWear() == observation.wear()) {
                if (old.paused()) {
                    // Resuming from unloaded chunk / world restart: 0 ticks credited for the partial resume window,
                    // while prior earned fraction is preserved across interval adjustments.
                    ticks = scalePausedProgressTicks(old.ticks(), old.interval(), targetInterval);
                } else if (continuous) {
                    if (old.interval() == targetInterval) {
                        ticks = old.ticks() + WINDOW_TICKS;
                    } else {
                        long transitionInterval = Math.max(old.interval(), targetInterval);
                        double earnedFraction = ((double) old.ticks() / (double) old.interval())
                            + ((double) WINDOW_TICKS / (double) transitionInterval);
                        ticks = (long) Math.floor(earnedFraction * (double) targetInterval + 1.0e-9);
                    }
                }
            }
            int points = (int) Math.min(observation.wear(), ticks / targetInterval);
            int remaining = observation.wear() - points;
            if (points > 0) {
                recoveries.put(key, new Recovery(observation.bookIdentity(), points, remaining));
            }
            if (remaining > 0) {
                next.put(key, new Progress(
                    observation.bookIdentity(),
                    remaining,
                    targetInterval,
                    ticks % targetInterval,
                    false
                ));
            }
        });

        previous.forEach((key, old) -> {
            if (!selected.containsKey(key) && !conflicts.contains(key) && retainPausedWhenUnobserved.test(key)) {
                next.put(key, new Progress(old.identity(), old.expectedWear(), old.interval(), old.ticks(), true));
            }
        });

        previous = next;
        lastWindow = window;
        return Map.copyOf(recoveries);
    }

    private static long scalePausedProgressTicks(long oldTicks, int oldInterval, int newInterval) {
        if (oldTicks <= 0 || oldInterval <= 0 || newInterval <= 0) {
            return 0L;
        }
        if (oldInterval == newInterval) {
            return Math.min(newInterval - 1L, oldTicks);
        }
        long scaled = Math.round(((double) oldTicks * (double) newInterval) / (double) oldInterval);
        return Math.max(0L, Math.min(newInterval - 1L, scaled));
    }

    /** Partial recovery only: fully restored slots are already evicted and need no acknowledgement. */
    public void acknowledgeReplacement(K slot, Object expectedIdentity, Object replacementIdentity, int remainingWear) {
        Objects.requireNonNull(replacementIdentity);
        Progress state = previous.get(slot);
        if (state == null || state.identity() != expectedIdentity || state.expectedWear() != remainingWear) {
            throw new IllegalStateException("Stale regeneration acknowledgement");
        }
        previous.put(slot, new Progress(replacementIdentity, remainingWear, state.interval(), state.ticks(), state.paused()));
    }

    /** Preserves fractional progress when an already-tracked tome incurs additional in-place wear without moving slots. */
    public void acknowledgeInPlaceWear(K slot, Object expectedIdentity, Object updatedIdentity, int updatedWear) {
        Objects.requireNonNull(updatedIdentity);
        if (updatedWear <= 0) return;
        Progress state = previous.get(slot);
        if (state != null && state.identity() == expectedIdentity) {
            previous.put(slot, new Progress(updatedIdentity, updatedWear, state.interval(), state.ticks(), state.paused()));
        }
    }

    /** Marks a tracked slot as paused under a serializable/reloadable identity (e.g. when its chunk unloads). */
    public void pauseWithIdentity(K slot, Object pausedIdentity) {
        Objects.requireNonNull(pausedIdentity);
        Progress state = previous.get(slot);
        if (state != null) {
            previous.put(slot, new Progress(pausedIdentity, state.expectedWear(), state.interval(), state.ticks(), true));
        }
    }

    /** Rebinds a paused slot's identity back to the live deserialized ItemStack once verified on chunk reload. */
    public boolean rebindPausedIdentity(K slot, Object expectedPausedIdentity, Object liveIdentity) {
        Objects.requireNonNull(liveIdentity);
        Progress state = previous.get(slot);
        if (state == null || !state.paused() || !Objects.equals(state.identity(), expectedPausedIdentity)) {
            return false;
        }
        previous.put(slot, new Progress(liveIdentity, state.expectedWear(), state.interval(), state.ticks(), true));
        return true;
    }

    public boolean isSlotPaused(K slot) {
        Progress state = previous.get(slot);
        return state != null && state.paused();
    }

    /** Elapsed ticks toward the next +1 charge for {@code slot}, or {@code -1} if untracked. */
    public long progressTicks(K slot) {
        Progress state = previous.get(slot);
        return state == null ? -1L : state.ticks();
    }

    /** Current per-charge interval for {@code slot}, or {@code -1} if untracked. */
    public int intervalTicks(K slot) {
        Progress state = previous.get(slot);
        return state == null ? -1 : state.interval();
    }

    public Object trackedIdentity(K slot) {
        Progress state = previous.get(slot);
        return state != null ? state.identity() : null;
    }

    public List<SavedEntry<K>> exportSavedEntries() {
        List<SavedEntry<K>> out = new ArrayList<>(previous.size());
        previous.forEach((slot, p) -> {
            if (p.expectedWear() > 0 && p.interval() >= WINDOW_TICKS) {
                out.add(new SavedEntry<>(slot, p.identity(), p.expectedWear(), p.interval(), p.ticks()));
            }
        });
        return List.copyOf(out);
    }

    public void restoreSavedEntries(Collection<SavedEntry<K>> entries) {
        Map<K, Progress> restored = new HashMap<>();
        if (entries != null) {
            for (SavedEntry<K> e : entries) {
                if (e != null) {
                    restored.put(e.slot(), new Progress(
                        e.bookIdentity(),
                        e.expectedWear(),
                        e.intervalTicks(),
                        e.progressTicks(),
                        true
                    ));
                }
            }
        }
        this.previous = restored;
        this.lastWindow = -1;
    }

    public int trackedSlots() { return previous.size(); }
    public void clear() { previous = new HashMap<>(); lastWindow = -1; }
}
