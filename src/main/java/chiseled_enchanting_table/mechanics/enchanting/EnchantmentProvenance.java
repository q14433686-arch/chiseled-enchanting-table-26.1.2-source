package chiseled_enchanting_table.mechanics.enchanting;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Deterministic level-slice provenance tracking ({@code cet_provenance} in {@link DataComponents#CUSTOM_DATA})
 * distinguishing levels paid at the Chiseled Enchanting Table from external enchantments obtained via
 * the vanilla Enchanting Table, chest loot, or villager trades ({@code D3}).
 *
 * <p>Algebraic anti-arbitrage properties across all levels {@code 1..255}:</p>
 * <ul>
 *   <li>Every enchantment ID stores a minimal trimmed {@link BitSet} over paid level slices {@code 0 .. L-1}
 *       in canonically sorted key order, keeping identical table-crafted books 100% stackable to {@code 64}.</li>
 *   <li>External items have empty bitsets ({@code CustomData} untouched) and refund at {@code externalXpRefundScale}.</li>
 *   <li>Direct Book Transfer ({@code 0 XP}) merges {@code targetBits | bookBits} up to {@code targetLevel}
 *       without ever fabricating table-paid provenance from a villager/chest book.</li>
 *   <li>Step-down downgrades satisfy exact additivity: {@code refund(k -> b) + refund(b -> 0) == refund(k -> 0)}.</li>
 * </ul>
 */
public final class EnchantmentProvenance {
    public static final String NBT_PROVENANCE_KEY = "cet_provenance";
    public static final double DEFAULT_EXTERNAL_XP_REFUND_SCALE = 0.25;

    private EnchantmentProvenance() {}

    /** Pure headless bitset helper marking slices {@code (fromExclusive .. toInclusive]} as paid. */
    public static BitSet markSliceRange(BitSet existing, int fromExclusive, int toInclusive) {
        BitSet next = existing != null ? (BitSet) existing.clone() : new BitSet();
        int lo = Math.max(0, fromExclusive);
        int hi = Math.max(lo, toInclusive);
        next.clear(hi, Math.max(hi, next.length()));
        if (hi > lo) {
            next.set(lo, hi);
        }
        return next;
    }

    /** Pure headless helper merging {@code target | source} truncated to {@code [0, targetLevel)}. */
    public static BitSet mergeTransferredSlices(BitSet targetBits, BitSet sourceBits, int targetLevel) {
        BitSet merged = new BitSet();
        if (targetBits != null) {
            merged.or(targetBits);
        }
        if (sourceBits != null) {
            merged.or(sourceBits);
        }
        int cap = Math.max(0, targetLevel);
        merged.clear(cap, Math.max(cap, merged.length()));
        return merged;
    }

    /** Pure headless helper truncating {@code existing} to {@code [0, remainingLevel)}. */
    public static BitSet truncateToLevel(BitSet existing, int remainingLevel) {
        if (existing == null || existing.isEmpty() || remainingLevel <= 0) {
            return new BitSet();
        }
        BitSet next = (BitSet) existing.clone();
        next.clear(remainingLevel, Math.max(remainingLevel, next.length()));
        return next;
    }

    public static BitSet getPaidSlices(ItemStack stack, Identifier enchantmentId) {
        if (stack == null || stack.isEmpty() || enchantmentId == null) {
            return new BitSet();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return new BitSet();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(NBT_PROVENANCE_KEY)) {
            return new BitSet();
        }
        CompoundTag prov = root.getCompoundOrEmpty(NBT_PROVENANCE_KEY);
        return prov.getByteArray(enchantmentId.toString())
            .map(BitSet::valueOf)
            .orElseGet(BitSet::new);
    }

    public static void setPaidSlices(ItemStack stack, Identifier enchantmentId, BitSet slices) {
        if (stack == null || stack.isEmpty() || enchantmentId == null) {
            return;
        }
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = existing != null ? existing.copyTag() : new CompoundTag();
        CompoundTag prov = root.getCompoundOrEmpty(NBT_PROVENANCE_KEY);

        if (slices == null || slices.isEmpty()) {
            prov.remove(enchantmentId.toString());
        } else {
            prov.putByteArray(enchantmentId.toString(), slices.toByteArray());
        }

        writeCanonicalProvenance(stack, root, prov);
    }

    public static void recordTablePayment(
        ItemStack stack,
        Identifier enchantmentId,
        int previousLevel,
        int newLevel
    ) {
        BitSet cur = getPaidSlices(stack, enchantmentId);
        BitSet updated = markSliceRange(cur, previousLevel, newLevel);
        setPaidSlices(stack, enchantmentId, updated);
    }

    public static void recordBookTransfer(
        ItemStack sourceBookSnapshot,
        ItemStack targetStack,
        Identifier enchantmentId,
        int targetLevel
    ) {
        BitSet bookSlices = getPaidSlices(sourceBookSnapshot, enchantmentId);
        BitSet targetSlices = getPaidSlices(targetStack, enchantmentId);
        BitSet merged = mergeTransferredSlices(targetSlices, bookSlices, targetLevel);
        setPaidSlices(targetStack, enchantmentId, merged);
    }

    public static void recordBookCombine(
        ItemStack targetSnapshot,
        ItemStack costSnapshot,
        ItemStack resultingBook,
        ItemEnchantments mergedEnchantments
    ) {
        ItemEnchantments oldTarget = EnchantmentHelper.getEnchantmentsForCrafting(targetSnapshot);
        ItemEnchantments oldCost = EnchantmentHelper.getEnchantmentsForCrafting(costSnapshot);
        for (var entry : mergedEnchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int finalLevel = entry.getIntValue();
            int maxInput = Math.max(oldTarget.getLevel(holder), oldCost.getLevel(holder));
            holder.unwrapKey().map(k -> k.identifier()).ifPresent(enchId -> {
                BitSet merged = mergeTransferredSlices(
                    getPaidSlices(targetSnapshot, enchId),
                    getPaidSlices(costSnapshot, enchId),
                    finalLevel
                );
                if (finalLevel > maxInput) {
                    merged.set(maxInput, finalLevel);
                }
                setPaidSlices(resultingBook, enchId, merged);
            });
        }
        pruneToCurrentEnchantments(resultingBook, mergedEnchantments);
    }

    public static void pruneToCurrentEnchantments(ItemStack stack, ItemEnchantments remaining) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing == null || existing.isEmpty()) {
            return;
        }
        CompoundTag root = existing.copyTag();
        if (!root.contains(NBT_PROVENANCE_KEY)) {
            return;
        }
        CompoundTag oldProv = root.getCompoundOrEmpty(NBT_PROVENANCE_KEY);
        CompoundTag nextProv = new CompoundTag();
        if (remaining != null && !remaining.isEmpty()) {
            for (var entry : remaining.entrySet()) {
                int lvl = entry.getIntValue();
                if (lvl <= 0) continue;
                entry.getKey().unwrapKey().map(k -> k.identifier()).ifPresent(enchId -> {
                    String key = enchId.toString();
                    oldProv.getByteArray(key).ifPresent(bytes -> {
                        BitSet truncated = truncateToLevel(BitSet.valueOf(bytes), lvl);
                        if (!truncated.isEmpty()) {
                            nextProv.putByteArray(key, truncated.toByteArray());
                        }
                    });
                });
            }
        }
        writeCanonicalProvenance(stack, root, nextProv);
    }

    private static void writeCanonicalProvenance(ItemStack stack, CompoundTag root, CompoundTag unsortedProv) {
        if (unsortedProv == null || unsortedProv.isEmpty()) {
            root.remove(NBT_PROVENANCE_KEY);
            if (root.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            }
            return;
        }
        List<String> sortedKeys = new ArrayList<>(unsortedProv.keySet());
        Collections.sort(sortedKeys);
        CompoundTag canonical = new CompoundTag();
        for (String key : sortedKeys) {
            unsortedProv.getByteArray(key).ifPresent(bytes -> {
                if (bytes.length > 0) {
                    canonical.putByteArray(key, bytes);
                }
            });
        }
        if (canonical.isEmpty()) {
            root.remove(NBT_PROVENANCE_KEY);
        } else {
            root.put(NBT_PROVENANCE_KEY, canonical);
        }
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }
}
