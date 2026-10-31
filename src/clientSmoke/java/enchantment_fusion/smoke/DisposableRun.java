package enchantment_fusion.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Fail closed before any probe, configuration mutation or result-file write. */
final class DisposableRun {
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private DisposableRun() {}

    static Path validate(Path gameDirectory, Path configurationDirectory) throws IOException {
        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, NOFOLLOW) || Files.isSymbolicLink(root))
            throw new IOException("Disposable game directory is missing or a symbolic link");
        root = root.toRealPath();
        Path marker = root.resolve(".enchantment-fusion-disposable");
        if (!Files.isRegularFile(marker, NOFOLLOW) || !"fresh-integrated-fixtures\n".equals(Files.readString(marker)))
            throw new IOException("Refusing an unmarked directory; launch through scripts/client_smoke.py");

        Path config = configurationDirectory.toAbsolutePath().normalize();
        if (!config.startsWith(root))
            throw new IOException("Configuration directory must be inside the disposable game directory");
        Path current = root;
        for (Path part : root.relativize(config)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Refusing a linked configuration path");
        }
        if (Files.exists(config, NOFOLLOW) && !config.toRealPath().startsWith(root))
            throw new IOException("Configuration directory must resolve inside the disposable game directory");
        Path ownConfig = config.resolve("enchantment_fusion");
        if (Files.isSymbolicLink(ownConfig) || Files.exists(ownConfig.resolve("overrides.json"), NOFOLLOW))
            throw new IOException("Refusing a linked or existing fusion configuration");

        Path saves = root.resolve("saves"), screenshots = root.resolve("screenshots");
        if (Files.isSymbolicLink(saves) || Files.isSymbolicLink(screenshots)
            || Files.exists(saves.resolve("audit-world"), NOFOLLOW))
            throw new IOException("Refusing an existing audit-world or linked output directory");
        for (Path output : new Path[]{root.resolve("acceptance-report.md"), root.resolve("acceptance-report.md.tmp"),
                                      root.resolve("client-smoke-result.txt"), screenshots.resolve("fusion-ui-zh_cn.png")}) {
            if (Files.exists(output, NOFOLLOW)) throw new IOException("Refusing existing probe output: " + output);
        }
        return root;
    }
}
