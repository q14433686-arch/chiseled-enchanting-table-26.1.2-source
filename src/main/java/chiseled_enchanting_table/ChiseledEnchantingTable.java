package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModBootstrapConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.EnchantCostSync;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.LootRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChiseledEnchantingTable implements ModInitializer {
    public static final String MOD_ID = "chiseled_enchanting_table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Chiseling the enchant system!");

        int bookStackSize = ModBootstrapConfig.load().enchantedBookStackSize();
        if (bookStackSize > 1) {
            DefaultItemComponentEvents.MODIFY.register(context ->
                context.modify(Items.ENCHANTED_BOOK, builder ->
                    builder.set(DataComponents.MAX_STACK_SIZE, bookStackSize)
                )
            );
        }

        ChiseledEnchantingTableData.init();
        chiseled_enchanting_table.config.ModConfigNetworking.init();
        EnchantCostSync.init();
        chiseled_enchanting_table.mechanics.resonance.WorldRegeneration.init();
        BlockRegistry.init();
        EntityRegistry.init();
        ScreenHandlerRegistry.init();
        StructureProcessorRegistry.init();
        LootRegistry.init();

        // Restrict minecraft:infinity strictly to Exploration loot, Vanilla Enchanting Table random rolls,
        // and Master Tome copying/inscription — strip/replace any Villager / Wandering Trader Infinity offer.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof AbstractVillager abstractVillager) {
                sanitizeVillagerInfinityOffers(world, abstractVillager.getOffers());
                if (abstractVillager instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
                    chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                        .ensureLibrarianDepletedTomeOffers(world, villager);
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static void sanitizeVillagerInfinityOffers(net.minecraft.world.level.Level world, MerchantOffers offers) {
        if (offers == null || offers.isEmpty()) {
            return;
        }
        var enchRegistry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var powerHolderOpt = enchRegistry.get(Enchantments.POWER);
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            if (!chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(result)) {
                continue;
            }
            ItemStack replacement = result.copy();
            EnchantmentHelper.updateEnchantments(replacement, mutable -> {
                mutable.removeIf(h -> h.unwrapKey()
                    .map(ResourceKey::identifier)
                    .filter(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.INFINITY_ENCHANTMENT_ID::equals)
                    .isPresent());
                if (mutable.keySet().isEmpty() && powerHolderOpt.isPresent()) {
                    mutable.set(powerHolderOpt.get(), 1);
                }
            });
            offers.set(i, new MerchantOffer(
                offer.getItemCostA(),
                offer.getItemCostB(),
                replacement,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()
            ));
        }
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
