package chiseled_enchanting_table.config;

/** Editable preferences are independent of rendering and published effective rules. */
public final class ConfigEditSession {
    private ModRuntimeConfig baseline;
    private ModRuntimeConfig.Draft draft;

    public ConfigEditSession(ModRuntimeConfig preferences) { acceptSaved(preferences); }
    public ModRuntimeConfig baseline() { return baseline; }
    public ModRuntimeConfig.Draft draft() { return draft; }
    public boolean dirty() { return !draft.build().equals(baseline); }
    /** Called only after a matching accepted save, never on rejected/stale acknowledgements. */
    public void acceptSaved(ModRuntimeConfig preferences) {
        baseline = java.util.Objects.requireNonNull(preferences);
        draft = baseline.toDraft();
    }
}
