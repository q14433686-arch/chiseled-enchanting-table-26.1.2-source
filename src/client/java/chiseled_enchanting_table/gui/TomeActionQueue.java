package chiseled_enchanting_table.gui;

import java.util.*;
import java.util.function.BiConsumer;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action;

/** Best-effort, paced existing server actions; no optimistic lock state and no permission bypass. */
final class TomeActionQueue {
    private List<TomeBookEntry> armed = List.of();
    private Action armedAction, action;
    private long deadline;
    private final ArrayDeque<TomeBookEntry> pending = new ArrayDeque<>();
    private int delay;
    boolean submit(Action requested, List<TomeBookEntry> books) {
        var eligible = books.stream().filter(b -> requested == Action.UNLOCK ? b.locked()
            : !b.locked() && (requested == Action.LOCK_PERMANENT || b.wear_used() > 0)).toList();
        if (eligible.isEmpty() || !pending.isEmpty()) return false;
        if (eligible.size() > 1 && (requested != armedAction || !eligible.equals(armed) || System.nanoTime() > deadline)) {
            armedAction = requested; armed = eligible;
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            return false;
        }
        action = requested; pending.addAll(eligible); armed = List.of(); armedAction = null;
        return true;
    }
    void cancel() { pending.clear(); armed = List.of(); armedAction = null; }
    void tick(List<TomeBookEntry> live, BiConsumer<Action,TomeBookEntry> send) {
        if (pending.isEmpty() || delay-- > 0) return;
        delay = 3;
        var old = pending.removeFirst();
        // A removed or observably replaced shelf book is never silently retargeted.
        live.stream().filter(b -> b.shelf_pos().equals(old.shelf_pos()) && b.slot() == old.slot()
            && b.enchantments().equals(old.enchantments()) && b.custom_name().equals(old.custom_name())
            && b.generation() == old.generation() && b.effective_max_uses() == old.effective_max_uses())
            .findFirst().filter(b -> action == Action.UNLOCK ? b.locked() : !b.locked()).ifPresent(b -> send.accept(action,b));
    }
    String status() {
        if (!pending.isEmpty()) return TomeDirectoryControls.text("pending", pending.size());
        if (!armed.isEmpty() && System.nanoTime() <= deadline) return TomeDirectoryControls.text("confirm", armed.size());
        return null;
    }
}
