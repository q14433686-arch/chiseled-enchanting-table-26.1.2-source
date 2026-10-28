package enchantment_fusion.registry;

import com.mojang.serialization.Codec;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.model.FusedEnchantmentList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Custom item data components for fused enchantment pairs, vessel slot expansion,
 * and tiered tattered knowledge pages.
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

    public static final DataComponentType<Integer> VESSEL_BONUS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("vessel_bonus"),
        DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 1))
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /** 0 = Common Tattered Page, 1 = Rare Ancient Page, 2 = Aberrant Research Note. */
    public static final DataComponentType<Integer> PAGE_TIER = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        EnchantmentFusion.id("page_tier"),
        DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 2))
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    private FusionDataComponents() {}

    public static void init() {
        // Explicit bootstrap trigger
    }
}
