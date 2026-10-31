package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Fortune-first: defer part of a real bonus, then spend identical units on a selected ore. */
public final class FortuneBankSkills {
    public static final String SIGNATURE = "smelt_fortune";
    private static final String FORTUNE = "minecraft:fortune", SILK = "minecraft:silk_touch";
    private static final int MAX_SESSIONS = 128;
    private static final class Session {
        final UUID owner; final ItemStack tool; final FusedPair pair; final ResourceKey<Level> dimension;
        boolean banking; @Nullable Block target;
        Session(ServerPlayer player, ItemStack tool, FusedPair pair) {
            this.owner = player.getUUID(); this.tool = tool; this.pair = pair; this.dimension = player.level().dimension();
        }
    }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private FortuneBankSkills() {}
    public static void init() { FortuneBankLedger.init(); }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && FORTUNE.equals(pair.primaryId()) && SILK.equals(pair.secondaryId()) && SIGNATURE.equals(signature);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return SealedOreSkills.supportsCarrier(roles); }
    public static boolean supportsHost(@Nullable ItemStack stack) { return SealedOreSkills.supportsHost(stack); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && supportsHost(stack);
    }
    private static @Nullable FusedPair selected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return pair;
        return null;
    }
    public static boolean hasEquipped(ServerPlayer player) { return selected(player.getMainHandItem()) != null && !player.isCreative(); }
    /** Read-only count for a concrete item and its complete component set. */
    public static int storedAmount(ServerPlayer player, ItemStack item) {
        return player == null || item == null || item.isEmpty() ? 0 : FortuneBankLedger.amount(player, item);
    }
    private static @Nullable Session current(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return null;
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || !player.level().dimension().equals(session.dimension)
            || player.getMainHandItem() != session.tool || !session.pair.equals(selected(session.tool))) {
            SESSIONS.remove(player.getUUID(), session); return null;
        }
        return session;
    }
    /** Sneak + activate toggles saving; standing + activate selects the looked-at ore. */
    public static void activate(ServerPlayer player) {
        if (!hasEquipped(player) || !player.isAlive() || player.isSpectator()) return;
        Session session = current(player);
        if (session == null) {
            if (SESSIONS.size() >= MAX_SESSIONS) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_busy"), true); return;
            }
            session = new Session(player, player.getMainHandItem(), selected(player.getMainHandItem()));
            SESSIONS.put(player.getUUID(), session);
        }
        if (player.isShiftKeyDown()) {
            session.banking = !session.banking;
            player.sendSystemMessage(Component.translatable(session.banking ? "message.enchantment_fusion.fortune_bank_on"
                : "message.enchantment_fusion.fortune_bank_off"), true);
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        ServerLevel level = player.level();
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
            || !level.hasChunkAt(blockHit.getBlockPos()) || SealedOreSkills.oreOutput(level.getBlockState(blockHit.getBlockPos())) == null) {
            // Emergency return does not require a loot table: it spends stored real units into the player's offhand.
            FortuneBankLedger.Withdrawal withdrawal = FortuneBankLedger.withdrawToOffhand(player);
            if (withdrawal == null) player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_no_target"), true);
            else {
                session.target = null;
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_paid",
                    withdrawal.count(), withdrawal.item().getHoverName(), withdrawal.remaining()), true);
            }
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!level.mayInteract(player, pos)) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_no_target"), true); return;
        }
        session.target = level.getBlockState(pos).getBlock();
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_selected", session.target.getName()), true);
    }
    /** Preserve the actual data-pack primary for mining tools that don't natively project Fortune. */
    public static ItemStack lootTool(ServerLevel level, ItemStack original) {
        FusedPair pair = selected(original); if (pair == null) return original;
        Holder<Enchantment> fortune = FusionStationOperations.lookupEnchantment(level.registryAccess(), FORTUNE).orElse(null);
        if (fortune == null || original.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fortune) > 0) return original;
        ItemStack copy = original.copy(); ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(fortune, pair.primaryLevel()); EnchantmentHelper.setEnchantments(copy, enchantments.toImmutable());
        return copy;
    }
    /** Called before copying an ApplyBonus input; all authority comes from the server session and actual tool. */
    public static boolean observes(ServerPlayer player, ItemStack lootTool) {
        Session session = current(player);
        if (session == null || ExtraBlockHarvest.isActive()
            || (player.isShiftKeyDown() ? !session.banking : session.target == null)) return false;
        FusedPair pair = selected(lootTool);
        return pair != null && pair.equals(session.pair);
    }
    /** Process only the exact unchanged item entity that contains the result of the real Fortune call. */
    public static void afterDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                                    @Nullable BlockEntity blockEntity, ItemStack lootTool) {
        Session session = current(player); Item expected = SealedOreSkills.oreOutput(state);
        if (session == null || blockEntity != null || expected == null || !observes(player, lootTool) || player.level() != level) return;
        boolean deposit = player.isShiftKeyDown();
        if (!deposit && session.target != state.getBlock()) return;
        List<HarvestDropCapture.FortuneOutcome> outcomes = HarvestDropCapture.fortuneOutcomes(level, pos, lootTool);
        List<ItemEntity> drops = HarvestDropCapture.items(level, pos, AABB.ofSize(Vec3.atCenterOf(pos), 6, 6, 6));
        ItemEntity chosen = null; HarvestDropCapture.FortuneOutcome matched = null;
        for (var outcome : outcomes) {
            if (!outcome.snapshot().is(expected)) continue;
            if (outcome.ambiguous()) { unverified(player, deposit); return; }
            for (ItemEntity entity : drops) {
                if (entity.getItem() != outcome.output() || !ItemStack.matches(entity.getItem(), outcome.snapshot())) continue;
                if (chosen != null) { unverified(player, deposit); return; } // multiple real candidates are left untouched
                chosen = entity; matched = outcome;
            }
        }
        if (chosen == null || matched == null) { unverified(player, deposit); return; }
        ItemStack stack = chosen.getItem();
        int amount;
        if (deposit) {
            int cap = FusionConfigStore.signatureInt(SIGNATURE, "bank_cap_per_item", 64, 1, 256);
            int space = FortuneBankLedger.availableCapacity(player, stack, cap);
            amount = FortuneBankMath.deposit(matched.before(), matched.after(), space);
            if (space == 0 && matched.after() > matched.before())
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_full"), true);
        } else {
            int perBreak = FusionConfigStore.signatureInt(SIGNATURE, "payout_per_break", 3, 1, 64);
            int stored = FortuneBankLedger.amount(player, stack);
            if (stored == 0) {
                session.target = null;
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_empty", stack.getHoverName()), true);
                return;
            }
            amount = FortuneBankMath.payout(stack.getCount(), stack.getMaxStackSize(), stored, perBreak);
        }
        if (amount <= 0 || !HarvestDropCapture.claimTransform(level, pos, chosen)) return;
        boolean done = deposit ? FortuneBankLedger.deposit(player, chosen, stack, amount,
            FusionConfigStore.signatureInt(SIGNATURE, "bank_cap_per_item", 64, 1, 256))
            : FortuneBankLedger.payout(player, chosen, stack, amount);
        if (!done) { HarvestDropCapture.releaseTransform(level, pos, chosen); return; }
        if (!deposit) session.target = null;
        CompendiumDiscoveryTracker.recordDiscovery(player, FORTUNE, SILK);
        player.sendSystemMessage(Component.translatable(deposit ? "message.enchantment_fusion.fortune_bank_deposited"
            : "message.enchantment_fusion.fortune_bank_paid", amount, stack.getHoverName(), FortuneBankLedger.amount(player, stack)), true);
    }
    private static void unverified(ServerPlayer player, boolean deposit) {
        if (!deposit) player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_bank_unverified"), true);
    }
    public static void tickServer(MinecraftServer server) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.owner);
            if (player == null) SESSIONS.remove(session.owner, session); else current(player);
        }
    }
    public static void onDisconnect(UUID player) { SESSIONS.remove(player); }
    public static void clear() { SESSIONS.clear(); }
}
