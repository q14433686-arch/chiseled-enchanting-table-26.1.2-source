package chiseled_enchanting_table.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/** Stateless scalar parsing shared by independent table domains. */
final class DataJson {
    private DataJson() {}
    static String stripTagPrefix(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    static Identifier parseIdentifier(String value) {
        return value.indexOf(':') >= 0 ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
    }

    static int stablePositiveHash(String value) {
        int hash = value.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    static boolean boolOr(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    static double doubleOr(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
