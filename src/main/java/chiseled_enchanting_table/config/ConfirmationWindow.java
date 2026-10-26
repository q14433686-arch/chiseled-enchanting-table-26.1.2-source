package chiseled_enchanting_table.config;

import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/** A destructive action is armed against a copied target, not just a timer. */
public final class ConfirmationWindow<T> {
    private final long duration;
    private final UnaryOperator<T> copy;
    private final BiPredicate<T, T> equivalent;
    private T snapshot;
    private long until;
    public ConfirmationWindow(long duration, UnaryOperator<T> copy, BiPredicate<T, T> equivalent) {
        if (duration <= 0) throw new IllegalArgumentException("duration");
        this.duration = duration;
        this.copy = java.util.Objects.requireNonNull(copy);
        this.equivalent = java.util.Objects.requireNonNull(equivalent);
    }
    public void arm(T target, long now) {
        snapshot = copy.apply(java.util.Objects.requireNonNull(target));
        until = now + duration;
    }
    public boolean pending(T current, long now) {
        if (snapshot != null && now <= until && equivalent.test(current, snapshot)) return true;
        clear();
        return false;
    }
    public void clear() { snapshot = null; until = 0; }
}
