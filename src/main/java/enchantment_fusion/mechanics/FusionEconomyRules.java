package enchantment_fusion.mechanics;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.model.FusedPair;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pure + runtime {@code tiered_risk} economy calculator for Book Synthesis ({@code 合成})
 * and Cross-Domain Single/Fused Conversion ({@code 转换}):
 * <ul>
 *   <li>Charges tiered XP Levels + Enchanting Reagents ({@code Lapis Lazuli / Amethyst Shard / Emerald})
 *       from the Catalyst slot or the player's inventory.</li>
 *   <li>Computes failure probability reducible via {@code Resonance Stabilizer} catalysts.</li>
 *   <li>Normal failure keeps host gear / primary book safe, consumes 1 book, and charges only half the fee
 *       (50% refund); high-risk max-level / repulsive failure produces an Aberrant book or taints gear.</li>
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
            return "gui.enchantment_fusion.reagent." + key;
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
        int stabilizersUsed
    ) {
        public static final EconomyQuote FREE = new EconomyQuote(0, ReagentKind.NONE, 0, 0.0, false, 0);

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
        int safeStab = Math.max(0, stabilizerCount);
        int sumLv = Math.max(2, pLv + sLv);

        if (repulsive) {
            int xp = zeroXpCatalyst ? 0 : (7 + Math.min(4, sumLv / 3));
            int mats = (pLv >= pMax && sLv >= sMax) ? 3 : 2;
            int used = Math.min(3, safeStab);
            double fail = clampProb(0.45 - 0.12 * used, 0.08, 0.85);
            return new EconomyQuote(xp, ReagentKind.EMERALD, mats, fail, true, used);
        }

        boolean bothMaxHigh = (pLv >= pMax && sLv >= sMax && (pMax >= 3 || sMax >= 3));
        if (bothMaxHigh) {
            int xp = zeroXpCatalyst ? 0 : Math.min(9, 4 + sumLv / 2);
            int used = Math.min(2, safeStab);
            double base = signature ? 0.28 : 0.22;
            double fail = clampProb(base - 0.15 * used, 0.0, 0.75);
            return new EconomyQuote(xp, ReagentKind.EMERALD, 2, fail, true, used);
        }

        if (signature || sumLv >= 5) {
            int xp = zeroXpCatalyst ? 0 : Math.max(3, Math.min(7, 2 + sumLv / 2));
            int mats = Math.max(1, Math.min(3, sumLv / 3));
            int used = Math.min(2, safeStab);
            double base = (signature ? 0.18 : 0.14) + 0.02 * Math.max(0, sumLv - 4);
            double fail = clampProb(base - 0.16 * used, 0.0, 0.65);
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, used);
        }

        int xp = zeroXpCatalyst ? 0 : Math.max(2, 1 + sumLv / 2);
        int mats = Math.max(2, Math.min(5, 1 + sumLv));
        int used = Math.min(2, safeStab);
        double base = 0.10 + 0.03 * Math.max(0, sumLv - 2);
        double fail = clampProb(base - 0.18 * used, 0.0, 0.50);
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, used);
    }

    public static EconomyQuote computeConversionQuote(
        int enchLevel,
        int maxLevel,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        int safeLv = Math.max(1, enchLevel);
        int safeMax = Math.max(1, maxLevel);
        int safeStab = Math.max(0, stabilizerCount);

        if (safeLv >= safeMax && safeMax >= 4) {
            int xp = zeroXpCatalyst ? 0 : 5;
            int used = Math.min(2, safeStab);
            double fail = clampProb(0.24 - 0.15 * used, 0.0, 0.60);
            return new EconomyQuote(xp, ReagentKind.EMERALD, 2, fail, true, used);
        }
        if (safeLv >= 3 || (safeLv >= safeMax && safeMax >= 2)) {
            int xp = zeroXpCatalyst ? 0 : Math.max(3, safeLv);
            int mats = Math.min(3, Math.max(1, safeLv - 1));
            int used = Math.min(2, safeStab);
            double fail = clampProb(0.15 - 0.16 * used, 0.0, 0.50);
            return new EconomyQuote(xp, ReagentKind.AMETHYST, mats, fail, false, used);
        }
        int xp = zeroXpCatalyst ? 0 : (1 + safeLv);
        int mats = 2 + safeLv;
        int used = Math.min(1, safeStab);
        double fail = clampProb(0.09 - 0.18 * used, 0.0, 0.40);
        return new EconomyQuote(xp, ReagentKind.LAPIS, mats, fail, false, used);
    }

    public static EconomyQuote computeSocketFusedBookQuote(
        FusedPair pair,
        int stabilizerCount,
        boolean zeroXpCatalyst
    ) {
        int safeStab = Math.max(0, stabilizerCount);
        int used = Math.min(1, safeStab);
        boolean ab = pair != null && pair.isAberrant();
        int xp = zeroXpCatalyst ? 0 : 2;
        ReagentKind kind = ab ? ReagentKind.EMERALD : ReagentKind.AMETHYST;
        double fail = clampProb((ab ? 0.15 : 0.06) - 0.16 * used, 0.0, 0.40);
        return new EconomyQuote(xp, kind, 1, fail, ab, used);
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
