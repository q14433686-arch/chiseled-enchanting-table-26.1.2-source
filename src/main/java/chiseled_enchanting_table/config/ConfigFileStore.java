package chiseled_enchanting_table.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** File-replacement side effects, independent of configuration semantics and runtime publication. */
final class ConfigFileStore {
    private ConfigFileStore() {}
    @FunctionalInterface interface MoveOperation {
        void move(Path source, Path target, CopyOption... options) throws IOException;
    }
    static void writeAtomically(Path path, String json) throws IOException {
        writeAtomically(path, json, Files::move);
    }
    /** Package-local fault-injection seam; production always uses the real filesystem move. */
    static void writeAtomically(Path path, String json, MoveOperation move) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".cet-config-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                move.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException atomicFailure) {
                clearWindowsReadOnly(path);
                IOException lastFailure = atomicFailure;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        move.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                        return;
                    } catch (IOException moveFailure) {
                        lastFailure = moveFailure;
                        pauseBeforeRetry(attempt);
                    }
                }
                // Never "recover" by copying/truncating the live file: an interrupted write
                // would destroy the last good configuration. Report failure, retain the draft.
                throw lastFailure;
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    private static void clearWindowsReadOnly(Path path) {
        try {
            if (Files.isRegularFile(path)) Files.setAttribute(path, "dos:readonly", false);
        } catch (Exception ignored) { }
    }
    private static void pauseBeforeRetry(int attempt) {
        try { Thread.sleep(25L * (attempt + 1)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
