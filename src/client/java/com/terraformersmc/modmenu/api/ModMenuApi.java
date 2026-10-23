package com.terraformersmc.modmenu.api;

import java.util.Map;
import java.util.function.Consumer;

public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }

    default void attachModpackBadges(Consumer<String> consumer) {
    }
}
