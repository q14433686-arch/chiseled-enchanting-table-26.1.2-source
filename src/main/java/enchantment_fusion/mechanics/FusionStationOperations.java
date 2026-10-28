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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * Evaluates and executes 3-Tab + Mode C Enchantment-Picker + Vessel-Socket operations:
 * <ul>
 *   <li>Tab 0 ({@code TAB_FUSE}): Book + Book (or multi-enchantment book) -> Fused Enchanted Book in Slot 3 (no blank book needed!) + Top-Up</li>
 *   <li>Tab 1 ({@code TAB_SOCKET}): Host Gear in Slot 3 -> Vessel Socket card management (Socket Fused Book, Flip Socket, Unsocket to Book, +1 Vessel Inlay) + In-Place Gear 2-in-1 Fusion!</li>
 *   <li>Tab 2 ({@code TAB_COMPENDIUM}): Fused Book Flip / Clean Split into two single books + Resonance Compendium browser</li>
 * </ul>
 */
public final class FusionStationOperations {
    public static final int SLOT_PRIMARY = 0;
    public static final int SLOT_SECONDARY = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_TARGET = 3;

    public static final int TAB_FUSE = 0;
    public static final int TAB_SOCKET = 1;
    public static final int TAB_COMPENDIUM = 2;

    public enum OperationKind {
        NONE,
        FUSE_BOOKS,
        SOCKET_FUSED_BOOK,
        FUSE_ONTO_GEAR,
        TOP_UP_LEVEL,
        EXPAND_VESSEL,
        INVALID
    }

    public record EnchantCandidate(
        int sourceSlot,
        Holder<Enchantment> holder,
        String id,
        int level,
        int maxLevel
    ) {}

    public record ResolvedSelection(
        List<EnchantCandidate> candidates,
        int primaryIndex,
        int secondaryIndex
    ) {
        public EnchantCandidate primary() {
            return (primaryIndex >= 0 && primaryIndex < candidates.size()) ? candidates.get(primaryIndex) : null;
        }

        public EnchantCandidate secondary() {
            return (secondaryIndex >= 0 && secondaryIndex < candidates.size()) ? candidates.get(secondaryIndex) : null;
        }
    }

    public record StationPreview(
        OperationKind kind,
        boolean executable,
        RejectReason rejectReason,
        FusedPair previewPair,
        boolean repulsive,
        double instabilityChance,
        int xpCost,
        ResolvedSelection selection,
        int manageXpCost
    ) {}

    private FusionStationOperations() {}

    public static List<EnchantCandidate> collectCandidates(Container inv, int activeTab) {
        List<EnchantCandidate> out = new ArrayList<>();
        if (activeTab == TAB_SOCKET) {
            // In Tab 1 (Gear Vessel & In-Place Fusion), include unfused standalone enchantments on Host Gear (Slot 3) FIRST,
            // plus any enchantments on books placed in Slot 0 or Slot 1!
            ItemStack gear = inv.getItem(SLOT_TARGET);
            if (VesselCapacityRules.isPotentialEquipmentHost(gear)) {
                FusedEnchantmentList fusedOnGear = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                ItemEnchantments gearEnchants = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : gearEnchants.entrySet()) {
                    Holder<Enchantment> h = entry.getKey();
                    h.unwrapKey().ifPresent(k -> {
                        String id = k.identifier().toString();
                        if (!fusedOnGear.containsEnchantmentId(id)) {
                            out.add(new EnchantCandidate(SLOT_TARGET, h, id, entry.getIntValue(), h.value().getMaxLevel()));
                        }
                    });
                }
            }
            appendBookEnchantments(inv.getItem(SLOT_PRIMARY), SLOT_PRIMARY, out);
            appendBookEnchantments(inv.getItem(SLOT_SECONDARY), SLOT_SECONDARY, out);
        } else {
            // In Tab 0 (Book Fusion), collect all stored enchantments from Slot 0 and Slot 1 (single or multi-enchant books!)
            appendBookEnchantments(inv.getItem(SLOT_PRIMARY), SLOT_PRIMARY, out);
            appendBookEnchantments(inv.getItem(SLOT_SECONDARY), SLOT_SECONDARY, out);
        }
        return out;
    }

    private static void appendBookEnchantments(ItemStack stack, int slotIndex, List<EnchantCandidate> out) {
        if (stack == null || !stack.is(Items.ENCHANTED_BOOK) || stack.getCount() != 1) {
            return;
        }
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> h = entry.getKey();
            h.unwrapKey().ifPresent(k -> out.add(
                new EnchantCandidate(slotIndex, h, k.identifier().toString(), entry.getIntValue(), h.value().getMaxLevel())
            ));
        }
    }

    public static ResolvedSelection resolveSelection(
        List<EnchantCandidate> candidates,
        int preferredA,
        int preferredB
    ) {
        int n = candidates.size();
        if (n == 0) {
            return new ResolvedSelection(candidates, -1, -1);
        }
        int a = (preferredA >= 0 && preferredA < n) ? preferredA : 0;
        int b = (preferredB >= 0 && preferredB < n && preferredB != a) ? preferredB : -1;
        if (b == -1) {
            // Prefer an enchantment from a different slot first, or any distinct enchantment ID
            for (int i = 0; i < n; i++) {
                if (i != a && candidates.get(i).sourceSlot() != candidates.get(a).sourceSlot()
                    && !candidates.get(i).id().equals(candidates.get(a).id())) {
                    b = i;
                    break;
                }
            }
            if (b == -1) {
                for (int i = 0; i < n; i++) {
                    if (i != a && !candidates.get(i).id().equals(candidates.get(a).id())) {
                        b = i;
                        break;
                    }
                }
            }
        }
        return new ResolvedSelection(candidates, a, b);
    }

    public static StationPreview inspect(
        Container inv,
        HolderLookup.Provider registries,
        int activeTab,
        int preferredA,
        int preferredB
    ) {
        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        ItemStack sSecondary = inv.getItem(SLOT_SECONDARY);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        ItemStack sTarget = inv.getItem(SLOT_TARGET);

        int manageXp = (CetSoftBridge.isSplitOrFlipCatalyst(sCatalyst) || CetSoftBridge.isStabilizerCatalyst(sCatalyst)) ? 0 : 2;
        List<EnchantCandidate> candidates = collectCandidates(inv, activeTab);
        ResolvedSelection sel = resolveSelection(candidates, preferredA, preferredB);

        if (activeTab == TAB_COMPENDIUM) {
            return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, sel, manageXp);
        }

        // === TAB 1 (or auto-detected Gear in Slot 3): Vessel Expansion, Socket Fused Book, Top-Up, or In-Place Gear Fusion ===
        if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
            FusedEnchantmentList targetFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);

            // 1. Vessel +1 Inlay (Echo Shard + Amethyst Cluster in Slots 0/1)
            boolean hasEchoAndCluster = (sPrimary.is(Items.ECHO_SHARD) && (sSecondary.is(Items.AMETHYST_CLUSTER) || CetSoftBridge.isStabilizerCatalyst(sSecondary)))
                || (sSecondary.is(Items.ECHO_SHARD) && (sPrimary.is(Items.AMETHYST_CLUSTER) || CetSoftBridge.isStabilizerCatalyst(sPrimary)));
            if (hasEchoAndCluster) {
                if (!VesselCapacityRules.canExpandStack(sTarget)) {
                    return new StationPreview(OperationKind.INVALID, false, RejectReason.ALREADY_MAX_VESSEL, null, false, 0.0, 0, sel, manageXp);
                }
                return new StationPreview(OperationKind.EXPAND_VESSEL, true, RejectReason.NONE, null, false, 0.0, 5, sel, manageXp);
            }

            // 2. Socket a Fused Enchanted Book from Slot 0 or Slot 1 onto ANY Gear (Universal Any-Gear!)
            ItemStack fusedBookSlot = sPrimary.is(FusionItems.FUSED_ENCHANTED_BOOK) ? sPrimary
                : (sSecondary.is(FusionItems.FUSED_ENCHANTED_BOOK) ? sSecondary : ItemStack.EMPTY);
            if (!fusedBookSlot.isEmpty()) {
                FusedEnchantmentList bookList = fusedBookSlot.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                if (!bookList.isEmpty()) {
                    FusedPair pair = bookList.pairs().getFirst();
                    int maxSlots = VesselCapacityRules.getMaxVesselSlots(sTarget);
                    if (targetFusions.size() >= maxSlots) {
                        return new StationPreview(OperationKind.INVALID, false, RejectReason.VESSEL_CAPACITY_FULL, pair, false, 0.0, 0, sel, manageXp);
                    }
                    Set<String> standaloneVanilla = readUnfusedVanillaEnchantIds(sTarget, targetFusions);
                    if (targetFusions.containsEnchantmentId(pair.primaryId())
                        || targetFusions.containsEnchantmentId(pair.secondaryId())
                        || standaloneVanilla.contains(pair.primaryId())
                        || standaloneVanilla.contains(pair.secondaryId())) {
                        return new StationPreview(OperationKind.INVALID, false, RejectReason.DUPLICATE_ON_HOST, pair, false, 0.0, 0, sel, manageXp);
                    }
                    int xp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst) ? 0 : 2;
                    return new StationPreview(OperationKind.SOCKET_FUSED_BOOK, true, RejectReason.NONE, pair, false, 0.0, xp, sel, manageXp);
                }
            }

            // 3. Top-up an existing socketed pair on Gear using a higher-level book in Slot 0/1
            Optional<StationPreview> topUp = checkTopUp(sTarget, targetFusions, sPrimary, sSecondary, sel, manageXp);
            if (topUp.isPresent()) {
                return topUp.get();
            }

            // 4. Fuse two picked candidate enchantments (e.g., two enchantments already on the Gear itself, or Gear + Book!) directly into a Gear Vessel Slot!
            List<EnchantCandidate> gearModeCandidates = activeTab == TAB_SOCKET ? candidates : collectCandidates(inv, TAB_SOCKET);
            ResolvedSelection gearSel = activeTab == TAB_SOCKET ? sel : resolveSelection(gearModeCandidates, preferredA, preferredB);
            EnchantCandidate p = gearSel.primary();
            EnchantCandidate s = gearSel.secondary();
            if (p != null && s != null) {
                Set<String> blockingVanilla = readUnfusedVanillaEnchantIds(sTarget, targetFusions);
                if (p.sourceSlot() == SLOT_TARGET) blockingVanilla.remove(p.id());
                if (s.sourceSlot() == SLOT_TARGET) blockingVanilla.remove(s.id());

                boolean hasStab = CetSoftBridge.isStabilizerCatalyst(sCatalyst);
                boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
                PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                    new EnchantInput(p.id(), p.level(), p.maxLevel(), true),
                    new EnchantInput(s.id(), s.level(), s.maxLevel(), true),
                    true, targetFusions, blockingVanilla,
                    VesselCapacityRules.getMaxVesselSlots(sTarget), hasStab, zeroXp
                );
                if (!eval.valid()) {
                    return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, gearSel, manageXp);
                }
                return new StationPreview(
                    OperationKind.FUSE_ONTO_GEAR, true, RejectReason.NONE,
                    eval.resultingPair(), eval.repulsiveExclusive(), eval.instabilityChance(), eval.xpLevelsCost(),
                    gearSel, manageXp
                );
            }

            return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, gearSel, manageXp);
        }

        // === TAB 0: Book Fusion -> Output Fused Enchanted Book into Slot 3 (NO blank book required!) ===
        if (sTarget.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            FusedEnchantmentList bookFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            Optional<StationPreview> topUp = checkTopUp(sTarget, bookFusions, sPrimary, sSecondary, sel, manageXp);
            if (topUp.isPresent()) {
                return topUp.get();
            }
        }

        EnchantCandidate p = sel.primary();
        EnchantCandidate s = sel.secondary();
        if (p != null && s != null) {
            if (!sTarget.isEmpty() && !(sTarget.is(Items.BOOK) && sTarget.getCount() >= 1)) {
                return new StationPreview(OperationKind.INVALID, false, RejectReason.OUTPUT_OCCUPIED, null, false, 0.0, 0, sel, manageXp);
            }
            boolean hasStab = CetSoftBridge.isStabilizerCatalyst(sCatalyst);
            boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
            PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                new EnchantInput(p.id(), p.level(), p.maxLevel(), true),
                new EnchantInput(s.id(), s.level(), s.maxLevel(), true),
                false, FusedEnchantmentList.EMPTY, Set.of(), 0, hasStab, zeroXp
            );
            if (!eval.valid()) {
                return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, sel, manageXp);
            }
            return new StationPreview(
                OperationKind.FUSE_BOOKS, true, RejectReason.NONE,
                eval.resultingPair(), eval.repulsiveExclusive(), eval.instabilityChance(), eval.xpLevelsCost(),
                sel, manageXp
            );
        }

        return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, sel, manageXp);
    }

    private static Optional<StationPreview> checkTopUp(
        ItemStack sTarget,
        FusedEnchantmentList targetFusions,
        ItemStack sPrimary,
        ItemStack sSecondary,
        ResolvedSelection sel,
        int manageXp
    ) {
        if (targetFusions.isEmpty()) {
            return Optional.empty();
        }
        List<EnchantCandidate> donors = new ArrayList<>();
        appendBookEnchantments(sPrimary, SLOT_PRIMARY, donors);
        appendBookEnchantments(sSecondary, SLOT_SECONDARY, donors);
        for (EnchantCandidate donor : donors) {
            for (FusedPair existing : targetFusions.pairs()) {
                if (existing.isAberrant()) continue;
                if (existing.primaryId().equals(donor.id()) && donor.level() > existing.primaryLevel()) {
                    return Optional.of(new StationPreview(
                        OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE,
                        existing.withPrimaryLevel(Math.min(donor.maxLevel(), donor.level())),
                        false, 0.0, 2, sel, manageXp
                    ));
                }
                if (existing.secondaryId().equals(donor.id()) && donor.level() > existing.secondaryLevel()) {
                    return Optional.of(new StationPreview(
                        OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE,
                        existing.withSecondaryLevel(Math.min(donor.maxLevel(), donor.level())),
                        false, 0.0, 2, sel, manageXp
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public static boolean executePrimaryOperation(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int activeTab,
        int preferredA,
        int preferredB
    ) {
        StationPreview preview = inspect(inv, registries, activeTab, preferredA, preferredB);
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
        FusedPair finalPair = preview.previewPair();

        // Roll unstable Aberrant Mutation if forcing a max-level repulsive pair without a stabilizer!
        if (finalPair != null && preview.repulsive() && preview.instabilityChance() > 0.001) {
            if (player.getRandom().nextDouble() < preview.instabilityChance()) {
                String abId = AberrantRegistry.pickRandomAberration(player.getRandom());
                finalPair = new FusedPair(
                    finalPair.primaryId(),
                    finalPair.primaryLevel(),
                    finalPair.secondaryId(),
                    finalPair.secondaryLevel(),
                    abId
                );
            }
        }

        switch (preview.kind()) {
            case EXPAND_VESSEL -> {
                sTarget.set(FusionDataComponents.VESSEL_BONUS, 1);
                sPrimary.shrink(1);
                sSecondary.shrink(1);
            }
            case FUSE_BOOKS -> {
                EnchantCandidate cA = preview.selection().primary();
                EnchantCandidate cB = preview.selection().secondary();
                if (cA == null || cB == null || finalPair == null) {
                    return false;
                }
                consumeCandidateFromSlot(inv, cA);
                consumeCandidateFromSlot(inv, cB);

                ItemStack outBook = FusedEnchantedBookItem.createForPair(finalPair);
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
                if (preview.repulsive() && CetSoftBridge.isStabilizerCatalyst(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case SOCKET_FUSED_BOOK -> {
                ItemStack bookSlot = sPrimary.is(FusionItems.FUSED_ENCHANTED_BOOK) ? sPrimary : sSecondary;
                if (finalPair == null || bookSlot.isEmpty()) {
                    return false;
                }
                applyFusedPairToGear(sTarget, finalPair, registries);
                bookSlot.shrink(1);
                if (CetSoftBridge.isZeroXpCetStabilizer(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case FUSE_ONTO_GEAR -> {
                EnchantCandidate cA = preview.selection().primary();
                EnchantCandidate cB = preview.selection().secondary();
                if (cA == null || cB == null || finalPair == null) {
                    return false;
                }
                if (cA.sourceSlot() != SLOT_TARGET) consumeCandidateFromSlot(inv, cA);
                if (cB.sourceSlot() != SLOT_TARGET) consumeCandidateFromSlot(inv, cB);
                // If Secondary B came from the gear's own standalone enchantments, remove its standalone copy cleanly
                if (cB.sourceSlot() == SLOT_TARGET) {
                    removeGearVanillaEnchant(sTarget, cB.id());
                }
                applyFusedPairToGear(sTarget, finalPair, registries);
                if (preview.repulsive() && CetSoftBridge.isStabilizerCatalyst(sCatalyst)) {
                    sCatalyst.shrink(1);
                }
            }
            case TOP_UP_LEVEL -> {
                List<EnchantCandidate> donors = new ArrayList<>();
                appendBookEnchantments(sPrimary, SLOT_PRIMARY, donors);
                appendBookEnchantments(sSecondary, SLOT_SECONDARY, donors);
                FusedEnchantmentList list = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                boolean upgradedAny = false;
                for (EnchantCandidate donor : donors) {
                    if (upgradedAny) break;
                    for (int i = 0; i < list.size(); i++) {
                        FusedPair old = list.pairs().get(i);
                        if (old.isAberrant()) continue;
                        if (old.primaryId().equals(donor.id()) && donor.level() > old.primaryLevel()) {
                            int nextLvl = Math.min(donor.maxLevel(), donor.level());
                            sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, old.withPrimaryLevel(nextLvl)));
                            if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
                                setGearVanillaEnchantLevel(sTarget, donor.holder(), nextLvl);
                            }
                            consumeCandidateFromSlot(inv, donor);
                            upgradedAny = true;
                            break;
                        }
                        if (old.secondaryId().equals(donor.id()) && donor.level() > old.secondaryLevel()) {
                            int nextLvl = Math.min(donor.maxLevel(), donor.level());
                            sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, old.withSecondaryLevel(nextLvl)));
                            consumeCandidateFromSlot(inv, donor);
                            upgradedAny = true;
                            break;
                        }
                    }
                }
                if (!upgradedAny) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }

        if (!player.getAbilities().instabuild && preview.xpCost() > 0) {
            player.giveExperienceLevels(-preview.xpCost());
        }
        if (player instanceof ServerPlayer serverPlayer && finalPair != null) {
            CompendiumDiscoveryTracker.recordDiscovery(
                serverPlayer,
                finalPair.primaryId(),
                finalPair.secondaryId()
            );
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.05F);
        return true;
    }

    /**
     * Flips [A ▷ B] <-> [B ▷ A] on a specific socket index of the item in Slot 3 (or Fused Book in Slot 0).
     */
    public static boolean executeFlipSocket(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex
    ) {
        ItemStack target = resolveManageableStack(inv);
        if (target.isEmpty()) {
            return false;
        }
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (socketIndex < 0 || socketIndex >= list.size()) {
            return false;
        }
        FusedPair oldPair = list.pairs().get(socketIndex);
        if (FusionRuleEngine.canFlipPair(oldPair) != RejectReason.NONE) {
            return false;
        }

        ItemStack cat = inv.getItem(SLOT_CATALYST);
        int xpCost = (CetSoftBridge.isSplitOrFlipCatalyst(cat) || CetSoftBridge.isStabilizerCatalyst(cat)) ? 0 : 2;
        if (!player.getAbilities().instabuild && player.experienceLevel < xpCost) {
            return false;
        }

        FusedPair flipped = oldPair.flipped();
        target.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(socketIndex, flipped));

        if (VesselCapacityRules.isPotentialEquipmentHost(target)) {
            removeGearVanillaEnchant(target, oldPair.primaryId());
            lookupEnchantment(registries, flipped.primaryId())
                .ifPresent(h -> setGearVanillaEnchantLevel(target, h, flipped.primaryLevel()));
        }

        if (xpCost == 0 && !cat.isEmpty()) {
            cat.shrink(1);
        } else if (!player.getAbilities().instabuild && xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            CompendiumDiscoveryTracker.recordDiscovery(serverPlayer, flipped.primaryId(), flipped.secondaryId());
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.15F);
        return true;
    }

    /**
     * On Gear: cleanly unsockets socketIndex into a Fused Enchanted Book (like removing a socketed gem!).
     * On Fused Enchanted Book: cleanly splits the Fused Book back into two separate single Enchanted Books!
     */
    public static boolean executeUnsocketOrSplit(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex
    ) {
        ItemStack target = resolveManageableStack(inv);
        if (target.isEmpty()) {
            return false;
        }
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (socketIndex < 0 || socketIndex >= list.size()) {
            return false;
        }
        FusedPair oldPair = list.pairs().get(socketIndex);
        if (FusionRuleEngine.canUnsocketOrSplitPair(oldPair) != RejectReason.NONE) {
            return false;
        }

        ItemStack cat = inv.getItem(SLOT_CATALYST);
        int xpCost = (CetSoftBridge.isSplitOrFlipCatalyst(cat) || CetSoftBridge.isStabilizerCatalyst(cat)) ? 0 : 2;
        if (!player.getAbilities().instabuild && player.experienceLevel < xpCost) {
            return false;
        }

        if (target.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            Optional<Holder<Enchantment>> pHolder = lookupEnchantment(registries, oldPair.primaryId());
            Optional<Holder<Enchantment>> sHolder = lookupEnchantment(registries, oldPair.secondaryId());
            if (pHolder.isEmpty() || sHolder.isEmpty()) {
                return false;
            }
            ItemStack bookA = createSingleEnchantedBook(pHolder.get(), oldPair.primaryLevel());
            ItemStack bookB = createSingleEnchantedBook(sHolder.get(), oldPair.secondaryLevel());

            target.shrink(1);
            placeInSlotOrInventory(inv, SLOT_PRIMARY, player, bookA);
            placeInSlotOrInventory(inv, SLOT_SECONDARY, player, bookB);
        } else {
            // Unsocket gem from Gear back into a reusable Fused Enchanted Book!
            FusedEnchantmentList remaining = list.removeAt(socketIndex);
            if (remaining.isEmpty()) {
                target.remove(FusionDataComponents.FUSED_PAIRS);
            } else {
                target.set(FusionDataComponents.FUSED_PAIRS, remaining);
            }
            removeGearVanillaEnchant(target, oldPair.primaryId());
            removeGearVanillaEnchant(target, oldPair.secondaryId());

            ItemStack extractedFusedBook = FusedEnchantedBookItem.createForPair(oldPair);
            placeInSlotOrInventory(inv, SLOT_PRIMARY, player, extractedFusedBook);
        }

        if (xpCost == 0 && !cat.isEmpty()) {
            cat.shrink(1);
        } else if (!player.getAbilities().instabuild && xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }
        inv.setChanged();
        playWorkstationSound(player, SoundEvents.GRINDSTONE_USE, 1.2F);
        return true;
    }

    public static ItemStack resolveManageableStack(Container inv) {
        ItemStack sTarget = inv.getItem(SLOT_TARGET);
        if (!sTarget.isEmpty() && !sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty()) {
            return sTarget;
        }
        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        if (sPrimary.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return sPrimary;
        }
        return sTarget;
    }

    private static void consumeCandidateFromSlot(Container inv, EnchantCandidate cand) {
        ItemStack book = inv.getItem(cand.sourceSlot());
        if (book.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) {
            return;
        }
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.size() <= 1) {
            book.shrink(1);
        } else {
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(stored);
            mut.removeIf(h -> h.unwrapKey().map(k -> k.identifier().toString().equals(cand.id())).orElse(false));
            book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        }
    }

    private static void placeInSlotOrInventory(Container inv, int slot, Player player, ItemStack stack) {
        if (inv.getItem(slot).isEmpty()) {
            inv.setItem(slot, stack);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
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

    public static Set<String> readUnfusedVanillaEnchantIds(ItemStack gear, FusedEnchantmentList fusedList) {
        Set<String> out = new HashSet<>();
        ItemEnchantments ench = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : ench.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(k -> {
                String id = k.identifier().toString();
                if (!fusedList.containsEnchantmentId(id)) {
                    out.add(id);
                }
            });
        }
        return out;
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
