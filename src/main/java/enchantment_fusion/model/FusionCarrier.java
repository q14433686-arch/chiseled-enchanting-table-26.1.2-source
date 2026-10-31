package enchantment_fusion.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;

/**
 * Explicit fusion-host categories for datapack-classified third-party enchantments. These are
 * not vanilla enchantability intersections and are independent of single-enchantment transmute
 * roles. An item may have several capabilities (for example a modded sword/pickaxe hybrid).
 */
public enum FusionCarrier {
    SWORD, AXE, SPEAR, MACE, TRIDENT, BOW, CROSSBOW,
    PICKAXE, SHOVEL, HOE, SHEARS, BRUSH, FLINT_AND_STEEL, FISHING_ROD, RIDING_ROD,
    SHIELD, HEAD_ARMOR, CHEST_ARMOR, LEG_ARMOR, FOOT_ARMOR, ELYTRA, OTHER_EQUIPMENT;

    public static Set<FusionCarrier> classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Set.of();
        EnumSet<FusionCarrier> roles = EnumSet.noneOf(FusionCarrier.class);
        Item item = stack.getItem();
        if (stack.is(ItemTags.SWORDS)) roles.add(SWORD);
        if (stack.is(ItemTags.AXES)) roles.add(AXE);
        if (HostForm.isSpear(stack)) roles.add(SPEAR);
        if (item instanceof MaceItem) roles.add(MACE);
        if (item instanceof TridentItem) roles.add(TRIDENT);
        if (item instanceof BowItem) roles.add(BOW);
        if (item instanceof CrossbowItem) roles.add(CROSSBOW);
        if (stack.is(ItemTags.PICKAXES)) roles.add(PICKAXE);
        if (stack.is(ItemTags.SHOVELS)) roles.add(SHOVEL);
        if (stack.is(ItemTags.HOES)) roles.add(HOE);
        if (item instanceof ShearsItem) roles.add(SHEARS);
        if (item instanceof BrushItem) roles.add(BRUSH);
        if (item instanceof FlintAndSteelItem) roles.add(FLINT_AND_STEEL);
        if (item instanceof FishingRodItem) roles.add(FISHING_ROD);
        if (stack.is(Items.CARROT_ON_A_STICK) || stack.is(Items.WARPED_FUNGUS_ON_A_STICK)) roles.add(RIDING_ROD);
        if (item instanceof ShieldItem) roles.add(SHIELD);
        if (stack.is(ItemTags.HEAD_ARMOR)) roles.add(HEAD_ARMOR);
        if (stack.is(ItemTags.CHEST_ARMOR)) roles.add(CHEST_ARMOR);
        if (stack.is(ItemTags.LEG_ARMOR)) roles.add(LEG_ARMOR);
        if (stack.is(ItemTags.FOOT_ARMOR)) roles.add(FOOT_ARMOR);
        if (stack.is(Items.ELYTRA) || stack.has(DataComponents.GLIDER)) roles.add(ELYTRA);
        if (roles.isEmpty()) roles.add(OTHER_EQUIPMENT);
        return Collections.unmodifiableSet(roles);
    }
}
