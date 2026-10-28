package chiseled_enchanting_table.catalog;

import java.util.*;

/** Detached catalogue state. Shelf/slot identities never depend on display order. No world or network access. */
public final class TomeCatalog {
    public record Key(long shelf, int slot) {}
    public record Row(Key key, List<String> enchantments, String title, int remaining, int total,
                      boolean locked, boolean mine) {
        public Row { enchantments = List.copyOf(enchantments); }
    }
    public enum Lock { ALL, LOCKED, UNLOCKED, MINE, OTHER }
    public enum Sort { ENCHANTMENT, REMAINING, TOTAL, LOCKED, LOCATION }
    public String enchantment;
    public Integer remaining, total;
    public Lock lock = Lock.ALL;
    public Sort sort = Sort.LOCATION;
    public boolean descending;
    private List<Row> source = List.of(), visible = List.of();
    private final Set<Key> selected = new LinkedHashSet<>();
    private Key anchor;
    public List<Row> source() { return source; }
    public List<Row> visible() { return visible; }
    public Set<Key> selected() { return Set.copyOf(selected); }
    public boolean isSelected(Key key) { return selected.contains(key); }
    public void update(List<Row> rows) { source = List.copyOf(rows); refresh(); }
    public void reset() { enchantment = null; remaining = total = null; lock = Lock.ALL; sort = Sort.LOCATION; descending = false; anchor = null; refresh(); }
    public void refresh() {
        Comparator<Row> order = switch (sort) {
            case ENCHANTMENT -> Comparator.comparing(Row::title, String.CASE_INSENSITIVE_ORDER);
            case REMAINING -> Comparator.comparingInt(Row::remaining);
            case TOTAL -> Comparator.comparingInt(Row::total);
            case LOCKED -> Comparator.comparing(Row::locked);
            case LOCATION -> Comparator.comparingLong((Row r) -> r.key().shelf()).thenComparingInt(r -> r.key().slot());
        };
        if (descending) order = order.reversed();
        order = order.thenComparingLong(r -> r.key().shelf()).thenComparingInt(r -> r.key().slot());
        visible = source.stream().filter(r -> enchantment == null || r.enchantments().contains(enchantment))
            .filter(r -> remaining == null || remaining == r.remaining()).filter(r -> total == null || total == r.total())
            .filter(r -> switch (lock) {
                case ALL -> true; case LOCKED -> r.locked(); case UNLOCKED -> !r.locked();
                case MINE -> r.locked() && r.mine(); case OTHER -> r.locked() && !r.mine();
            }).sorted(order).toList();
        var visibleKeys = visible.stream().map(Row::key).toList();
        // Never act on an invisible or removed book after filters/live updates change.
        selected.retainAll(visibleKeys);
        if (anchor != null && !visibleKeys.contains(anchor)) anchor = null;
    }
    public void select(Key key, boolean toggle) {
        if (!toggle) selected.clear();
        if (visible.stream().noneMatch(r -> r.key().equals(key))) return;
        if (!toggle || !selected.remove(key)) {
            selected.add(key);
            anchor = key;
        }
    }
    public void selectInteractive(Key key, boolean shiftRange, boolean ctrlToggle) {
        int targetIdx = indexOfVisible(key);
        if (targetIdx < 0) return;
        if (shiftRange) {
            int anchorIdx = anchor != null ? indexOfVisible(anchor) : -1;
            if (anchorIdx < 0) {
                anchorIdx = 0;
                anchor = visible.getFirst().key();
            }
            if (!ctrlToggle) selected.clear();
            int lo = Math.min(anchorIdx, targetIdx);
            int hi = Math.max(anchorIdx, targetIdx);
            for (int i = lo; i <= hi; i++) {
                selected.add(visible.get(i).key());
            }
            return;
        }
        if (ctrlToggle) {
            if (selected.isEmpty() && targetIdx > 0) {
                selected.add(visible.getFirst().key());
            }
            if (!selected.remove(key)) {
                selected.add(key);
            }
            anchor = key;
            return;
        }
        selected.clear();
        selected.add(key);
        anchor = key;
    }
    private int indexOfVisible(Key key) {
        if (key == null) return -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).key().equals(key)) return i;
        }
        return -1;
    }
    public void clearSelection() { selected.clear(); anchor = null; }
    public void selectVisible() {
        visible.forEach(r -> selected.add(r.key()));
        if (anchor == null && !visible.isEmpty()) anchor = visible.getFirst().key();
    }
}
