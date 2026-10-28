package enchantment_fusion.model;

import java.util.Locale;

/**
 * The 8 Primary Trigger Carriers defined in GDD §5.1.
 * Determined by the Primary enchantment {@code A} in {@code [A ▷ B]}.
 */
public enum TriggerType {
    ON_MELEE_STRIKE(30),
    ON_SWEEP_OR_CLEAVE(25),
    ON_PROJECTILE_IMPACT(20),
    ON_TRIDENT_CHANNEL(30),
    ON_BLOCK_HARVEST(20),
    ON_DAMAGE_TAKEN(60),
    ON_MOVEMENT_OR_LAND(40),
    ON_UTILITY_CYCLE(30);

    private final int cooldownTicks;

    TriggerType(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public String translationKey() {
        return "fusion.enchantment_fusion.trigger." + name().toLowerCase(Locale.ROOT);
    }

    public static TriggerType fromString(String raw, TriggerType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return TriggerType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
