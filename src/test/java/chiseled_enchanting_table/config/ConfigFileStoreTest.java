package chiseled_enchanting_table.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFileStoreTest {
    @TempDir Path directory;
    @Test void replacementFailureNeverTruncatesLastGoodConfiguration() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "last good preferences");
        var attempts = new AtomicInteger();
        assertThrows(IOException.class, () -> ConfigFileStore.writeAtomically(file, "new preferences", (from, to, options) -> {
            attempts.incrementAndGet();
            throw new IOException("simulated locked destination");
        }));
        assertEquals(4, attempts.get());
        assertEquals("last good preferences", Files.readString(file));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }
    @Test void providerWithoutAtomicMoveCanStillReplaceAndCleanTemporaryFile() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "old");
        ConfigFileStore.writeAtomically(file, "new", (from, to, options) -> {
            if (options[0] == StandardCopyOption.ATOMIC_MOVE) throw new IOException("atomic move unavailable");
            Files.move(from, to, options);
        });
        assertEquals("new", Files.readString(file));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }
}
