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

/**
 * Tiered-risk economy calculator aligned with {@link EconomyConfig} (EF-003 / BAL-03 / BAL-04 / BAL-05 / BAL-07):
 * <ul>
 *   <li><b>Low Tier</b>: {@code 0%} failure rate (beginner-friendly, consumes {@code 0} stabilizers).</li>
 *   <li><b>Mid Tier</b>: Moderate failure rate reducible to {@code 0%} via Resonance Stabilizer;
 *       failure NEVER destroys any book or gear (charges only half XP & reagent rounded down).</li>
 *   <li><b>High / Repulsive Tier</b>: High-tier max-level or mutually-exclusive clash;
 *       Stabilizers are consumed strictly by needed count ({@code ceil((base - floor) / reduction)}),
 *       with repulsive fusions retaining an honest {@code 8%} minimum instability floor (unless CET Arcane Catalyst is used).</li>
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

    public record EconomyQuote(
        int xpLevels,
        ReagentKind reagent,
        int reagentCount,
        double failureChance,
        boolean highRiskTier,
        boolean destroysBookOnFail,
        int stabilizersUsed
    ) {
        public static final EconomyQuote FREE = new EconomyQuote(0, ReagentKind.NONE, 0, 0.0, false, false, 0);

        public EconomyQuote(
            int xpLevels,
            ReagentKind reagent,
            int reagentCount,
            double failureChance,
            boolean highRiskTier,
            int stabilizersUsed
        ) {
            this(xpLevels, reagent, reagentCount, failureChance, highRiskTier, false, stabilizersUsed);
        }

        public int halfChargedXp() {
            return xpLevels / 2;
        }

        public int halfChargedReagents() {
            return reagentCount / 2;
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
        EconomyConfig eco = FusionMatrixRegistry.current().economy();
        int safeStab = Math.max(0, stabilizerCount);
        int sumLv = Math.max(2, pLv + sLv);

        if (repulsive) {
            int xp = zeroXpCatalyst ? 0 : (7 + Math.min(4, sumLv / 3));
            int mats = (pLv >= pMax && sLv >= sMax) ? 3 : 2;
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, mats, 0.0, true, false, Math.min(1, safeStab));
            }
            double base = eco.repulsiveFailChance();
            double floor = eco.repulsiveMinFloor();
            double step = eco.stabilizerReductionRepulsive();
            int used = computeNeededStabilizers(base, floor, step, eco.maxRepulsiveStabilizers(), safeStab);
            double fail = clampProb(base - step * used, floor, 0.85);
            return new EconomyQuote(xp, ReagentKind.EMERALD, mats, fail, true, false, used);
        }

        boolean bothMaxHigh = (pLv >= pMax && sLv >= sMax && (pMax >= 3 || sMax >= 3));
        if (bothMaxHigh) {
            int xp = zeroXpCatalyst ? 0 : Math.min(9, 4 + sumLv / 2);
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, 2, 0.0, true, false, Math.min(1, safeStab));
            }
            double base = signature ? eco.highFailChance() : Math.max(0.0, eco.highFailChance() - 0.06);
            double step = eco.stabilizerReductionNormal();
            int used = computeNeededStabilizers(base, 0.0, step, 2, safeStab);
            double fail = clampProb(base - step * used, 0.0, 0.75);
            return new EconomyQuote(xp, ReagentKind.EMERALD, 2, fail, true, false, used);
        }

        if (signature || sumLv >= 5) {
            int xp = zeroXpCatalyst ? 0 : Math.max(3, Math.min(7, 2 + sumLv / 2));
            int mats = Math.max(1, Math.min(3, sumLv / 3));
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.AMETHYST, mats, 0.0, false, false, Math.min(1, safeStab));
            }
            double base = eco.midFailChance() + 0.02 * Math.max(0, sumLv - 5);
            double step = eco.stabilizerReductionNormal();
            int used = computeNeededStabilizers(base, 0.0, step, 2, safeStab);
            double fail = clampProb(base - step * used, 0.0, 0.65);
            // Mid-tier failure NEVER destroys books — only charges half fee! (Choice 3A / BAL-05)
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, false, used);
        }

        // Low-tier beginner fusion: 0% failure rate, 0 stabilizers consumed! (Choice 3A)
        int xp = zeroXpCatalyst ? 0 : Math.max(2, 1 + sumLv / 2);
        int mats = Math.max(2, Math.min(4, 1 + sumLv));
        double base = eco.lowFailChance();
        int used = computeNeededStabilizers(base, 0.0, eco.stabilizerReductionNormal(), 1, safeStab);
        double fail = clampProb(base - eco.stabilizerReductionNormal() * used, 0.0, 0.50);
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, false, used);
    }

    public static EconomyQuote computeConversionQuote(
        int enchLevel,
        int maxLevel,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        return computeConversionQuote(enchLevel, maxLevel, 1, stabilizerCount, zeroXpCatalyst);
    }

    /**
     * Conversion quote factoring both enchantment level ratio and rarity/anvilCost so rare
     * 1-level enchantments (Mending, Infinity, Silk Touch, Channeling, Wind Burst) are priced in
     * Mid/High tier rather than Low tier (BAL-07).
     */
    public static EconomyQuote computeConversionQuote(
        int enchLevel,
        int maxLevel,
        int anvilCost,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig eco = FusionMatrixRegistry.current().economy();
        int safeLv = Math.max(1, enchLevel);
        int safeMax = Math.max(1, maxLevel);
        int safeAnvil = Math.max(1, anvilCost);
        int safeStab = Math.max(0, stabilizerCount);

        boolean highRarity = safeAnvil >= 8 || (safeLv >= safeMax && safeMax >= 4);
        if (highRarity) {
            int xp = zeroXpCatalyst ? 0 : Math.max(5, Math.min(8, 3 + safeAnvil / 2));
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.EMERALD, 2, 0.0, true, false, Math.min(1, safeStab));
            }
            double base = Math.max(0.0, eco.highFailChance() - 0.04);
            int used = computeNeededStabilizers(base, 0.0, eco.stabilizerReductionNormal(), 2, safeStab);
            double fail = clampProb(base - eco.stabilizerReductionNormal() * used, 0.0, 0.60);
            return new EconomyQuote(xp, ReagentKind.EMERALD, 2, fail, true, false, used);
        }

        boolean midTier = safeAnvil >= 4 || safeLv >= 3 || (safeLv >= safeMax && safeMax >= 2);
        if (midTier) {
            int xp = zeroXpCatalyst ? 0 : Math.max(3, Math.max(safeLv, safeAnvil));
            int mats = Math.min(3, Math.max(2, safeLv));
            if (zeroXpCatalyst) {
                return new EconomyQuote(0, ReagentKind.AMETHYST, mats, 0.0, false, false, Math.min(1, safeStab));
            }
            double base = eco.midFailChance();
            int used = computeNeededStabilizers(base, 0.0, eco.stabilizerReductionNormal(), 2, safeStab);
            double fail = clampProb(base - eco.stabilizerReductionNormal() * used, 0.0, 0.50);
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, false, used);
        }

        // Low-tier conversion: 0% failure rate!
        int xp = zeroXpCatalyst ? 0 : (1 + safeLv);
        int mats = 2 + safeLv;
        double base = eco.lowFailChance();
        int used = computeNeededStabilizers(base, 0.0, eco.stabilizerReductionNormal(), 1, safeStab);
        double fail = clampProb(base - eco.stabilizerReductionNormal() * used, 0.0, 0.40);
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, false, used);
    }

    public static EconomyQuote computeSocketFusedBookQuote(
        FusedPair pair,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        EconomyConfig eco = FusionMatrixRegistry.current().economy();
        int safeStab = Math.max(0, stabilizerCount);
        boolean ab = pair != null && pair.isAberrant();
        int xp = zeroXpCatalyst ? 0 : 2;
        ReagentKind kind = ab ? ReagentKind.EMERALD : ReagentKind.AMETHYST;
        if (!ab || zeroXpCatalyst) {
            return new EconomyQuote(xp, kind, 1, 0.0, ab, false, zeroXpCatalyst ? Math.min(1, safeStab) : 0);
        }
        double base = 0.15;
        int used = computeNeededStabilizers(base, 0.0, eco.stabilizerReductionNormal(), 1, safeStab);
        double fail = clampProb(base - eco.stabilizerReductionNormal() * used, 0.0, 0.40);
        return new EconomyQuote(xp, kind, 1, fail, true, false, used);
    }

    public static int countAvailableReagent(Container stationInv, Player player, ReagentKind kind) {
        if (kind == ReagentKind.NONE) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        ItemStack cat = stationInv.getItem(FusionStationOperations.SLOT_CATALYST);
        if (kind.matches(cat)) {
            total += cat.getCount();
        }
        if (player != null) {
            Inventory pInv = player.getInventory();
            for (int i = 0; i < pInv.getContainerSize(); i++) {
                ItemStack st = pInv.getItem(i);
                if (kind.matches(st)) {
                    total += st.getCount();
                }
            }
        }
        return total;
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
            int rem = matToTake;
            ItemStack cat = stationInv.getItem(FusionStationOperations.SLOT_CATALYST);
            if (quote.reagent().matches(cat)) {
                int take = Math.min(rem, cat.getCount());
                cat.shrink(take);
                rem -= take;
            }
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
