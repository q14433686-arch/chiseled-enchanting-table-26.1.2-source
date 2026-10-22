package chiseled_enchanting_table.mechanics.loot;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.LootInjection;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.LootInjectionEntry;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetLoreFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Applies {@link ChiseledEnchantingTableData#lootInjections()} to vanilla loot tables via
 * {@link LootTableEvents#MODIFY}.
 *
 * <p><b>Architecture:</b> this class is Java-only mechanism (what tables, when to fire, how to
 * translate JSON entries into loot pool entries). The <i>content</i> — which loot tables get which
 * enchanted books at what weight — lives entirely in
 * {@code data/chiseled_enchanting_table/chiseled_enchanting_table/loot_injections.json}, which is
 * auto-derived from upstream by {@code scripts/import_upstream_loot.py} and hand-editable
 * afterwards.</p>
 *
 * <p><b>Behaviour vs upstream:</b> we do <b>not</b> strip vanilla enchanted books from chests
 * (user decision "keep_both"). Injected books get a purple "Chiseled Enchanting Table" lore stamp
 * via {@link SetLoreFunction} so players can distinguish injected books from vanilla ones at a
 * glance. This also makes the optional {@link RemoveEnchantedBooksFunction} utility safe to use in
 * data packs — it strips only books that lack the lore stamp.</p>
 *
 * <p><b>Cross-mod safety:</b> each injection is guarded by {@code source.isBuiltin()} so we don't
 * clobber another mod's overrides of the same table. Missing / unknown enchantment ids are logged
 * and skipped (not fatal). If {@code loot_injections.json} is empty (default), this class
 * effectively does nothing — the feature is fully data-driven.</p>
 */
public final class EnchantedBookLootInjector {
    /**
     * Lore line stamped on every injected enchanted book.
     *
     * <p>Translatable, not a literal — same reasoning as
     * {@link chiseled_enchanting_table.mechanics.structure.BookshelfBookFiller}: the component is
     * stored in the stack and resolved per client, so a book rolled by a dedicated server reads in
     * each player's own language. The 26.3 side converted the bookshelf filler's copy of this
     * string; this is the second (chest-loot) copy of the same stamp.
     * {@link RemoveEnchantedBooksFunction} detects "ours" by <i>having</i> a lore line, not by
     * matching its text, so the switch is behaviour-preserving.</p>
     */
    private static final Component LORE = Component
        .translatable("book.chiseled_enchanting_table.lore")
        .withStyle(style -> style.withColor(0x800080));

    private EnchantedBookLootInjector() {}

    public static void init() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;

            List<LootInjection> injections = ChiseledEnchantingTableData.lootInjections();
            if (injections.isEmpty()) return;

            HolderGetter<Enchantment> enchantmentLookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            Identifier tableId = key.identifier();

            for (LootInjection injection : injections) {
                if (!matches(injection, tableId)) continue;
                LootPool.Builder pool = buildPool(injection, enchantmentLookup);
                if (pool != null) {
                    tableBuilder.withPool(pool);
                }
            }
        });
    }

    private static boolean matches(LootInjection injection, Identifier tableId) {
        if (injection.isTag()) {
            Identifier tag = injection.tableId();
            return tableId.getNamespace().equals(tag.getNamespace())
                && tableId.getPath().startsWith(tag.getPath());
        }
        return injection.tableId().equals(tableId);
    }

    private static LootPool.Builder buildPool(LootInjection injection, HolderGetter<Enchantment> enchantmentLookup) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ContextIntProviders.exactly(injection.rolls()));
        boolean addedAny = false;
        for (LootInjectionEntry entry : injection.entries()) {
            UniformContainerBase.Builder<?> singleton;
            if (entry.empty()) {
                singleton = EmptyLootItem.emptyItem();
            } else {
                Optional<Holder.Reference<Enchantment>> holder = enchantmentLookup.get(
                    ResourceKey.create(Registries.ENCHANTMENT, entry.enchantmentId())
                );
                if (holder.isEmpty()) {
                    ChiseledEnchantingTable.LOGGER.warn(
                        "loot_injections.json references unknown enchantment {} in table {}; skipping entry",
                        entry.enchantmentId(), injection.tableSelector()
                    );
                    continue;
                }
                var itemBuilder = LootItem.lootTableItem(Items.ENCHANTED_BOOK);
                if ("depth_based_efficiency".equals(entry.special())) {
                    itemBuilder.apply(DepthBasedEfficiencyFunction::new);
                } else {
                    itemBuilder.apply(new SetEnchantmentsFunction.Builder()
                        .withEnchantment(holder.get(), ContextIntProviders.exactly(entry.level())));
                }
                singleton = itemBuilder.apply(SetLoreFunction.setLore().addLine(LORE));
            }
            pool.add(singleton.setWeight(entry.weight()));
            addedAny = true;
        }
        return addedAny ? pool : null;
    }
}
