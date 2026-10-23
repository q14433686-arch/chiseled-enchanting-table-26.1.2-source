package chiseled_enchanting_table.config;

/**
 * Early bootstrap configuration wrapper delegating directly to {@link ModRuntimeConfig}.
 */
public final class ModBootstrapConfig {
    private ModBootstrapConfig() {}

    public int enchantedBookStackSize() {
        return ModRuntimeConfig.get().enchantedBookStackSize();
    }

    public static ModBootstrapConfig load() {
        return new ModBootstrapConfig();
    }

    public static ModBootstrapConfig get() {
        return new ModBootstrapConfig();
    }
}
