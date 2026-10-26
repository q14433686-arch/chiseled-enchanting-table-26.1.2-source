package chiseled_enchanting_table.config;
import chiseled_enchanting_table.config.ConfigCatalog.RuleTraceEntry;

/** Trace edits are editor operations, independently testable without a screen or renderer. */
public final class ConfigTraceActions {
    private ConfigTraceActions() {}
    public static void toggle(ConfigDraft draft, RuleTraceEntry trace) {
        if (trace.derived() || trace.optionKey() == null) return;
        boolean currentlyClassic = trace.isUsingClassic103(draft);
        if ("tome_consumption_mode".equals(trace.optionKey())) {
            draft.tomeConsumptionMode = currentlyClassic
                ? chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR
                : chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP;
            draft.tomeRegenEnabled = currentlyClassic;
            draft.markCustomized("tome_consumption_mode");
            draft.markCustomized("tome_regen_enabled");
        } else if (currentlyClassic) {
            trace.applyModern110(draft);
        } else {
            trace.applyClassic103(draft);
        }
    }
}
