package enchantment_fusion.mechanics.signatures;

/** A bank may defer only units actually added by this Fortune roll. */
public final class FortuneBankMath {
    private FortuneBankMath() {}
    public static int deposit(int base, int result, int capacity) {
        if (base < 1 || result <= base || capacity <= 0) return 0;
        long added = (long) result - base;
        return (int) Math.min(capacity, (added + 1) / 2);
    }
    public static int payout(int current, int maxStack, int bank, int perBreak) {
        if (current < 1 || maxStack <= current || bank <= 0 || perBreak <= 0) return 0;
        return Math.min(Math.min(maxStack - current, bank), perBreak);
    }
}
