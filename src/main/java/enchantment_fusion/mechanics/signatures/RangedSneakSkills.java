package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
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
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Sneak-and-aim shot credit and an input-directed, collision-respecting crouched retreat. */
public final class RangedSneakSkills {
    public static final String SWIFT = "minecraft:swift_sneak";
    private static final int MAX_OWNERS = 256, MAX_FLIGHTS = 256, LIFETIME = 100;
    private static final class Attachments {
        static final AttachmentType<Integer> AIM_VIEW = AttachmentRegistry.create(
            EnchantmentFusion.id("ranged_sneak_aim_view"), builder -> builder
                .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    }
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Shot(UUID owner, ResourceKey<Level> dimension, Gear gear, long born) {}
    private record Aim(UUID owner, ResourceKey<Level> dimension, Gear gear, UUID target, long born) {}
    private record Slip(UUID owner, ResourceKey<Level> dimension, Gear gear, long born) {}
    private record LoadedUse(ItemStack weapon, InteractionHand hand, int credit) {}
    private record Ticket(@Nullable Gear launcherGear, @Nullable Gear leggings, @Nullable Aim aim) {}
    private static final Map<AbstractArrow, Shot> SHOTS = new WeakHashMap<>();
    private static final Map<UUID, Aim> AIMS = new HashMap<>();
    private static final Map<UUID, Slip> SLIPS = new HashMap<>();
    private static final Map<UUID, LoadedUse> LOADED = new HashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return shootingFirst(pair) && RangedSneakSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) { onHit(hit); }
        @Override public void missed(ServerPlayer owner, ItemStack launcher, FusedPair pair, AbstractArrow arrow) { SHOTS.remove(arrow); }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            if (!ready(owner) || !owner.isShiftKeyDown() || !RangedWearSkills.supportsCarrier(FusionCarrier.classify(launcher))) return null;
            Gear forward = selected(launcher, true), legs = worn(owner); Aim aim = AIMS.get(owner.getUUID());
            if (!validAim(owner, aim) || aim.gear().stack() != launcher) aim = null;
            return forward == null && legs == null && aim == null ? null : new Ticket(forward, legs, aim);
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object raw) {
            if (!(raw instanceof Ticket ticket) || !ready(owner) || !owner.isShiftKeyDown() || arrow.getOwner() != owner
                || owner.level().getEntity(arrow.getUUID()) != arrow) return;
            Aim aim = ticket.aim();
            if (aim != null && AIMS.get(owner.getUUID()) == aim && aim.gear().stack() == launcher && validAim(owner, aim)) {
                AIMS.remove(owner.getUUID(), aim); updateView(owner);
                CompendiumDiscoveryTracker.recordDiscovery(owner, aim.gear().pair().primaryId(), aim.gear().pair().secondaryId());
            }
            Gear gear = ticket.launcherGear();
            if (gear != null && owns(owner, gear) && SHOTS.size() < MAX_FLIGHTS) {
                SHOTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner.getUUID()) && entry.getValue().gear().stack() == launcher);
                SHOTS.put(arrow, new Shot(owner.getUUID(), owner.level().dimension(), gear, owner.level().getGameTime()));
            }
            Gear legs = ticket.leggings();
            if (legs != null && wearing(owner, legs) && (SLIPS.containsKey(owner.getUUID()) || SLIPS.size() < MAX_OWNERS)) {
                SLIPS.put(owner.getUUID(), new Slip(owner.getUUID(), owner.level().dimension(), legs, owner.level().getGameTime()));
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_sneak_slip_ready"), true);
            }
        }
    };
    private RangedSneakSkills() {}
    public static void init() {
        java.util.Objects.requireNonNull(Attachments.AIM_VIEW);
        ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS);
        PrimaryAttributeProjection.register(RangedSneakSkills::primarySources);
    }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && SWIFT.equals(pair.secondaryId());
    }
    public static boolean sneakFirst(@Nullable FusedPair pair) {
        return pair != null && SWIFT.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || sneakFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        return roles != null && (shootingFirst(pair) ? RangedWearSkills.supportsCarrier(roles)
            : sneakFirst(pair) && roles.contains(FusionCarrier.LEG_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static int primaryLevel(@Nullable ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return 0; int level = 0;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (shootingFirst(pair) && id.equals(pair.primaryId()) && applies(pair, stack)) level = Math.max(level, pair.primaryLevel());
        return level;
    }
    private static @Nullable Gear selected(ItemStack stack, boolean forward) {
        if (stack == null || stack.isEmpty() || stack.isBroken()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((forward ? shootingFirst(pair) : sneakFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static @Nullable Gear worn(ServerPlayer owner) { return selected(owner.getItemBySlot(EquipmentSlot.LEGS), false); }
    public static boolean hasEquipped(ServerPlayer owner) { return worn(owner) != null; }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (gear == null || gear.stack().isBroken() || !applies(gear.pair(), gear.stack())
            || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean wearing(ServerPlayer owner, Gear gear) {
        return gear != null && owner.getItemBySlot(EquipmentSlot.LEGS) == gear.stack() && sneakFirst(gear.pair()) && owns(owner, gear);
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static boolean within(ServerPlayer owner, ResourceKey<Level> dimension, long born) {
        long now = owner.level().getGameTime(); return owner.level().dimension().equals(dimension) && now >= born && now - born < LIFETIME;
    }
    private static boolean held(ServerPlayer owner, ItemStack stack) {
        return owner.getMainHandItem() == stack || owner.getOffhandItem() == stack;
    }
    private static boolean aimed(ServerPlayer owner, LivingEntity target) {
        if (target.level() != owner.level() || !LoyaltyGuard.eligibleTarget(owner, target)
            || !owner.level().mayInteract(owner, target.blockPosition())) return false;
        Vec3 eye = owner.getEyePosition(), look = owner.getLookAngle();
        if (!RangedTideMath.finite(eye) || !RangedTideMath.finite(look)) return false;
        Vec3 end = eye.add(look.scale(24.0D));
        Vec3 point = target.getBoundingBox().inflate(.15D).clip(eye, end).orElse(null);
        return point != null && VirtualCombatSight.clear(owner.level(), owner, eye, point);
    }
    private static boolean validAim(ServerPlayer owner, @Nullable Aim aim) {
        if (aim == null || !ready(owner) || !owner.isShiftKeyDown() || !owner.getUUID().equals(aim.owner())
            || !within(owner, aim.dimension(), aim.born()) || !owns(owner, aim.gear()) || !held(owner, aim.gear().stack())) return false;
        Entity target = owner.level().getEntity(aim.target()); return target instanceof LivingEntity living && aimed(owner, living);
    }
    private static void onHit(ConfirmedArrow.Hit hit) {
        Shot shot = SHOTS.remove(hit.arrow()); ServerPlayer owner = hit.owner();
        if (shot == null || !ready(owner) || !owner.isShiftKeyDown() || !shot.owner().equals(owner.getUUID())
            || !within(owner, shot.dimension(), shot.born()) || shot.gear().stack() != hit.launcher()
            || !shot.gear().pair().equals(hit.pair()) || !owns(owner, shot.gear()) || !held(owner, shot.gear().stack())
            || !aimed(owner, hit.target()) || (!AIMS.containsKey(owner.getUUID()) && AIMS.size() >= MAX_OWNERS)) return;
        AIMS.put(owner.getUUID(), new Aim(owner.getUUID(), owner.level().dimension(), shot.gear(), hit.target().getUUID(), owner.level().getGameTime()));
        updateView(owner); owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_sneak_aim_ready"), true);
    }
    /** Server-authoritative boost, with a target-only client view for crossbow animation/prediction. */
    private static int credit(LivingEntity user, ItemStack weapon) {
        if (!(user instanceof Player player) || selected(weapon, true) == null) return 0;
        if (player instanceof ServerPlayer serverPlayer) {
            LoadedUse loaded = LOADED.get(serverPlayer.getUUID());
            if (validLoaded(serverPlayer, loaded) && loaded.weapon() == weapon) return loaded.credit();
            if (!player.isShiftKeyDown() || (weapon.getItem() instanceof CrossbowItem
                && !(player.getProjectile(weapon).getItem() instanceof ArrowItem))) return 0;
            Aim aim = AIMS.get(serverPlayer.getUUID());
            return validAim(serverPlayer, aim) && aim.gear().stack() == weapon ? RangedSneakMath.credit(aim.gear().pair().secondaryLevel()) : 0;
        }
        Integer view = player.getAttached(Attachments.AIM_VIEW);
        if (view == null || (!player.isShiftKeyDown() && (view & 256) == 0)) return 0;
        return player.getMainHandItem() == weapon ? view & 15 : player.getOffhandItem() == weapon ? (view >>> 4) & 15 : 0;
    }
    public static int bowTicks(LivingEntity user, ItemStack weapon, int elapsed) {
        return RangedSneakMath.bowTicks(elapsed, credit(user, weapon));
    }
    public static int crossbowDuration(LivingEntity user, ItemStack weapon, int vanilla) {
        return RangedSneakMath.crossbowDuration(vanilla, credit(user, weapon));
    }
    /** Client item-model preview; exact timing still comes from the server at release. */
    public static int visualBowTicks(LivingEntity user, ItemStack weapon, int elapsed) {
        int bonus = credit(user, weapon); if (bonus <= 0) return elapsed;
        int slowed = HeavyCrushSkill.selected(weapon) == null ? elapsed : HeavyCrushMath.slowedUseTicks(elapsed);
        return RangedSneakMath.bowTicks(slowed, bonus);
    }
    private static boolean validLoaded(ServerPlayer owner, @Nullable LoadedUse loaded) {
        return loaded != null && ready(owner) && owner.isUsingItem() && owner.getUseItem() == loaded.weapon()
            && owner.getUsedItemHand() == loaded.hand() && owner.getItemInHand(loaded.hand()) == loaded.weapon()
            && CrossbowItem.isCharged(loaded.weapon());
    }
    /** A successful real-arrow load spends credit, but retains it until this use action is released. */
    public static void loadedCrossbow(LivingEntity user, ItemStack weapon) {
        if (!(user instanceof ServerPlayer owner)) return; Aim aim = AIMS.get(owner.getUUID());
        List<ItemStack> ammunition = weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).itemCopies();
        if (aim != null && aim.gear().stack() == weapon && validAim(owner, aim) && !ammunition.isEmpty()
            && ammunition.getFirst().getItem() instanceof ArrowItem && owner.isUsingItem() && owner.getUseItem() == weapon) {
            LOADED.put(owner.getUUID(), new LoadedUse(weapon, owner.getUsedItemHand(), RangedSneakMath.credit(aim.gear().pair().secondaryLevel())));
            AIMS.remove(owner.getUUID(), aim); updateView(owner);
            CompendiumDiscoveryTracker.recordDiscovery(owner, aim.gear().pair().primaryId(), aim.gear().pair().secondaryId());
        }
    }
    private static boolean validSlip(ServerPlayer owner, @Nullable Slip slip) {
        return slip != null && ready(owner) && owner.getUUID().equals(slip.owner()) && within(owner, slip.dimension(), slip.born())
            && wearing(owner, slip.gear());
    }
    public static boolean activate(ServerPlayer owner) {
        Slip slip = SLIPS.get(owner.getUUID());
        if (!validSlip(owner, slip) || !owner.isShiftKeyDown() || !owner.onGround() || owner.isPassenger()
            || owner.isInWater() || owner.isFallFlying() || owner.getAbilities().flying || owner.containerMenu != owner.inventoryMenu) return false;
        Vec3 choice = owner.getLastClientMoveIntent();
        if (!RangedTideMath.finite(choice) || choice.horizontalDistanceSqr() < 1.0E-6D) choice = owner.getLookAngle().scale(-1);
        Vec3 direction = new Vec3(choice.x, 0, choice.z);
        if (!RangedTideMath.finite(direction) || direction.horizontalDistanceSqr() < 1.0E-6D) return false;
        direction = direction.normalize();
        if (!owner.level().noCollision(owner, owner.getBoundingBox().move(direction.scale(.35D)))) return false;
        SLIPS.remove(owner.getUUID(), slip);
        Vec3 push = direction.scale(RangedSneakMath.retreat(slip.gear().pair().secondaryLevel()));
        owner.push(push.x, 0, push.z); owner.hurtMarked = true; owner.connection.send(new ClientboundSetEntityMotionPacket(owner));
        CompendiumDiscoveryTracker.recordDiscovery(owner, slip.gear().pair().primaryId(), slip.gear().pair().secondaryId());
        return true;
    }
    private static List<PrimaryAttributeProjection.Source> primarySources(ServerPlayer owner) {
        ItemStack legs = owner.getItemBySlot(EquipmentSlot.LEGS);
        if (legs.isEmpty() || legs.isBroken() || !FusionCarrier.classify(legs).contains(FusionCarrier.LEG_ARMOR)
            || !FusionStationOperations.lookupEnchantment(owner.registryAccess(), SWIFT)
                .map(holder -> holder.value().matchingSlot(EquipmentSlot.LEGS)).orElse(false)) return List.of();
        List<PrimaryAttributeProjection.Source> result = new ArrayList<>();
        for (FusedPair pair : legs.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (sneakFirst(pair) && applies(pair, legs)) result.add(new PrimaryAttributeProjection.Source(EquipmentSlot.LEGS, legs, pair));
        return List.copyOf(result);
    }
    private static void updateView(ServerPlayer owner) {
        Aim aim = AIMS.get(owner.getUUID()); LoadedUse loaded = LOADED.get(owner.getUUID()); int view = 0;
        if (validLoaded(owner, loaded)) {
            view = 256 | (loaded.hand() == InteractionHand.MAIN_HAND ? loaded.credit() : loaded.credit() << 4);
        } else if (validAim(owner, aim)) {
            int credit = RangedSneakMath.credit(aim.gear().pair().secondaryLevel());
            if (owner.getMainHandItem() == aim.gear().stack()) view = credit;
            else if (owner.getOffhandItem() == aim.gear().stack()) view = credit << 4;
        }
        Integer old = owner.getAttached(Attachments.AIM_VIEW);
        if (view == 0 && old != null) owner.removeAttached(Attachments.AIM_VIEW);
        else if (view != 0 && (old == null || old != view)) owner.setAttached(Attachments.AIM_VIEW, view);
    }
    public static void tickServer(MinecraftServer server) {
        SHOTS.entrySet().removeIf(entry -> {
            Shot shot = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(shot.owner());
            return owner == null || !ready(owner) || !owner.isShiftKeyDown() || !within(owner, shot.dimension(), shot.born())
                || !owns(owner, shot.gear()) || !held(owner, shot.gear().stack())
                || entry.getKey().isRemoved() || entry.getKey().level() != owner.level();
        });
        for (var entry : List.copyOf(AIMS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { AIMS.remove(entry.getKey(), entry.getValue()); continue; }
            if (!validAim(owner, entry.getValue())) AIMS.remove(entry.getKey(), entry.getValue());
            updateView(owner);
        }
        for (var entry : List.copyOf(LOADED.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { LOADED.remove(entry.getKey(), entry.getValue()); continue; }
            if (!validLoaded(owner, entry.getValue())) LOADED.remove(entry.getKey(), entry.getValue());
            updateView(owner);
        }
        SLIPS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !validSlip(owner, entry.getValue());
        });
    }
    public static void clearPlayer(ServerPlayer owner) {
        UUID id = owner.getUUID(); AIMS.remove(id); SLIPS.remove(id); LOADED.remove(id); SHOTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
        if (owner.getAttached(Attachments.AIM_VIEW) != null) owner.removeAttached(Attachments.AIM_VIEW);
    }
    public static void clearServer(MinecraftServer server) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) clearPlayer(owner);
        AIMS.clear(); SLIPS.clear(); LOADED.clear(); SHOTS.clear();
    }
}
