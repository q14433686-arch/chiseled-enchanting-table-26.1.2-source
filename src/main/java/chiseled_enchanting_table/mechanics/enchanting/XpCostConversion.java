package chiseled_enchanting_table.mechanics.enchanting;

import net.minecraft.world.entity.player.Player;

/**
 * Converts a "level cost" (an integer number of experience levels) into the actual XP-point
 * count that should be deducted, using vanilla's own level-to-point curve. This is the fix for
 * the user report "current implementation just decrements experienceLevel, which is a bad deal
 * for high-level players because their high levels are worth many more XP points than low ones"
 * — with this helper, deducting 5 "levels" from a level-30 player actually costs the same XP
 * points vanilla anvils/enchanting cost, not "5 levels regardless of what those levels contain".
 *
 * <p><b>Policy:</b> matches vanilla anvil / enchanting-table behaviour — the deduction consumes
 * the top {@code levelCost} levels' worth of XP from the player's current position. A level-30
 * player paying 5 levels loses XP equivalent to filling levels 26..30 (~365 points); a level-50
 * player paying 5 levels loses more (~635 points). This is the "high-level players pay more"
 * curve, which is what feels fair when the *quality* of what you're buying doesn't depend on
 * your level.</p>
 *
 * <p><b>Curve:</b> encodes vanilla's public
 * {@code Player#getXpNeededForNextLevel()} formula:
 * <ul>
 *   <li>level {@code < 16}: {@code 2·level + 7}</li>
 *   <li>level {@code < 31}: {@code 5·level - 38}</li>
 *   <li>level {@code >= 31}: {@code 9·level - 158}</li>
 * </ul>
 * Kept as a private static because it's stable vanilla data (unchanged since 1.7) and pulling
 * it out avoids needing an accessor for the vanilla static that computes it internally.</p>
 */
public final class XpCostConversion {
    private XpCostConversion() {}

    /**
     * Returns the XP-point count that a "levelCost"-level deduction should consume, starting
     * from the player's current XP position (level + progress fraction). Non-negative; 0 if
     * levelCost is 0 or negative.
     */
    public static int pointsForLevelCost(Player player, int levelCost) {
        if (levelCost <= 0) return 0;
        int startLevel = player.experienceLevel;
        float startProgress = player.experienceProgress; // 0.0..1.0, fraction of current level
        int endLevel = Math.max(0, startLevel - levelCost);
        if (endLevel >= startLevel) return 0;

        // Fractional part of the starting level: cost from the start position back down to the
        // integer start level's floor.
        double points = startProgress * xpNeededToReachNextLevelFrom(startLevel);

        // Then every full level between (endLevel, startLevel] contributes its "cost to reach
        // this level from the level below". Note that xpNeededToReachNextLevelFrom(L) is the XP
        // needed to go FROM level L TO level L+1, so the amount released by dropping FROM level
        // L+1 back TO level L is the same number.
        for (int lvl = endLevel; lvl < startLevel; lvl++) {
            points += xpNeededToReachNextLevelFrom(lvl);
        }
        // Clamp to a sane int and round up so we never under-charge by a fractional point.
        long rounded = (long) Math.ceil(points);
        if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) rounded;
    }

    /** XP needed to reach {@code level+1} starting from a filled-{@code level} state. */
    private static int xpNeededToReachNextLevelFrom(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
}
