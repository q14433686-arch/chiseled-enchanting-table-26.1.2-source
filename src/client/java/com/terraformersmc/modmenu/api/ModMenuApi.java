package com.terraformersmc.modmenu.api;

import java.util.Map;

/**
 * Minimal compile-only subset of upstream ModMenu's {@code ModMenuApi} interface,
 * excluded from packaged binary and sources JARs via {@code build.gradle}.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }
}
