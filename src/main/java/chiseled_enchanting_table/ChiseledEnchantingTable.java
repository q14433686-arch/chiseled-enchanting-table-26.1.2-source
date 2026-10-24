package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModRuntimeConfig;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
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
    private static volatile int bootEnchantedBookStackSize = 1;

    /** Returns the {@code Items.ENCHANTED_BOOK} max stack size applied at JVM boot. */
    public static int bootEnchantedBookStackSize() {
        return bootEnchantedBookStackSize;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Chiseling the enchant system!");

        int bookStackSize = Math.max(1, Math.min(64, ModRuntimeConfig.get().enchantedBookStackSize()));
        bootEnchantedBookStackSize = bookStackSize;
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

        // Additive librarian Depleted Tome trade injection on right-click; wear-immune book offer
        // sanitization runs universally inside VillagerInfinitySanitizeMixin on AbstractVillager#getOffers.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof Villager villager) {
                chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .ensureLibrarianDepletedTomeOffers(world, villager);
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Strips {@code #chiseled_enchanting_table:wear_immune} enchanted-book offers (replaced by
     * Power I when that leaves zero enchantments). Called from
     * {@code VillagerInfinitySanitizeMixin} on every server-side {@code getOffers()},
     * covering right-clicks, wandering traders, and programmatic trade screens alike.
     * Stripping the entire {@code #wear_immune} tag guarantees idempotence even when datapacks
     * add custom wear-immune enchantments.
     */
    public static void sanitizeVillagerInfinityOffers(net.minecraft.world.level.Level world, MerchantOffers offers) {
        if (offers == null || offers.isEmpty()
            || !ModRuntimeConfig.effective().restrictVillagerInfinityBooks()) {
            return;
        }
        java.util.Optional<net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment>> enchRegistryOpt =
            world.registryAccess().lookup(Registries.ENCHANTMENT);
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            // Restrict the book economy, not third-party enchanted equipment or arbitrary rewards.
            if (!result.is(Items.ENCHANTED_BOOK)
                || !chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(result)) {
                continue;
            }
            var powerHolderOpt = enchRegistryOpt.flatMap(reg -> reg.get(Enchantments.POWER));
            // Clone the result stack and replace the MerchantOffer instead of mutating offer.getResult()
            // in-place (API-03), while preserving uses, demand, XP, price multiplier, and specialPriceDiff.
            ItemStack sanitizedResult = result.copy();
            EnchantmentHelper.updateEnchantments(sanitizedResult, mutable -> {
                mutable.removeIf(chiseled_enchanting_table.mechanics.resonance.TomeWearData::isWearImmuneEnchantment);
                if (mutable.keySet().isEmpty()
                    && powerHolderOpt.isPresent()
                    && !chiseled_enchanting_table.mechanics.resonance.TomeWearData.isWearImmuneEnchantment(powerHolderOpt.get())) {
                    mutable.set(powerHolderOpt.get(), 1);
                }
            });
            MerchantOffer replacement = new MerchantOffer(
                offer.getItemCostA(),
                offer.getItemCostB(),
                sanitizedResult,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()
            );
            replacement.setSpecialPriceDiff(offer.getSpecialPriceDiff());
            offers.set(i, replacement);
        }
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
