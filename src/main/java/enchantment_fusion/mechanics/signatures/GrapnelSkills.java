package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 40.1 adapters: direct melee target marks and strictly proven block-drop marks. */
public final class GrapnelSkills {
    public static final String SIGNATURE = "spirit_grapnel", LURE = "minecraft:lure";
    public static final String EFFICIENCY = "minecraft:efficiency", SILK = "minecraft:silk_touch", FORTUNE = "minecraft:fortune";
    private static final Set<FusionCarrier> TOOLS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE);
    private static final Set<String> MINING_PRIMARIES = Set.of(EFFICIENCY, SILK, FORTUNE);
    private static final int MAX_OWNERS = 256, MAX_DROPS = 64;
    private static final long MARK_TICKS = 160;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private sealed interface Mark permits TargetMark, DropsMark {
        Gear gear(); ResourceKey<Level> dimension(); long until();
    }
    private record TargetMark(Gear gear, ResourceKey<Level> dimension, long until, UUID target) implements Mark {}
    private record Drop(ItemEntity entity, ItemStack stack, ItemStack snapshot) {}
    private record DropsMark(Gear gear, ResourceKey<Level> dimension, long until, BlockPos origin, List<Drop> drops) implements Mark {}
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private GrapnelSkills() {}
    public static void init() {
        ConfirmedMelee.register(GrapnelSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("grapnel_attack", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && LURE.equals(pair.secondaryId());
    }
    public static boolean miningFirst(@Nullable FusedPair pair) {
        return pair != null && MINING_PRIMARIES.contains(pair.primaryId()) && LURE.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (attackFirst(pair) || miningFirst(pair))
            && SignatureCatalog.resolve(pair).map(entry -> SIGNATURE.equals(entry.id()) && "*".equals(entry.primaryId())).orElse(false);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (roles == null) return false;
        if (attackFirst(pair)) return supportsCarrier(roles);
        return miningFirst(pair) && roles.stream().anyMatch(TOOLS::contains);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean mining) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((mining ? miningFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair())) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean canMark(ServerPlayer owner) { return MARKS.size() < MAX_OWNERS || MARKS.containsKey(owner.getUUID()); }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); Gear gear = selected(hit.weapon(), false);
        if (gear == null || !ready(owner) || hit.slot() != EquipmentSlot.MAINHAND || owner.getMainHandItem() != hit.weapon()
            || !hit.eligibleBefore() || !hit.target().isAlive() || !MeleeAttackThemes.matchesTarget(gear.pair().primaryId(), hit.target())
            || (MeleeAttackThemes.SHARP.equals(gear.pair().primaryId()) && !hit.full()) || !owns(owner, gear) || !canMark(owner)) return;
        MARKS.put(owner.getUUID(), new TargetMark(gear, owner.level().dimension(), owner.level().getGameTime() + MARK_TICKS, hit.target().getUUID()));
        owner.level().sendParticles(ParticleTypes.FISHING, hit.target().getX(), hit.target().getY(0.6D), hit.target().getZ(),
            8, 0.2D, 0.2D, 0.2D, 0.02D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_marked"), true);
    }
    /** Project a missing Silk/Fortune primary only onto the copy used by the real block loot call. */
    public static ItemStack lootTool(ServerLevel level, ItemStack tool) {
        Gear gear = selected(tool, true);
        if (gear == null || EFFICIENCY.equals(gear.pair().primaryId())) return tool;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return tool;
        ItemStack copy = tool.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(primary, gear.pair().primaryLevel()); EnchantmentHelper.setEnchantments(copy, enchantments.toImmutable());
        return copy;
    }
    /** Called inside the matching real playerDestroy after the strict loot-to-entity observation. */
    public static void afterMined(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, ItemStack lootTool) {
        if (!ready(player) || player.level() != level || ExtraBlockHarvest.isActive() || MatrixEffectExecutor.isExecuting()
            || !level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.mayInteract(player, pos)
            || level.getServer().isUnderSpawnProtection(level, pos, player) || state.isAir() || !canMark(player)) return;
        Gear gear = selected(player.getMainHandItem(), true), loot = selected(lootTool, true);
        if (gear == null || loot == null || !gear.pair().equals(loot.pair()) || !owns(player, gear)
            || !HarvestDropCapture.isOriginalMainHand(level, player, pos, gear.stack())) return;
        List<ItemEntity> verified = HarvestDropCapture.verifiedBlockItems(level, pos, lootTool);
        if (verified.isEmpty() || verified.size() > MAX_DROPS) return;
        List<Drop> drops = new ArrayList<>(verified.size());
        for (ItemEntity item : verified) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty()) drops.add(new Drop(item, stack, stack.copy()));
        }
        if (drops.isEmpty()) return;
        MARKS.put(player.getUUID(), new DropsMark(gear, level.dimension(), level.getGameTime() + MARK_TICKS,
            pos.immutable(), List.copyOf(drops)));
        level.sendParticles(ParticleTypes.FISHING, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_drops_marked"), true);
    }
    private static boolean valid(ServerPlayer owner, Mark mark) {
        return ready(owner) && owner.level().dimension().equals(mark.dimension()) && owner.level().getGameTime() < mark.until()
            && owns(owner, mark.gear());
    }
    private static boolean unchangedDrop(ServerPlayer owner, Drop drop) {
        ItemEntity entity = drop.entity();
        if (!entity.isAlive() || entity.level() != owner.level() || owner.level().getEntity(entity.getUUID()) != entity
            || entity.getItem() != drop.stack() || !ItemStack.matches(drop.stack(), drop.snapshot())) return false;
        DroppedItemOwnerAccess metadata = (DroppedItemOwnerAccess) (Object) entity;
        var thrower = metadata.ef$throwerReference();
        return HarvestDropCapture.ownerCompatible(owner.getUUID(), thrower == null ? null : thrower.getUUID(), metadata.ef$pickupTarget());
    }
    private static boolean reachableDrop(ServerPlayer owner, DropsMark mark, Drop drop) {
        if (!unchangedDrop(owner, drop) || drop.entity().hasPickUpDelay() || !owner.level().hasChunkAt(drop.entity().blockPosition())
            || !owner.level().mayInteract(owner, mark.origin()) || !owner.level().mayInteract(owner, drop.entity().blockPosition())) return false;
        Vec3 from = owner.getEyePosition(), to = drop.entity().getBoundingBox().getCenter();
        return from.distanceToSqr(to) <= 12.0D * 12.0D && VirtualCombatSight.clear(owner.level(), owner, from, to);
    }
    public static boolean hasEquipped(ServerPlayer player) {
        return selected(player.getMainHandItem(), false) != null || selected(player.getMainHandItem(), true) != null;
    }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Mark mark = MARKS.get(owner.getUUID());
        if (mark == null || !valid(owner, mark)) {
            MARKS.remove(owner.getUUID()); owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_need"), true); return false;
        }
        if (owner.getMainHandItem() != mark.gear().stack()) return false;
        return mark instanceof TargetMark target ? recallTarget(owner, target) : recallDrops(owner, (DropsMark) mark);
    }
    private static boolean recallTarget(ServerPlayer owner, TargetMark mark) {
        ServerLevel level = owner.level();
        if (!(level.getEntity(mark.target()) instanceof LivingEntity target) || !MeleeAttackThemes.eligible(owner, target, mark.gear().pair().primaryId())) return false;
        Vec3 from = owner.getEyePosition(), to = target.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > 12.0D * 12.0D || !VirtualCombatSight.clear(level, owner, from, to)) return false;
        double awayX = target.getX() - owner.getX(), awayZ = target.getZ() - owner.getZ();
        if (awayX * awayX + awayZ * awayZ < 0.01D) return false;
        Vec3 before = target.getDeltaMovement();
        if (!Double.isFinite(before.x) || !Double.isFinite(before.y) || !Double.isFinite(before.z)) return false;
        double force = Math.min(1.25D, 0.45D + 0.10D * Math.clamp(mark.gear().pair().secondaryLevel(), 1, 8));
        if (!MARKS.remove(owner.getUUID(), mark)) return false; // do not reenter while another mod observes knockback
        target.knockback(force, awayX, awayZ); // ordinary knockback resistance and movement collisions still apply
        if (target.getDeltaMovement().distanceToSqr(before) < 1.0E-8D) { MARKS.putIfAbsent(owner.getUUID(), mark); return false; }
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        level.sendParticles(ParticleTypes.FISHING, target.getX(), target.getY(0.6D), target.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.gear().pair().primaryId(), mark.gear().pair().secondaryId());
        return true;
    }
    private static boolean recallDrops(ServerPlayer owner, DropsMark mark) {
        if (mark.drops().stream().noneMatch(drop -> reachableDrop(owner, mark, drop)) || !MARKS.remove(owner.getUUID(), mark)) return false;
        int recovered = 0;
        for (Drop drop : mark.drops()) {
            if (!ready(owner) || !owner.level().dimension().equals(mark.dimension()) || owner.getMainHandItem() != mark.gear().stack()
                || !reachableDrop(owner, mark, drop)) continue;
            int before = drop.stack().getCount();
            drop.entity().playerTouch(owner); // vanilla pickup delay, restrictions, inventory insertion and pickup hooks
            int after = drop.entity().isAlive() ? drop.entity().getItem().getCount() : 0;
            recovered += Math.max(0, before - after);
        }
        if (recovered <= 0) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_no_room"), true); return false;
        }
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_recovered", recovered), true);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.gear().pair().primaryId(), mark.gear().pair().secondaryId());
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(MARKS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Mark mark = entry.getValue();
            boolean noTarget = owner != null && (mark instanceof TargetMark target ? owner.level().getEntity(target.target()) == null
                : ((DropsMark) mark).drops().stream().noneMatch(drop -> unchangedDrop(owner, drop)));
            if (owner == null || !valid(owner, mark) || noTarget) MARKS.remove(entry.getKey(), mark);
        }
    }
    public static void clearPlayer(UUID id) {
        MARKS.remove(id);
        MARKS.entrySet().removeIf(entry -> entry.getValue() instanceof TargetMark target && target.target().equals(id));
    }
    public static void clearServer() { MARKS.clear(); }
}
