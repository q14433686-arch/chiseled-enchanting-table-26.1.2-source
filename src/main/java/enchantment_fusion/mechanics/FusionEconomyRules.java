package enchantment_fusion.mechanics;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.model.FusedPair;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Tiered-risk economy calculator aligned with full-curve {@link EconomyConfig} (NUM-1 / NUM-2 / NUM-9 / CPL-1):
 * <ul>
 *   <li><b>Low Tier</b>: {@code 0%} default failure rate (consumes {@code 0} stabilizers).</li>
 *   <li><b>Mid Tier</b>: Moderate failure rate reducible to {@code 0%} via Resonance Stabilizer;
 *       failure NEVER destroys any book or gear (charges {@code normal_fail_fee_ratio}, default {@code 50%} rounded down).</li>
 *   <li><b>High / Repulsive Tier</b>: High-tier max-level or mutually-exclusive clash;
 *       Stabilizers are consumed strictly by needed count ({@code ceil((base - floor) / reduction)}),
 *       with repulsive fusions retaining {@code repulsive_min_floor} (default {@code 8%}) unless CET Arcane Catalyst is used.</li>
 *   <li><b>Native Aberrant Purification</b>: {@code purify_aberrant_stabilizers} (default {@code 3}) Resonance Stabilizers
 *       + {@code purify_aberrant_xp} (default {@code 25} Lv) strips {@code aberrationId} into pure {@code [A ▷ B]}!</li>
 * </ul>
 */
public final class FusionEconomyRules {
    public enum ReagentKind {
        NONE("none"),
        LAPIS("lapis"),
        AMETHYST("amethyst"),
        EMERALD("emerald");

        private final String key;

        ReagentKind(String key) {
            this.key = key;
        }

        public String translationKey() {
            return switch (this) {
                case NONE -> "gui.enchantment_fusion.reagent.none";
                case LAPIS -> "gui.enchantment_fusion.reagent.lapis";
                case AMETHYST -> "gui.enchantment_fusion.reagent.amethyst";
                case EMERALD -> "gui.enchantment_fusion.reagent.emerald";
            };
        }

        public Item asVanillaItem() {
            return switch (this) {
                case LAPIS -> Items.LAPIS_LAZULI;
                case AMETHYST -> Items.AMETHYST_SHARD;
                case EMERALD -> Items.EMERALD;
                case NONE -> Items.AIR;
            };
        }

        public boolean matches(ItemStack stack) {
            if (this == NONE || stack == null || stack.isEmpty()) {
                return false;
            }
            return stack.is(asVanillaItem());
        }
    }

    public record ReagentSourceBreakdown(
        int inCatalystSlot,
        int inBackpack,
        int takenFromSlot,
        int takenFromBackpack
    ) {
        public int totalAvailable() {
            return inCatalystSlot + inBackpack;
        }
    }

    public record EconomyQuote(
        int xpLevels,
        ReagentKind reagent,
        int reagentCount,
        double failureChance,
        boolean highRiskTier,
        boolean destroysBookOnFail,
        int stabilizersUsed,
        double failFeeRatio
    ) {
        public static final EconomyQuote FREE =
            new EconomyQuote(0, ReagentKind.NONE, 0, 0.0, false, false, 0, 0.50);

        public EconomyQuote(
            int xpLevels,
            ReagentKind reagent,
            int reagentCount,
            double failureChance,
            boolean highRiskTier,
            boolean destroysBookOnFail,
            int stabilizersUsed
        ) {
            this(xpLevels, reagent, reagentCount, failureChance, highRiskTier, destroysBookOnFail, stabilizersUsed, 0.50);
        }

        public EconomyQuote(
            int xpLevels,
            ReagentKind reagent,
            int reagentCount,
            double failureChance,
            boolean highRiskTier,
            int stabilizersUsed
        ) {
            this(xpLevels, reagent, reagentCount, failureChance, highRiskTier, false, stabilizersUsed, 0.50);
        }

        public int halfChargedXp() {
            return (int) Math.floor(xpLevels * Math.clamp(failFeeRatio, 0.0, 1.0));
        }

        public int halfChargedReagents() {
            return (int) Math.floor(reagentCount * Math.clamp(failFeeRatio, 0.0, 1.0));
        }
    }

    private FusionEconomyRules() {}

    public static int countStabilizers(ItemStack catalystSlot) {
        if (catalystSlot == null || catalystSlot.isEmpty()) {
            return 0;
        }
        return CetSoftBridge.isStabilizerCatalyst(catalystSlot) ? catalystSlot.getCount() : 0;
    }

    /** Computes the exact stabilizer count needed so extra crystals in Slot 2 are never wasted (BAL-03). */
    public static int computeNeededStabilizers(double baseFail, double floorFail, double perStab, int cap, int available) {
        if (available <= 0 || cap <= 0 || baseFail <= floorFail + 1e-6 || perStab <= 1e-6) {
            return 0;
        }
        int needed = (int) Math.ceil((baseFail - floorFail - 1e-9) / perStab);
        return Math.max(0, Math.min(available, Math.min(cap, needed)));
    }

    public static EconomyQuote computeSynthesisQuote(
        String primaryId,
        int pLv,
        int pMax,
        String secondaryId,
        int sLv,
        int sMax,
        boolean repulsive,
        boolean signature,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        return computeSynthesisQuote(
            FusionMatrixRegistry.serverSnapshot().economy(),
            primaryId, pLv, pMax, secondaryId, sLv, sMax,
            repulsive, signature, stabilizerCount, zeroXpCatalyst
        );
    }

    public static EconomyQuote computeSynthesisQuote(
        EconomyConfig eco,
        String primaryId,
        int pLv,
        int pMax,
        String secondaryId,
        int sLv,
        int sMax,
        boolean repulsive,
        boolean signature,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig cfg = eco != null ? eco : EconomyConfig.DEFAULT;
        int safeStab = Math.max(0, stabilizerCount);
        int sumLv = Math.max(2, pLv + sLv);
        double feeRatio = cfg.normalFailFeeRatio();

        if (repulsive) {
            int xp = zeroXpCatalyst ? 0 : (cfg.repulsiveBaseXp() + Math.min(cfg.repulsiveMaxBonusXp(), sumLv / 3));
            int mats = (pLv >= pMax && sLv >= sMax) ? cfg.repulsiveMaxPairReagents() : cfg.repulsiveDefaultReagents();
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, mats, 0.0, true, false, Math.min(1, safeStab), feeRatio);
            }
            double base = cfg.repulsiveFailChance();
            double floor = cfg.repulsiveMinFloor();
            double step = cfg.stabilizerReductionRepulsive();
            int used = computeNeededStabilizers(base, floor, step, cfg.maxRepulsiveStabilizers(), safeStab);
            double fail = clampProb(base - step * used, floor, cfg.failCeilingRepulsive());
            return new EconomyQuote(xp, ReagentKind.EMERALD, mats, fail, true, false, used, feeRatio);
        }

        boolean bothMaxHigh = (pLv >= pMax && sLv >= sMax && (pMax >= 3 || sMax >= 3));
        if (bothMaxHigh) {
            int xp = zeroXpCatalyst ? 0 : Math.min(cfg.highMaxXp(), cfg.highBaseXp() + sumLv / 2);
            int mats = cfg.highReagents();
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, mats, 0.0, true, false, Math.min(1, safeStab), feeRatio);
            }
            double base = signature ? cfg.highFailChance() : Math.max(0.0, cfg.highFailChance() - 0.06);
            double step = cfg.stabilizerReductionNormal();
            int used = computeNeededStabilizers(base, 0.0, step, cfg.maxNormalStabilizers(), safeStab);
            double fail = clampProb(base - step * used, 0.0, cfg.failCeilingHigh());
            return new EconomyQuote(xp, ReagentKind.EMERALD, mats, fail, true, false, used, feeRatio);
        }

        if (signature || sumLv >= 5) {
            int xp = zeroXpCatalyst ? 0 : Math.max(cfg.midMinXp(), Math.min(cfg.midMaxXp(), cfg.midBaseXp() + sumLv / 2));
            int mats = Math.max(1, Math.min(cfg.midMaxReagents(), sumLv / 3));
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.AMETHYST, mats, 0.0, false, false, Math.min(1, safeStab), feeRatio);
            }
            double base = cfg.midFailChance() + 0.02 * Math.max(0, sumLv - 5);
            double step = cfg.stabilizerReductionNormal();
            int used = computeNeededStabilizers(base, 0.0, step, cfg.maxNormalStabilizers(), safeStab);
            double fail = clampProb(base - step * used, 0.0, cfg.failCeilingMid());
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, false, used, feeRatio);
        }

        int xp = zeroXpCatalyst ? 0 : Math.max(cfg.lowMinXp(), cfg.lowBaseXp() + sumLv / 2);
        int mats = Math.max(2, Math.min(cfg.lowMaxReagents(), 1 + sumLv));
        double base = cfg.lowFailChance();
        int used = computeNeededStabilizers(base, 0.0, cfg.stabilizerReductionNormal(), cfg.maxLowStabilizers(), safeStab);
        double fail = clampProb(base - cfg.stabilizerReductionNormal() * used, 0.0, cfg.failCeilingLow());
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, false, used, feeRatio);
    }

    public static EconomyQuote computeConversionQuote(
        int enchLevel,
        int maxLevel,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        return computeConversionQuote(enchLevel, maxLevel, 1, stabilizerCount, zeroXpCatalyst);
    }

    public static EconomyQuote computeConversionQuote(
        int enchLevel,
        int maxLevel,
        int anvilCost,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        return computeConversionQuote(
            FusionMatrixRegistry.serverSnapshot().economy(),
            enchLevel, maxLevel, anvilCost, stabilizerCount, zeroXpCatalyst
        );
    }

    public static EconomyQuote computeConversionQuote(
        EconomyConfig eco,
        int enchLevel,
        int maxLevel,
        int anvilCost,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig cfg = eco != null ? eco : EconomyConfig.DEFAULT;
        int safeLv = Math.max(1, enchLevel);
        int safeMax = Math.max(1, maxLevel);
        int safeAnvil = Math.max(1, anvilCost);
        int safeStab = Math.max(0, stabilizerCount);
        double feeRatio = cfg.normalFailFeeRatio();

        boolean highRarity = safeAnvil >= 8 || (safeLv >= safeMax && safeMax >= 4);
        if (highRarity) {
            int xp = zeroXpCatalyst ? 0 : Math.max(5, Math.min(cfg.highMaxXp(), 3 + safeAnvil / 2));
            int mats = cfg.highReagents();
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, mats, 0.0, true, false, Math.min(1, safeStab), feeRatio);
            }
            double base = Math.max(0.0, cfg.highFailChance() - 0.04);
            int used = computeNeededStabilizers(base, 0.0, cfg.stabilizerReductionNormal(), cfg.maxNormalStabilizers(), safeStab);
            double fail = clampProb(base - cfg.stabilizerReductionNormal() * used, 0.0, cfg.failCeilingConversion());
            return new EconomyQuote(xp, ReagentKind.EMERALD, mats, fail, true, false, used, feeRatio);
        }

        boolean midTier = safeAnvil >= 4 || safeLv >= 3 || (safeLv >= safeMax && safeMax >= 2);
        if (midTier) {
            int xp = zeroXpCatalyst ? 0 : Math.max(cfg.midMinXp(), Math.max(safeLv, safeAnvil));
            int mats = Math.min(cfg.midMaxReagents(), Math.max(2, safeLv));
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.AMETHYST, mats, 0.0, false, false, Math.min(1, safeStab), feeRatio);
            }
            double base = cfg.midFailChance();
            int used = computeNeededStabilizers(base, 0.0, cfg.stabilizerReductionNormal(), cfg.maxNormalStabilizers(), safeStab);
            double fail = clampProb(base - cfg.stabilizerReductionNormal() * used, 0.0, cfg.failCeilingMid());
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, false, used, feeRatio);
        }

        int xp = zeroXpCatalyst ? 0 : (cfg.lowBaseXp() + safeLv);
        int mats = 2 + safeLv;
        double base = cfg.lowFailChance();
        int used = computeNeededStabilizers(base, 0.0, cfg.stabilizerReductionNormal(), cfg.maxLowStabilizers(), safeStab);
        double fail = clampProb(base - cfg.stabilizerReductionNormal() * used, 0.0, cfg.failCeilingLow());
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, false, used, feeRatio);
    }

    public static EconomyQuote computeSocketFusedBookQuote(
        FusedPair pair,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        return computeSocketFusedBookQuote(
            FusionMatrixRegistry.serverSnapshot().economy(),
            pair, stabilizerCount, zeroXpCatalyst
        );
    }

    public static EconomyQuote computeSocketFusedBookQuote(
        EconomyConfig eco,
        FusedPair pair,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig cfg = eco != null ? eco : EconomyConfig.DEFAULT;
        int safeStab = Math.max(0, stabilizerCount);
        boolean ab = pair != null && pair.isAberrant();
        int xp = zeroXpCatalyst ? 0 : cfg.socketBookXp();
        ReagentKind kind = ab ? ReagentKind.EMERALD : ReagentKind.AMETHYST;
        double feeRatio = cfg.normalFailFeeRatio();
        if (!ab || zeroXpCatalyst) {
            return new EconomyQuote(xp, kind, 1, 0.0, ab, false, zeroXpCatalyst ? Math.min(1, safeStab) : 0, feeRatio);
        }
        double base = cfg.aberrantSocketFailChance();
        int used = computeNeededStabilizers(base, 0.0, cfg.stabilizerReductionNormal(), cfg.maxLowStabilizers(), safeStab);
        double fail = clampProb(base - cfg.stabilizerReductionNormal() * used, 0.0, cfg.failCeilingSocket());
        return new EconomyQuote(xp, kind, 1, fail, true, false, used, feeRatio);
    }

    /** Native high-cost purification quote for stripping {@code aberrationId} at the Fusion Table (CPL-1). */
    public static EconomyQuote computePurifyAberrantQuote(
        EconomyConfig eco,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig cfg = eco != null ? eco : EconomyConfig.DEFAULT;
        int xp = zeroXpCatalyst ? 0 : cfg.purifyAberrantXp();
        int neededStab = zeroXpCatalyst ? 1 : cfg.purifyAberrantStabilizers();
        return new EconomyQuote(xp, ReagentKind.NONE, 0, 0.0, false, false, neededStab, cfg.normalFailFeeRatio());
    }

    /**
     * Single source of truth for inspecting Catalyst Slot 2 vs Player Backpack reagent counts (NUM-9 / DEAD-3).
     */
    public static ReagentSourceBreakdown inspectReagentSources(
        Container stationInv,
        @Nullable Player player,
        ReagentKind kind,
        int requiredCount
    ) {
        if (kind == ReagentKind.NONE) {
            return new ReagentSourceBreakdown(0, 0, 0, 0);
        }
        int inSlot = 0;
        if (stationInv != null) {
            ItemStack cat = stationInv.getItem(FusionStationOperations.SLOT_CATALYST);
            if (kind.matches(cat)) {
                inSlot = cat.getCount();
            }
        }
        int inBag = 0;
        if (player != null) {
            Inventory pInv = player.getInventory();
            for (int i = 0; i < pInv.getContainerSize(); i++) {
                ItemStack st = pInv.getItem(i);
                if (kind.matches(st)) {
                    inBag += st.getCount();
                }
            }
        }
        int req = Math.max(0, requiredCount);
        int fromSlot = Math.min(req, inSlot);
        int fromBag = Math.min(Math.max(0, req - fromSlot), inBag);
        return new ReagentSourceBreakdown(inSlot, inBag, fromSlot, fromBag);
    }

    public static int countAvailableReagent(Container stationInv, @Nullable Player player, ReagentKind kind) {
        if (kind == ReagentKind.NONE) {
            return Integer.MAX_VALUE;
        }
        return inspectReagentSources(stationInv, player, kind, 0).totalAvailable();
    }

    public static boolean canAfford(Container stationInv, Player player, EconomyQuote quote) {
        if (quote == null || player.getAbilities().instabuild) {
            return true;
        }
        if (player.experienceLevel < quote.xpLevels()) {
            return false;
        }
        return countAvailableReagent(stationInv, player, quote.reagent()) >= quote.reagentCount();
    }

    public static void deductFeeAndStabilizers(
        Container stationInv,
        Player player,
        EconomyQuote quote,
        boolean halfChargeNormalFailure
    ) {
        if (quote == null || player.getAbilities().instabuild) {
            return;
        }
        int xpToTake = halfChargeNormalFailure ? quote.halfChargedXp() : quote.xpLevels();
        int matToTake = halfChargeNormalFailure ? quote.halfChargedReagents() : quote.reagentCount();

        if (xpToTake > 0) {
            player.giveExperienceLevels(-xpToTake);
        }
        if (matToTake > 0 && quote.reagent() != ReagentKind.NONE) {
            ReagentSourceBreakdown split = inspectReagentSources(stationInv, player, quote.reagent(), matToTake);
            if (split.takenFromSlot() > 0) {
                ItemStack cat = stationInv.getItem(FusionStationOperations.SLOT_CATALYST);
                cat.shrink(split.takenFromSlot());
            }
            int rem = split.takenFromBackpack();
            if (rem > 0) {
                Inventory pInv = player.getInventory();
                for (int i = 0; i < pInv.getContainerSize() && rem > 0; i++) {
                    ItemStack st = pInv.getItem(i);
                    if (quote.reagent().matches(st)) {
                        int take = Math.min(rem, st.getCount());
                        st.shrink(take);
                        rem -= take;
                    }
                }
            }
        }
        if (quote.stabilizersUsed() > 0) {
            ItemStack cat = stationInv.getItem(FusionStationOperations.SLOT_CATALYST);
            if (CetSoftBridge.isStabilizerCatalyst(cat)) {
                cat.shrink(Math.min(cat.getCount(), quote.stabilizersUsed()));
            }
        }
    }

    private static double clampProb(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
