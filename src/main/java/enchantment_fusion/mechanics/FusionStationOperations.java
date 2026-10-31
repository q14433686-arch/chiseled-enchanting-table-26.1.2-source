package enchantment_fusion.mechanics;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionEconomyRules.EconomyQuote;
import enchantment_fusion.mechanics.FusionRuleEngine.EnchantInput;
import enchantment_fusion.mechanics.FusionRuleEngine.PairEvaluation;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.FlameKineticEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates and executes 2-Tab Enchantment Fusion Table operations:
 * <ul>
 *   <li>Tab 0 ({@code TAB_FUSE}): Book + Book (or single multi-enchantment book, atomic conservation EF-001)
 *       -> Fused Enchanted Book in Slot 3, integrated Fused-Book Flip / Split, and Native Aberrant Purification (CPL-1).</li>
 *   <li>Tab 1 ({@code TAB_SOCKET}): Host Gear in Slot 3 -> Vessel Socket management (Socket, Flip, Remove, +1 Vessel Inlay),
 *       In-Place Gear 2-in-1 Fusion, Native Aberrant Purification, and {@code 0◇} Cross-Domain Transmutations
 *       stored in {@code TRANSMUTED_ENCHANTS} (API-01 / DATA-1).</li>
 * </ul>
 */
public final class FusionStationOperations {
    public static final int SLOT_PRIMARY = 0;
    public static final int SLOT_SECONDARY = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_TARGET = 3;

    public static final int TAB_FUSE = 0;
    public static final int TAB_SOCKET = 1;

    public enum OperationKind {
        NONE,
        FUSE_BOOKS,
        SOCKET_FUSED_BOOK,
        FUSE_ONTO_GEAR,
        INSCRIBE_SINGLE_CROSS,
        TOP_UP_LEVEL,
        EXPAND_VESSEL,
        PURIFY_ABERRANT,
        INVALID
    }

    public record ActionResult(boolean success, RejectReason reason) {
        public static ActionResult ok() {
            return new ActionResult(true, RejectReason.NONE);
        }

        public static ActionResult failed(RejectReason reason) {
            return new ActionResult(false, reason == null ? RejectReason.NONE : reason);
        }
    }

    public record EnchantCandidate(
        int sourceSlot,
        Holder<Enchantment> holder,
        String id,
        int level,
        int maxLevel
    ) {
        public String selectionToken() {
            return sourceSlot + ":" + id;
        }
    }

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

        public String buildSelectionToken() {
            return buildSelectionToken(null);
        }

        public String buildSelectionToken(@Nullable FusedPair fallbackPair) {
            EnchantCandidate p = primary();
            EnchantCandidate s = secondary();
            if (p == null && s == null && fallbackPair != null) {
                return "pair|" + fallbackPair.fingerprint();
            }
            String ps = p == null ? "-" : (p.sourceSlot() + ":" + p.id() + ":" + p.level());
            String ss = s == null ? "-" : (s.sourceSlot() + ":" + s.id() + ":" + s.level());
            return ps + "|" + ss;
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
        int manageXpCost,
        CrossDomainTransmutations.TransmuteVariant previewTransmute,
        EnchantCandidate singleCandidate,
        EconomyQuote economyQuote
    ) {
        public StationPreview(
            OperationKind kind,
            boolean executable,
            RejectReason rejectReason,
            FusedPair previewPair,
            boolean repulsive,
            double instabilityChance,
            int xpCost,
            ResolvedSelection selection,
            int manageXpCost
        ) {
            this(
                kind, executable, rejectReason, previewPair, repulsive, instabilityChance, xpCost,
                selection, manageXpCost, null, null,
                new EconomyQuote(xpCost, FusionEconomyRules.ReagentKind.NONE, 0, instabilityChance, repulsive, false, 0)
            );
        }

        public String expectedExecuteToken() {
            return selection.buildSelectionToken(previewPair);
        }
    }

    private FusionStationOperations() {}

    public static List<EnchantCandidate> collectCandidates(Container inv, int activeTab) {
        List<EnchantCandidate> out = new ArrayList<>();
        if (activeTab == TAB_SOCKET) {
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
            appendBookEnchantments(inv.getItem(SLOT_PRIMARY), SLOT_PRIMARY, out);
            appendBookEnchantments(inv.getItem(SLOT_SECONDARY), SLOT_SECONDARY, out);
        }
        // ItemEnchantments does not promise iteration order across network round trips.
        out.sort(Comparator.comparingInt(EnchantCandidate::sourceSlot)
            .thenComparing(EnchantCandidate::id)
            .thenComparingInt(EnchantCandidate::level));
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
        return resolveSelection(candidates, preferredA, preferredB, preferredB == -2);
    }

    public static ResolvedSelection resolveSelection(
        List<EnchantCandidate> candidates,
        int preferredA,
        int preferredB,
        boolean singleOnlyMode
    ) {
        int n = candidates.size();
        if (n == 0) {
            return new ResolvedSelection(candidates, -1, -1);
        }
        int a = (preferredA >= 0 && preferredA < n) ? preferredA : 0;
        if (singleOnlyMode) {
            return new ResolvedSelection(candidates, a, -1);
        }
        int b = (preferredB >= 0 && preferredB < n && preferredB != a) ? preferredB : -1;
        if (b == -1) {
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
        return inspect(
            inv, registries, activeTab, preferredA, preferredB,
            preferredB == -2, FusionMatrixRegistry.serverSnapshot()
        );
    }

    public static StationPreview inspect(
        Container inv,
        HolderLookup.Provider registries,
        int activeTab,
        int preferredA,
        int preferredB,
        boolean singleOnlyMode,
        FusionMatrixRegistry.Snapshot snapshot
    ) {
        FusionMatrixRegistry.Snapshot snap = snapshot != null ? snapshot : FusionMatrixRegistry.serverSnapshot();
        EconomyConfig eco = snap.economy();

        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        ItemStack sSecondary = inv.getItem(SLOT_SECONDARY);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        ItemStack sTarget = inv.getItem(SLOT_TARGET);

        // CPL-05: Only dedicated flip/split catalysts (Amethyst / Lapis / CET Grit) waive the manage XP cost!
        int manageXp = CetSoftBridge.isSplitOrFlipCatalyst(sCatalyst) ? 0 : eco.manageFlipSplitXp();
        List<EnchantCandidate> candidates = collectCandidates(inv, activeTab);
        ResolvedSelection sel = resolveSelection(candidates, preferredA, preferredB, singleOnlyMode);

        // === Native High-Cost Aberrant Purification (CPL-1 / TXT-14): Aberrant item in Slot 3 + Stabilizers in Slot 2 ===
        if (sPrimary.isEmpty() && sSecondary.isEmpty() && !sTarget.isEmpty() && CetSoftBridge.isStabilizerCatalyst(sCatalyst)) {
            FusedEnchantmentList targetFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (targetFusions.hasAberration()) {
                Optional<StationPreview> purify = evaluateNativeAberrantPurify(targetFusions, sCatalyst, sel, manageXp, eco);
                if (purify.isPresent()) {
                    return purify.get();
                }
            }
        }

        // === TAB 1 (or auto-detected Gear in Slot 3): Vessel Expansion, Socket Fused Book, Top-Up, Cross-Domain, or In-Place Gear Fusion ===
        if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
            FusedEnchantmentList targetFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);

            // 1. Vessel +1 Inlay (Echo Shard + Amethyst Cluster in Slots 0/1)
            boolean hasEchoAndCluster = (sPrimary.is(Items.ECHO_SHARD) && (sSecondary.is(Items.AMETHYST_CLUSTER) || CetSoftBridge.isStabilizerCatalyst(sSecondary)))
                || (sSecondary.is(Items.ECHO_SHARD) && (sPrimary.is(Items.AMETHYST_CLUSTER) || CetSoftBridge.isStabilizerCatalyst(sPrimary)));
            if (hasEchoAndCluster) {
                if (!VesselCapacityRules.canExpandStack(sTarget)) {
                    return new StationPreview(OperationKind.INVALID, false, RejectReason.ALREADY_MAX_VESSEL, null, false, 0.0, 0, sel, manageXp);
                }
                return new StationPreview(OperationKind.EXPAND_VESSEL, true, RejectReason.NONE, null, false, 0.0, eco.expandVesselXp(), sel, manageXp);
            }

            // 2. Socket a Fused Enchanted Book from Slot 0 or Slot 1 onto ANY Gear
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
                    Set<String> allStandalone = readAllStandaloneEnchantIds(sTarget, targetFusions);
                    if (targetFusions.containsEnchantmentId(pair.primaryId())
                        || targetFusions.containsEnchantmentId(pair.secondaryId())
                        || allStandalone.contains(pair.primaryId())
                        || allStandalone.contains(pair.secondaryId())) {
                        return new StationPreview(OperationKind.INVALID, false, RejectReason.DUPLICATE_ON_HOST, pair, false, 0.0, 0, sel, manageXp);
                    }
                    Set<String> vanillaOnlyIds = readUnfusedVanillaEnchantIds(sTarget, targetFusions);
                    if (isIncompatibleWithHost(sTarget, targetFusions, vanillaOnlyIds, pair.primaryId(), snap, registries)) {
                        return new StationPreview(OperationKind.INVALID, false, RejectReason.EXCLUSIVE_ON_HOST, pair, false, 0.0, 0, sel, manageXp);
                    }
                    RejectReason carrier = DirectionalHostRules.validateNewOrChanged(sTarget, pair);
                    if (carrier != RejectReason.NONE) {
                        return new StationPreview(OperationKind.INVALID, false, carrier, pair, false, 0.0, 0, sel, manageXp);
                    }
                    int stabCount = FusionEconomyRules.countStabilizers(sCatalyst);
                    boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
                    EconomyQuote sockQuote = FusionEconomyRules.computeSocketFusedBookQuote(eco, pair, stabCount, zeroXp);
                    return new StationPreview(
                        OperationKind.SOCKET_FUSED_BOOK, true, RejectReason.NONE, pair, pair.isAberrant(),
                        sockQuote.failureChance(), sockQuote.xpLevels(), sel, manageXp, null, null, sockQuote
                    );
                }
            }

            // 3. Top-up an existing socketed pair on Gear using a higher-level book in Slot 0/1
            Optional<StationPreview> topUp = checkTopUp(sTarget, targetFusions, sPrimary, sSecondary, sel, manageXp, eco);
            if (topUp.isPresent()) {
                return topUp.get();
            }

            // 4. Single Cross-Domain Book Inscription (0 Vessel Slots!) or In-Place Gear 2-in-1 Fusion
            List<EnchantCandidate> gearModeCandidates = activeTab == TAB_SOCKET ? candidates : collectCandidates(inv, TAB_SOCKET);
            ResolvedSelection gearSel = activeTab == TAB_SOCKET
                ? sel
                : resolveSelection(gearModeCandidates, preferredA, preferredB, singleOnlyMode);
            EnchantCandidate p = gearSel.primary();
            EnchantCandidate s = gearSel.secondary();
            if (p != null && s == null) {
                Optional<StationPreview> singleCross = evaluateSingleCrossDomain(sTarget, targetFusions, gearSel, p, sCatalyst, manageXp, eco);
                if (singleCross.isPresent()) {
                    return singleCross.get();
                }
            }
            if (p != null && s != null) {
                Set<String> blockingIds = readAllStandaloneEnchantIds(sTarget, targetFusions);
                Set<String> vanillaBlockingIds = readUnfusedVanillaEnchantIds(sTarget, targetFusions);
                if (p.sourceSlot() == SLOT_TARGET) {
                    blockingIds.remove(p.id());
                    vanillaBlockingIds.remove(p.id());
                }
                if (s.sourceSlot() == SLOT_TARGET) {
                    blockingIds.remove(s.id());
                    vanillaBlockingIds.remove(s.id());
                }

                boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
                boolean runtimeIncompatPair = !Enchantment.areCompatible(p.holder(), s.holder());
                boolean incompatHost = isIncompatibleWithHost(sTarget, targetFusions, vanillaBlockingIds, p.id(), snap, registries);
                PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                    snap,
                    new EnchantInput(p.id(), p.level(), p.maxLevel()),
                    new EnchantInput(s.id(), s.level(), s.maxLevel()),
                    true, targetFusions, blockingIds,
                    VesselCapacityRules.getMaxVesselSlots(sTarget),
                    runtimeIncompatPair, incompatHost
                );
                if (!eval.valid()) {
                    EnchantCandidate bookCand = p.sourceSlot() != SLOT_TARGET ? p : (s.sourceSlot() != SLOT_TARGET ? s : null);
                    if (bookCand != null) {
                        Optional<StationPreview> fallbackSingle = evaluateSingleCrossDomain(
                            sTarget, targetFusions, gearSel, bookCand, sCatalyst, manageXp, eco
                        );
                        if (fallbackSingle.isPresent() && fallbackSingle.get().executable()) {
                            return fallbackSingle.get();
                        }
                    }
                    return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, gearSel, manageXp);
                }
                RejectReason carrier = DirectionalHostRules.validateNewOrChanged(sTarget, eval.resultingPair());
                if (carrier != RejectReason.NONE) {
                    // Do not silently turn a rejected two-enchantment fusion into a single inscription.
                    return new StationPreview(OperationKind.INVALID, false, carrier, eval.resultingPair(), false, 0.0, 0, gearSel, manageXp);
                }
                int stabCount = FusionEconomyRules.countStabilizers(sCatalyst);
                boolean isSig = SignaturePairs.resolve(eval.resultingPair()).isPresent();
                EconomyQuote gearQuote = FusionEconomyRules.computeSynthesisQuote(
                    eco, p.id(), p.level(), p.maxLevel(), s.id(), s.level(), s.maxLevel(),
                    eval.repulsiveExclusive(), isSig, stabCount, zeroXp
                );
                return new StationPreview(
                    OperationKind.FUSE_ONTO_GEAR, true, RejectReason.NONE,
                    eval.resultingPair(), eval.repulsiveExclusive(), gearQuote.failureChance(), gearQuote.xpLevels(),
                    gearSel, manageXp, null, null, gearQuote
                );
            }

            return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, gearSel, manageXp);
        }

        // === TAB 1 Special Cross-Domain Non-Equipment Hosts (Totem, Glass Bottle, Consumables — 0 Vessel Slots!) ===
        if (!sTarget.isEmpty() && !sTarget.is(Items.BOOK) && !sTarget.is(FusionItems.FUSED_ENCHANTED_BOOK)
            && CrossDomainTransmutations.isEligibleCrossDomainHost(sTarget)) {
            if (sTarget.getCount() != 1) {
                return new StationPreview(OperationKind.INVALID, false, RejectReason.STACK_MUST_BE_SINGLE, null, false, 0.0, 0, sel, manageXp);
            }
            EnchantCandidate p = sel.primary();
            if (p != null && p.sourceSlot() != SLOT_TARGET) {
                Optional<StationPreview> specialSingle = evaluateSingleCrossDomain(
                    sTarget, FusedEnchantmentList.EMPTY, sel, p, sCatalyst, manageXp, eco
                );
                if (specialSingle.isPresent()) {
                    return specialSingle.get();
                }
            }
            return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, sel, manageXp);
        }

        // === TAB 0: Book Fusion -> Output Fused Enchanted Book into Slot 3 ===
        if (sTarget.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            FusedEnchantmentList bookFusions = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            Optional<StationPreview> topUp = checkTopUp(sTarget, bookFusions, sPrimary, sSecondary, sel, manageXp, eco);
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
            boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
            boolean runtimeIncompatPair = !Enchantment.areCompatible(p.holder(), s.holder());
            PairEvaluation eval = FusionRuleEngine.evaluateFusion(
                snap,
                new EnchantInput(p.id(), p.level(), p.maxLevel()),
                new EnchantInput(s.id(), s.level(), s.maxLevel()),
                false, FusedEnchantmentList.EMPTY, Set.of(), 0, runtimeIncompatPair, false
            );
            if (!eval.valid()) {
                return new StationPreview(OperationKind.INVALID, false, eval.rejectReason(), null, false, 0.0, 0, sel, manageXp);
            }
            int stabCount = FusionEconomyRules.countStabilizers(sCatalyst);
            boolean isSig = SignaturePairs.resolve(eval.resultingPair()).isPresent();
            EconomyQuote fuseQuote = FusionEconomyRules.computeSynthesisQuote(
                eco, p.id(), p.level(), p.maxLevel(), s.id(), s.level(), s.maxLevel(),
                eval.repulsiveExclusive(), isSig, stabCount, zeroXp
            );
            return new StationPreview(
                OperationKind.FUSE_BOOKS, true, RejectReason.NONE,
                eval.resultingPair(), eval.repulsiveExclusive(), fuseQuote.failureChance(), fuseQuote.xpLevels(),
                sel, manageXp, null, null, fuseQuote
            );
        }

        return new StationPreview(OperationKind.NONE, false, RejectReason.NONE, null, false, 0.0, 0, sel, manageXp);
    }

    private static Optional<StationPreview> evaluateNativeAberrantPurify(
        FusedEnchantmentList targetFusions,
        ItemStack sCatalyst,
        ResolvedSelection sel,
        int manageXp,
        EconomyConfig eco
    ) {
        FusedPair firstAberrant = null;
        for (FusedPair fp : targetFusions.pairs()) {
            if (fp.isAberrant()) {
                firstAberrant = fp;
                break;
            }
        }
        if (firstAberrant == null) {
            return Optional.empty();
        }
        boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
        int neededStab = zeroXp ? 1 : eco.purifyAberrantStabilizers();
        int availableStab = FusionEconomyRules.countStabilizers(sCatalyst);
        FusedPair purified = firstAberrant.withoutAberration();
        if (availableStab < neededStab) {
            return Optional.of(new StationPreview(
                OperationKind.INVALID, false, RejectReason.NEED_PURIFY_STABILIZERS,
                purified, false, 0.0, 0, sel, manageXp
            ));
        }
        EconomyQuote quote = FusionEconomyRules.computePurifyAberrantQuote(eco, zeroXp);
        return Optional.of(new StationPreview(
            OperationKind.PURIFY_ABERRANT, true, RejectReason.NONE,
            purified, false, 0.0, quote.xpLevels(), sel, manageXp, null, null, quote
        ));
    }

    private static boolean isIncompatibleWithHost(
        ItemStack host,
        FusedEnchantmentList targetFusions,
        Set<String> standaloneIds,
        String newEnchantId,
        FusionMatrixRegistry.Snapshot snap,
        @Nullable HolderLookup.Provider registries
    ) {
        for (FusedPair fp : targetFusions.pairs()) {
            if (snap.areRepulsiveExclusives(newEnchantId, fp.primaryId())
                || snap.areRepulsiveExclusives(newEnchantId, fp.secondaryId())) {
                return true;
            }
        }
        if (registries == null) {
            return false;
        }
        Optional<Holder<Enchantment>> newHolderOpt = lookupEnchantment(registries, newEnchantId);
        if (newHolderOpt.isEmpty()) {
            return false;
        }
        Holder<Enchantment> newHolder = newHolderOpt.get();
        if (!newHolder.value().canEnchant(host)) {
            return false;
        }
        for (FusedPair fp : targetFusions.pairs()) {
            Optional<Holder<Enchantment>> exP = lookupEnchantment(registries, fp.primaryId());
            if (exP.isPresent() && exP.get().value().canEnchant(host) && !Enchantment.areCompatible(newHolder, exP.get())) {
                return true;
            }
            Optional<Holder<Enchantment>> exS = lookupEnchantment(registries, fp.secondaryId());
            if (exS.isPresent() && exS.get().value().canEnchant(host) && !Enchantment.areCompatible(newHolder, exS.get())) {
                return true;
            }
        }
        for (String sid : standaloneIds) {
            Optional<Holder<Enchantment>> ex = lookupEnchantment(registries, sid);
            if (ex.isPresent() && !Enchantment.areCompatible(newHolder, ex.get())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<StationPreview> evaluateSingleCrossDomain(
        ItemStack sTarget,
        FusedEnchantmentList targetFusions,
        ResolvedSelection sel,
        EnchantCandidate bookCand,
        ItemStack sCatalyst,
        int manageXp,
        EconomyConfig eco
    ) {
        if (bookCand == null || bookCand.sourceSlot() == SLOT_TARGET) {
            return Optional.empty();
        }
        Optional<CrossDomainTransmutations.TransmuteVariant> variantOpt =
            CrossDomainTransmutations.resolve(sTarget, bookCand.id());
        if (variantOpt.isEmpty()) {
            return Optional.of(new StationPreview(
                OperationKind.INVALID, false, RejectReason.NOT_CROSS_DOMAIN, null, false, 0.0, 0, sel, manageXp
            ));
        }
        if (targetFusions.containsEnchantmentId(bookCand.id())) {
            return Optional.of(new StationPreview(
                OperationKind.INVALID, false, RejectReason.DUPLICATE_ON_HOST, null, false, 0.0, 0, sel, manageXp
            ));
        }
        Map<String, Integer> transmutedMap = sTarget.getOrDefault(
            FusionDataComponents.TRANSMUTED_ENCHANTS, Map.<String, Integer>of()
        );
        if (transmutedMap.size() >= FusionDataComponents.MAX_TRANSMUTED_ENCHANTS
            && !transmutedMap.containsKey(bookCand.id())) {
            return Optional.of(new StationPreview(
                OperationKind.INVALID, false, RejectReason.TRANSMUTE_CAP_FULL, null, false, 0.0, 0, sel, manageXp
            ));
        }
        int clampedLv = Math.min(bookCand.maxLevel(), bookCand.level());
        int currentTransmuted = transmutedMap.getOrDefault(bookCand.id(), 0);
        int currentVanilla = sTarget.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
            .getLevel(bookCand.holder());
        if (Math.max(currentTransmuted, currentVanilla) >= clampedLv) {
            return Optional.of(new StationPreview(
                OperationKind.INVALID, false, RejectReason.DUPLICATE_ON_HOST, null, false, 0.0, 0, sel, manageXp
            ));
        }

        int stabCount = FusionEconomyRules.countStabilizers(sCatalyst);
        boolean zeroXp = CetSoftBridge.isZeroXpCetStabilizer(sCatalyst);
        int anvilCost = bookCand.holder().value().getAnvilCost();
        EconomyQuote convQuote = FusionEconomyRules.computeConversionQuote(
            eco, clampedLv, bookCand.maxLevel(), anvilCost, stabCount, zeroXp
        );
        return Optional.of(new StationPreview(
            OperationKind.INSCRIBE_SINGLE_CROSS, true, RejectReason.NONE,
            null, false, convQuote.failureChance(), convQuote.xpLevels(), sel, manageXp,
            variantOpt.get(), bookCand, convQuote
        ));
    }

    private static Optional<StationPreview> checkTopUp(
        ItemStack sTarget,
        FusedEnchantmentList targetFusions,
        ItemStack sPrimary,
        ItemStack sSecondary,
        ResolvedSelection sel,
        int manageXp,
        EconomyConfig eco
    ) {
        if (targetFusions.isEmpty()) {
            return Optional.empty();
        }
        List<EnchantCandidate> donors = new ArrayList<>();
        appendBookEnchantments(sPrimary, SLOT_PRIMARY, donors);
        appendBookEnchantments(sSecondary, SLOT_SECONDARY, donors);
        for (EnchantCandidate donor : donors) {
            int clampedDonor = Math.min(donor.maxLevel(), donor.level());
            for (FusedPair existing : targetFusions.pairs()) {
                if (existing.isAberrant()) continue;
                if (existing.primaryId().equals(donor.id()) && clampedDonor > existing.primaryLevel()) {
                    FusedPair upgraded = existing.withPrimaryLevel(clampedDonor);
                    RejectReason carrier = VesselCapacityRules.isPotentialEquipmentHost(sTarget)
                        ? DirectionalHostRules.validateNewOrChanged(sTarget, upgraded) : RejectReason.NONE;
                    if (carrier != RejectReason.NONE) {
                        return Optional.of(new StationPreview(OperationKind.INVALID, false, carrier, upgraded,
                            false, 0.0, 0, sel, manageXp));
                    }
                    return Optional.of(new StationPreview(
                        OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE, upgraded,
                        false, 0.0, eco.topUpXp(), sel, manageXp
                    ));
                }
                if (existing.secondaryId().equals(donor.id()) && clampedDonor > existing.secondaryLevel()) {
                    FusedPair upgraded = existing.withSecondaryLevel(clampedDonor);
                    RejectReason carrier = VesselCapacityRules.isPotentialEquipmentHost(sTarget)
                        ? DirectionalHostRules.validateNewOrChanged(sTarget, upgraded) : RejectReason.NONE;
                    if (carrier != RejectReason.NONE) {
                        return Optional.of(new StationPreview(OperationKind.INVALID, false, carrier, upgraded,
                            false, 0.0, 0, sel, manageXp));
                    }
                    return Optional.of(new StationPreview(
                        OperationKind.TOP_UP_LEVEL, true, RejectReason.NONE, upgraded,
                        false, 0.0, eco.topUpXp(), sel, manageXp
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
        return executePrimaryOperation(inv, player, registries, activeTab, preferredA, preferredB, "");
    }

    public static boolean executePrimaryOperation(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int activeTab,
        int preferredA,
        int preferredB,
        String expectedToken
    ) {
        return executePrimaryWithReason(
            inv, player, registries, activeTab, preferredA, preferredB, preferredB == -2, expectedToken
        ).success();
    }

    public static ActionResult executePrimaryWithReason(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int activeTab,
        int preferredA,
        int preferredB,
        boolean singleOnlyMode,
        String expectedToken
    ) {
        StationPreview preview = inspect(
            inv, registries, activeTab, preferredA, preferredB,
            singleOnlyMode, FusionMatrixRegistry.serverSnapshot()
        );
        if (!preview.executable()) {
            return ActionResult.failed(preview.rejectReason());
        }
        // NET-03: Verify candidate/pair token matches what the player saw on client
        if (expectedToken != null && !expectedToken.isEmpty()
            && !expectedToken.equals(preview.expectedExecuteToken())
            && !expectedToken.equals(preview.selection().buildSelectionToken())) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        EconomyQuote quote = preview.economyQuote();
        if (!player.getAbilities().instabuild) {
            if (player.experienceLevel < preview.xpCost()) {
                return ActionResult.failed(RejectReason.INSUFFICIENT_XP);
            }
            if (quote != null && !FusionEconomyRules.canAfford(inv, player, quote)) {
                return ActionResult.failed(RejectReason.INSUFFICIENT_REAGENTS);
            }
        }

        ItemStack sPrimary = inv.getItem(SLOT_PRIMARY);
        ItemStack sSecondary = inv.getItem(SLOT_SECONDARY);
        ItemStack sCatalyst = inv.getItem(SLOT_CATALYST);
        ItemStack sTarget = inv.getItem(SLOT_TARGET);
        FusedPair finalPair = preview.previewPair();

        double failChance = quote != null ? quote.failureChance() : preview.instabilityChance();
        boolean rolledFailure = failChance > 0.0005 && player.getRandom().nextDouble() < failChance;
        boolean highRisk = quote != null ? quote.highRiskTier() : preview.repulsive();

        // Mid-tier failure (Choice 3A / EF-004 / BAL-05): ALL books and gear stay 100% intact; charges configured fee ratio!
        if (rolledFailure && !highRisk && (
            preview.kind() == OperationKind.FUSE_BOOKS
                || preview.kind() == OperationKind.FUSE_ONTO_GEAR
                || preview.kind() == OperationKind.INSCRIBE_SINGLE_CROSS
                || preview.kind() == OperationKind.SOCKET_FUSED_BOOK
        )) {
            if (player instanceof ServerPlayer sp) {
                EnchantCandidate cA = preview.selection().primary();
                EnchantCandidate cB = preview.selection().secondary();
                if (cA != null && cB != null) {
                    CompendiumDiscoveryTracker.recordPairHeard(sp, cA.id(), cB.id());
                } else if (preview.previewTransmute() != null) {
                    CompendiumDiscoveryTracker.recordTransmuteProgress(
                        sp, preview.previewTransmute().id(), CompendiumDiscoveryTracker.STATE_HEARD
                    );
                }
            }
            FusionEconomyRules.deductFeeAndStabilizers(inv, player, quote, true);
            inv.setChanged();
            emitStationRitualEffect(inv, player, enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_FAIL);
            if (player instanceof ServerPlayer sp) {
                int halfXp = quote == null ? 0 : quote.halfChargedXp();
                int halfMat = quote == null ? 0 : quote.halfChargedReagents();
                sp.sendSystemMessage(
                    Component.translatable("message.enchantment_fusion.failure.normal_refund", halfXp, halfMat)
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
            }
            return ActionResult.ok();
        }

        // High-Risk Failure (max-level / repulsive): Mutates into Aberrant Fused Pair!
        if (rolledFailure && highRisk && finalPair != null) {
            String abId = AberrantRegistry.pickAberration(finalPair.primaryId(), finalPair.secondaryId(), player.getRandom());
            finalPair = new FusedPair(
                finalPair.primaryId(),
                finalPair.primaryLevel(),
                finalPair.secondaryId(),
                finalPair.secondaryLevel(),
                abId
            );
            if (player instanceof ServerPlayer sp) {
                CompendiumDiscoveryTracker.recordAberrantMastered(sp, abId);
                sp.sendSystemMessage(
                    Component.translatable(
                        "message.enchantment_fusion.failure.aberrant",
                        Component.translatable(AberrantRegistry.titleKey(abId))
                    ).withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
                );
            }
        }

        switch (preview.kind()) {
            case PURIFY_ABERRANT -> {
                FusedEnchantmentList list = sTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                int abIdx = -1;
                FusedPair oldAb = null;
                for (int i = 0; i < list.size(); i++) {
                    if (list.pairs().get(i).isAberrant()) {
                        abIdx = i;
                        oldAb = list.pairs().get(i);
                        break;
                    }
                }
                if (abIdx < 0 || oldAb == null) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                FusedPair cleanPair = oldAb.withoutAberration();
                finalPair = cleanPair;
                sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(abIdx, cleanPair));
                if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
                    if (replacesNativePrimary(cleanPair)) {
                        removeGearVanillaEnchant(sTarget, cleanPair.primaryId());
                    } else {
                        lookupEnchantment(registries, cleanPair.primaryId()).ifPresent(h -> {
                            if (h.value().canEnchant(sTarget)) {
                                setGearVanillaEnchantLevel(sTarget, h, cleanPair.primaryLevel());
                            }
                        });
                    }
                }
                syncGlintOverride(sTarget);
            }
            case EXPAND_VESSEL -> {
                sTarget.set(FusionDataComponents.VESSEL_BONUS, 1);
                sPrimary.shrink(1);
                sSecondary.shrink(1);
            }
            case FUSE_BOOKS -> {
                EnchantCandidate cA = preview.selection().primary();
                EnchantCandidate cB = preview.selection().secondary();
                if (cA == null || cB == null || finalPair == null) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                if (!consumeCandidatesAtomically(inv, List.of(cA, cB))) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }

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
            }
            case SOCKET_FUSED_BOOK -> {
                ItemStack bookSlot = sPrimary.is(FusionItems.FUSED_ENCHANTED_BOOK) ? sPrimary : sSecondary;
                if (finalPair == null || bookSlot.isEmpty()) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                applyFusedPairToGear(sTarget, finalPair, registries);
                bookSlot.shrink(1);
            }
            case FUSE_ONTO_GEAR -> {
                EnchantCandidate cA = preview.selection().primary();
                EnchantCandidate cB = preview.selection().secondary();
                if (cA == null || cB == null || finalPair == null) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                List<EnchantCandidate> fromBooks = new ArrayList<>(2);
                if (cA.sourceSlot() != SLOT_TARGET) fromBooks.add(cA);
                if (cB.sourceSlot() != SLOT_TARGET) fromBooks.add(cB);
                if (!fromBooks.isEmpty() && !consumeCandidatesAtomically(inv, fromBooks)) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                if (cA.sourceSlot() == SLOT_TARGET) {
                    removeGearVanillaEnchant(sTarget, cA.id());
                }
                if (cB.sourceSlot() == SLOT_TARGET) {
                    removeGearVanillaEnchant(sTarget, cB.id());
                }
                applyFusedPairToGear(sTarget, finalPair, registries);
            }
            case INSCRIBE_SINGLE_CROSS -> {
                EnchantCandidate cand = preview.singleCandidate();
                if (cand == null || preview.previewTransmute() == null || sTarget.getCount() != 1) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                Map<String, Integer> prevTrans = sTarget.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                if (prevTrans.size() >= FusionDataComponents.MAX_TRANSMUTED_ENCHANTS && !prevTrans.containsKey(cand.id())) {
                    return ActionResult.failed(RejectReason.TRANSMUTE_CAP_FULL);
                }
                int clampedLv = Math.min(cand.maxLevel(), cand.level());
                if (!consumeCandidatesAtomically(inv, List.of(cand))) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
                if (rolledFailure && highRisk) {
                    ItemEnchantments current = sTarget.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                    ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
                    lookupEnchantment(registries, "minecraft:vanishing_curse").ifPresent(c -> mut.set(c, 1));
                    sTarget.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
                    if (player instanceof ServerPlayer sp) {
                        CompendiumDiscoveryTracker.recordTransmuteProgress(
                            sp, preview.previewTransmute().id(), CompendiumDiscoveryTracker.STATE_HEARD
                        );
                        sp.sendSystemMessage(
                            Component.translatable("message.enchantment_fusion.failure.cursed").withStyle(ChatFormatting.RED),
                            true
                        );
                    }
                } else {
                    Map<String, Integer> nextTrans = new LinkedHashMap<>(prevTrans);
                    nextTrans.put(cand.id(), Math.max(prevTrans.getOrDefault(cand.id(), 0), clampedLv));
                    sTarget.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Collections.unmodifiableMap(nextTrans));
                    syncGlintOverride(sTarget);
                    if (player instanceof ServerPlayer sp) {
                        CompendiumDiscoveryTracker.recordTransmuteProgress(
                            sp, preview.previewTransmute().id(), CompendiumDiscoveryTracker.STATE_MASTERED
                        );
                    }
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
                    int nextLvl = Math.min(donor.maxLevel(), donor.level());
                    for (int i = 0; i < list.size(); i++) {
                        FusedPair old = list.pairs().get(i);
                        if (old.isAberrant()) continue;
                        if (old.primaryId().equals(donor.id()) && nextLvl > old.primaryLevel()) {
                            if (!consumeCandidatesAtomically(inv, List.of(donor))) {
                                return ActionResult.failed(RejectReason.MISSING_INPUT);
                            }
                            FusedPair upgraded = old.withPrimaryLevel(nextLvl);
                            sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, upgraded));
                            if (VesselCapacityRules.isPotentialEquipmentHost(sTarget)) {
                                if (replacesNativePrimary(upgraded)) {
                                    removeGearVanillaEnchant(sTarget, upgraded.primaryId());
                                } else if (donor.holder().value().canEnchant(sTarget)) {
                                    setGearVanillaEnchantLevel(sTarget, donor.holder(), nextLvl);
                                }
                            }
                            syncGlintOverride(sTarget);
                            upgradedAny = true;
                            break;
                        }
                        if (old.secondaryId().equals(donor.id()) && nextLvl > old.secondaryLevel()) {
                            if (!consumeCandidatesAtomically(inv, List.of(donor))) {
                                return ActionResult.failed(RejectReason.MISSING_INPUT);
                            }
                            sTarget.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(i, old.withSecondaryLevel(nextLvl)));
                            normalizeRedesignedPrimary(sTarget, registries);
                            syncGlintOverride(sTarget);
                            upgradedAny = true;
                            break;
                        }
                    }
                }
                if (!upgradedAny) {
                    return ActionResult.failed(RejectReason.MISSING_INPUT);
                }
            }
            default -> {
                return ActionResult.failed(RejectReason.MISSING_INPUT);
            }
        }

        if (quote != null && (quote.reagentCount() > 0 || quote.stabilizersUsed() > 0 || quote.xpLevels() > 0)) {
            FusionEconomyRules.deductFeeAndStabilizers(inv, player, quote, false);
        } else if (!player.getAbilities().instabuild && preview.xpCost() > 0) {
            player.giveExperienceLevels(-preview.xpCost());
        }
        if (CetSoftBridge.isZeroXpCetStabilizer(sCatalyst) && (quote == null || quote.stabilizersUsed() == 0)) {
            sCatalyst.shrink(1);
        }
        if (player instanceof ServerPlayer serverPlayer && finalPair != null && !finalPair.isAberrant()) {
            CompendiumDiscoveryTracker.recordDiscovery(
                serverPlayer,
                finalPair.primaryId(),
                finalPair.secondaryId()
            );
        }
        inv.setChanged();
        emitStationRitualEffect(
            inv,
            player,
            rolledFailure
                ? enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_ABERRANT
                : enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_SUCCESS
        );
        return ActionResult.ok();
    }

    public static boolean consumeCandidatesAtomically(Container inv, List<EnchantCandidate> candidates) {
        Map<Integer, Set<String>> bySlot = new HashMap<>();
        for (EnchantCandidate c : candidates) {
            if (c == null || c.sourceSlot() == SLOT_TARGET) continue;
            bySlot.computeIfAbsent(c.sourceSlot(), k -> new HashSet<>()).add(c.id());
        }
        for (Map.Entry<Integer, Set<String>> entry : bySlot.entrySet()) {
            ItemStack book = inv.getItem(entry.getKey());
            if (book.isEmpty() || !book.is(Items.ENCHANTED_BOOK) || book.getCount() != 1) {
                return false;
            }
            ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            Set<String> presentIds = new HashSet<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> se : stored.entrySet()) {
                se.getKey().unwrapKey().ifPresent(k -> presentIds.add(k.identifier().toString()));
            }
            if (!presentIds.containsAll(entry.getValue())) {
                return false;
            }
        }
        for (Map.Entry<Integer, Set<String>> entry : bySlot.entrySet()) {
            ItemStack book = inv.getItem(entry.getKey());
            Set<String> idsToRemove = entry.getValue();
            ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(stored);
            mut.removeIf(h -> h.unwrapKey().map(k -> idsToRemove.contains(k.identifier().toString())).orElse(false));
            ItemEnchantments remaining = mut.toImmutable();
            if (remaining.isEmpty()) {
                book.shrink(1);
            } else {
                book.set(DataComponents.STORED_ENCHANTMENTS, remaining);
            }
        }
        return true;
    }

    public static boolean executeFlipSocket(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex
    ) {
        return executeFlipSocketWithReason(inv, player, registries, socketIndex, "").success();
    }

    /**
     * Flips {@code [A ▷ B] <-> [B ▷ A]} on {@code socketIndex}, verifying {@code expectedPairToken} against
     * shared-table slot races and returning a specific {@link RejectReason} on failure (NET-1 / NET-5).
     */
    public static ActionResult executeFlipSocketWithReason(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex,
        String expectedPairToken
    ) {
        ItemStack target = resolveManageableStack(inv);
        if (target.isEmpty()) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (socketIndex < 0 || socketIndex >= list.size()) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        FusedPair oldPair = list.pairs().get(socketIndex);
        if (expectedPairToken != null && !expectedPairToken.isEmpty() && !expectedPairToken.equals(oldPair.fingerprint())) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        RejectReason lockCheck = FusionRuleEngine.canFlipPair(oldPair);
        if (lockCheck != RejectReason.NONE) {
            return ActionResult.failed(lockCheck);
        }

        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.serverSnapshot();
        FusedPair flipped = oldPair.flipped();
        if (VesselCapacityRules.isPotentialEquipmentHost(target)) {
            RejectReason carrier = DirectionalHostRules.validateNewOrChanged(target, flipped);
            if (carrier != RejectReason.NONE) {
                return ActionResult.failed(carrier);
            }
            FusedEnchantmentList otherFusions = list.removeAt(socketIndex);
            Set<String> standalone = readAllStandaloneEnchantIds(target, list);
            if (isIncompatibleWithHost(target, otherFusions, standalone, flipped.primaryId(), snap, registries)) {
                return ActionResult.failed(RejectReason.EXCLUSIVE_ON_HOST);
            }
        }

        ItemStack cat = inv.getItem(SLOT_CATALYST);
        boolean freeByCat = CetSoftBridge.isSplitOrFlipCatalyst(cat);
        int xpCost = freeByCat ? 0 : snap.economy().manageFlipSplitXp();
        if (!player.getAbilities().instabuild && player.experienceLevel < xpCost) {
            return ActionResult.failed(RejectReason.INSUFFICIENT_XP);
        }

        target.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(socketIndex, flipped));

        if (VesselCapacityRules.isPotentialEquipmentHost(target)) {
            removeGearVanillaEnchant(target, oldPair.primaryId());
            if (!replacesNativePrimary(flipped)) {
                lookupEnchantment(registries, flipped.primaryId()).ifPresent(h -> {
                    if (h.value().canEnchant(target)) {
                        setGearVanillaEnchantLevel(target, h, flipped.primaryLevel());
                    }
                });
            }
            syncGlintOverride(target);
        }

        if (freeByCat) {
            cat.shrink(1);
        } else if (!player.getAbilities().instabuild && xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            CompendiumDiscoveryTracker.recordDiscovery(serverPlayer, flipped.primaryId(), flipped.secondaryId());
        }
        inv.setChanged();
        emitStationRitualEffect(inv, player, enchantment_fusion.menu.FusionNetworking.VISUAL_FLIP_ORDER);
        return ActionResult.ok();
    }

    public static boolean executeUnsocketOrSplit(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex
    ) {
        return executeUnsocketOrSplitWithReason(inv, player, registries, socketIndex, "").success();
    }

    /**
     * Unsockets {@code socketIndex} from Gear or splits a Fused Enchanted Book into two vanilla books,
     * verifying {@code expectedPairToken} and returning a specific {@link RejectReason} on failure (NET-1 / NET-5).
     */
    public static ActionResult executeUnsocketOrSplitWithReason(
        Container inv,
        Player player,
        HolderLookup.Provider registries,
        int socketIndex,
        String expectedPairToken
    ) {
        ItemStack target = resolveManageableStack(inv);
        if (target.isEmpty()) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (socketIndex < 0 || socketIndex >= list.size()) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        FusedPair oldPair = list.pairs().get(socketIndex);
        if (expectedPairToken != null && !expectedPairToken.isEmpty() && !expectedPairToken.equals(oldPair.fingerprint())) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        RejectReason lockCheck = FusionRuleEngine.canUnsocketOrSplitPair(oldPair);
        if (lockCheck != RejectReason.NONE) {
            return ActionResult.failed(lockCheck);
        }

        ItemStack cat = inv.getItem(SLOT_CATALYST);
        boolean freeByCat = CetSoftBridge.isSplitOrFlipCatalyst(cat);
        int xpCost = freeByCat ? 0 : FusionMatrixRegistry.serverSnapshot().economy().manageFlipSplitXp();
        if (!player.getAbilities().instabuild && player.experienceLevel < xpCost) {
            return ActionResult.failed(RejectReason.INSUFFICIENT_XP);
        }

        if (target.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            Optional<Holder<Enchantment>> pHolder = lookupEnchantment(registries, oldPair.primaryId());
            Optional<Holder<Enchantment>> sHolder = lookupEnchantment(registries, oldPair.secondaryId());
            if (pHolder.isEmpty() || sHolder.isEmpty()) {
                return ActionResult.failed(RejectReason.MISSING_INPUT);
            }
            ItemStack bookA = createSingleEnchantedBook(pHolder.get(), oldPair.primaryLevel());
            ItemStack bookB = createSingleEnchantedBook(sHolder.get(), oldPair.secondaryLevel());

            target.shrink(1);
            placeInSlotOrInventory(inv, SLOT_PRIMARY, player, bookA);
            placeInSlotOrInventory(inv, SLOT_SECONDARY, player, bookB);
        } else {
            FusedEnchantmentList remaining = list.removeAt(socketIndex);
            if (remaining.isEmpty()) {
                target.remove(FusionDataComponents.FUSED_PAIRS);
            } else {
                target.set(FusionDataComponents.FUSED_PAIRS, remaining);
            }
            removeGearVanillaEnchant(target, oldPair.primaryId());
            removeGearVanillaEnchant(target, oldPair.secondaryId());
            syncGlintOverride(target);

            ItemStack extractedFusedBook = FusedEnchantedBookItem.createForPair(oldPair);
            placeInSlotOrInventory(inv, SLOT_PRIMARY, player, extractedFusedBook);
        }

        if (freeByCat) {
            cat.shrink(1);
        } else if (!player.getAbilities().instabuild && xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }
        inv.setChanged();
        emitStationRitualEffect(inv, player, enchantment_fusion.menu.FusionNetworking.VISUAL_SPLIT_EXTRACT);
        return ActionResult.ok();
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

    public static Set<String> readAllStandaloneEnchantIds(ItemStack gear, FusedEnchantmentList fusedList) {
        Set<String> out = new HashSet<>(readUnfusedVanillaEnchantIds(gear, fusedList));
        Map<String, Integer> transmuted = gear.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        out.addAll(transmuted.keySet());
        return out;
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
        if (!replacesNativePrimary(pair)) {
            lookupEnchantment(registries, pair.primaryId()).ifPresent(h -> {
                if (h.value().canEnchant(gear)) {
                    setGearVanillaEnchantLevel(gear, h, pair.primaryLevel());
                }
            });
        } else {
            // In-place fusion can start from a primary already on the item.
            removeGearVanillaEnchant(gear, pair.primaryId());
        }
        syncGlintOverride(gear);
    }

    /** Only explicitly redesigned ordered pairs suppress the old vanilla primary. */
    public static boolean replacesNativePrimary(FusedPair pair) {
        return SignaturePairs.resolve(pair).map(entry -> FlameKineticEffects.matches(pair, entry.id())).orElse(false);
    }

    /**
     * Migrate legacy equipment when it reaches a player's inventory/equipment. FusedPair
     * remains authoritative; unrelated enchantments are untouched. If a datapack removes
     * this particular redesign later, restore the native primary representation.
     */
    public static boolean normalizeRedesignedPrimary(ItemStack gear, HolderLookup.Provider registries) {
        if (gear == null || gear.isEmpty()) return false;
        FusedEnchantmentList pairs = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean changed = false;
        for (FusedPair pair : pairs.pairs()) {
            if (!FlameKineticEffects.isDefaultPair(pair)) continue;
            Optional<Holder<Enchantment>> holder = lookupEnchantment(registries, pair.primaryId());
            if (holder.isEmpty()) continue;
            int current = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder.get());
            if (replacesNativePrimary(pair)) {
                if (current > 0) {
                    removeGearVanillaEnchant(gear, pair.primaryId());
                    changed = true;
                }
            } else if (current == 0 && holder.get().value().canEnchant(gear)) {
                setGearVanillaEnchantLevel(gear, holder.get(), pair.primaryLevel());
                changed = true;
            }
        }
        if (changed) syncGlintOverride(gear);
        return changed;
    }

    public static void syncGlintOverride(ItemStack stack) {
        boolean hasFused = !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty();
        boolean hasTransmuted = !stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()).isEmpty();
        boolean hasVanilla = !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
        if ((hasFused || hasTransmuted) && !hasVanilla) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (!hasFused && !hasTransmuted) {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    private static void setGearVanillaEnchantLevel(ItemStack gear, Holder<Enchantment> holder, int level) {
        ItemEnchantments current = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
        mut.set(holder, Math.max(current.getLevel(holder), level));
        EnchantmentHelper.setEnchantments(gear, mut.toImmutable());
    }

    public static void removeGearVanillaEnchant(ItemStack gear, String enchantId) {
        ItemEnchantments current = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
        mut.removeIf(h -> h.unwrapKey().map(k -> k.identifier().toString().equals(enchantId)).orElse(false));
        EnchantmentHelper.setEnchantments(gear, mut.toImmutable());
    }

    private static void emitStationRitualEffect(Container inv, Player player, int visualKind) {
        if (player == null || player.level().isClientSide()) return;
        ServerLevel sl = (inv instanceof net.minecraft.world.level.block.entity.BlockEntity be
            && be.getLevel() instanceof ServerLevel stationLevel) ? stationLevel
            : (player.level() instanceof ServerLevel playerLevel ? playerLevel : null);
        if (sl == null) return;
        net.minecraft.core.BlockPos pos = (inv instanceof net.minecraft.world.level.block.entity.BlockEntity be)
            ? be.getBlockPos()
            : player.blockPosition();
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.06D;
        double cz = pos.getZ() + 0.5D;

        switch (visualKind) {
            case enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_SUCCESS -> {
                sl.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.85F, 1.06F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.95F, 1.28F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.65F, 1.12F);
                sl.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.25D, cz, 32, 0.35D, 0.25D, 0.35D, 0.45D);
                sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 14, 0.24D, 0.18D, 0.24D, 0.04D);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 10, 0.22D, 0.14D, 0.22D, 0.06D);
            }
            case enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_ABERRANT -> {
                sl.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.72F, 0.86F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.95F, 0.58F);
                sl.sendParticles(ParticleTypes.WITCH, cx, cy, cz, 26, 0.30D, 0.22D, 0.30D, 0.05D);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 16, 0.25D, 0.18D, 0.25D, 0.03D);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 10, 0.28D, 0.18D, 0.28D, 0.02D);
            }
            case enchantment_fusion.menu.FusionNetworking.VISUAL_FUSE_FAIL -> {
                sl.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.78F, 0.85F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, 0.65F);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 14, 0.28D, 0.18D, 0.28D, 0.02D);
                sl.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 10, 0.24D, 0.16D, 0.24D, 0.04D);
            }
            case enchantment_fusion.menu.FusionNetworking.VISUAL_FLIP_ORDER -> {
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.90F, 1.22F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.65F, 1.38F);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 16, 0.25D, 0.14D, 0.25D, 0.05D);
                sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 10, 0.22D, 0.14D, 0.22D, 0.03D);
            }
            case enchantment_fusion.menu.FusionNetworking.VISUAL_SPLIT_EXTRACT -> {
                sl.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.72F, 1.22F);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.85F, 1.32F);
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, cx, cy, cz, 18, 0.26D, 0.16D, 0.26D, 0.06D);
                sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 10, 0.20D, 0.14D, 0.20D, 0.03D);
                sl.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 6, 0.20D, 0.10D, 0.20D, 0.01D);
            }
            default -> {}
        }
        enchantment_fusion.menu.FusionNetworking.broadcastVisualEvent(sl, pos, visualKind);
    }
}
