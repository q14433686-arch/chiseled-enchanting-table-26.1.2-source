package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Recipe-book index for this mod's enchanting mechanic, shared by every viewer integration
 * (JEI today, REI next).
 *
 * <p>This class deliberately has <b>no third-party dependencies</b>: it is plain Minecraft data, so
 * a viewer adapter only has to translate {@link Entry} into its own display objects. That keeps the
 * "book → what can it enchant" / "item → which books work on it" query in one place instead of being
 * re-implemented per viewer.</p>
 *
 * <p><b>Why a category with two inputs gives both directions for free:</b> a recipe that takes
 * (target item, enchanted book) and produces (enchanted item) can be focused on either input, so
 * JEI/REI answer "what can this book do?" and "which books work on this item?" from the same data —
 * no separate reverse index, no custom screen.</p>
 *
 * <p><b>What is data and what is world state.</b> The entries below are pure registry + datapack
 * data. The one thing that cannot be a recipe is the mod's requirement that the enchanted book is
 * lying in a nearby chiseled bookshelf: that is live world state, so it belongs in the viewer's info
 * page / catalyst list, not in the recipe.</p>
 *
 * <p><b>Cost caveat.</b> {@link ChiseledEnchantingTableData#itemCosts(EnchantmentWithLevel, String,
 * int)} is salted with the player UUID at runtime, which picks among the alternative cost options.
 * The index resolves costs with an empty salt, i.e. the canonical/first option. Viewers should treat
 * the shown material as one of the accepted options, not as the only one.</p>
 */
public final class EnchantQueryIndex {
    private EnchantQueryIndex() {}

    /**
     * One (enchantment, level) row with everything a viewer needs to draw it.
     *
     * @param enchantmentId the enchantment id
     * @param level         the level this row describes (1..{@code maxLevel})
     * @param maxLevel      the enchantment's maximum level
     * @param supportedItems every item the enchantment can be applied to, as display stacks
     *                       (derived from {@code Enchantment#canEnchant}, so modded items and
     *                       "universal enchants"-style datapacks are included automatically)
     * @param itemCosts      canonical material cost stacks (first one is the primary cost)
     * @param xpLevelCost    XP-level cost for this level
     * @param conflicts      enchantments this one is mutually exclusive with
     */
    public record Entry(
        Identifier enchantmentId,
        int level,
        int maxLevel,
        List<ItemStack> supportedItems,
        List<ItemStack> itemCosts,
        int xpLevelCost,
        List<Identifier> conflicts
    ) {
        /** The enchanted book a player would need for this row. */
        public ItemStack bookStack(Holder<Enchantment> enchantment) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(enchantment, level));
            return book;
        }

        public boolean supports(ItemStack stack) {
            if (stack.isEmpty()) return false;
            Item item = stack.getItem();
            for (ItemStack supported : supportedItems) {
                if (supported.is(item)) return true;
            }
            return false;
        }
    }

    /**
     * Builds every (enchantment, level) row. Safe on the client: it only reads the synced
     * enchantment/item/item-component registries.
     *
     * <p>Anything the authoritative server-side datapack changed in {@code enchantment_costs.json}
     * is reflected when this runs on a client whose data was synced from the server (single-player
     * and LAN always are; on a dedicated server the client uses its own copy, which is why the mod
     * ships the same defaults in the jar).</p>
     */
    public static List<Entry> build(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> enchantments =
            registries.lookupOrThrow(Registries.ENCHANTMENT);

        List<Holder.Reference<Enchantment>> holders = enchantments.listElements().toList();
        if (holders.isEmpty()) return List.of();

        // Pre-compute the displayable item set once; ~1.2k items, so this is cheap and keeps the
        // per-enchantment loop a plain set-membership test.
        List<ItemStack> candidateItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            candidateItems.add(stack);
        }

        List<Entry> entries = new ArrayList<>();
        for (Holder.Reference<Enchantment> holder : holders) {
            Enchantment enchantment = holder.value();
            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            Identifier id = holder.key().identifier();

            List<ItemStack> supported = new ArrayList<>();
            for (ItemStack candidate : candidateItems) {
                if (enchantment.canEnchant(candidate)) supported.add(candidate);
            }
            // An enchantment that applies to nothing (creative-only or fully replaced by a
            // datapack) would render as an empty slot, which viewers dislike: skip it.
            if (supported.isEmpty()) continue;

            List<Identifier> conflicts = new ArrayList<>();
            for (Holder.Reference<Enchantment> other : holders) {
                if (other.key().equals(holder.key())) continue;
                if (!Enchantment.areCompatible(holder, other)) {
                    conflicts.add(other.key().identifier());
                }
            }

            for (int level = 1; level <= maxLevel; level++) {
                EnchantmentWithLevel key = new EnchantmentWithLevel(id, level);
                entries.add(new Entry(
                    id,
                    level,
                    maxLevel,
                    List.copyOf(supported),
                    List.copyOf(ChiseledEnchantingTableData.itemCosts(key, "", maxLevel)),
                    ChiseledEnchantingTableData.xpCost(key, maxLevel),
                    List.copyOf(conflicts)
                ));
            }
        }
        return Collections.unmodifiableList(entries);
    }

    /** Rows whose enchantment can be applied to {@code stack} ("item → books"). */
    public static List<Entry> forItem(List<Entry> all, ItemStack stack) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.supports(stack)) out.add(entry);
        }
        return out;
    }

    /** The row matching this enchanted book, if the index knows it ("book → items"). */
    public static Optional<Entry> forBook(List<Entry> all, ItemStack book) {
        if (book.isEmpty()) return Optional.empty();
        var stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return Optional.empty();
        var single = stored.entrySet().iterator().next();
        Identifier id = single.getKey().unwrapKey().map(key -> key.identifier()).orElse(null);
        if (id == null) return Optional.empty();
        int level = single.getIntValue();
        for (Entry entry : all) {
            if (entry.enchantmentId().equals(id) && entry.level() == level) return Optional.of(entry);
        }
        return Optional.empty();
    }

    /** Resolves a holder by id, for adapters that need the live {@link Holder} to build books. */
    public static Optional<Holder.Reference<Enchantment>> holder(
        HolderLookup.Provider registries,
        Identifier enchantmentId
    ) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).get(
            net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)
        );
    }
}
