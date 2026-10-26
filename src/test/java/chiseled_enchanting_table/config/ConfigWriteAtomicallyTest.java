package chiseled_enchanting_table.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fault-injection regressions for the config file replacement: when every move fails the last
 * good configuration must survive byte-for-byte (the previous copy/direct-write "recovery" could
 * truncate it). Uses a real temporary directory, not mocked file contents.
 */
class ConfigWriteAtomicallyTest {
    @TempDir Path directory;

    @Test void atomicUnsupportedFallsBackToPlainRename() throws Exception {
        Path target = directory.resolve("chiseled_enchanting_table.json");
        Files.writeString(target, "old");
        AtomicInteger calls = new AtomicInteger();
        ModRuntimeConfig.writeAtomically(target, "new", (source, dest, options) -> {
            if (calls.incrementAndGet() == 1 && java.util.List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new IOException("atomic unsupported");
            }
            Files.move(source, dest, options);
        });
        assertEquals("new", Files.readString(target));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void allMovesFailingPreservesOriginalAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("chiseled_enchanting_table.json");
        Files.writeString(target, "last good config");
        assertThrows(IOException.class, () -> ModRuntimeConfig.writeAtomically(target, "new",
            (source, dest, options) -> { throw new IOException("denied"); }));
        assertEquals("last good config", Files.readString(target));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }
}
