package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A real Silk Touch ore drop is either kept as a block or consumed for one Fortune loot roll. */
public final class SealedOreSkills {
    public static final String SIGNATURE = "raw_ore_geode";
    private static final String SILK = "minecraft:silk_touch", FORTUNE = "minecraft:fortune";
    private static final Set<FusionCarrier> TOOLS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE);
    // Conservative native ores: no block-entity contents and an unambiguous normal output item.
    private static final Map<Block, Item> ORES = Map.ofEntries(
        Map.entry(Blocks.COAL_ORE, Items.COAL), Map.entry(Blocks.DEEPSLATE_COAL_ORE, Items.COAL),
        Map.entry(Blocks.IRON_ORE, Items.RAW_IRON), Map.entry(Blocks.DEEPSLATE_IRON_ORE, Items.RAW_IRON),
        Map.entry(Blocks.COPPER_ORE, Items.RAW_COPPER), Map.entry(Blocks.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER),
        Map.entry(Blocks.GOLD_ORE, Items.RAW_GOLD), Map.entry(Blocks.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD),
        Map.entry(Blocks.NETHER_GOLD_ORE, Items.GOLD_NUGGET),
        Map.entry(Blocks.REDSTONE_ORE, Items.REDSTONE), Map.entry(Blocks.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE),
        Map.entry(Blocks.LAPIS_ORE, Items.LAPIS_LAZULI), Map.entry(Blocks.DEEPSLATE_LAPIS_ORE, Items.LAPIS_LAZULI),
        Map.entry(Blocks.DIAMOND_ORE, Items.DIAMOND), Map.entry(Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND),
        Map.entry(Blocks.EMERALD_ORE, Items.EMERALD), Map.entry(Blocks.DEEPSLATE_EMERALD_ORE, Items.EMERALD),
        Map.entry(Blocks.NETHER_QUARTZ_ORE, Items.QUARTZ));
    private SealedOreSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && SILK.equals(pair.primaryId()) && FORTUNE.equals(pair.secondaryId()) && SIGNATURE.equals(signature);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(TOOLS::contains); }
    static @Nullable Item oreOutput(BlockState state) { return state == null ? null : ORES.get(state.getBlock()); }
    /** A host must actually be able to obtain at least one of the supported ore drops. */
    public static boolean supportsHost(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack))
            && ORES.keySet().stream().anyMatch(block -> stack.isCorrectToolForDrops(block.defaultBlockState()));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && supportsHost(stack);
    }
    private static @Nullable FusedPair selected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return pair;
        return null;
    }
    /** The actual durability/permission path is untouched; only the already-copied loot tool can receive a missing primary. */
    public static ItemStack lootTool(ServerLevel level, ItemStack original) {
        FusedPair pair = selected(original); if (pair == null) return original;
        Holder<Enchantment> silk = FusionStationOperations.lookupEnchantment(level.registryAccess(), SILK).orElse(null);
        if (silk == null || original.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(silk) > 0) return original;
        ItemStack copy = original.copy(); ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(silk, pair.primaryLevel()); EnchantmentHelper.setEnchantments(copy, enchantments.toImmutable());
        return copy;
    }
    /** Mark one exact, newly spawned Silk Touch block drop; never scan existing nearby entities. */
    public static void sealDrop(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                                @Nullable BlockEntity blockEntity, ItemStack lootTool) {
        FusedPair pair = selected(lootTool);
        if (pair == null || player.isCreative() || blockEntity != null || !ORES.containsKey(state.getBlock())) return;
        for (ItemEntity entity : HarvestDropCapture.verifiedBlockItems(level, pos, lootTool)) {
            ItemStack drop = entity.getItem();
            if (drop.getCount() != 1 || !drop.is(state.getBlock().asItem()) || drop.has(FusionDataComponents.SEALED_ORE_FORTUNE)) continue;
            if (!HarvestDropCapture.claimTransform(level, pos, entity)) continue;
            ItemStack marked = drop.copy(); marked.set(FusionDataComponents.SEALED_ORE_FORTUNE, pair.secondaryLevel());
            entity.setItem(marked);
            return; // one choice per actually harvested block
        }
    }
    public static boolean hasOpenableSample(ServerPlayer player) {
        ItemStack sample = player.getOffhandItem(); Integer level = sample.get(FusionDataComponents.SEALED_ORE_FORTUNE);
        FusedPair pair = selected(player.getMainHandItem());
        return !player.isCreative() && level != null && pair != null && pair.secondaryLevel() == level
            && ORES.containsKey(Block.byItem(sample.getItem()));
    }
    /** Atomic one-slot exchange. Unsupported/ambiguous loot permanently falls back to the intact ore. */
    public static void openSample(ServerPlayer player) {
        if (!hasOpenableSample(player) || !player.isAlive() || player.isSpectator()) return;
        ItemStack sample = player.getOffhandItem();
        if (sample.getCount() != 1) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.sealed_ore_one"), true); return;
        }
        ServerLevel level = player.level(); Block block = Block.byItem(sample.getItem()); Item expected = ORES.get(block);
        FusedPair pair = selected(player.getMainHandItem());
        Holder<Enchantment> silk = FusionStationOperations.lookupEnchantment(level.registryAccess(), SILK).orElse(null);
        Holder<Enchantment> fortune = FusionStationOperations.lookupEnchantment(level.registryAccess(), FORTUNE).orElse(null);
        if (pair == null || expected == null || silk == null || fortune == null) return;
        ItemStack held = player.getMainHandItem(), heldBefore = held.copy(), sampleBefore = sample.copy();
        Vec3 position = player.position(); ItemStack tool = held.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.removeIf(holder -> holder.equals(silk)); enchantments.set(fortune, pair.secondaryLevel());
        EnchantmentHelper.setEnchantments(tool, enchantments.toImmutable());
        List<ItemStack> drops;
        try {
            // Run the current real block loot table once, at the player's current position, using a copy of their actual tool.
            drops = Block.getDrops(block.defaultBlockState(), level, player.blockPosition(), null, player, tool);
        } catch (RuntimeException exception) {
            EnchantmentFusion.LOGGER.warn("Could not resolve sealed ore loot; the original item will not be consumed", exception);
            if (unchanged(player, level, position, held, heldBefore, sample, sampleBefore)) discardChoice(player, sample);
            return;
        }
        // Loot callbacks can run arbitrary mod code. Never overwrite an item that moved or changed during the roll.
        if (!unchanged(player, level, position, held, heldBefore, sample, sampleBefore)) return;
        int count = drops == null ? 0 : drops.size();
        ItemStack single = count == 1 && drops.getFirst() != null ? drops.getFirst() : ItemStack.EMPTY;
        if (!SealedOreChoice.exchangeable(sample.getCount(), count, single.getCount(), single.isEmpty() ? 0 : single.getMaxStackSize(),
            !single.isEmpty() && single.is(expected), !single.isEmpty() && single.isItemEnabled(level.enabledFeatures()))) {
            discardChoice(player, sample); return;
        }
        // If a data pack copies tool components, a returned item must not itself keep a new free choice.
        ItemStack result = single.copy(); result.remove(FusionDataComponents.SEALED_ORE_FORTUNE);
        player.setItemInHand(InteractionHand.OFF_HAND, result); // one input disappears as one output appears
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.sealed_ore_opened"), true);
    }
    private static boolean unchanged(ServerPlayer player, ServerLevel level, Vec3 position, ItemStack held, ItemStack heldBefore,
                                     ItemStack sample, ItemStack sampleBefore) {
        return player.level() == level && player.isAlive() && !player.isSpectator() && !player.isCreative()
            && player.position().equals(position) && player.getMainHandItem() == held && ItemStack.matches(held, heldBefore)
            && player.getOffhandItem() == sample && ItemStack.matches(sample, sampleBefore);
    }
    private static void discardChoice(ServerPlayer player, ItemStack sample) {
        sample.remove(FusionDataComponents.SEALED_ORE_FORTUNE); // intact ore remains, but no repeated rerolls
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.sealed_ore_unsupported"), true);
    }
}
