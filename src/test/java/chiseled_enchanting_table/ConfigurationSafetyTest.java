package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSafetyTest {
    @TempDir Path directory;

    @Test void lanGuestsAreNotImplicitAdministrators() {
        assertFalse(ModConfigNetworking.mayManage(false, false, false));
        assertTrue(ModConfigNetworking.mayManage(false, true, false));
        assertTrue(ModConfigNetworking.mayManage(false, false, true));
        assertFalse(ModConfigNetworking.mayManage(true, true, false));
        assertTrue(ModConfigNetworking.mayManage(true, false, true));
    }

    @Test void atomicSaveReplacesValidConfigurationAndCleansTemporaryFiles() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "old content");
        ModRuntimeConfig.writeAtomically(file, ModRuntimeConfig.DEFAULT);
        assertEquals(ModRuntimeConfig.DEFAULT, ModRuntimeConfig.fromJsonString(Files.readString(file)));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void failedReplacementDoesNotDestroyExistingTarget() throws Exception {
        Path target = directory.resolve("config.json");
        Files.createDirectory(target);
        Files.writeString(target.resolve("sentinel"), "preserve");
        assertThrows(java.io.IOException.class, () -> ModRuntimeConfig.writeAtomically(target, ModRuntimeConfig.DEFAULT));
        assertEquals("preserve", Files.readString(target.resolve("sentinel")));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void malformedUpdatesAreRejectedAndSaveIdsDoNotRepeat() {
        assertThrows(RuntimeException.class, () -> ModRuntimeConfig.fromJsonString("{broken"));
        assertThrows(RuntimeException.class, () -> ModRuntimeConfig.fromJsonString("[]"));
        long first = ModRuntimeConfig.nextSaveRequestId();
        long second = ModRuntimeConfig.nextSaveRequestId();
        assertTrue(first > 0 && second > first);
        ModRuntimeConfig.acknowledgeSave(first, false);
        assertEquals(first, ModRuntimeConfig.acknowledgedSaveId());
        assertFalse(ModRuntimeConfig.acknowledgedSaveAccepted());
    }

    @Test void allFourPresetsRoundTripPreserveStackSizeAndRestore103Semantics() {
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.enchantedBookStackSize = 64;
        draft.overrideDatapacks = true;
        assertTrue(ModRuntimeConfig.Preset.STANDARD.matches(draft));

        for (ModRuntimeConfig.Preset preset : ModRuntimeConfig.Preset.values()) {
            preset.applyTo(draft);
            assertTrue(preset.matches(draft), "Preset must match draft right after applyTo: " + preset);
            assertEquals(64, draft.enchantedBookStackSize,
                "Presets must NEVER auto-shrink enchantedBookStackSize (save-truncation safety): " + preset);
            assertTrue(draft.overrideDatapacks, "Presets must preserve overrideDatapacks governance toggle");
            var roundTripped = ModRuntimeConfig.fromJsonString(draft.build().toJsonString()).toDraft();
            assertTrue(preset.matches(roundTripped), "Preset must survive JSON round-trip: " + preset);
        }

        ModRuntimeConfig.Preset.CLASSIC_1_0_3.applyTo(draft);
        var classic = draft.build();
        assertTrue(classic.classic103Mode());
        assertEquals(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP, classic.tomeConsumptionMode());
        assertFalse(classic.tomeRegenEnabled());
        assertFalse(classic.disenchantEnabled());
        assertEquals(0.0, classic.epiphanyMaxChance(), 1e-9);
        assertEquals(2, classic.horizontalScanRadius());
        assertEquals(2, classic.verticalScanRadius());
        assertFalse(classic.requireBreathingRoom());
        assertFalse(classic.requireLineOfSight());
        assertEquals(0, classic.maxWoodenDiscountPercent());
        assertEquals(0, classic.maxTotalXpDiscountPercent());

        var original = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
        try {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                classic, ModRuntimeConfig.PackPolicy.NONE, () -> {}
            );
            var classicFrame = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
            assertEquals(2, classicFrame.itemTransformations().size(),
                "1.0.3 Classic mode must expose only the 2 original item transformations (Golden Apple & Experience Bottle)");
        } finally {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {}
            );
        }
    }
}
