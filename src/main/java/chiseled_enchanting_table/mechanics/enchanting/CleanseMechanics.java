package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.resonance.TomeWearData;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.menu.MenuCleanseQuotes;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import chiseled_enchanting_table.utils.InventoryUtils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Survival Cleanse extensions: single-enchantment extraction onto a book, one-click batch curse
 * purification, and Mending-first direct XP bar refund (preventing block-trapped XP orbs).
 */
public final class CleanseMechanics {
    public static final Identifier EXTRACT_BOOK_ID = ChiseledEnchantingTable.identifier("extract_enchantment_book");
    public static final Identifier PURGE_ALL_CURSES_ID = ChiseledEnchantingTable.identifier("purge_all_curses");

    private static final EquipmentSlot[] MENDING_SCAN_SLOTS = {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private CleanseMechanics() {}

    /**
     * Pure lineage rule for extracted books:
     * <ul>
     *   <li>{@code Arcane Catalyst} extraction or 100% table-paid slices preserve {@code sourceGen}
     *       without advancing generation ({@code Gen 0} stays {@code Gen 0}).</li>
     *   <li>External / wild traits extracted without Catalyst stamp at least {@code Gen 1} so
     *       mob-farm / villager gear cannot mint infinite {@code Gen 0} Master Tomes.</li>
     * </ul>
     */
    public static int computeExtractedBookGeneration(int sourceGeneration, boolean fullyTablePaid, boolean catalystRoute) {
        if (catalystRoute || fullyTablePaid) {
            return TomeWearData.clampGeneration(sourceGeneration);
        }
        return TomeWearData.clampGeneration(Math.max(1, sourceGeneration));
    }

    /** Pure vanilla Mending durability step: 2 durability per XP point, returning unspent XP points. */
    public static int repairMendingDamageStep(int currentDamage, int availableXpPoints, int[] outNewDamage) {
        int safeXp = Math.max(0, availableXpPoints);
        int safeDmg = Math.max(0, currentDamage);
        if (safeXp == 0 || safeDmg == 0) {
            if (outNewDamage != null && outNewDamage.length > 0) {
                outNewDamage[0] = safeDmg;
            }
            return safeXp;
        }
        int repaired = Math.min(safeDmg, (int) Math.min(Integer.MAX_VALUE, (long) safeXp * 2L));
        int spentXp = (repaired + 1) / 2;
        if (outNewDamage != null && outNewDamage.length > 0) {
            outNewDamage[0] = safeDmg - repaired;
        }
        return Math.max(0, safeXp - spentXp);
    }

    public static boolean hasMendingEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantments enchs = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchs.entrySet()) {
            if (entry.getIntValue() > 0
                && entry.getKey().unwrapKey().map(k -> InscriptionRitualMechanics.MENDING_ID.equals(k.identifier())).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Repairs damaged Mending items in {@code equipped} first and returns remaining XP points. */
    public static int repairEquippedMendingItems(List<ItemStack> equipped, int refundPoints) {
        int remaining = Math.max(0, refundPoints);
        if (remaining <= 0 || equipped == null) {
            return 0;
        }
        int[] nextDamage = new int[1];
        for (ItemStack stack : equipped) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.isEmpty() || !stack.isDamaged() || !hasMendingEnchantment(stack)) {
                continue;
            }
            remaining = repairMendingDamageStep(stack.getDamageValue(), remaining, nextDamage);
            stack.setDamageValue(nextDamage[0]);
        }
        return remaining;
    }

    /**
     * Repairs the player's equipped/held Mending gear first, then credits all remaining XP points
     * directly to the player's XP bar with chime feedback (never spawning block-trapped orbs).
     */
    public static void awardSmartXpRefund(Player player, @Nullable BlockPos tablePos, int refundPoints) {
        if (player == null || refundPoints <= 0) {
            return;
        }
        List<ItemStack> equipped = new ArrayList<>(MENDING_SCAN_SLOTS.length);
        for (EquipmentSlot slot : MENDING_SCAN_SLOTS) {
            ItemStack candidate = player.getItemBySlot(slot);
            if (!candidate.isEmpty()) {
                equipped.add(candidate);
            }
        }
        int remaining = repairEquippedMendingItems(equipped, refundPoints);
        if (remaining > 0) {
            player.giveExperiencePoints(remaining);
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            BlockPos soundPos = tablePos != null ? tablePos : player.blockPosition();
            serverLevel.playSound(null, soundPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55f, 1.05f);
            if (remaining < refundPoints && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                chiseled_enchanting_table.menu.TableVisualEvents.onMendingGearRepaired(sp);
            }
        }
    }

    public static int countCurses(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchs = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int count = 0;
        for (var entry : enchs.entrySet()) {
            if (entry.getIntValue() > 0 && DisenchantmentApplication.isCurse(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    public static boolean canPurgeAnyCurseNow(ChiseledEnchantingTableScreenHandler menu) {
        if (menu == null || !menu.disenchantEnabled() || !menu.disenchantAllowCursePurge() || menu.isRitualActive()) {
            return false;
        }
        ItemStack target = menu.get_enchantable_item();
        if (target.isEmpty() || target.getCount() != 1) {
            return false;
        }
        ItemEnchantments enchs = EnchantmentHelper.getEnchantmentsForCrafting(target);
        for (var entry : enchs.entrySet()) {
            int lvl = entry.getIntValue();
            if (lvl <= 0 || !DisenchantmentApplication.isCurse(entry.getKey())) {
                continue;
            }
            Identifier id = entry.getKey().unwrapKey().map(k -> k.identifier()).orElse(null);
            if (id == null) continue;
            var ewl = new EnchantmentWithLevel(id, lvl);
            var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.PURGE_CURSE);
            var bundle = menu.resolveCleanseCostBundle(ewl, OptionType.PURGE_CURSE, ApplyEnchantmentPayload.AUTO_COST_OPTION);
            if (xpReq.isMetBy(menu.player) && (menu.player.getAbilities().instabuild || bundle != null)) {
                return true;
            }
        }
        return false;
    }

    public static void applyExtractBook(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier targetId,
        int costOptionIndex
    ) {
        if (!MenuCleanseQuotes.canExtractToBook(target)) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        Holder<Enchantment> matched = DisenchantmentApplication.findHolderById(current, targetId);
        if (matched == null || DisenchantmentApplication.isCurse(matched)) {
            return;
        }
        int curLvl = current.getLevel(matched);
        if (curLvl <= 0) {
            return;
        }
        var ewl = new EnchantmentWithLevel(targetId, curLvl);
        List<ItemStack> matchedBundle = menu.resolveCleanseCostBundle(
            ewl, OptionType.EXTRACT_ENCHANTMENT_BOOK, costOptionIndex);
        if (!menu.player.getAbilities().instabuild && matchedBundle == null) {
            return;
        }
        boolean catalystRoute = MenuCleanseQuotes.bundleUsesCatalyst(matchedBundle);
        XpCostConversion.XpRequirement xpReq = catalystRoute
            ? XpCostConversion.XpRequirement.FREE
            : menu.resolveEffectiveXpRequirement(ewl, OptionType.EXTRACT_ENCHANTMENT_BOOK);
        if (!xpReq.isMetBy(menu.player)) {
            return;
        }

        if (!catalystRoute) {
            XpCostConversion.deductXp(menu.player, xpReq);
        }
        menu.deductCleanseBundle(matchedBundle);

        ItemStack extractedBook = new ItemStack(Items.ENCHANTED_BOOK, 1);
        ItemEnchantments.Mutable single = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        single.set(matched, curLvl);
        extractedBook.set(DataComponents.STORED_ENCHANTMENTS, single.toImmutable());

        BitSet sourceSlices = EnchantmentProvenance.getPaidSlices(target, targetId);
        BitSet extractedSlices = EnchantmentProvenance.truncateToLevel(sourceSlices, curLvl);
        EnchantmentProvenance.setPaidSlices(extractedBook, targetId, extractedSlices);

        int sourceGen = TomeWearData.getGeneration(target);
        boolean fullyTablePaid = extractedSlices.cardinality() >= curLvl;
        int nextGen = computeExtractedBookGeneration(sourceGen, fullyTablePaid, catalystRoute);
        TomeWearData.setGeneration(extractedBook, nextGen);

        ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (!entry.getKey().equals(matched)) {
                remaining.set(entry.getKey(), entry.getIntValue());
            }
        }
        ItemStack resulting = DisenchantmentApplication.finishEnchantmentWrite(target, remaining.toImmutable());
        InkStainLore.reconcileAfterRemovalCleanse(resulting);
        menu.setEnchantableSlot(resulting);
        InventoryUtils.insertItem(menu.player, extractedBook, Optional.empty());
        DisenchantmentApplication.playCleanseFeedback(menu, 1.22f);
        chiseled_enchanting_table.menu.TableVisualEvents.onCleanseExtractOrStrip(menu.world(), menu.tableBlockPos(), true);
        Advancement.give(menu.player, "cleanse_enchantment");
    }

    public static void applyPurgeAllCurses(ChiseledEnchantingTableScreenHandler menu, ItemStack target) {
        if (!menu.disenchantAllowCursePurge()) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        if (current.isEmpty()) {
            return;
        }
        List<Holder<Enchantment>> curses = new ArrayList<>();
        for (var entry : current.entrySet()) {
            if (entry.getIntValue() > 0 && DisenchantmentApplication.isCurse(entry.getKey())) {
                curses.add(entry.getKey());
            }
        }
        if (curses.isEmpty()) {
            return;
        }
        curses.sort((a, b) -> a.getRegisteredName().compareTo(b.getRegisteredName()));

        List<Holder<Enchantment>> purged = new ArrayList<>();
        for (Holder<Enchantment> curse : curses) {
            Identifier id = curse.unwrapKey().map(k -> k.identifier()).orElse(null);
            if (id == null) continue;
            int lvl = current.getLevel(curse);
            var ewl = new EnchantmentWithLevel(id, lvl);
            var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.PURGE_CURSE);
            var bundle = menu.resolveCleanseCostBundle(ewl, OptionType.PURGE_CURSE, ApplyEnchantmentPayload.AUTO_COST_OPTION);
            if (!xpReq.isMetBy(menu.player) || (!menu.player.getAbilities().instabuild && bundle == null)) {
                continue;
            }
            XpCostConversion.deductXp(menu.player, xpReq);
            menu.deductCleanseBundle(bundle);
            purged.add(curse);
        }
        if (purged.isEmpty()) {
            return;
        }

        ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (!purged.contains(entry.getKey())) {
                next.set(entry.getKey(), entry.getIntValue());
            }
        }
        ItemStack resulting = DisenchantmentApplication.finishEnchantmentWrite(target, next.toImmutable());
        InkStainLore.reconcileAfterRemovalCleanse(resulting);
        menu.setEnchantableSlot(resulting);
        DisenchantmentApplication.playCleanseFeedback(menu, 0.68f);
        chiseled_enchanting_table.menu.TableVisualEvents.onCleansePurgeCurse(
            menu.world(), menu.tableBlockPos(), purged.size()
        );
        Advancement.give(menu.player, "purge_curse");
    }
}
