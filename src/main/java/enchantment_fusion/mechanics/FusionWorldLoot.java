package enchantment_fusion.mechanics;

import enchantment_fusion.item.FusionPageItem;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Injects {@code 《融合残页》} into vanilla dungeon, library, temple, bastion, and ancient-city
 * chests, and adds Apprentice/Master Librarian trades so survival players can naturally discover
 * Grimoire entries via exploration or trading.
 */
public final class FusionWorldLoot {
    private static final Set<String> COMMON_PAGE_CHESTS = Set.of(
        "chests/simple_dungeon",
        "chests/abandoned_mineshaft",
        "chests/desert_pyramid",
        "chests/jungle_temple",
        "chests/shipwreck_map",
        "chests/pillager_outpost",
        "chests/woodland_mansion"
    );

    private static final Set<String> HIGH_TIER_CHESTS = Set.of(
        "chests/stronghold_library",
        "chests/ancient_city",
        "chests/bastion_treasure",
        "chests/end_city_treasure"
    );

    private FusionWorldLoot() {}

    public static void init() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            Identifier id = key.identifier();
            if (!"minecraft".equals(id.getNamespace())) {
                return;
            }
            String path = id.getPath();
            if (COMMON_PAGE_CHESTS.contains(path)) {
                LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(FusionItems.FUSION_TATTERED_PAGE).setWeight(26))
                    .add(EmptyLootItem.emptyItem().setWeight(74));
                tableBuilder.withPool(pool);
            } else if (HIGH_TIER_CHESTS.contains(path)) {
                LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(FusionItems.FUSION_TATTERED_PAGE).setWeight(28))
                    .add(
                        LootItem.lootTableItem(FusionItems.FUSION_TATTERED_PAGE)
                            .apply(SetComponentsFunction.setComponent(FusionDataComponents.PAGE_TIER, 1))
                            .apply(SetComponentsFunction.setComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
                            .setWeight(18)
                    )
                    .add(
                        LootItem.lootTableItem(FusionItems.FUSION_TATTERED_PAGE)
                            .apply(SetComponentsFunction.setComponent(FusionDataComponents.PAGE_TIER, 2))
                            .apply(SetComponentsFunction.setComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
                            .setWeight(8)
                    )
                    .add(EmptyLootItem.emptyItem().setWeight(46));
                tableBuilder.withPool(pool);
            }
        });

        // Librarian Villager trades for Common & Rare Tattered Fusion Pages
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof Villager villager) {
                var vData = villager.getVillagerData();
                if (vData.profession().is(VillagerProfession.LIBRARIAN)) {
                    MerchantOffers offers = villager.getOffers();
                    int level = vData.level();
                    if (level >= 2 && !hasPageOffer(offers, 0)) {
                        offers.add(new MerchantOffer(
                            new ItemCost(Items.EMERALD, 6),
                            Optional.of(new ItemCost(Items.PAPER, 1)),
                            FusionPageItem.createStack(0, 1),
                            8,
                            5,
                            0.05F
                        ));
                    }
                    if (level >= 4 && !hasPageOffer(offers, 1)) {
                        offers.add(new MerchantOffer(
                            new ItemCost(Items.EMERALD, 16),
                            Optional.of(new ItemCost(Items.AMETHYST_SHARD, 2)),
                            FusionPageItem.createStack(1, 1),
                            4,
                            12,
                            0.05F
                        ));
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean hasPageOffer(MerchantOffers offers, int tier) {
        for (MerchantOffer offer : offers) {
            ItemStack res = offer.getResult();
            if (res.is(FusionItems.FUSION_TATTERED_PAGE)
                && res.getOrDefault(FusionDataComponents.PAGE_TIER, 0) == tier) {
                return true;
            }
        }
        return false;
    }
}
