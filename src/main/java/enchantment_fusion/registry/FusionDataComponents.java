package enchantment_fusion.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Custom item data components for fused enchantment pairs, cross-domain transmuted enchantments,
 * vessel slot expansion, and tiered tattered knowledge pages.
 */
public final class FusionDataComponents {
    public static final int MAX_TRANSMUTED_ENCHANTS = 64;

    private static final Codec<String> TRANSMUTED_ID_CODEC = Codec.STRING.comapFlatMap(
        raw -> {
            if (raw == null) {
                return DataResult.error(() -> "Null transmuted enchantment identifier");
            }
            try {
                return DataResult.success(FusedPair.canonicalizeEnchantId(raw));
            } catch (IllegalArgumentException ex) {
                return DataResult.error(ex::getMessage);
            }
        },
        s -> s
    );

    private static final Codec<Integer> CLAMPED_TRANSMUTED_LEVEL_CODEC = Codec.INT.xmap(
        v -> Math.clamp(v, FusedPair.MIN_LEVEL, FusedPair.MAX_LEVEL),
        v -> Math.clamp(v, FusedPair.MIN_LEVEL, FusedPair.MAX_LEVEL)
    );

    private static final StreamCodec<ByteBuf, String> TRANSMUTED_ID_STREAM =
        ByteBufCodecs.stringUtf8(FusedPair.MAX_ENCHANT_ID_LENGTH).map(
            raw -> {
                try {
                    return FusedPair.canonicalizeEnchantId(raw);
                } catch (IllegalArgumentException ex) {
                    throw new DecoderException(ex.getMessage(), ex);
                }
            },
            id -> {
                try {
                    return FusedPair.canonicalizeEnchantId(id);
                } catch (IllegalArgumentException ex) {
                    throw new EncoderException(ex.getMessage(), ex);
                }
            }
        );

    private static Map<String, Integer> sanitizeTransmutedMap(Map<String, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> bounded = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : raw.entrySet()) {
            if (bounded.size() >= MAX_TRANSMUTED_ENCHANTS) {
                EnchantmentFusion.LOGGER.warn(
                    "Truncating transmuted_enchants map from {} entries to hard cap {}",
                    raw.size(),
                    MAX_TRANSMUTED_ENCHANTS
                );
                break;
            }
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            bounded.put(e.getKey(), Math.clamp(e.getValue(), FusedPair.MIN_LEVEL, FusedPair.MAX_LEVEL));
        }
        return Collections.unmodifiableMap(bounded);
    }

    public static final DataComponentType<FusedEnchantmentList> FUSED_PAIRS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("fused_pairs"),
        DataComponentType.<FusedEnchantmentList>builder()
            .persistent(FusedEnchantmentList.CODEC)
            .networkSynchronized(FusedEnchantmentList.STREAM_CODEC)
            .build()
    );

    /**
     * Stores cross-domain single-enchantment transmutations (enchantmentId -> clampedLevel)
     * outside vanilla {@code DataComponents.ENCHANTMENTS} so vanilla slot-driven enchantment
     * effects do not double-trigger on non-native hosts (API-01).
     * Persistent and network codecs enforce identical {@link #MAX_TRANSMUTED_ENCHANTS}, identifier
     * canonicalization, and {@code [1, 255]} level clamping so world saves can never diverge from packet limits (DATA-1 / DATA-2).
     */
    public static final DataComponentType<Map<String, Integer>> TRANSMUTED_ENCHANTS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("transmuted_enchants"),
        DataComponentType.<Map<String, Integer>>builder()
            .persistent(
                Codec.unboundedMap(TRANSMUTED_ID_CODEC, CLAMPED_TRANSMUTED_LEVEL_CODEC)
                    .xmap(FusionDataComponents::sanitizeTransmutedMap, FusionDataComponents::sanitizeTransmutedMap)
            )
            .networkSynchronized(
                ByteBufCodecs.map(
                    HashMap::new,
                    TRANSMUTED_ID_STREAM,
                    ByteBufCodecs.VAR_INT.map(
                        v -> Math.clamp(v, FusedPair.MIN_LEVEL, FusedPair.MAX_LEVEL),
                        v -> Math.clamp(v, FusedPair.MIN_LEVEL, FusedPair.MAX_LEVEL)
                    ),
                    MAX_TRANSMUTED_ENCHANTS
                ).map(FusionDataComponents::sanitizeTransmutedMap, m -> new HashMap<>(sanitizeTransmutedMap(m)))
            )
            .build()
    );

    /** A shadowed secondary remains separate from any independent same-name enchantment. */
    public static final int MAX_RETAINED_ENCHANTS = 64;
    public static final DataComponentType<List<RetainedEnchant>> RETAINED_ENCHANTS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("retained_enchants"),
        DataComponentType.<List<RetainedEnchant>>builder()
            .persistent(RetainedEnchant.CODEC.listOf(0, MAX_RETAINED_ENCHANTS).xmap(List::copyOf, List::copyOf))
            .networkSynchronized(RetainedEnchant.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RETAINED_ENCHANTS))
                .map(List::copyOf, List::copyOf))
            .build()
    );

    /** Level of a vanilla Loyalty projection that this mod itself created for the guard fusion. */
    public static final DataComponentType<Integer> LOYALTY_GUARD_PROJECTION = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("loyalty_guard_projection"),
        DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(1, FusedPair.MAX_LEVEL))
            .networkSynchronized(ByteBufCodecs.VAR_INT.map(
                v -> {
                    if (v < 1 || v > FusedPair.MAX_LEVEL) throw new DecoderException("Invalid projection level: " + v);
                    return v;
                },
                v -> {
                    if (v < 1 || v > FusedPair.MAX_LEVEL) throw new EncoderException("Invalid projection level: " + v);
                    return v;
                }))
            .build()
    );

    public static final DataComponentType<Integer> VESSEL_BONUS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("vessel_bonus"),
        DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 1))
            .networkSynchronized(ByteBufCodecs.VAR_INT.map(v -> Math.clamp(v, 0, 1), v -> Math.clamp(v, 0, 1)))
            .build()
    );

    /** 0 = Common Tattered Page, 1 = Rare Ancient Page, 2 = Aberrant Research Note. */
    public static final DataComponentType<Integer> PAGE_TIER = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("page_tier"),
        DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 2))
            .networkSynchronized(ByteBufCodecs.VAR_INT.map(v -> Math.clamp(v, 0, 2), v -> Math.clamp(v, 0, 2)))
            .build()
    );

    private FusionDataComponents() {}

    public static void init() {
        // Explicit bootstrap trigger
    }
}
