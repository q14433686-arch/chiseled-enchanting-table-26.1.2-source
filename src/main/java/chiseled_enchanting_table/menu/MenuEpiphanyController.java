package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.registry.CetTags;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic Grand Library Treasure Epiphany probability, slot count, candidate rolling, and
 * freeze/reroll payment controller extracted from {@link ChiseledEnchantingTableScreenHandler}
 * (Audit COUP-06).
 */
public final class MenuEpiphanyController {
    private static final TagKey<Enchantment> TREASURE_TAG = CetTags.Enchantments.TREASURE;
    private static final TagKey<Enchantment> CURSE_TAG = CetTags.Enchantments.CURSE;

    private MenuEpiphanyController() {}

    public static int epiphanyMinBookshelves(ChiseledEnchantingTableScreenHandler h) {
        return h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyMinBookshelves()
            : ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves();
    }

    public static double epiphanyChance(ChiseledEnchantingTableScreenHandler h, ItemStack targetStack) {
        if (!h.isEpiphanyEnabled()) {
            return 0.0;
        }
        double base = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyBaseChance()
            : ChiseledEnchantingTableData.treasureEpiphanyBaseChance();
        double perShelf = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyPerShelfChance()
            : ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance();
        double perTome = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyPerTomeChance()
            : ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance();
        double max = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyMaxChance()
            : ChiseledEnchantingTableData.treasureEpiphanyMaxChance();
        return LibraryResonance.epiphanyProbability(
            h.libraryResonanceConfig(),
            h.regularBookshelves,
            epiphanyMinBookshelves(h),
            base,
            perShelf,
            perTome,
            max,
            h.totalMasteryUnits(),
            h.affinityMasteryUnits(targetStack)
        );
    }

    public static int epiphanyMaxSlots(ChiseledEnchantingTableScreenHandler h, ItemStack targetStack) {
        if (!h.isEpiphanyEnabled()) {
            return 0;
        }
        int cap = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyMaxSlotsCap()
            : ChiseledEnchantingTableData.treasureEpiphanyMaxSlotsCap();
        int step = h.world().isClientSide()
            ? SyncedEnchantmentCosts.epiphanyScorePerSlot()
            : ChiseledEnchantingTableData.treasureEpiphanyScorePerSlot();
        return LibraryResonance.epiphanyMaxSlots(
            h.libraryResonanceConfig(),
            h.regularBookshelves,
            epiphanyMinBookshelves(h),
            cap,
            step,
            h.totalMasteryUnits(),
            h.affinityMasteryUnits(targetStack)
        );
    }

    public static boolean isTreasureEnchantment(
        ChiseledEnchantingTableScreenHandler h,
        Holder.Reference<Enchantment> holder
    ) {
        if (holder.is(CURSE_TAG)) return false;
        Identifier id = holder.key().identifier();
        if (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(id)) {
            return false;
        }
        Set<Identifier> explicit = h.world().isClientSide()
            ? SyncedEnchantmentCosts.explicitTreasureIds()
            : ChiseledEnchantingTableData.treasureEpiphanyExplicitIds(h.world().registryAccess());
        return holder.is(TREASURE_TAG) || explicit.contains(id);
    }

    public static List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(
        ChiseledEnchantingTableScreenHandler h,
        ItemStack targetStack
    ) {
        if (!h.isEpiphanyEnabled()) {
            return List.of();
        }
        int minShelves = epiphanyMinBookshelves(h);
        int maxSlots = epiphanyMaxSlots(h, targetStack);
        if (h.regularBookshelves < minShelves
            || maxSlots <= 0
            || targetStack.isEmpty()
            || targetStack.is(Items.ENCHANTED_BOOK)
            || ChiseledEnchantingTableData.isResidualBook(targetStack)) {
            return List.of();
        }
        boolean isBlankBook = targetStack.is(Items.BOOK);
        var registry = h.world().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> poolHolders = new ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            if (!isTreasureEnchantment(h, holder)) continue;
            Identifier id = holder.key().identifier();
            int maxLvl = Math.max(1, holder.value().getMaxLevel());
            if (h.storedMasterLevel(id) >= maxLvl) continue;
            if (isBlankBook || holder.value().canEnchant(targetStack)) {
                poolHolders.add(holder);
            }
        }
        if (poolHolders.isEmpty()) {
            return List.of();
        }
        poolHolders.sort(Comparator.comparing(holder -> holder.key().identifier().toString()));
        List<Identifier> pool = new ArrayList<>(poolHolders.size());
        List<Double> poolWeights = new ArrayList<>(poolHolders.size());
        for (Holder.Reference<Enchantment> holder : poolHolders) {
            pool.add(holder.key().identifier());
            poolWeights.add(InscriptionRitualMechanics.computeEpiphanySelectionWeight(holder));
        }

        int seed = h.resolvedEpiphanySeed();
        int cycle = h.resolvedEpiphanyCycleIndex();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(targetStack.getItem());
        long baseHash = 0x9E3779B97F4A7C15L;
        baseHash = splitMix64(baseHash ^ Integer.toUnsignedLong(seed));
        baseHash = splitMix64(baseHash ^ Integer.toUnsignedLong(cycle));
        baseHash = splitMix64(baseHash ^ h.player.getUUID().getMostSignificantBits());
        baseHash = splitMix64(baseHash ^ h.player.getUUID().getLeastSignificantBits());
        baseHash = splitMix64(baseHash ^ itemId.toString().hashCode());

        double totalUnits = h.totalMasteryUnits();
        double affinityUnits = h.affinityMasteryUnits(targetStack);
        var resonanceCfg = h.libraryResonanceConfig();
        double resScore = LibraryResonance.epiphanyResonanceScore(
            resonanceCfg,
            h.regularBookshelves,
            minShelves,
            totalUnits,
            affinityUnits
        );
        double baseProb = epiphanyChance(h, targetStack);
        List<EnchantmentWithLevel> out = new ArrayList<>(maxSlots);
        double slotFactor = 1.0;
        for (int slotIdx = 0; slotIdx < maxSlots && !pool.isEmpty(); slotIdx++) {
            long hash = splitMix64(baseHash ^ (0x517CC1B727220A95L * (slotIdx + 1L)));
            double roll = ((hash >>> 11) * (1.0 / (1L << 53)));
            if (roll < baseProb * slotFactor) {
                long pickHash = splitMix64(hash ^ 0xBF58476D1CE4E5B9L);
                int index = InscriptionRitualMechanics.pickWeightedIndex(poolWeights, pickHash);
                Identifier chosen = pool.remove(index);
                poolWeights.remove(index);
                int maxLvl = LibraryResonance.resolveMaxLevel(h.world(), chosen);
                int storedLvl = h.storedMasterLevel(chosen);
                int minTier = Math.min(maxLvl, storedLvl + 1);
                double ownR = LibraryResonance.relativeMastery(storedLvl, maxLvl);
                int tier = LibraryResonance.rollEpiphanyTier(
                    resonanceCfg,
                    splitMix64(pickHash ^ 0x94D049BB133111EBL),
                    minTier,
                    maxLvl,
                    resScore,
                    ownR
                );
                out.add(new EnchantmentWithLevel(chosen, tier));
            }
            slotFactor *= 0.78;
        }
        return List.copyOf(out);
    }

    public static boolean matchesActiveTreasureEpiphany(
        ChiseledEnchantingTableScreenHandler h,
        ApplyEnchantmentPayload payload
    ) {
        if (payload.option_type() != OptionType.TREASURE_EPIPHANY || payload.enchantment_level() < 1) {
            return false;
        }
        for (EnchantmentWithLevel active : computeActiveTreasureEpiphanies(h, h.getEnchantableItem())) {
            if (active.enchantment_id().equals(payload.enchantment_id())
                && active.enchantment_level() == payload.enchantment_level()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static ItemStack findAffordableSingleOption(ChiseledEnchantingTableScreenHandler h, List<ItemStack> options) {
        ItemStack costSlot = h.getCostItem();
        for (ItemStack opt : options) {
            if (opt.isEmpty()) continue;
            int total = 0;
            if (ItemStack.isSameItemSameComponents(costSlot, opt)) {
                total += costSlot.getCount();
            }
            for (int slot = 0; slot < h.player.getInventory().getContainerSize(); slot++) {
                ItemStack inv = h.player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(inv, opt)) {
                    total += inv.getCount();
                }
            }
            if (total >= opt.getCount()) {
                return opt;
            }
        }
        return null;
    }

    static void consumeSingleStackOption(ChiseledEnchantingTableScreenHandler h, ItemStack req) {
        int remaining = req.getCount();
        ItemStack costSlot = h.getCostItem();
        if (ItemStack.isSameItemSameComponents(costSlot, req)) {
            int take = Math.min(remaining, costSlot.getCount());
            costSlot.shrink(take);
            remaining -= take;
        }
        for (int slot = 0; slot < h.player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack inv = h.player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(inv, req)) {
                int take = Math.min(remaining, inv.getCount());
                inv.shrink(take);
                remaining -= take;
            }
        }
        h.markInventoryChanged();
        h.player.getInventory().setChanged();
    }

    public static boolean canAffordRerollEpiphany(ChiseledEnchantingTableScreenHandler h) {
        if (!h.isEpiphanyEnabled()) return false;
        if (h.regularBookshelves < h.epiphanyMinBookshelves()) return false;
        if (h.player.getAbilities().instabuild) return true;
        if (h.player.experienceLevel < h.epiphanyRerollXpLevels()) return false;
        return findAffordableSingleOption(h, h.epiphanyRerollCosts()) != null;
    }

    public static boolean canAffordFreezeEpiphany(ChiseledEnchantingTableScreenHandler h) {
        if (!h.isEpiphanyEnabled()) return false;
        if (h.regularBookshelves < h.epiphanyMinBookshelves()) return false;
        int maxSec = Math.max(10, h.epiphanyMaxFreezeTicks() / 20);
        if (h.isEpiphanyFrozen() && h.countdownSeconds() >= maxSec - 2) return false;
        if (h.player.getAbilities().instabuild) return true;
        if (h.player.experienceLevel < h.epiphanyFreezeXpLevels()) return false;
        return findAffordableSingleOption(h, h.epiphanyFreezeCosts()) != null;
    }

    public static boolean freezeEpiphany(ChiseledEnchantingTableScreenHandler h) {
        h.refreshLiveShelfState();
        if (!canAffordFreezeEpiphany(h)) {
            return false;
        }
        if (!h.player.getAbilities().instabuild) {
            int xpLevels = h.epiphanyFreezeXpLevels();
            if (xpLevels > 0) {
                h.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(h.player, xpLevels));
            }
            ItemStack req = findAffordableSingleOption(h, h.epiphanyFreezeCosts());
            if (req != null) {
                consumeSingleStackOption(h, req);
            }
        }
        int cycleTicks = h.epiphanyCycleTicks();
        int addTicks = h.epiphanyFreezeDurationTicks();
        int maxTicks = h.epiphanyMaxFreezeTicks();
        h.executeAtTable((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof ChiseledEnchantingTableBlockEntity be) {
                be.addFreezeTicks(lvl.getGameTime(), cycleTicks, addTicks, maxTicks);
            }
        });
        h.broadcastChanges();
        Advancement.give(h.player, "freeze_epiphany");
        TableVisualEvents.onEpiphanyFreeze(h.world(), h.tableBlockPos());
        return true;
    }

    public static boolean rerollEpiphany(ChiseledEnchantingTableScreenHandler h) {
        h.refreshLiveShelfState();
        if (!canAffordRerollEpiphany(h)) {
            return false;
        }
        if (!h.player.getAbilities().instabuild) {
            int xpLevels = h.epiphanyRerollXpLevels();
            if (xpLevels > 0) {
                h.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(h.player, xpLevels));
            }
            ItemStack req = findAffordableSingleOption(h, h.epiphanyRerollCosts());
            if (req != null) {
                consumeSingleStackOption(h, req);
            }
        }
        h.advanceTableEpiphanySeed(true);
        TableVisualEvents.onArcanePulse(h.world(), h.tableBlockPos(), TableVisualEvents.COLOR_ARCANE_VIOLET);
        return true;
    }

    static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
