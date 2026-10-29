package enchantment_fusion.compat;

import enchantment_fusion.EnchantmentFusion;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stateless synthetic {@link Identifier} codec used to project {@code 融合附魔} binary pairs,
 * aberrant side-effects, and standalone cross-domain transmutations into {@code 纂刻附魔台}'s
 * Cleanse & Curse-Purge list while remaining 100% self-describing.
 *
 * <ul>
 *   <li>{@code enchantment_fusion:cet_aberrant/<index>/<primary_ns>/<primary_path>}</li>
 *   <li>{@code enchantment_fusion:cet_fused/<index>/<primary_ns>/<primary_path>}</li>
 *   <li>{@code enchantment_fusion:cet_transmute/<ench_ns>/<ench_path>}</li>
 * </ul>
 */
public final class CetCleanseIds {
    public static final String PREFIX_ABERRANT = "cet_aberrant/";
    public static final String PREFIX_FUSED = "cet_fused/";
    public static final String PREFIX_TRANSMUTE = "cet_transmute/";

    public enum Kind {
        ABERRANT,
        FUSED,
        TRANSMUTE
    }

    public record ParsedCleanseId(Kind kind, int pairIndex, String underlyingEnchantId) {}

    private CetCleanseIds() {}

    public static Identifier aberrantId(int pairIndex, String primaryEnchantId) {
        String[] nsPath = splitEnchantId(primaryEnchantId);
        return EnchantmentFusion.id(PREFIX_ABERRANT + Math.max(0, pairIndex) + "/" + nsPath[0] + "/" + nsPath[1]);
    }

    public static Identifier fusedId(int pairIndex, String primaryEnchantId) {
        String[] nsPath = splitEnchantId(primaryEnchantId);
        return EnchantmentFusion.id(PREFIX_FUSED + Math.max(0, pairIndex) + "/" + nsPath[0] + "/" + nsPath[1]);
    }

    public static Identifier transmuteId(String enchantId) {
        String[] nsPath = splitEnchantId(enchantId);
        return EnchantmentFusion.id(PREFIX_TRANSMUTE + nsPath[0] + "/" + nsPath[1]);
    }

    public static boolean isSyntheticCleanseId(@Nullable Identifier id) {
        if (id == null || !EnchantmentFusion.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith(PREFIX_ABERRANT)
            || path.startsWith(PREFIX_FUSED)
            || path.startsWith(PREFIX_TRANSMUTE);
    }

    public static @Nullable ParsedCleanseId parse(@Nullable Identifier id) {
        if (!isSyntheticCleanseId(id)) {
            return null;
        }
        String path = id.getPath();
        if (path.startsWith(PREFIX_ABERRANT)) {
            return parseIndexed(Kind.ABERRANT, path.substring(PREFIX_ABERRANT.length()));
        }
        if (path.startsWith(PREFIX_FUSED)) {
            return parseIndexed(Kind.FUSED, path.substring(PREFIX_FUSED.length()));
        }
        if (path.startsWith(PREFIX_TRANSMUTE)) {
            String rest = path.substring(PREFIX_TRANSMUTE.length());
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash >= rest.length() - 1) {
                return null;
            }
            String ns = rest.substring(0, slash);
            String enchPath = rest.substring(slash + 1);
            return new ParsedCleanseId(Kind.TRANSMUTE, -1, ns + ":" + enchPath);
        }
        return null;
    }

    public static @Nullable Identifier underlyingEnchantmentIdentifier(@Nullable Identifier id) {
        ParsedCleanseId parsed = parse(id);
        if (parsed == null) {
            return null;
        }
        return Identifier.tryParse(parsed.underlyingEnchantId());
    }

    private static @Nullable ParsedCleanseId parseIndexed(Kind kind, String rest) {
        int s1 = rest.indexOf('/');
        if (s1 <= 0) {
            return null;
        }
        int idx;
        try {
            idx = Integer.parseInt(rest.substring(0, s1));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (idx < 0) {
            return null;
        }
        String afterIdx = rest.substring(s1 + 1);
        int s2 = afterIdx.indexOf('/');
        if (s2 <= 0 || s2 >= afterIdx.length() - 1) {
            return null;
        }
        String ns = afterIdx.substring(0, s2);
        String enchPath = afterIdx.substring(s2 + 1);
        return new ParsedCleanseId(kind, idx, ns + ":" + enchPath);
    }

    private static String[] splitEnchantId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"minecraft", "unbreaking"};
        }
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon >= raw.length() - 1) {
            return new String[]{"minecraft", sanitizeSegment(raw)};
        }
        return new String[]{
            sanitizeSegment(raw.substring(0, colon)),
            sanitizeSegment(raw.substring(colon + 1))
        };
    }

    private static String sanitizeSegment(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "unbreaking" : sb.toString();
    }
}
