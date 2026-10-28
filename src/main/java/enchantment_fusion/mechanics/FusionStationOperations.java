package enchantment_fusion.mechanics;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.EnchantInput;
import enchantment_fusion.mechanics.FusionRuleEngine.PairEvaluation;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Evaluates and executes workstation actions across the 4-slot Enchantment Fusion Table container:
 * <ul>
 *   <li>Slot {@code 0}: Primary Carrier {@code A} (Enchanted Book, Fused Enchanted Book, or Echo Shard)</li>
 *   <li>Slot {@code 1}: Secondary Graft {@code B} (Enchanted Book or Amethyst Cluster)</li>
 *   <li>Slot {@code 2}: Catalyst / Stabilizer (Resonance Stabilizer, Amethyst Shard, Lapis, or CET catalysts)</li>
 *   <li>Slot {@code 3}: Host Target (Blank/Enchanted Equipment, Plain Book, or Fused Book for Flip/Split)</li>
 * </ul>
 */
public final class FusionStationOperations {
    public static final int SLOT_PRIMARY = 0;
    public static final int SLOT_SECONDARY = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_TARGET = 3;

    public enum OperationKind {
        NONE,
        FUSE_BOOKS,
        INSCRIBE_FUSED_BOOK,
        GRAFT_BOOKS_TO_GEAR,
        TOP_UP_LEVEL,
        EXPAND_VESSEL,
        INVALID
    }

    public record SingleBookEnchant(
        Holder<Enchantment> holder,
        String id,
        int level,
        int maxLevel
    ) {}

    public record StationPreview(
        OperationKind kind,
        boolean executable,
        RejectReason rejectReason,
        FusedPair previewPair,
        boolean repulsive,
        double instabilityChance,
        int xpCost,
        boolean canFlipTarget,
        boolean canSplitTarget,
        int flipOrSplitXpCost
    ) {
        public static final StationPreview IDLE = new StationPreview(
            OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, false, false, 0
        );
    }

    private FusionStationOperations() {}

    public static Optional<SingleBookEnchant> extractSingleStoredEnchantment(ItemStack stack) {
        if (stack == null || !stack.is(Items.ENCHANTED_BOOK) || stack.getCount() != 1) {
            return Optional.empty();
        }
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.size() != 1) {
            return Optional.empty();
        }
        Object2IntMap.Entry<Holder<Enchantment>> entry = stored.entrySet().iterator().next();
        Holder<Enchantment> holder = entry.getKey();
        Optional<ResourceKey<Enchantment>> keyOpt = holder.unwrapKey();
        if (keyOpt.isEmpty()) {
            return Optional.empty();
        }
        int lvl = entry.getIntValue();
        int maxLvl = holder.value().getMaxLevel();
        return Optional.of(new SingleBookEnchant(holder, keyOpt.get().identifier().toString(), lvl, maxLvl));
    }

    public static Optional<Holder<Enchantment>> lookupEnchantment(HolderLookup.Provider registries, String idStr) {
        if (registries == null || idStr == null || idStr.isBlank()) {
            return Optional.empty();
        }
        Identifier parsed = Identifier.tryParse(idStr);
        if (parsed == null) {
            return Optional.empty();
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsed);
        return registries.lookupOrThrow(Registries.ENCHANTMENT).get(key).map(h -> (Holder<Enchantment>) h);
    }

    public static Set<String> readVanillaEnchantIds(ItemStack gear) {
        Set<String> out = new HashSet<>();
        ItemEnchantments ench = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : ench.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(k -> out.add(k.identifier().toString()));
        }
        return out;
    }

    public static StationPreview inspect(Container inv, HolderLookup.Provider registries) {
        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        ItemStack sSecondary = inv.getItem(SLOT_SECONDARY);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        ItemStack sTarget = inv.getItem(SLOT_TARGET);

        boolean hasSplitFlipCat = CetSoftBridge.isSplitOrFlipCatalyst(sCatalyst);
        int flipSplitXp = CetSoftBridge.isZeroXpCetCleanseCatalyst(sCatalyst) ? 0 : 2;
        boolean canFlip = false;
        boolean canSplit = false;

        FusedEnchantmentList targetFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!sTarget.isEmpty() && !targetFusions.isEmpty()) {
            FusedPair last = targetFusions.pairs().getLast();
            boolean targetingGear = VesselCapacityRules.isPotentialEquipmentHost(sTarget);
            boolean secFitsHost = true;
            if (targetingGear) {
                secFitsHost = lookupEnchantment(registries, last.secondaryId())
                    .map(h -> h.value().canEnchant(sTarget))
                    .orElse(false);
            }
            canFlip = FusionRuleEngine.canFlipPair(last, targetingGear, secFitsHost, hasSplitFlipCat) == RejectReason.NONE;
            canSplit = FusionRuleEngine.canSplitPair(last, hasSplitFlipCat) == RejectReason.NONE
                && (sTarget.is(FusionItems.FUSED_ENCHANTED_BOOK) ? (sPrimary.isEmpty() && sSecondary.isEmpty()) : sPrimary.isEmpty());
        }

        // Case 1: Vessel Expansion (Target = 1-slot Gear, Primary = Echo Shard, Secondary = Amethyst Cluster or Stabilizer)
        if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)
            && sPrimary.is(Items.ECHO_SHARD)
            && (sSecondary.is(Items.AMETHYST_CLUSTER) || CetSoftBridge.isStabilizerCatalyst(sSecondary))) {
            if (!VesselCapacityRules.canExpandStack(sTarget)) {
                return new StationPreview(OperationKind.INVALID, false, RejectReason.ALREADY_MAX_VESSEL, null, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
            }
            return new StationPreview(OperationKind.EXPAND_VESSEL, true, RejectReason.NONE, null, false, 0.0, 5, canFlip, canSplit, flipSplitXp);
        }

        // Case 2: Inscribe Fused Enchanted Book (Slot 0) onto Target Equipment (Slot 3)
        if (sPrimary.is(FusionItems.FUSED_ENCHANTED_BOOK) && sSecondary.isEmpty() && VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
            FusedEnchantmentList bookList = sPrimary.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (!bookList.isEmpty()) {
                FusedPair pair = bookList.pairs().getFirst();
                Optional<Holder<Enchantment>> pHolder = lookupEnchantment(registries, pair.primaryId());
                if (pHolder.isEmpty() || !pHolder.get().value().canEnchant(sTarget)) {
                    return new StationPreview(OperationKind.INVALID, false, RejectReason.PRIMARY_INCOMPATIBLE_HOST, pair, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
                }
                int maxSlots = VesselCapacityRules.getMaxVesselSlots(sTarget);
                if (targetFusions.size() >= maxSlots) {
                    return new StationPreview(OperationKind.INVALID, false, RejectReason.VESSEL_CAPACITY_FULL, pair, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
                }
                Set<String> hostVanillaIds = readVanillaEnchantIds(sTarget);
                if (targetFusions.containsEnchantmentId(pair.primaryId())
                    || targetFusions.containsEnchantmentId(pair.secondaryId())
                    || hostVanillaIds.contains(pair.secondaryId())) {
                    return new StationPreview(OperationKind.INVALID, false, RejectReason.DUPLICATE_ON_HOST, pair, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
                }
                int xp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst) ? 0 : 3;
                return new StationPreview(OperationKind.INSCRIBE_FUSED_BOOK, true, RejectReason.NONE, pair, false, 0.0, xp, canFlip, canSplit, flipSplitXp);
            }
        }

        // Case 3: Top-up an existing fused pair on Target (Slot 3) using a higher-level single book in Primary (Slot 0) or Secondary (Slot 1)
        if (!sTarget.isEmpty() && !targetFusions.isEmpty() && (sPrimary.isEmpty() != sSecondary.isEmpty())) {
            ItemStack donorStack = !sPrimary.isEmpty() ? sPrimary : sSecondary;
            Optional<SingleBookEnchant> donorOpt = extractSingleStoredEnchantment(donorStack);
            if (donorOpt.isPresent()) {
                SingleBookEnchant donor = donorOpt.get();
                for (FusedPair existing : targetFusions.pairs()) {
                    if (!existing.isAberrant()) {
                        if (existing.primaryId().equals(donor.id()) && donor.level() > existing.primaryLevel()) {
                            FusedPair upgraded = existing.withPrimaryLevel(Math.min(donor.maxLevel(), donor.level()));
                            return new StationPreview(OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE, upgraded, false, 0.0, 2, canFlip, canSplit, flipSplitXp);
                        }
                        if (existing.secondaryId().equals(donor.id()) && donor.level() > existing.secondaryLevel()) {
                            FusedPair upgraded = existing.withSecondaryLevel(Math.min(donor.maxLevel(), donor.level()));
                            return new StationPreview(OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE, upgraded, false, 0.0, 2, canFlip, canSplit, flipSplitXp);
                        }
                    }
                }
            }
        }

        // Case 4: Two single-enchantment books in Slot 0 (Primary A) + Slot 1 (Secondary B)
        Optional<SingleBookEnchant> primaryBook = extractSingleStoredEnchantment(sPrimary);
        Optional<SingleBookEnchant> secondaryBook = extractSingleStoredEnchantment(sSecondary);
        if (primaryBook.isPresent() && secondaryBook.isPresent()) {
            SingleBookEnchant p = primaryBook.get();
            SingleBookEnchant s = secondaryBook.get();
            boolean hasStab = CetSoftBridge.isStabilizerCatalyst(sCatalyst);
            boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);

            if (sTarget.isEmpty() || (sTarget.is(Items.BOOK) && sTarget.getCount() >= 1)) {
                PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                    new EnchantInput(p.id(), p.level(), p.maxLevel(), true),
                    new EnchantInput(s.id(), s.level(), s.maxLevel(), true),
                    false, FusedEnchantmentList.EMPTY, Set.of(), 0, hasStab, zeroXp
                );
                if (!eval.valid()) {
                    return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
                }
                return new StationPreview(
                    OperationKind.FUSE_BOOKS, true, RejectReason.NONE,
                    eval.resultingPair(), eval.repulsiveExclusive(), eval.instabilityChance(), eval.xpLevelsCost(),
                    canFlip, canSplit, flipSplitXp
                );
            }

            if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
                boolean pFits = p.holder().value().canEnchant(sTarget);
                PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                    new EnchantInput(p.id(), p.level(), p.maxLevel(), pFits),
                    new EnchantInput(s.id(), s.level(), s.maxLevel(), true),
                    true, targetFusions, readVanillaEnchantIds(sTarget),
                    VesselCapacityRules.getMaxVesselSlots(sTarget), hasStab, zeroXp
                );
                if (!eval.valid()) {
                    return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
                }
                return new StationPreview(
                    OperationKind.GRAFT_BOOKS_TO_GEAR, true, RejectReason.NONE,
                    eval.resultingPair(), eval.repulsiveExclusive(), eval.instabilityChance(), eval.xpLevelsCost(),
                    canFlip, canSplit, flipSplitXp
                );
            }
        }

        return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, canFlip, canSplit, flipSplitXp);
    }

    public static boolean swapInputSlots(Container inv) {
        ItemStack a = inv.getItem(SLOT_PRIMARY);
        ItemStack b = inv.getItem(SLOT_SECONDARY);
        if (a.isEmpty() && b.isEmpty()) {
            return false;
        }
        inv.setItem(SLOT_PRIMARY, b);
        inv.setItem(SLOT_SECONDARY, a);
        inv.setChanged();
        return true;
    }

    public static boolean executePrimaryOperation(Container inv, Player player, HolderLookup.Provider registries) {
        StationPreview preview = inspect(inv, registries);
        if (!preview.executable()) {
            return false;
        }
        if (!player.getAbilities().instabuild && player.experienceLevel < preview.xpCost()) {
            return false;
        }

        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        ItemStack sSecondary = inv.getItem(SLOT_SECONDARY);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        ItemStack sTarget = inv.getItem(SLOT_TARGET);

        switch (preview.kind()) {
            case EXPAND_VESSEL -> {
                sTarget.set(FusionDataComponents.VESSEL_BONUS, 1);
                sPrimary.shrink(1);
                sSecondary.shrink(1);
            }
            case FUSE_BOOKS -> {
                FusedPair pair = preview.previewPair();
                ItemStack outBook = FusedEnchantedBookItem.createForPair(pair);
                if (sTarget.is(Items.BOOK)) {
                    sTarget.shrink(1);
                    if (sTarget.isEmpty()) {
                        inv.setItem(SLOT_TARGET, outBook);
                    } else {
                        player.getInventory().placeItemBackInInventory(outBook);
                    }
                } else {
                    inv.setItem(SLOT_TARGET, outBook);
                }
                sPrimary.shrink(1);
                sSecondary.shrink(1);
                if (preview.repulsive() && CetSoftBridge.isStabilizerCatalyst(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case INSCRIBE_FUSED_BOOK -> {
                FusedPair pair = preview.previewPair();
                applyFusedPairToGear(sTarget, pair, registries);
                sPrimary.shrink(1);
                if (CetSoftBridge.isZeroXpCetStabilizer(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case GRAFT_BOOKS_TO_GEAR -> {
                FusedPair pair = preview.previewPair();
                applyFusedPairToGear(sTarget, pair, registries);
                sPrimary.shrink(1);
                sSecondary.shrink(1);
                if (preview.repulsive() && CetSoftBridge.isStabilizerCatalyst(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case TOP_UP_LEVEL -> {
                ItemStack donorStack = !sPrimary.isEmpty() ? sPrimary : sSecondary;
                SingleBookEnchant donor = extractSingleStoredEnchantment(donorStack).orElseThrow();
                FusedEnchantmentList list = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                for (int i = 0; i < list.size(); i++) {
                    FusedPair old = list.pairs().get(i);
                    if (!old.isAberrant() && old.primaryId().equals(donor.id()) && donor.level() > old.primaryLevel()) {
                        int nextLvl = Math.min(donor.maxLevel(), donor.level());
                        sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, old.withPrimaryLevel(nextLvl)));
                        if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
                            setGearVanillaEnchantLevel(sTarget, donor.holder(), nextLvl);
                        }
                        break;
                    }
                    if (!old.isAberrant() && old.secondaryId().equals(donor.id()) && donor.level() > old.secondaryLevel()) {
                        int nextLvl = Math.min(donor.maxLevel(), donor.level());
                        sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, old.withSecondaryLevel(nextLvl)));
                        break;
                    }
                }
                donorStack.shrink(1);
            }
            default -> {
                return false;
            }
        }

        if (!player.getAbilities().instabuild && preview.xpCost() > 0) {
            player.giveExperienceLevels(-preview.xpCost());
        }
        if (player instanceof ServerPlayer serverPlayer && preview.previewPair() != null) {
            CompendiumDiscoveryTracker.recordDiscovery(
                serverPlayer,
                preview.previewPair().primaryId(),
                preview.previewPair().secondaryId()
            );
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.05F);
        return true;
    }

    public static boolean executeFlipOperation(Container inv, Player player, HolderLookup.Provider registries) {
        StationPreview preview = inspect(inv, registries);
        if (!preview.canFlipTarget()) {
            return false;
        }
        if (!player.getAbilities().instabuild && player.experienceLevel < preview.flipOrSplitXpCost()) {
            return false;
        }
        ItemStack sTarget = inv.getItem(SLOT_TARGET);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        FusedEnchantmentList list = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        int idx = list.size() - 1;
        FusedPair oldPair = list.pairs().get(idx);
        FusedPair flipped = oldPair.flipped();
        sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(idx, flipped));

        if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
            removeGearVanillaEnchant(sTarget, oldPair.primaryId());
            lookupEnchantment(registries, flipped.primaryId())
                .ifPresent(h -> setGearVanillaEnchantLevel(sTarget, h, flipped.primaryLevel()));
        }

        sCatalyst.shrink(1);
        if (!player.getAbilities().instabuild && preview.flipOrSplitXpCost() > 0) {
            player.giveExperienceLevels(-preview.flipOrSplitXpCost());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CompendiumDiscoveryTracker.recordDiscovery(serverPlayer, flipped.primaryId(), flipped.secondaryId());
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.15F);
        return true;
    }

    public static boolean executeSplitOperation(Container inv, Player player, HolderLookup.Provider registries) {
        StationPreview preview = inspect(inv, registries);
        if (!preview.canSplitTarget()) {
            return false;
        }
        if (!player.getAbilities().instabuild && player.experienceLevel < preview.flipOrSplitXpCost()) {
            return false;
        }
        ItemStack sTarget = inv.getItem(SLOT_TARGET);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        FusedEnchantmentList list = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        int idx = list.size() - 1;
        FusedPair oldPair = list.pairs().get(idx);

        Optional<Holder<Enchantment>> pHolder = lookupEnchantment(registries, oldPair.primaryId());
        Optional<Holder<Enchantment>> sHolder = lookupEnchantment(registries, oldPair.secondaryId());
        if (pHolder.isEmpty() || sHolder.isEmpty()) {
            return false;
        }

        if (sTarget.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            inv.setItem(SLOT_PRIMARY, createSingleEnchantedBook(pHolder.get(), oldPair.primaryLevel()));
            inv.setItem(SLOT_SECONDARY, createSingleEnchantedBook(sHolder.get(), oldPair.secondaryLevel()));
            inv.setItem(SLOT_TARGET, ItemStack.EMPTY);
        } else {
            // Equipment host: keep Primary A as normal vanilla enchantment on the gear, extract Secondary B to Slot 0!
            FusedEnchantmentList remaining = list.removeAt(idx);
            if (remaining.isEmpty()) {
                sTarget.remove(FusionDataComponents.FUSED_PAIRS);
            } else {
                sTarget.set(FusionDataComponents.FUSED_PAIRS, remaining);
            }
            inv.setItem(SLOT_PRIMARY, createSingleEnchantedBook(sHolder.get(), oldPair.secondaryLevel()));
        }

        sCatalyst.shrink(1);
        if (!player.getAbilities().instabuild && preview.flipOrSplitXpCost() > 0) {
            player.giveExperienceLevels(-preview.flipOrSplitXpCost());
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.GRINDSTONE_USE, 1.2F);
        return true;
    }

    public static ItemStack createSingleEnchantedBook(Holder<Enchantment> holder, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, 1);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mut.set(holder, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        return book;
    }

    public static void applyFusedPairToGear(ItemStack gear, FusedPair pair, HolderLookup.Provider registries) {
        FusedEnchantmentList existing = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        gear.set(FusionDataComponents.FUSED_PAIRS, existing.append(pair));
        lookupEnchantment(registries, pair.primaryId())
            .ifPresent(h -> setGearVanillaEnchantLevel(gear, h, pair.primaryLevel()));
    }

    private static void setGearVanillaEnchantLevel(ItemStack gear, Holder<Enchantment> holder, int level) {
        ItemEnchantments current = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
        mut.set(holder, Math.max(current.getLevel(holder), level));
        EnchantmentHelper.setEnchantments(gear, mut.toImmutable());
    }

    private static void removeGearVanillaEnchant(ItemStack gear, String enchantId) {
        ItemEnchantments current = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
        mut.removeIf(h -> h.unwrapKey().map(k -> k.identifier().toString().equals(enchantId)).orElse(false));
        EnchantmentHelper.setEnchantments(gear, mut.toImmutable());
    }

    private static void playWorkstationSound(Player player, net.minecraft.sounds.SoundEvent sound, float pitch) {
        if (!player.level().isClientSide()) {
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.BLOCKS, 0.75F, pitch);
        }
    }
}
