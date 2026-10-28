package enchantment_fusion.compat;

import enchantment_fusion.registry.FusionItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Zero-hard-dependency soft synergy bridge with Chiseled Enchanting Table (纂刻附魔台):
 * <ul>
 *   <li>{@code chiseled_enchanting_table:arcane_catalyst} acts as a {@code 0-XP}, {@code 100%} Resonance Stabilizer.</li>
 *   <li>{@code chiseled_enchanting_table:cleansing_grit} acts as a {@code 0-XP} clean Splitter / Order-Flipper.</li>
 * </ul>
 */
public final class CetSoftBridge {
    public static final String CET_MOD_ID = "chiseled-enchanting-table";
    public static final String CET_NAMESPACE = "chiseled_enchanting_table";
    public static final Identifier CET_ARCANE_CATALYST =
        Identifier.fromNamespaceAndPath(CET_NAMESPACE, "arcane_catalyst");
    public static final Identifier CET_CLEANSING_GRIT =
        Identifier.fromNamespaceAndPath(CET_NAMESPACE, "cleansing_grit");

    private CetSoftBridge() {}

    public static boolean isCetLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded(CET_MOD_ID) || loader.isModLoaded(CET_NAMESPACE);
    }

    public static boolean isStabilizerCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(FusionItems.RESONANCE_STABILIZER)) {
            return true;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CET_ARCANE_CATALYST.equals(key);
    }

    public static boolean isZeroXpCetStabilizer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CET_ARCANE_CATALYST.equals(key);
    }

    public static boolean isZeroXpCetCleanseCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CET_CLEANSING_GRIT.equals(key);
    }

    /**
     * Flip / Split catalyst check (CPL-05): strictly accepts Amethyst Shard, Lapis Lazuli, or
     * CET Cleansing Grit — NEVER consumes precious Resonance Stabilizers for a 2-XP flip!
     */
    public static boolean isSplitOrFlipCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.AMETHYST_SHARD)
            || stack.is(Items.LAPIS_LAZULI)
            || isZeroXpCetCleanseCatalyst(stack);
    }
}
