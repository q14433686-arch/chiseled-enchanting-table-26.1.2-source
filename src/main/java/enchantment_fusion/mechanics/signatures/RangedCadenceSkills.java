package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A hit-earned bow draw and a player-armed, real-ammunition crossbow shot. */
public final class RangedCadenceSkills {
    public static final String POWER = "minecraft:power", QUICK = "minecraft:quick_charge";
    public static final String BOW = "overdraw_snipe", CROSSBOW = "swift_draw_burst";
    private static final int MAX_ITEMS_PER_PLAYER = 4, MAX_TRACKED_ARROWS = 4096;
    private static final class Attachments {
        static final AttachmentType<Integer> DRAW_VIEW = AttachmentRegistry.create(
            EnchantmentFusion.id("ranged_cadence_draw_view"), builder -> builder
                .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    }
    private record ArrowFlight(UUID owner, ResourceKey<Level> dimension, ItemStack bow, FusedPair pair, long expires) {}
    private record DrawCredit(ItemStack bow, FusedPair pair, ResourceKey<Level> dimension, int ticks, int generation, long expires) {}
    private record ActiveDraw(ItemStack bow, InteractionHand hand, int ticks, int generation) {}
    private static final class Loads {
        final ItemStack crossbow;
        final FusedPair pair;
        final ResourceKey<Level> dimension;
        int count;
        long expires;
        boolean armed;
        Loads(ItemStack crossbow, FusedPair pair, ResourceKey<Level> dimension) {
            this.crossbow = crossbow; this.pair = pair; this.dimension = dimension;
        }
    }
    private static final class ShotFrame {
        final @Nullable ShotFrame previous;
        final @Nullable ServerPlayer owner;
        final ItemStack weapon;
        @Nullable Loads loads;
        @Nullable AbstractArrow arrow;
        ShotFrame(@Nullable ShotFrame previous, @Nullable ServerPlayer owner, ItemStack weapon) {
            this.previous = previous; this.owner = owner; this.weapon = weapon;
        }
    }
    private static final Map<AbstractArrow, ArrowFlight> BOW_FLIGHTS = new WeakHashMap<>();
    private static final Map<UUID, List<DrawCredit>> DRAW_CREDITS = new HashMap<>();
    private static final Map<UUID, ActiveDraw> ACTIVE_DRAWS = new HashMap<>();
    private static final Map<UUID, Integer> DRAW_GENERATIONS = new HashMap<>();
    private static final Map<UUID, List<Loads>> CROSSBOW_LOADS = new HashMap<>();
    private static final ThreadLocal<ShotFrame> SHOT = new ThreadLocal<>();
    private RangedCadenceSkills() {}

    public static void init() { java.util.Objects.requireNonNull(Attachments.DRAW_VIEW); }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        if (pair == null) return false;
        return POWER.equals(pair.primaryId()) && QUICK.equals(pair.secondaryId()) && BOW.equals(signature)
            || QUICK.equals(pair.primaryId()) && POWER.equals(pair.secondaryId()) && CROSSBOW.equals(signature);
    }
    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> carriers) {
        return pair != null && carriers != null && (POWER.equals(pair.primaryId()) && QUICK.equals(pair.secondaryId())
            && carriers.contains(FusionCarrier.BOW) || QUICK.equals(pair.primaryId()) && POWER.equals(pair.secondaryId())
            && carriers.contains(FusionCarrier.CROSSBOW));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    private static boolean owns(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) return true;
        return false;
    }
    private static boolean same(ItemStack stack, FusedPair pair, ResourceKey<Level> dimension, ServerPlayer player) {
        return !stack.isEmpty() && owns(player, stack) && dimension.equals(player.level().dimension())
            && pair.equals(selected(stack));
    }
    private static boolean held(ServerPlayer player, ItemStack stack) {
        return player.getMainHandItem() == stack || player.getOffhandItem() == stack;
    }
    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }

    /** Called on vanilla's central arrow only, after the bow really released at full draw. */
    public static void trackBow(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack actualBow) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner)
            || !(owner.level() instanceof ServerLevel level) || index != 0 || !arrow.isCritArrow()
            || PhantomVolley.isGhost(arrow) || actualBow == null || !held(owner, actualBow)) return;
        FusedPair pair = selected(actualBow);
        if (pair == null || !POWER.equals(pair.primaryId()) || BOW_FLIGHTS.size() >= MAX_TRACKED_ARROWS) return;
        BOW_FLIGHTS.put(arrow, new ArrowFlight(owner.getUUID(), level.dimension(), actualBow, pair, level.getGameTime() + 1200));
    }
    /** The normal arrow damage call must have succeeded; a miss, wall or cancelled hit earns nothing. */
    public static void finishBowHit(AbstractArrow arrow, Entity target, boolean succeeded) {
        if (!succeeded || !(target instanceof LivingEntity) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || !(arrow.level() instanceof ServerLevel level) || PhantomVolley.isGhost(arrow)) return;
        ArrowFlight flight = BOW_FLIGHTS.remove(arrow);
        if (flight == null || !eligible(owner) || !flight.owner().equals(owner.getUUID())
            || level.getGameTime() >= flight.expires() || !same(flight.bow(), flight.pair(), flight.dimension(), owner)) return;
        int ticks = RangedCadenceMath.drawCredit(flight.pair().secondaryLevel(),
            FusionConfigStore.signatureInt(BOW, "draw_ticks_per_level", 2, 0, 10),
            FusionConfigStore.signatureInt(BOW, "maximum_draw_credit_ticks", 12, 0, 19));
        if (ticks <= 0) return;
        long expiry = level.getGameTime() + FusionConfigStore.signatureInt(BOW, "draw_credit_lifetime_ticks", 400, 20, 1200);
        List<DrawCredit> credits = DRAW_CREDITS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>());
        credits.removeIf(credit -> credit.bow() == flight.bow());
        if (credits.size() >= MAX_ITEMS_PER_PLAYER) credits.removeFirst();
        int generation = DRAW_GENERATIONS.merge(owner.getUUID(), 1, (old, ignored) -> old >= 32767 ? 1 : old + 1);
        credits.add(new DrawCredit(flight.bow(), flight.pair(), flight.dimension(), ticks, generation, expiry));
        updateView(owner);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.draw_credit_ready", ticks), true);
        CompendiumDiscoveryTracker.recordDiscovery(owner, POWER, QUICK);
    }
    /** Runs only after vanilla actually entered an item-use action; credit is spent once on starting to draw. */
    public static int startBowDraw(ServerPlayer owner, InteractionHand hand, ItemStack bow) {
        if (!eligible(owner) || owner.getItemInHand(hand) != bow || selected(bow) == null) return 0;
        List<DrawCredit> credits = DRAW_CREDITS.get(owner.getUUID());
        if (credits == null) return 0;
        long now = owner.level().getGameTime();
        Iterator<DrawCredit> iter = credits.iterator();
        while (iter.hasNext()) {
            DrawCredit credit = iter.next();
            if (credit.bow() != bow) continue;
            iter.remove();
            if (now >= credit.expires() || !same(bow, credit.pair(), credit.dimension(), owner)) break;
            ACTIVE_DRAWS.put(owner.getUUID(), new ActiveDraw(bow, hand, credit.ticks(), credit.generation()));
            updateView(owner);
            return credit.ticks();
        }
        updateView(owner);
        return 0;
    }
    /** Client-side animation/prediction only; the server remains authoritative for the shot. */
    public static int clientDrawView(Player player, InteractionHand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        FusedPair pair = selected(stack);
        if (pair == null || !POWER.equals(pair.primaryId())) return 0;
        Integer view = player.getAttached(Attachments.DRAW_VIEW);
        if (view == null || RangedCadenceMath.viewOffhand(view) != (hand == InteractionHand.OFF_HAND)
            || RangedCadenceMath.viewTicks(view) == 0) return 0;
        return view;
    }
    private static void updateView(ServerPlayer player) {
        ActiveDraw active = ACTIVE_DRAWS.get(player.getUUID());
        int view = 0;
        if (active != null && player.isUsingItem() && player.getUseItem() == active.bow()
            && player.getUsedItemHand() == active.hand()) {
            view = RangedCadenceMath.drawView(active.ticks(), active.hand() == InteractionHand.OFF_HAND, true, active.generation());
        } else {
            List<DrawCredit> credits = DRAW_CREDITS.get(player.getUUID());
            if (credits != null) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack held = player.getItemInHand(hand);
                    for (DrawCredit credit : credits) if (credit.bow() == held) {
                        view = RangedCadenceMath.drawView(credit.ticks(), hand == InteractionHand.OFF_HAND, false, credit.generation());
                        break;
                    }
                    if (view != 0) break;
                }
            }
        }
        Integer existing = player.getAttached(Attachments.DRAW_VIEW);
        if (view == 0 && existing != null) player.removeAttached(Attachments.DRAW_VIEW);
        else if (view != 0 && (existing == null || existing != view)) player.setAttached(Attachments.DRAW_VIEW, view);
    }
    private static boolean hasArrow(ItemStack crossbow) {
        ChargedProjectiles charged = crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        List<ItemStack> projectiles = charged.itemCopies();
        return !projectiles.isEmpty() && projectiles.getFirst().getItem() instanceof ArrowItem;
    }
    private static @Nullable Loads findLoads(ServerPlayer owner, ItemStack stack) {
        List<Loads> loads = CROSSBOW_LOADS.get(owner.getUUID());
        if (loads != null) for (Loads entry : loads) if (entry.crossbow == stack) return entry;
        return null;
    }
    /** Hooked immediately after vanilla successfully loads actual arrow ammunition. */
    public static void loadedCrossbow(LivingEntity user, ItemStack weapon) {
        if (!(user instanceof ServerPlayer owner) || !eligible(owner) || !held(owner, weapon) || !hasArrow(weapon)) return;
        FusedPair pair = selected(weapon);
        if (pair == null || !QUICK.equals(pair.primaryId())) return;
        List<Loads> entries = CROSSBOW_LOADS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>());
        Loads state = findLoads(owner, weapon);
        if (state == null || !state.pair.equals(pair) || !state.dimension.equals(owner.level().dimension())
            || owner.level().getGameTime() >= state.expires) {
            if (state != null) entries.remove(state);
            if (entries.size() >= MAX_ITEMS_PER_PLAYER) entries.removeFirst();
            state = new Loads(weapon, pair, owner.level().dimension()); entries.add(state);
        }
        int cap = FusionConfigStore.signatureInt(CROSSBOW, "load_capacity", 3, 1, 10);
        state.count = RangedCadenceMath.nextLoad(state.count, cap);
        state.armed = false;
        state.expires = owner.level().getGameTime() + FusionConfigStore.signatureInt(CROSSBOW, "load_lifetime_ticks", 400, 20, 1200);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.crossbow_loads", state.count, cap), true);
    }
    public static boolean hasEquippedCrossbow(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            FusedPair pair = selected(player.getItemInHand(hand));
            if (pair != null && QUICK.equals(pair.primaryId())) return true;
        }
        return false;
    }
    /** G arms or disarms a loaded arrow; it does not fire or consume the stored loads. */
    public static boolean activateCrossbow(ServerPlayer owner) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack weapon = owner.getItemInHand(hand);
            FusedPair pair = selected(weapon);
            if (pair == null || !QUICK.equals(pair.primaryId())) continue;
            Loads loads = findLoads(owner, weapon);
            if (!eligible(owner) || loads == null || loads.count <= 0 || owner.level().getGameTime() >= loads.expires
                || !same(weapon, loads.pair, loads.dimension, owner) || !CrossbowItem.isCharged(weapon) || !hasArrow(weapon)) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.crossbow_not_ready"), true);
                return false;
            }
            loads.armed = !loads.armed;
            owner.sendSystemMessage(Component.translatable(loads.armed
                ? "message.enchantment_fusion.crossbow_armed" : "message.enchantment_fusion.crossbow_disarmed", loads.count), true);
            return true;
        }
        return false;
    }
    /** Scope a real crossbow firing call; reserve but don't debit until an arrow enters the world. */
    public static void beginCrossbowShot(LivingEntity shooter, ItemStack weapon) {
        SHOT.set(new ShotFrame(SHOT.get(), shooter instanceof ServerPlayer player ? player : null, weapon));
    }
    public static void configureCrossbowShot(Projectile projectile, LivingEntity shooter, int index) {
        ShotFrame frame = SHOT.get();
        if (frame == null || frame.owner != shooter || !(projectile instanceof AbstractArrow arrow) || index != 0
            || frame.arrow != null || PhantomVolley.isGhost(arrow)) return;
        Loads loads = findLoads(frame.owner, frame.weapon);
        if (loads == null || !loads.armed || loads.count <= 0 || frame.owner.level().getGameTime() >= loads.expires
            || !same(frame.weapon, loads.pair, loads.dimension, frame.owner) || !held(frame.owner, frame.weapon)) return;
        int percent = FusionConfigStore.signatureInt(CROSSBOW, "arrow_bonus_percent_per_stack_and_level", 8, 0, 50);
        arrow.setBaseDamage(((AbstractArrowAccessor) arrow).ef$getBaseDamage()
            * RangedCadenceMath.arrowMultiplier(loads.count, loads.pair.secondaryLevel(), percent));
        frame.loads = loads; frame.arrow = arrow;
    }
    public static void endCrossbowShot() {
        ShotFrame frame = SHOT.get();
        if (frame == null) return;
        if (frame.previous == null) SHOT.remove(); else SHOT.set(frame.previous);
        if (frame.owner == null || frame.loads == null || frame.arrow == null
            || !(frame.arrow.level() instanceof ServerLevel level) || level.getEntity(frame.arrow.getUUID()) != frame.arrow) return;
        List<Loads> entries = CROSSBOW_LOADS.get(frame.owner.getUUID());
        if (entries != null && entries.remove(frame.loads)) {
            CompendiumDiscoveryTracker.recordDiscovery(frame.owner, QUICK, POWER);
            frame.owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.crossbow_released"), true);
        }
    }
    public static void tickPlayer(ServerPlayer player) {
        if (!eligible(player)) { onDisconnect(player); return; }
        long now = player.level().getGameTime(); UUID id = player.getUUID();
        List<DrawCredit> credits = DRAW_CREDITS.get(id);
        if (credits != null) {
            credits.removeIf(c -> now >= c.expires() || !same(c.bow(), c.pair(), c.dimension(), player));
            if (credits.isEmpty()) DRAW_CREDITS.remove(id);
        }
        ActiveDraw active = ACTIVE_DRAWS.get(id);
        if (active != null && (!player.isUsingItem() || player.getUseItem() != active.bow()
            || player.getUsedItemHand() != active.hand())) ACTIVE_DRAWS.remove(id);
        List<Loads> loads = CROSSBOW_LOADS.get(id);
        if (loads != null) {
            loads.removeIf(c -> now >= c.expires || !same(c.crossbow, c.pair, c.dimension, player));
            if (loads.isEmpty()) CROSSBOW_LOADS.remove(id);
        }
        if (now % 40L == 0L) BOW_FLIGHTS.entrySet().removeIf(e -> now >= e.getValue().expires());
        updateView(player);
    }
    public static void onDisconnect(ServerPlayer player) {
        UUID id = player.getUUID(); DRAW_CREDITS.remove(id); ACTIVE_DRAWS.remove(id); DRAW_GENERATIONS.remove(id); CROSSBOW_LOADS.remove(id);
        BOW_FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
        if (player.getAttached(Attachments.DRAW_VIEW) != null) player.removeAttached(Attachments.DRAW_VIEW);
    }
    public static void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onDisconnect(player);
        DRAW_CREDITS.clear(); ACTIVE_DRAWS.clear(); DRAW_GENERATIONS.clear(); CROSSBOW_LOADS.clear(); BOW_FLIGHTS.clear(); SHOT.remove();
    }
}
