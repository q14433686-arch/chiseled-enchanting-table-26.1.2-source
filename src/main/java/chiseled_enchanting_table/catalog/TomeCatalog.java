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
    public List<Row> source() { return source; }
    public List<Row> visible() { return visible; }
    public Set<Key> selected() { return Set.copyOf(selected); }
    public boolean isSelected(Key key) { return selected.contains(key); }
    public void update(List<Row> rows) { source = List.copyOf(rows); refresh(); }
    public void reset() { enchantment = null; remaining = total = null; lock = Lock.ALL; sort = Sort.LOCATION; descending = false; refresh(); }
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
        // Never act on an invisible or removed book after filters/live updates change.
        selected.retainAll(visible.stream().map(Row::key).toList());
    }
    public void select(Key key, boolean toggle) {
        if (!toggle) selected.clear();
        if (visible.stream().noneMatch(r -> r.key().equals(key))) return;
        if (!toggle || !selected.remove(key)) selected.add(key);
    }
    public void clearSelection() { selected.clear(); }
    public void selectVisible() { visible.forEach(r -> selected.add(r.key())); }
}
