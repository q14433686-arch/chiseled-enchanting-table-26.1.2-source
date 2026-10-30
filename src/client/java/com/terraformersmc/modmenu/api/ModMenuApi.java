package com.terraformersmc.modmenu.api;

import java.util.Map;

/**
 * Minimal compile-only subset of upstream ModMenu's {@code ModMenuApi} interface
 * (Audit API-04 / DEAD-02), excluded from the packaged JAR by {@code build.gradle}.
 *
 * <p>Only the two canonical factory methods published by ModMenu are declared here;
 * verify signatures against upstream {@code TerraformersMC/ModMenu} when upgrading
 * target Minecraft versions.</p>
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }
}
