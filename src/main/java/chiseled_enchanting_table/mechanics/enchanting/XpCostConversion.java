package chiseled_enchanting_table.mechanics.enchanting;

import net.minecraft.world.entity.player.Player;

/**
 * Converts a level cost into XP points using vanilla's level curve up to level 30, while capping
 * the per-level point width at level 30 so high-level players (50, 80, 100+) are never penalized
 * with thousands of extra XP points for a routine enchantment, and preserving fractional bar progress.
 */
public final class XpCostConversion {
    private static final int MARGINAL_CAP_LEVEL = 30;

    private XpCostConversion() {}

    public static int pointsForLevelCost(Player player, int levelCost) {
        if (levelCost <= 0) return 0;
        int actualStart = player.experienceLevel;
        if (actualStart <= 0) return 0;

        int effectiveStart = Math.min(actualStart, MARGINAL_CAP_LEVEL);
        int effectiveCost = Math.min(levelCost, actualStart);
        int effectiveEnd = Math.max(0, effectiveStart - effectiveCost);

        float progress = player.experienceProgress;
        long startPartial = Math.round(progress * xpNeededToReachNextLevelFrom(effectiveStart));
        long endPartial = Math.round(progress * xpNeededToReachNextLevelFrom(effectiveEnd));
        long points = startPartial - endPartial;

        // If cost exceeds 30 while capped (e.g., datapack custom cost > 30), charge remaining levels at lv0.. cap floor.
        for (int i = 0; i < effectiveCost; i++) {
            int barLevel = Math.max(0, effectiveStart - 1 - i);
            points += xpNeededToReachNextLevelFrom(barLevel);
        }

        if (points <= 0L) return 1;
        if (points > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) points;
    }

    private static int xpNeededToReachNextLevelFrom(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
}
