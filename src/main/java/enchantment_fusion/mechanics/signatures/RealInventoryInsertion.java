package enchantment_fusion.mechanics.signatures;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A conservative identity receipt for one non-stackable item inserted by an actual inventory call.
 * Existing equal items do not count: exactly one previously empty slot must acquire the expected
 * item, and every other slot must retain both its original object and value.
 */
public final class RealInventoryInsertion {
    private final List<ItemStack> identities, values;
    private RealInventoryInsertion(List<ItemStack> identities, List<ItemStack> values) {
        this.identities = identities; this.values = values;
    }
    private static List<ItemStack> items(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) result.add(inventory.getItem(i));
        return result;
    }
    public static RealInventoryInsertion capture(Inventory inventory) {
        List<ItemStack> refs = items(inventory), snapshots = new ArrayList<>(refs.size());
        for (ItemStack ref : refs) snapshots.add(ref.copy());
        return new RealInventoryInsertion(List.copyOf(refs), List.copyOf(snapshots));
    }
    public @Nullable ItemStack inserted(Inventory inventory, ItemStack expected) {
        if (expected.isEmpty() || expected.getCount() != 1 || expected.getMaxStackSize() != 1) return null;
        List<ItemStack> now = items(inventory);
        int index = uniqueInsertionIndex(identities, values, now, ItemStack::isEmpty, ItemStack::matches,
            stack -> !stack.isEmpty() && ItemStack.matches(stack, expected));
        return index < 0 ? null : now.get(index);
    }
    public static boolean unique(Inventory inventory, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) count++;
        return count == 1;
    }

    /** Pure identity/value comparison also used by tests; equality alone never identifies the new object. */
    public static <T, V> int uniqueInsertionIndex(List<T> before, List<V> snapshots, List<T> after,
        Predicate<V> wasEmpty, BiPredicate<T, V> unchangedValue, Predicate<T> matchesExpected) {
        if (before.size() != snapshots.size() || before.size() != after.size()) return -1;
        int found = -1;
        for (int i = 0; i < before.size(); i++) {
            T old = before.get(i), current = after.get(i); V value = snapshots.get(i);
            if (wasEmpty.test(value) && matchesExpected.test(current)) {
                if (found >= 0 || current == old) return -1;
                found = i;
            } else if (current != old || !unchangedValue.test(current, value)) return -1;
        }
        if (found < 0) return -1;
        T inserted = after.get(found); int copies = 0;
        for (T current : after) if (current == inserted) copies++;
        return copies == 1 ? found : -1;
    }
}
