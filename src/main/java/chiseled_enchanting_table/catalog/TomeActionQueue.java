package chiseled_enchanting_table.catalog;

import java.util.*;
import java.util.function.BiConsumer;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action;

/** Best-effort, paced existing server actions; no optimistic lock state and no permission bypass. */
public final class TomeActionQueue {
    public record Status(String key, int count) {}
    private final java.util.function.LongSupplier clock;
    public TomeActionQueue() { this(System::nanoTime); }
    public TomeActionQueue(java.util.function.LongSupplier clock) { this.clock = clock; }
    private List<TomeBookEntry> armed = List.of();
    private Action armedAction, action;
    private long deadline;
    private final ArrayDeque<TomeBookEntry> pending = new ArrayDeque<>();
    private int delay;
    public boolean submit(Action requested, List<TomeBookEntry> books) {
        var eligible = books.stream().filter(b -> requested == Action.UNLOCK ? b.locked()
            : !b.locked() && (requested == Action.LOCK_PERMANENT || b.wear_used() > 0)).toList();
        if (eligible.isEmpty() || !pending.isEmpty()) return false;
        if (eligible.size() > 1 && (requested != armedAction || !eligible.equals(armed) || clock.getAsLong() > deadline)) {
            armedAction = requested; armed = eligible;
            deadline = clock.getAsLong() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            return false;
        }
        action = requested; pending.addAll(eligible); armed = List.of(); armedAction = null;
        return true;
    }
    public void cancel() { pending.clear(); armed = List.of(); armedAction = null; }
    public void tick(List<TomeBookEntry> live, BiConsumer<Action,TomeBookEntry> send) {
        if (pending.isEmpty() || delay-- > 0) return;
        delay = 3;
        var old = pending.removeFirst();
        // A removed or observably replaced shelf book is never silently retargeted.
        live.stream().filter(b -> b.shelf_pos().equals(old.shelf_pos()) && b.slot() == old.slot()
            && b.enchantments().equals(old.enchantments()) && b.custom_name().equals(old.custom_name())
            && b.generation() == old.generation() && b.effective_max_uses() == old.effective_max_uses())
            .findFirst().filter(b -> action == Action.UNLOCK ? b.locked() : !b.locked()).ifPresent(b -> send.accept(action,b));
    }
    public Status status() {
        if (!pending.isEmpty()) return new Status("pending", pending.size());
        if (!armed.isEmpty() && clock.getAsLong() <= deadline) return new Status("confirm", armed.size());
        return null;
    }
}
