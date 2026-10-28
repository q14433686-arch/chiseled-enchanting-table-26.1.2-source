package enchantment_fusion.model;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;

/**
 * Classifies host equipment into four adaptive families for secondary payload resolution,
 * including 26.1+ Spears (via {@code #minecraft:spears}) and utility tools (Fishing Rod, Shears, Brush, Flint & Steel — HC-06).
 */
public enum HostForm {
    MELEE,
    RANGED,
    TOOL,
    ARMOR;

    public static final TagKey<Item> SPEARS_TAG = TagKey.create(
        Registries.ITEM,
        Identifier.withDefaultNamespace("spears")
    );

    public String translationKey() {
        return "fusion.enchantment_fusion.host." + name().toLowerCase(Locale.ROOT);
    }

    public static boolean isSpear(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(SPEARS_TAG);
    }

    public static HostForm resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MELEE;
        }
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) {
            return RANGED;
        }
        if (isSpear(stack) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || item instanceof MaceItem) {
            return MELEE;
        }
        if (item instanceof ShieldItem
            || stack.is(ItemTags.HEAD_ARMOR)
            || stack.is(ItemTags.CHEST_ARMOR)
            || stack.is(ItemTags.LEG_ARMOR)
            || stack.is(ItemTags.FOOT_ARMOR)
            || stack.has(DataComponents.EQUIPPABLE)) {
            return ARMOR;
        }
        if (item instanceof FishingRodItem
            || item instanceof ShearsItem
            || item instanceof FlintAndSteelItem
            || item instanceof BrushItem
            || stack.is(ItemTags.PICKAXES)
            || stack.is(ItemTags.SHOVELS)
            || stack.is(ItemTags.HOES)
            || stack.has(DataComponents.TOOL)) {
            return TOOL;
        }
        return MELEE;
    }
}
