package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Exercises candidate models with real loaded enchantment registries but no Widget instance. */
public final class ListModelProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError("candidate boundary: " + message);
    }
    public static void verify(Player player) {
        var sharp = Identifier.withDefaultNamespace("sharpness");
        var efficiency = Identifier.withDefaultNamespace("efficiency");
        var catalog = new InscribeOptionModel.Catalog() {
            public boolean allowsDirectTierUpgrade() { return false; }
            public boolean usesClassicShelfBoost() { return true; }
            public boolean canDirectUpgradeExistingEnchantment(Holder<Enchantment> h, Identifier id, int old, int next) { return false; }
            public boolean canDirectResearchUpgradeTo(Identifier id, int next) { return false; }
            public int requiredBookshelvesForDirectTier(Identifier id, int next, int max) { return 38; }
            public List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(ItemStack target) { return List.of(); }
            public Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack target) { return Set.of(); }
            public boolean syncedTransformationMatches(Identifier id, ItemStack target) { return false; }
        };
        var target = new ItemStack(Items.DIAMOND_SWORD);
        var costs = new ItemStack(Items.LAPIS_LAZULI);
        var model = InscribeOptionModel.build(catalog, player, target, costs,
            Set.of(new EnchantmentWithLevel(sharp, 2), new EnchantmentWithLevel(sharp, 1), new EnchantmentWithLevel(efficiency, 1)),
            option -> option.id().toString());
        check(model.pinned().isEmpty() && model.groups().size() == 1 && model.groups().containsKey(sharp), "support filter/grouping differs");
        var rows = model.groups().get(sharp);
        check(rows.size() == 2 && rows.get(0).option().level() == 1 && rows.get(1).option().level() == 2, "tier sort differs");
        try { rows.clear(); throw new AssertionError("candidate list is mutable"); }
        catch (UnsupportedOperationException expected) { }
        target.enchant(EnchantmentDisplayNames.identifierToEnchantment(sharp, player), 1);
        var upgraded = InscribeOptionModel.build(catalog, player, target, costs,
            Set.of(new EnchantmentWithLevel(sharp, 1), new EnchantmentWithLevel(sharp, 2)), option -> option.id().toString());
        check(upgraded.groups().get(sharp).size() == 1 && upgraded.groups().get(sharp).getFirst().option().level() == 2, "same-level dead candidate returned");
        var cleanse = CleanseOptionModel.build(target, false);
        check(cleanse.pinned().size() == 1 && cleanse.groups().isEmpty(), "single level-one trait should only expose full cleanse");
        check(costs.getCount() == 1, "candidate construction consumed cost items");
        System.out.println("CANDIDATE BOUNDARY PASS: registry-backed support filter + tier sorting + immutable groups + no same-level candidate + cleanse availability + no Widget ownership");
    }
}
