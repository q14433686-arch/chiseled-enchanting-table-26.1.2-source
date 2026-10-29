package enchantment_fusion.model;

import java.util.Locale;

/**
 * The 14 Secondary Payload Cores defined in GDD §5.2.
 * Determined by the Secondary graft enchantment {@code B} in {@code [A ▷ B]}.
 * Presentation ARGB palette tokens live exclusively on the client in {@code FusionGuiSkin} (CPL-4).
 */
public enum PayloadType {
    PYRO,
    CRYO,
    VOLT,
    GALE,
    TIDE,
    RETURN,
    PIERCE,
    SCATTER,
    GRAVITY,
    EDGE,
    BANE,
    FORTUNE,
    AEGIS,
    MEND;

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
