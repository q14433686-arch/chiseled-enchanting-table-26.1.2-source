package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.EnchantCostSync;
import chiseled_enchanting_table.menu.PayloadLimits;
import chiseled_enchanting_table.registry.BlockEntityRegistry;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.LootRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChiseledEnchantingTable implements ModInitializer {
    public static final String MOD_ID = "chiseled_enchanting_table";
    /** Fabric metadata id differs from the resource namespace; also identifies the bundled pack. */
    public static final String FABRIC_MOD_ID = "chiseled-enchanting-table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile int bootEnchantedBookStackSize = 1;

    /** Returns the {@code Items.ENCHANTED_BOOK} max stack size applied at JVM boot. */
    public static int bootEnchantedBookStackSize() {
        return bootEnchantedBookStackSize;
    }

    /**
     * Deterministic fair emerald price for an enchanted-book offer whose sole wear-immune
     * enchantment (e.g. {@code minecraft:infinity}) was sanitized into {@code Power I}
     * (Audit BAL-05): caps high treasure/L1 book prices down into vanilla L1 non-treasure range
     * ({@code 5..8} emeralds) while never raising an already cheaper custom offer.
     */
    public static int repricedPowerOneEmeralds(int originalEmeraldCount) {
        if (originalEmeraldCount <= 8) {
            return Math.max(1, originalEmeraldCount);
        }
        return 5 + (Math.max(0, originalEmeraldCount) % 4);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Chiseling the enchant system!");

        int bookStackSize = Math.max(
            1,
            Math.min(PayloadLimits.MAX_ENCHANTED_BOOK_STACK, ModRuntimeConfig.get().enchantedBookStackSize())
        );
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
        BlockEntityRegistry.init();
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
     * Power I and repriced to fair Power I market emeralds when that leaves zero enchantments).
     * Called from {@code VillagerInfinitySanitizeMixin} on server-side {@code getOffers()}.
     *
     * <p>Audit MIXIN-07 / BAL-05: pre-scans {@code offers} before touching registries or copying
     * stacks so already-clean villagers incur zero allocations on hot AI/trade ticks, and lowers
     * single-enchantment Power I replacements to fair L1 book emerald &amp; XP bounds while
     * preserving {@code uses}, {@code maxUses}, {@code priceMultiplier}, {@code demand}, and
     * {@code specialPriceDiff}.</p>
     */
    public static void sanitizeVillagerInfinityOffers(net.minecraft.world.level.Level world, MerchantOffers offers) {
        if (offers == null || offers.isEmpty()
            || !ModRuntimeConfig.effective().restrictVillagerInfinityBooks()) {
            return;
        }
        int firstHitIndex = -1;
        for (int i = 0; i < offers.size(); i++) {
            ItemStack candidate = offers.get(i).getResult();
            if (candidate.is(Items.ENCHANTED_BOOK)
                && chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(candidate)) {
                firstHitIndex = i;
                break;
            }
        }
        if (firstHitIndex < 0) {
            return;
        }

        var enchRegistryOpt = world.registryAccess().lookup(Registries.ENCHANTMENT);
        var powerHolderOpt = enchRegistryOpt.flatMap(reg -> reg.get(Enchantments.POWER));
        for (int i = firstHitIndex; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            if (!result.is(Items.ENCHANTED_BOOK)
                || !chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(result)) {
                continue;
            }
            ItemStack sanitizedResult = result.copy();
            AtomicBoolean substitutedPowerOne = new AtomicBoolean(false);
            EnchantmentHelper.updateEnchantments(sanitizedResult, mutable -> {
                mutable.removeIf(chiseled_enchanting_table.mechanics.resonance.TomeWearData::isWearImmuneEnchantment);
                if (mutable.keySet().isEmpty()
                    && powerHolderOpt.isPresent()
                    && !chiseled_enchanting_table.mechanics.resonance.TomeWearData.isWearImmuneEnchantment(powerHolderOpt.get())) {
                    mutable.set(powerHolderOpt.get(), 1);
                    substitutedPowerOne.set(true);
                }
            });
            ItemCost costA = offer.getItemCostA();
            int villagerXp = offer.getXp();
            if (substitutedPowerOne.get()) {
                if (costA.item().value() == Items.EMERALD) {
                    int fairEmeralds = repricedPowerOneEmeralds(costA.count());
                    if (fairEmeralds != costA.count()) {
                        costA = new ItemCost(Items.EMERALD, fairEmeralds);
                    }
                }
                villagerXp = Math.min(villagerXp, 5);
            }
            MerchantOffer replacement = new MerchantOffer(
                costA,
                offer.getItemCostB(),
                sanitizedResult,
                offer.getUses(),
                offer.getMaxUses(),
                villagerXp,
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
