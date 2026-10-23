package chiseled_enchanting_table.config;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Early bootstrap configuration loaded once during {@link ChiseledEnchantingTable#onInitialize()}
 * before item default-component modifications run.
 *
 * <p>Allows modpack authors and server administrators to customize or disable global item
 * component overrides (such as setting {@code enchanted_book_stack_size} to {@code 1} when pairing
 * with third-party automation/library machines that assume non-stackable Enchanted Books).</p>
 */
public final class ModBootstrapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "chiseled_enchanting_table.json";

    private static volatile ModBootstrapConfig instance;

    private final int enchantedBookStackSize;

    private ModBootstrapConfig(int enchantedBookStackSize) {
        this.enchantedBookStackSize = Math.clamp(enchantedBookStackSize, 1, 64);
    }

    public int enchantedBookStackSize() {
        return enchantedBookStackSize;
    }

    public static ModBootstrapConfig load() {
        return get();
    }

    public static ModBootstrapConfig get() {
        ModBootstrapConfig current = instance;
        if (current == null) {
            synchronized (ModBootstrapConfig.class) {
                current = instance;
                if (current == null) {
                    current = loadOrWriteDefault();
                    instance = current;
                }
            }
        }
        return current;
    }

    private static ModBootstrapConfig loadOrWriteDefault() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Path path = configDir.resolve(FILE_NAME);
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    int stackSize = obj.has("enchanted_book_stack_size")
                        ? obj.get("enchanted_book_stack_size").getAsInt()
                        : 64;
                    return new ModBootstrapConfig(stackSize);
                }
            }
            JsonObject defaultJson = new JsonObject();
            defaultJson.addProperty(
                "_comment",
                "Bootstrap settings applied at startup. Set enchanted_book_stack_size to 1 to keep vanilla single-stack Enchanted Books for third-party machine compatibility."
            );
            defaultJson.addProperty("enchanted_book_stack_size", 64);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(defaultJson, writer);
                writer.write('\n');
            }
            return new ModBootstrapConfig(64);
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to read/write {}; falling back to default stack size 64", FILE_NAME, e);
            return new ModBootstrapConfig(64);
        }
    }
}
