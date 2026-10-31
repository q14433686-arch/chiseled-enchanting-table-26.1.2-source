package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Actual arrows continue through a repeat hit or earn a single next-load time reduction. */
public final class PiercingChainSkills {
    public static final String QUICK = "minecraft:quick_charge", PIERCING = "minecraft:piercing";
    public static final String FORWARD = "threading_needle", REVERSE = "bamboo_split_momentum";
    private static final int MAX_FLIGHTS = 4096, MAX_ITEMS = 4, MAX_HITS = 32;
    private static final class Attachments {
        static final AttachmentType<Integer> RELOAD_VIEW = AttachmentRegistry.create(
            EnchantmentFusion.id("piercing_chain_reload_view"), builder -> builder
                .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    }
    private static final class Flight {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack weapon;
        final FusedPair pair;
        final long sequence, expires;
        boolean firstHit, forwardPending, redirectPending;
        final Set<UUID> hit = new HashSet<>(), pending = new HashSet<>();
        Flight(ServerPlayer owner, ItemStack weapon, FusedPair pair, long sequence) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.weapon = weapon; this.pair = pair;
            this.sequence = sequence; this.expires = owner.level().getGameTime() + 1200;
        }
    }
    private record Hole(ItemStack weapon, FusedPair pair, ResourceKey<Level> dimension, UUID target,
                        Vec3 direction, long sequence, long expires) {}
    private static final class ReloadCredit {
        final ItemStack weapon;
        final FusedPair pair;
        final ResourceKey<Level> dimension;
        int ticks;
        long expires;
        ReloadCredit(ItemStack weapon, FusedPair pair, ResourceKey<Level> dimension) {
            this.weapon = weapon; this.pair = pair; this.dimension = dimension;
        }
    }
    public static final class Impact {
        private final AbstractArrow arrow;
        private final Flight flight;
        private final LivingEntity target;
        private final Vec3 direction, location;
        private final boolean qualified;
        private Impact(AbstractArrow arrow, Flight flight, LivingEntity target, Vec3 direction, Vec3 location, boolean qualified) {
            this.arrow = arrow; this.flight = flight; this.target = target; this.direction = direction;
            this.location = location; this.qualified = qualified;
        }
    }
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private static final Map<UUID, Long> SEQUENCES = new HashMap<>();
    private static final Map<UUID, List<Hole>> HOLES = new HashMap<>();
    private static final Map<UUID, List<ReloadCredit>> RELOADS = new HashMap<>();
    private PiercingChainSkills() {}
    public static void init() { java.util.Objects.requireNonNull(Attachments.RELOAD_VIEW); }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (QUICK.equals(pair.primaryId()) && PIERCING.equals(pair.secondaryId()) && FORWARD.equals(signature)
            || PIERCING.equals(pair.primaryId()) && QUICK.equals(pair.secondaryId()) && REVERSE.equals(signature));
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, Set<FusionCarrier> roles) {
        if (pair == null || roles == null) return false;
        if (QUICK.equals(pair.primaryId()) && PIERCING.equals(pair.secondaryId()))
            return roles.contains(FusionCarrier.BOW) || roles.contains(FusionCarrier.CROSSBOW);
        return PIERCING.equals(pair.primaryId()) && QUICK.equals(pair.secondaryId()) && roles.contains(FusionCarrier.CROSSBOW);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)) return pair;
        return null;
    }
    public static boolean hasLegacySignature(@Nullable ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return false;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            if (id.equals(signature) && !applies(pair, stack, signature)) return true;
        }
        return false;
    }
    public static boolean playerHasLegacySignature(ServerPlayer player, String id) {
        for (var slot : SignatureCatalog.ALL_EQUIP_SLOTS) if (hasLegacySignature(player.getItemBySlot(slot), id)) return true;
        return false;
    }
    private static boolean held(ServerPlayer player, ItemStack stack) {
        return player.getMainHandItem() == stack || player.getOffhandItem() == stack;
    }
    private static boolean owns(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) return true;
        return false;
    }
    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean same(ServerPlayer player, ItemStack weapon, FusedPair pair, ResourceKey<Level> dimension) {
        return eligible(player) && dimension.equals(player.level().dimension()) && !weapon.isEmpty()
            && owns(player, weapon) && pair.equals(selected(weapon));
    }
    private static int param(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }
    /** Hook only a true central vanilla arrow; a later hit proves the arrow really entered the world. */
    public static void launched(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack launcher) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner) || index != 0
            || launcher == null || !eligible(owner) || !held(owner, launcher) || PhantomVolley.isGhost(arrow)
            || FLIGHTS.size() >= MAX_FLIGHTS) return;
        FusedPair pair = selected(launcher);
        if (pair == null) return;
        long sequence = SEQUENCES.merge(owner.getUUID(), 1L, (old, one) -> old == Long.MAX_VALUE ? 1L : old + 1L);
        if (sequence == 1L) {
            HOLES.remove(owner.getUUID());
            // Sequence wrap must not allow an old in-flight arrow to supersede new history.
            FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner.getUUID()));
        }
        FLIGHTS.put(arrow, new Flight(owner, launcher, pair, sequence));
    }
    private static boolean qualified(ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive() || target == owner || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(owner) || owner.isAlliedTo(target) || !owner.level().mayInteract(owner, target.blockPosition())) return false;
        if (target instanceof Player player) return !player.getAbilities().invulnerable && owner.canHarmPlayer(player);
        if (PhantomVolley.isProtected(owner, target)) return false;
        if (target instanceof NeutralMob) return target instanceof Mob mob && mob.getTarget() == owner;
        return target instanceof Enemy;
    }
    /** Reserve a real hit before callbacks; canceled hits do not earn or consume anything. */
    public static @Nullable Impact beginImpact(AbstractArrow arrow, Entity entity, Vec3 location) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !(entity instanceof LivingEntity target) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || !(arrow.level() instanceof ServerLevel level) || !owner.getUUID().equals(flight.owner)
            || level.getGameTime() >= flight.expires || !same(owner, flight.weapon, flight.pair, flight.dimension)
            || PhantomVolley.isGhost(arrow) || flight.pending.size() >= MAX_HITS || flight.hit.contains(target.getUUID())
            || (QUICK.equals(flight.pair.primaryId()) && (flight.firstHit || flight.forwardPending))
            || !flight.pending.add(target.getUUID())) return null;
        // Damage callbacks may reenter with the same arrow and a different target.
        // The outer successful hit, not a nested callback, is its first impact.
        if (QUICK.equals(flight.pair.primaryId())) flight.forwardPending = true;
        Vec3 velocity = arrow.getDeltaMovement();
        Vec3 direction = velocity.lengthSqr() < 1.0E-8D ? Vec3.ZERO : velocity.normalize();
        boolean qualified = PIERCING.equals(flight.pair.primaryId()) && arrow.getPierceLevel() > 0
            && target.getType() != EntityType.ENDERMAN && qualified(owner, target);
        return new Impact(arrow, flight, target, direction, location, qualified);
    }
    public static void finishImpact(@Nullable Impact impact, boolean succeeded) {
        if (impact == null) return;
        Flight flight = impact.flight;
        flight.pending.remove(impact.target.getUUID());
        if (QUICK.equals(flight.pair.primaryId())) flight.forwardPending = false;
        if (!succeeded || FLIGHTS.get(impact.arrow) != flight || !(impact.arrow.getOwner() instanceof ServerPlayer owner)
            || !(impact.arrow.level() instanceof ServerLevel level) || !owner.getUUID().equals(flight.owner)
            || level.getGameTime() >= flight.expires || !same(owner, flight.weapon, flight.pair, flight.dimension)) return;
        if (flight.hit.size() >= MAX_HITS || !flight.hit.add(impact.target.getUUID())) return;
        if (QUICK.equals(flight.pair.primaryId())) {
            if (flight.firstHit) return;
            flight.firstHit = true;
            finishRepeatHit(owner, impact);
        } else if (impact.qualified) {
            addReload(owner, flight.weapon, flight.pair);
        }
    }
    private static void finishRepeatHit(ServerPlayer owner, Impact impact) {
        Flight flight = impact.flight;
        if (impact.direction.lengthSqr() < 1.0E-8D) return;
        List<Hole> holes = HOLES.computeIfAbsent(owner.getUUID(), unused -> new ArrayList<>());
        Hole prior = null;
        for (Hole hole : holes) if (hole.weapon() == flight.weapon) { prior = hole; break; }
        long now = owner.level().getGameTime();
        // Keep an expired hole briefly as a sequence watermark: a delayed earlier
        // arrow must not replace a newer shot even after the usable window expires.
        if (prior != null && prior.sequence() >= flight.sequence) return;
        Vec3 direction = impact.direction;
        // In a vanilla batch the arrow's position is only advanced to the first
        // collision. Do not redirect a later successful hit from that stale position.
        boolean atImpact = impact.arrow.position().distanceToSqr(impact.location) < 1.0E-8D;
        if (atImpact && prior != null && prior.pair().equals(flight.pair) && prior.dimension().equals(flight.dimension)
            && prior.target().equals(impact.target.getUUID())
            && PiercingChainMath.canFollow(prior.sequence(), flight.sequence, now, prior.expires(), prior.direction().dot(direction))) {
            // Vanilla already dealt damage. Add its first hit to the arrow's normal ignore set
            // before granting real piercing, so it cannot repeatedly hit or clone that target.
            AbstractArrowAccessor access = (AbstractArrowAccessor) impact.arrow;
            if (impact.arrow.getPierceLevel() <= 0) {
                if (access.ef$getPiercingIgnoreEntityIds() == null) access.ef$setPiercingIgnoreEntityIds(new IntOpenHashSet());
                access.ef$getPiercingIgnoreEntityIds().add(impact.target.getId());
                access.ef$setPierceLevel((byte) 1);
            }
            double speed = impact.arrow.getDeltaMovement().length();
            if (Double.isFinite(speed) && speed > 0.0D) {
                impact.arrow.setDeltaMovement(prior.direction().scale(speed));
                // Vanilla precomputes all hits and block collision along the *old* ray.
                // Stop that batch at the confirmed impact; next tick recomputes the new ray.
                flight.redirectPending = true;
            }
            direction = prior.direction();
            CompendiumDiscoveryTracker.recordDiscovery(owner, QUICK, PIERCING);
        }
        holes.removeIf(hole -> hole.weapon() == flight.weapon);
        if (holes.size() >= MAX_ITEMS) holes.removeFirst();
        int ticks = param(FORWARD, "repeat_window_ticks", 80, 1, 400);
        holes.add(new Hole(flight.weapon, flight.pair, flight.dimension, impact.target.getUUID(), direction, flight.sequence, now + ticks));
    }
    /** Consume once inside the current vanilla collision batch; not a physical deflection. */
    public static boolean consumeRedirectStop(AbstractArrow arrow) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !flight.redirectPending) return false;
        flight.redirectPending = false;
        return true;
    }
    private static @Nullable ReloadCredit find(ServerPlayer owner, ItemStack stack) {
        List<ReloadCredit> entries = RELOADS.get(owner.getUUID());
        if (entries != null) for (ReloadCredit credit : entries) if (credit.weapon == stack) return credit;
        return null;
    }
    private static void addReload(ServerPlayer owner, ItemStack weapon, FusedPair pair) {
        List<ReloadCredit> entries = RELOADS.computeIfAbsent(owner.getUUID(), unused -> new ArrayList<>());
        ReloadCredit credit = find(owner, weapon);
        long now = owner.level().getGameTime();
        if (credit == null || now >= credit.expires || !same(owner, credit.weapon, credit.pair, credit.dimension)) {
            if (credit != null) entries.remove(credit);
            if (entries.size() >= MAX_ITEMS) entries.removeFirst();
            credit = new ReloadCredit(weapon, pair, owner.level().dimension()); entries.add(credit);
        }
        int before = credit.ticks;
        credit.ticks = PiercingChainMath.accumulate(credit.ticks, pair.secondaryLevel(),
            param(REVERSE, "reload_ticks_per_enemy_per_level", 1, 0, 10), param(REVERSE, "reload_credit_cap", 12, 0, 20));
        credit.expires = now + param(REVERSE, "reload_credit_lifetime_ticks", 400, 20, 1200);
        updateView(owner);
        if (credit.ticks > before) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.piercing_reload_ready", credit.ticks), true);
            CompendiumDiscoveryTracker.recordDiscovery(owner, PIERCING, QUICK);
        }
    }
    private static int credit(ServerPlayer owner, ItemStack stack) {
        ReloadCredit credit = find(owner, stack);
        return credit != null && held(owner, stack) && owner.level().getGameTime() < credit.expires
            && same(owner, credit.weapon, credit.pair, credit.dimension) ? credit.ticks : 0;
    }
    /** Called on both sides after native and heavy-weapon charge time are calculated. */
    public static int chargeDuration(ItemStack weapon, LivingEntity user, int original) {
        FusedPair pair = selected(weapon);
        if (pair == null || !PIERCING.equals(pair.primaryId()) || !(user instanceof Player player)) return original;
        int ticks;
        if (player instanceof ServerPlayer serverPlayer) ticks = credit(serverPlayer, weapon);
        else {
            Integer view = player.getAttached(Attachments.RELOAD_VIEW);
            if (view == null) return original;
            ticks = player.getMainHandItem() == weapon ? view & 31 : player.getOffhandItem() == weapon ? (view >>> 5) & 31 : 0;
        }
        return ticks <= 0 ? original : PiercingChainMath.duration(original, ticks);
    }
    /** Only a successful real load spends the whole current reduction; aborting does not. */
    public static void loaded(LivingEntity user, ItemStack weapon) {
        if (!(user instanceof ServerPlayer owner) || credit(owner, weapon) <= 0) return;
        List<ReloadCredit> entries = RELOADS.get(owner.getUUID());
        ReloadCredit credit = find(owner, weapon);
        if (entries != null && credit != null) entries.remove(credit);
        updateView(owner);
    }
    private static void updateView(ServerPlayer owner) {
        int view = credit(owner, owner.getMainHandItem()) | (credit(owner, owner.getOffhandItem()) << 5);
        Integer old = owner.getAttached(Attachments.RELOAD_VIEW);
        if (view == 0 && old != null) owner.removeAttached(Attachments.RELOAD_VIEW);
        else if (view != 0 && (old == null || old != view)) owner.setAttached(Attachments.RELOAD_VIEW, view);
    }
    public static void tickPlayer(ServerPlayer owner) {
        if (!eligible(owner)) { onDisconnect(owner); return; }
        long now = owner.level().getGameTime();
        List<Hole> holes = HOLES.get(owner.getUUID());
        if (holes != null) {
            // An expired hole cannot be followed, but its sequence remains as a
            // bounded watermark until any earlier flight has necessarily expired.
            holes.removeIf(hole -> now >= hole.expires() + 1200 || !same(owner, hole.weapon(), hole.pair(), hole.dimension()));
            if (holes.isEmpty()) HOLES.remove(owner.getUUID());
        }
        List<ReloadCredit> credits = RELOADS.get(owner.getUUID());
        if (credits != null) {
            credits.removeIf(credit -> now >= credit.expires || !same(owner, credit.weapon, credit.pair, credit.dimension));
            if (credits.isEmpty()) RELOADS.remove(owner.getUUID());
        }
        updateView(owner);
    }
    public static void onDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        SEQUENCES.remove(id); HOLES.remove(id); RELOADS.remove(id);
        FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(id));
        if (player.getAttached(Attachments.RELOAD_VIEW) != null) player.removeAttached(Attachments.RELOAD_VIEW);
    }
    public static void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onDisconnect(player);
        SEQUENCES.clear(); HOLES.clear(); RELOADS.clear(); FLIGHTS.clear();
    }
}
