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
}
