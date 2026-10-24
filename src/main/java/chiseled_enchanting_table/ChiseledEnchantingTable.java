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
        chiseled_enchanting_table.mechanics.resonance.TomeLockGuards.init();
        chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects.init();
        BlockRegistry.init();
        EntityRegistry.init();
        ScreenHandlerRegistry.init();
        StructureProcessorRegistry.init();
        LootRegistry.init();
        chiseled_enchanting_table.mechanics.structure.BookshelfBookFiller.verifyVanillaKeys();

        // Restrict minecraft:infinity strictly to Exploration loot, Vanilla Enchanting Table random rolls,
        // and Master Tome copying/inscription — strip/replace any Villager / Wandering Trader Infinity offer.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof AbstractVillager abstractVillager) {
                // Audit: this is a destructive global rewrite of trade offers (including
                // other mods' intentional Infinity trades), so the server owner can turn
                // it off via general.restrict_villager_infinity_books=false. The additive
                // librarian depleted-tome offers below are unaffected.
                if (chiseled_enchanting_table.config.ModRuntimeConfig.effective().restrictVillagerInfinityBooks()) {
                    sanitizeVillagerInfinityOffers(world, abstractVillager.getOffers());
                }
                if (abstractVillager instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
                    chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                        .ensureLibrarianDepletedTomeOffers(world, villager);
                }
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Strips Infinity enchanted-book offers (replaced by Power I when that was the only
     * enchantment). Called from the use-entity callback and from
     * {@code VillagerInfinitySanitizeMixin} on every server-side {@code getOffers()},
     * so offers created or opened without a right-click (wandering traders, other
     * mods opening trade screens programmatically) are covered too. The
     * {@code restrict_villager_infinity_books} gate lives here so every callsite is
     * covered by it; the rewrite is idempotent, so repeated calls are harmless.
     */
    public static void sanitizeVillagerInfinityOffers(net.minecraft.world.level.Level world, MerchantOffers offers) {
        if (offers == null || offers.isEmpty()
            || !chiseled_enchanting_table.config.ModRuntimeConfig.effective().restrictVillagerInfinityBooks()) {
            return;
        }
        var enchRegistry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var powerHolderOpt = enchRegistry.get(Enchantments.POWER);
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            // Restrict the book economy, not third-party enchanted equipment or arbitrary rewards.
            if (!result.is(Items.ENCHANTED_BOOK)
                || !chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(result)) {
                continue;
            }
            // The offer owns this stack. Mutating only its result preserves uses, discounts,
            // demand, special price and out-of-stock state (reconstructing an offer loses them).
            EnchantmentHelper.updateEnchantments(result, mutable -> {
                mutable.removeIf(h -> h.unwrapKey()
                    .map(ResourceKey::identifier)
                    .filter(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.INFINITY_ENCHANTMENT_ID::equals)
                    .isPresent());
                if (mutable.keySet().isEmpty() && powerHolderOpt.isPresent()) {
                    mutable.set(powerHolderOpt.get(), 1);
                }
            });

        }
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
