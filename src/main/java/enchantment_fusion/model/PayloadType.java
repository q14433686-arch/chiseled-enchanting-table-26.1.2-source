package enchantment_fusion.model;

import java.util.Locale;

/**
 * The 14 Secondary Payload Cores defined in GDD §5.2.
 * Determined by the Secondary graft enchantment {@code B} in {@code [A ▷ B]}.
 */
public enum PayloadType {
    PYRO(0xFFFF7A36),
    CRYO(0xFF72E4FF),
    VOLT(0xFFFFEA5A),
    GALE(0xFFB8F7E6),
    TIDE(0xFF3FA9FF),
    RETURN(0xFF6CF2B5),
    PIERCE(0xFFFF6B8B),
    SCATTER(0xFFFFB454),
    GRAVITY(0xFFB388FF),
    EDGE(0xFFFFD56B),
    BANE(0xFFD67BFF),
    FORTUNE(0xFF7DFF7A),
    AEGIS(0xFF8CC8FF),
    MEND(0xFF9EFFB0);

    private final int accentArgb;

    PayloadType(int accentArgb) {
        this.accentArgb = accentArgb;
    }

    public int accentArgb() {
        return accentArgb;
    }

    public String translationKey() {
        return "fusion.enchantment_fusion.payload." + name().toLowerCase(Locale.ROOT);
    }

    public String effectKey(HostForm hostForm) {
        String group = (hostForm == HostForm.TOOL || hostForm == HostForm.ARMOR) ? "utility" : "combat";
        return "fusion.enchantment_fusion.effect." + name().toLowerCase(Locale.ROOT) + "." + group;
    }

    public static PayloadType fromString(String raw, PayloadType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return PayloadType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
