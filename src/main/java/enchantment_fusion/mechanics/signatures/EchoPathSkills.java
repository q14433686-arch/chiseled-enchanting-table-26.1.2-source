package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
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
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Infinity ▷ Unbreaking: replay a successful real arrow's recorded path as a virtual echo. */
public final class EchoPathSkills {
    public static final String SIGNATURE = "eternal_bowstring";
    public static final String INFINITY = "minecraft:infinity", UNBREAKING = "minecraft:unbreaking";
    private static final int MAX_NODES = 128, MAX_FLIGHTS = 2048, MAX_CREDITS = 4, MAX_ECHOES = 128;
    private static final double MAX_STEP_SQUARED = 16.0D * 16.0D;
    private static final class Flight {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack launcher;
        final FusedPair pair;
        final long born;
        final List<Vec3> nodes = new ArrayList<>();
        boolean incomplete;
        Flight(ServerPlayer owner, ItemStack launcher, FusedPair pair) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension();
            this.launcher = launcher; this.pair = pair; this.born = owner.level().getGameTime();
        }
        void append(Vec3 pos) {
            if (incomplete || pos == null || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
                incomplete = true; return;
            }
            if (!nodes.isEmpty()) {
                double distance = nodes.getLast().distanceToSqr(pos);
                if (distance < 1.0E-7D) return;
                if (distance > MAX_STEP_SQUARED) { incomplete = true; return; }
            }
            if (nodes.size() >= MAX_NODES) { incomplete = true; return; }
            nodes.add(pos);
        }
    }
    private record Credit(ItemStack launcher, FusedPair pair, ResourceKey<Level> dimension,
                          List<Vec3> nodes, float damage, long expires) {}
    private static final class Echo {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final List<Vec3> nodes;
        final float damage;
        int cursor = 1;
        Echo(ServerPlayer owner, Credit credit) {
            this.owner = owner.getUUID(); this.dimension = credit.dimension(); this.nodes = credit.nodes(); this.damage = credit.damage();
        }
    }
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private static final Map<UUID, List<Credit>> CREDITS = new HashMap<>();
    private static final List<Echo> ECHOES = new ArrayList<>();
    private EchoPathSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && INFINITY.equals(pair.primaryId()) && UNBREAKING.equals(pair.secondaryId())
            && SIGNATURE.equals(signature);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) {
        return roles != null && (roles.contains(FusionCarrier.BOW) || roles.contains(FusionCarrier.CROSSBOW));
    }
    public static boolean supportsCarrier(@Nullable ItemStack stack) {
        return DurabilityEconomy.supportsCarrier(stack) && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && supportsCarrier(stack);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    /** Old unsupported items retain their historical automatic path echo. */
    public static boolean hasLegacySignature(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            if (SIGNATURE.equals(signature) && !applies(pair, stack, signature)) return true;
        }
        return false;
    }
    public static boolean playerHasLegacySignature(ServerPlayer player) {
        for (var slot : SignatureCatalog.ALL_EQUIP_SLOTS) if (hasLegacySignature(player.getItemBySlot(slot))) return true;
        return false;
    }
    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean owns(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) return true;
        return false;
    }
    private static boolean same(ServerPlayer player, ItemStack stack, FusedPair pair, ResourceKey<Level> dimension) {
        return !stack.isEmpty() && owns(player, stack) && dimension.equals(player.level().dimension()) && pair.equals(selected(stack));
    }
    /** Exact firing method and index disallow arrows from unrelated launchers or generated sides. */
    public static void launched(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack launcher) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner) || index != 0
            || launcher == null || !eligible(owner) || PhantomVolley.isGhost(arrow) || FLIGHTS.size() >= MAX_FLIGHTS
            || (owner.getMainHandItem() != launcher && owner.getOffhandItem() != launcher)) return;
        FusedPair pair = selected(launcher);
        if (pair == null) return;
        Flight flight = new Flight(owner, launcher, pair);
        flight.append(arrow.position());
        FLIGHTS.put(arrow, flight);
    }
    public static void sample(AbstractArrow arrow) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !(arrow.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() - flight.born >= 200L) { FLIGHTS.remove(arrow); return; }
        flight.append(arrow.position());
    }
    public static void hit(AbstractArrow arrow, Entity target, Vec3 impact, float baseHit, boolean succeeded) {
        if (!succeeded || !(target instanceof LivingEntity) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || !(arrow.level() instanceof ServerLevel level) || PhantomVolley.isGhost(arrow)) return;
        Flight flight = FLIGHTS.remove(arrow);
        if (flight == null || !eligible(owner) || !owner.getUUID().equals(flight.owner)
            || level.getGameTime() - flight.born >= 200L || !same(owner, flight.launcher, flight.pair, flight.dimension)) return;
        flight.append(impact);
        if (flight.incomplete || flight.nodes.size() < 2 || !Float.isFinite(baseHit) || baseHit <= 0.0F) return;
        int percent = FusionConfigStore.signatureInt(SIGNATURE, "echo_damage_percent", 60, 1, 200);
        float damage = Math.clamp(baseHit * percent / 100.0F, 0.1F, 100.0F);
        long expires = level.getGameTime() + FusionConfigStore.signatureInt(SIGNATURE, "echo_lifetime_ticks", 200, 20, 1200);
        List<Credit> credits = CREDITS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>());
        credits.removeIf(credit -> credit.launcher() == flight.launcher);
        if (credits.size() >= MAX_CREDITS) credits.removeFirst();
        credits.add(new Credit(flight.launcher, flight.pair, flight.dimension, List.copyOf(flight.nodes), damage, expires));
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_ready"), true);
    }
    public static boolean hasEquipped(ServerPlayer player) {
        return selected(player.getMainHandItem()) != null || selected(player.getOffhandItem()) != null;
    }
    /** Cost follows real vanilla wear; near-break weapons are rejected before the echo starts. */
    public static boolean activate(ServerPlayer player) {
        if (!eligible(player)) return false;
        List<Credit> credits = CREDITS.get(player.getUUID());
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack launcher = player.getItemInHand(hand);
            if (selected(launcher) == null || credits == null) continue;
            Credit chosen = null;
            for (Credit credit : credits) if (credit.launcher() == launcher) { chosen = credit; break; }
            if (chosen == null || player.level().getGameTime() >= chosen.expires()
                || !same(player, launcher, chosen.pair(), chosen.dimension())) continue;
            int cost = FusionConfigStore.signatureInt(SIGNATURE, "echo_wear_cost", 3, 1, 50);
            long active = ECHOES.stream().filter(echo -> echo.owner.equals(player.getUUID())).count();
            if (active >= 2 || ECHOES.size() >= MAX_ECHOES) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_busy"), true); return false;
            }
            if (!launcher.isDamageableItem() || launcher.getMaxDamage() - launcher.getDamageValue() <= cost) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_too_worn"), true); return false;
            }
            credits.remove(chosen);
            launcher.hurtAndBreak(cost, player, hand.asEquipmentSlot());
            // Reentrant callbacks may replace the item; never spend another item's state.
            if (player.getItemInHand(hand) != launcher || launcher.isEmpty()) return false;
            ECHOES.add(new Echo(player, chosen));
            CompendiumDiscoveryTracker.recordDiscovery(player, INFINITY, UNBREAKING);
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_released"), true);
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_not_ready"), true);
        return false;
    }
    private static boolean protectedTarget(ServerPlayer owner, LivingEntity target) {
        if (target instanceof Player other) return other == owner || other.isSpectator() || other.isInvulnerable()
            || other.isAlliedTo(owner) || owner.isAlliedTo(other) || !owner.canHarmPlayer(other);
        return PhantomVolley.isProtected(owner, target);
    }
    public static void tickServer(MinecraftServer server) {
        // Damage callbacks may kill a player or modify other echoes reentrantly.
        for (Echo echo : List.copyOf(ECHOES)) {
            if (!ECHOES.contains(echo)) continue;
            ServerLevel level = server.getLevel(echo.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.owner);
            if (level == null || owner == null || !eligible(owner) || owner.level() != level || echo.cursor >= echo.nodes.size()) {
                ECHOES.remove(echo); continue;
            }
            Vec3 from = echo.nodes.get(echo.cursor - 1), to = echo.nodes.get(echo.cursor++);
            if (!level.hasChunkAt(BlockPos.containing(from)) || !level.hasChunkAt(BlockPos.containing(to))) {
                ECHOES.remove(echo); continue;
            }
            HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, owner, from, end,
                new AABB(from, end).inflate(0.5D), entity -> entity != owner && entity.isAlive()
                    && !entity.isSpectator() && entity.canBeHitByProjectile(), 0.31F);
            Vec3 visible = hit == null ? end : hit.getLocation();
            level.sendParticles(ParticleTypes.END_ROD, visible.x, visible.y, visible.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);
            if (hit != null) {
                ECHOES.remove(echo); // Consume before invoking arbitrary damage callbacks.
                if (hit.getEntity() instanceof LivingEntity target && !protectedTarget(owner, target)
                    && level.mayInteract(owner, target.blockPosition())) {
                    // A detached, non-pickup source preserves vanilla projectile damage tags and
                    // a meaningful hit direction; it is never inserted into the world or inventory.
                    Arrow source = new Arrow(EntityType.ARROW, level);
                    source.pickup = AbstractArrow.Pickup.DISALLOWED;
                    source.setPos(from);
                    source.setDeltaMovement(end.subtract(from));
                    try { target.hurtServer(level, level.damageSources().arrow(source, owner), echo.damage); }
                    finally { source.discard(); }
                }
            } else if (wall.getType() != HitResult.Type.MISS || echo.cursor >= echo.nodes.size()) ECHOES.remove(echo);
        }
    }
    public static void tickPlayer(ServerPlayer player) {
        if (!eligible(player)) { onDisconnect(player.getUUID()); return; }
        List<Credit> credits = CREDITS.get(player.getUUID());
        if (credits != null) {
            long now = player.level().getGameTime();
            credits.removeIf(credit -> now >= credit.expires() || !same(player, credit.launcher(), credit.pair(), credit.dimension()));
            if (credits.isEmpty()) CREDITS.remove(player.getUUID());
        }
    }
    public static void onDisconnect(UUID id) {
        CREDITS.remove(id); ECHOES.removeIf(echo -> echo.owner.equals(id));
        FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(id));
    }
    public static void clear() { FLIGHTS.clear(); CREDITS.clear(); ECHOES.clear(); }
}
