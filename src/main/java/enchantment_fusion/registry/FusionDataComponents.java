package enchantment_fusion.registry;

import com.mojang.serialization.Codec;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.model.FusedEnchantmentList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Custom item data components for fused enchantment pairs, cross-domain transmuted enchantments,
 * vessel slot expansion, and tiered tattered knowledge pages.
 */
public final class FusionDataComponents {
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
     */
    public static final DataComponentType<Map<String, Integer>> TRANSMUTED_ENCHANTS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("transmuted_enchants"),
        DataComponentType.<Map<String, Integer>>builder()
            .persistent(
                Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 255))
                    .xmap(Collections::unmodifiableMap, m -> m)
            )
            .networkSynchronized(
                ByteBufCodecs.map(
                    HashMap::new,
                    ByteBufCodecs.stringUtf8(128),
                    ByteBufCodecs.VAR_INT.map(v -> Math.clamp(v, 1, 255), v -> Math.clamp(v, 1, 255)),
                    64
                ).map(Collections::unmodifiableMap, HashMap::new)
            )
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
