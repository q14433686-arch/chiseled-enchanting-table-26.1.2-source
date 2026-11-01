package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Virtual friendly screen and one actual-mitigation-paid real-arrow protection. */
public final class RangedGuardSkills {
    public static final String PROTECTION = "minecraft:protection";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int MAX_OWNERS = 256, MAX_FLIGHTS = 1024, MAX_GEAR = 6;
    private static final int WINDOW = 200, SCREEN_TICKS = 100;
    public record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private static final class Charge {
        final ResourceKey<Level> dimension; final Gear gear; long born; float amount;
        Charge(ServerPlayer owner, Gear gear) { this.dimension = owner.level().dimension(); this.gear = gear; this.born = owner.level().getGameTime(); }
    }
    private record Prepared(ResourceKey<Level> dimension, Gear gear, long born) {}
    private record Guarded(UUID owner, ResourceKey<Level> dimension, Gear gear, long born) {}
    private static final class Screen {
        final UUID owner; final ResourceKey<Level> dimension; final ItemStack launcher; final FusedPair pair;
        final long born; final Vec3 center; int remaining;
        Screen(ServerPlayer owner, ItemStack launcher, FusedPair pair, Vec3 center) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.launcher = launcher; this.pair = pair;
            this.born = owner.level().getGameTime(); this.center = center; this.remaining = RangedGuardMath.blocks(pair.secondaryLevel());
        }
    }
    private static final Map<UUID, List<Charge>> CHARGES = new HashMap<>();
    private static final Map<UUID, Prepared> PREPARED = new HashMap<>();
    private static final Map<AbstractArrow, Guarded> GUARDED = new WeakHashMap<>();
    private static final Map<UUID, Screen> SCREENS = new HashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return shootingFirst(pair) && RangedGuardSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) {
            ServerPlayer owner = hit.owner(); Vec3 point = hit.location();
            if (!ready(owner) || !owns(owner, hit.launcher(), hit.pair()) || !RangedTideMath.finite(point)
                || !owner.level().hasChunkAt(BlockPos.containing(point)) || !owner.level().mayInteract(owner, BlockPos.containing(point))
                || (!SCREENS.containsKey(owner.getUUID()) && SCREENS.size() >= MAX_OWNERS)) return;
            SCREENS.put(owner.getUUID(), new Screen(owner, hit.launcher(), hit.pair(), point));
            owner.level().sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 10, .35D, .35D, .35D, .01D);
        }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            Prepared prepared = PREPARED.get(owner.getUUID());
            return RangedWearSkills.supportsCarrier(FusionCarrier.classify(launcher)) && validPrepared(owner, prepared) ? prepared : null;
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object raw) {
            if (!(raw instanceof Prepared prepared) || PREPARED.get(owner.getUUID()) != prepared || !validPrepared(owner, prepared)
                || arrow.getOwner() != owner || owner.level().getEntity(arrow.getUUID()) != arrow) return;
            PREPARED.remove(owner.getUUID(), prepared);
            if (GUARDED.size() >= MAX_FLIGHTS) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_guard_full"), true); return;
            }
            GUARDED.put(arrow, new Guarded(owner.getUUID(), owner.level().dimension(), prepared.gear(), owner.level().getGameTime()));
            CompendiumDiscoveryTracker.recordDiscovery(owner, prepared.gear().pair().primaryId(), prepared.gear().pair().secondaryId());
        }
    };
    private RangedGuardSkills() {}
    public static void init() { ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS); }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && PROTECTION.equals(pair.secondaryId());
    }
    public static boolean protectionFirst(@Nullable FusedPair pair) {
        return pair != null && PROTECTION.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || protectionFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        return roles != null && (shootingFirst(pair) ? RangedWearSkills.supportsCarrier(roles)
            : protectionFirst(pair) && FireTradeoffSkills.supportsCarrier(roles));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static int primaryLevel(@Nullable ItemStack item, String id) {
        if (item == null || item.isEmpty()) return 0; int level = 0;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (shootingFirst(pair) && id.equals(pair.primaryId()) && applies(pair, item)) level = Math.max(level, pair.primaryLevel());
        return level;
    }
    public static List<Gear> equipped(ServerPlayer owner) {
        List<Gear> result = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack item = owner.getItemBySlot(slot); if (!FireTradeoffSkills.slotMatches(item, slot) || item.isBroken()) continue;
            for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                if (protectionFirst(pair) && applies(pair, item)) { result.add(new Gear(slot, item, pair)); break; }
            }
        }
        return result;
    }
    public static boolean hasEquipped(ServerPlayer owner) { return !equipped(owner).isEmpty(); }
    public static boolean stillEquipped(ServerPlayer owner, Gear gear) {
        return gear != null && owner.getItemBySlot(gear.slot()) == gear.stack() && !gear.stack().isBroken()
            && FireTradeoffSkills.slotMatches(gear.stack(), gear.slot()) && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static boolean same(Gear a, Gear b) { return a.slot() == b.slot() && a.stack() == b.stack() && a.pair().equals(b.pair()); }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static boolean owns(ServerPlayer owner, ItemStack item, FusedPair pair) {
        if (item == null || item.isBroken() || !applies(pair, item)
            || !item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair)) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == item) return true;
        return false;
    }
    private static boolean within(ServerPlayer owner, ResourceKey<Level> dimension, long born, long duration) {
        long now = owner.level().getGameTime(); return dimension.equals(owner.level().dimension()) && now >= born && now - born < duration;
    }
    private static boolean validCharge(ServerPlayer owner, Charge charge) {
        return ready(owner) && stillEquipped(owner, charge.gear) && within(owner, charge.dimension, charge.born, WINDOW);
    }
    private static boolean validPrepared(ServerPlayer owner, @Nullable Prepared prepared) {
        return prepared != null && ready(owner) && stillEquipped(owner, prepared.gear()) && within(owner, prepared.dimension(), prepared.born(), WINDOW);
    }
    /** Called only by the shared real defense capture, after the whole damage attempt. */
    public static void addCharge(ServerPlayer owner, Gear gear, float amount) {
        if (!ready(owner) || !stillEquipped(owner, gear) || !Float.isFinite(amount) || amount <= 0) return;
        List<Charge> charges = CHARGES.get(owner.getUUID());
        if (charges == null) {
            if (CHARGES.size() >= MAX_OWNERS) return; charges = new ArrayList<>(); CHARGES.put(owner.getUUID(), charges);
        }
        charges.removeIf(entry -> !validCharge(owner, entry)); Charge charge = null;
        for (Charge entry : charges) if (same(entry.gear, gear)) { charge = entry; break; }
        if (charge == null) {
            if (charges.size() >= MAX_GEAR) return; charge = new Charge(owner, gear); charges.add(charge);
        }
        charge.amount = RangedGuardMath.charge(charge.amount, amount); charge.born = owner.level().getGameTime();
    }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Prepared old = PREPARED.get(owner.getUUID());
        if (validPrepared(owner, old)) return false;
        if (old != null) PREPARED.remove(owner.getUUID(), old);
        if (PREPARED.size() >= MAX_OWNERS) return false;
        List<Charge> charges = CHARGES.get(owner.getUUID());
        Charge chosen = charges == null ? null : charges.stream().filter(entry -> validCharge(owner, entry) && entry.amount >= 2.0F)
            .max(Comparator.comparingDouble(entry -> entry.amount)).orElse(null);
        if (chosen == null) { owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_guard_need_defense"), true); return false; }
        chosen.amount -= Math.min(4.0F, chosen.amount);
        if (chosen.amount < 1.0E-5F) { charges.remove(chosen); if (charges.isEmpty()) CHARGES.remove(owner.getUUID()); }
        PREPARED.put(owner.getUUID(), new Prepared(owner.level().dimension(), chosen.gear, owner.level().getGameTime()));
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_guard_set"), true); return true;
    }
    private static boolean validScreen(ServerPlayer owner, Screen screen) {
        return ready(owner) && owner.getUUID().equals(screen.owner) && screen.remaining > 0
            && owner.level().hasChunkAt(BlockPos.containing(screen.center))
            && within(owner, screen.dimension, screen.born, SCREEN_TICKS) && owns(owner, screen.launcher, screen.pair);
    }
    private static boolean validGuard(ServerPlayer owner, AbstractArrow arrow, Guarded guard) {
        return ready(owner) && owner.getUUID().equals(guard.owner()) && arrow.getOwner() == owner && arrow.isAlive()
            && owner.level() == arrow.level() && owner.level().getEntity(arrow.getUUID()) == arrow
            && within(owner, guard.dimension(), guard.born(), WINDOW) && stillEquipped(owner, guard.gear())
            && !((AbstractArrowAccessor) arrow).ef$isInGround() && !PhantomVolley.isGhost(arrow);
    }
    private static boolean friendly(ServerPlayer owner, Entity entity) {
        if (entity == owner) return true;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) return false;
        if (owner.isAlliedTo(living) || living.isAlliedTo(owner) || living instanceof AbstractVillager) return true;
        if (living instanceof OwnableEntity pet) {
            LivingEntity keeper = pet.getOwner();
            return keeper == owner || keeper != null && (owner.isAlliedTo(keeper) || keeper.isAlliedTo(owner));
        }
        return false;
    }
    private static boolean hostile(ServerPlayer owner, Projectile incoming, @Nullable Entity friendlyTarget) {
        if (incoming instanceof LlamaSpit || !incoming.isAlive() || incoming.level() != owner.level()
            || owner.level().getEntity(incoming.getUUID()) != incoming || !(incoming.getOwner() instanceof LivingEntity shooter)
            || !LoyaltyGuard.eligibleTarget(owner, shooter) || !owner.level().mayInteract(owner, shooter.blockPosition())) return false;
        return !(friendlyTarget instanceof Player victim) || !(shooter instanceof Player attacker) || attacker.canHarmPlayer(victim);
    }
    private static boolean reverse(Projectile incoming, ServerPlayer owner) {
        Entity shooter = incoming.getOwner();
        return shooter != null && incoming.deflect(ProjectileDeflection.REVERSE, owner, EntityReference.of(shooter), false);
    }
    /** Only called on a real projectile/entity impact; never predicts or scans future hits. */
    public static boolean intercept(Projectile moving, EntityHitResult impact) {
        if (!(moving.level() instanceof net.minecraft.server.level.ServerLevel level) || !moving.isAlive()) return false;
        Entity victim = impact.getEntity();
        // Either projectile may be the moving side of a genuine redirectable-projectile collision.
        AbstractArrow protectedArrow = moving instanceof AbstractArrow movingArrow && GUARDED.containsKey(movingArrow) ? movingArrow
            : victim instanceof AbstractArrow hitArrow && GUARDED.containsKey(hitArrow) ? hitArrow : null;
        Projectile incoming = protectedArrow == moving && victim instanceof Projectile other ? other
            : protectedArrow == victim ? moving : null;
        Guarded guard = protectedArrow == null ? null : GUARDED.get(protectedArrow);
        if (protectedArrow != null && incoming != null && guard != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(guard.owner());
            if (owner != null && validGuard(owner, protectedArrow, guard) && hostile(owner, incoming, owner) && reverse(incoming, owner)) {
                GUARDED.remove(protectedArrow, guard);
                level.sendParticles(ParticleTypes.CRIT, impact.getLocation().x, impact.getLocation().y, impact.getLocation().z, 6, .1D, .1D, .1D, .01D);
                return true;
            }
        }
        if (!(victim instanceof LivingEntity)) return false;
        for (Screen screen : List.copyOf(SCREENS.values())) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(screen.owner);
            if (owner == null || owner.level() != level || !validScreen(owner, screen) || !friendly(owner, victim)
                || !hostile(owner, moving, victim) || !RangedTideMath.finite(impact.getLocation())
                || impact.getLocation().distanceToSqr(screen.center) > Math.pow(RangedGuardMath.radius(screen.pair.secondaryLevel()), 2)
                || !VirtualCombatSight.clear(level, owner, screen.center, impact.getLocation())
                || !level.hasChunkAt(BlockPos.containing(impact.getLocation())) || !level.mayInteract(owner, BlockPos.containing(impact.getLocation()))) continue;
            if (!reverse(moving, owner)) continue;
            screen.remaining--; if (screen.remaining <= 0) SCREENS.remove(owner.getUUID(), screen);
            level.sendParticles(ParticleTypes.END_ROD, impact.getLocation().x, impact.getLocation().y, impact.getLocation().z, 6, .1D, .1D, .1D, .01D);
            CompendiumDiscoveryTracker.recordDiscovery(owner, screen.pair.primaryId(), screen.pair.secondaryId()); return true;
        }
        return false;
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(CHARGES.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { CHARGES.remove(entry.getKey()); continue; }
            entry.getValue().removeIf(charge -> !validCharge(owner, charge)); if (entry.getValue().isEmpty()) CHARGES.remove(entry.getKey());
        }
        PREPARED.entrySet().removeIf(entry -> { ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !validPrepared(owner, entry.getValue()); });
        GUARDED.entrySet().removeIf(entry -> { ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner()); return owner == null || !validGuard(owner, entry.getKey(), entry.getValue()); });
        SCREENS.entrySet().removeIf(entry -> {
            Screen screen = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !validScreen(owner, screen)) return true;
            if (owner.level().getGameTime() % 10 == 0) owner.level().sendParticles(ParticleTypes.END_ROD, screen.center.x, screen.center.y,
                screen.center.z, 3, .5D, .5D, .5D, .01D); return false;
        });
    }
    public static void clearPlayer(UUID id) {
        CHARGES.remove(id); PREPARED.remove(id); SCREENS.remove(id); GUARDED.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
    }
    public static void clearServer() { CHARGES.clear(); PREPARED.clear(); SCREENS.clear(); GUARDED.clear(); }
}
