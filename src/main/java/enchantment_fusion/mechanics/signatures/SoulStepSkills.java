package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The two selected movement directions; no position is teleported and vanilla collision remains authoritative. */
public final class SoulStepSkills {
    public static final String SOUL = "minecraft:soul_speed", SWIFT = "minecraft:swift_sneak";
    public static final String FORWARD = "phantom_assassin_step", REVERSE = "soul_crawl_slip";
    private static final Identifier SPEED_ID = EnchantmentFusion.id("soul_step_sneak_speed");
    private static final class Attachments {
        static final AttachmentType<Boolean> CRAWL = AttachmentRegistry.create(
            EnchantmentFusion.id("soul_step_crawl"), builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
    }
    private record Gear(ItemStack stack, FusedPair pair, ResourceKey<Level> dimension) {}
    private static final class ForwardState {
        final Gear gear;
        Vec3 last;
        int energy;
        long expires, muffleTick = Long.MIN_VALUE;
        ForwardState(Gear gear, Vec3 last) { this.gear = gear; this.last = last; }
    }
    private static final class ReverseState {
        final Gear gear;
        Vec3 last;
        long readyUntil, activeUntil;
        ReverseState(Gear gear, Vec3 last) { this.gear = gear; this.last = last; }
    }
    private static final Map<UUID, ForwardState> FORWARD_STATE = new HashMap<>();
    private static final Map<UUID, ReverseState> REVERSE_STATE = new HashMap<>();
    private static final Map<UUID, AttributeModifier> OWNED_SPEED = new HashMap<>();
    private SoulStepSkills() {}

    public static void init() { java.util.Objects.requireNonNull(Attachments.CRAWL); }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        if (pair == null) return false;
        return (SOUL.equals(pair.primaryId()) && SWIFT.equals(pair.secondaryId()) && FORWARD.equals(signature))
            || (SWIFT.equals(pair.primaryId()) && SOUL.equals(pair.secondaryId()) && REVERSE.equals(signature));
    }
    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> carriers) {
        if (pair == null || carriers == null) return false;
        return SOUL.equals(pair.primaryId()) && SWIFT.equals(pair.secondaryId()) && carriers.contains(FusionCarrier.FOOT_ARMOR)
            || SWIFT.equals(pair.primaryId()) && SOUL.equals(pair.secondaryId()) && carriers.contains(FusionCarrier.LEG_ARMOR);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable Gear find(ServerPlayer player, boolean forward) {
        EquipmentSlot slot = forward ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) return null;
        Gear best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            if (!applies(pair, stack, signature) || (forward != FORWARD.equals(signature))) continue;
            if (best == null || pair.primaryLevel() + (long) pair.secondaryLevel()
                > best.pair().primaryLevel() + (long) best.pair().secondaryLevel()) {
                best = new Gear(stack, pair, player.level().dimension());
            }
        }
        return best;
    }
    private static boolean sameGear(@Nullable Gear a, @Nullable Gear b) {
        return a != null && b != null && a.stack() == b.stack() && a.pair().equals(b.pair()) && a.dimension().equals(b.dimension());
    }
    public static boolean hasEquippedCrawl(ServerPlayer player) { return find(player, false) != null; }
    private static double moved(Vec3 before, Vec3 after) {
        return Math.hypot(after.x - before.x, after.z - before.z);
    }
    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
            && !player.getAbilities().flying && !player.isFallFlying() && !player.isInWater();
    }
    private static boolean soulGround(ServerPlayer player) {
        return player.onGround() && player.getBlockStateOn().is(BlockTags.SOUL_SPEED_BLOCKS);
    }
    private static boolean speedAvailable(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.SNEAKING_SPEED);
        if (attr == null) return false;
        AttributeModifier ours = OWNED_SPEED.get(player.getUUID());
        return !attr.hasModifier(SPEED_ID) || ours != null && ours.equals(attr.getModifier(SPEED_ID))
            && !attr.getPermanentModifiers().contains(ours);
    }
    private static void clearSpeed(ServerPlayer player) {
        AttributeModifier ours = OWNED_SPEED.remove(player.getUUID());
        AttributeInstance attr = player.getAttribute(Attributes.SNEAKING_SPEED);
        if (ours != null && attr != null && ours.equals(attr.getModifier(SPEED_ID)) && !attr.getPermanentModifiers().contains(ours)) {
            attr.removeModifier(SPEED_ID);
        }
    }
    private static void setSpeed(ServerPlayer player, double target) {
        AttributeInstance attr = player.getAttribute(Attributes.SNEAKING_SPEED);
        AttributeModifier existing = OWNED_SPEED.get(player.getUUID());
        if (existing != null && attr != null && existing.equals(attr.getModifier(SPEED_ID))
            && !attr.getPermanentModifiers().contains(existing) && Math.abs(attr.getValue() - target) < 1.0E-6D) return;
        clearSpeed(player);
        if (attr == null || attr.hasModifier(SPEED_ID) || target <= attr.getValue()) return;
        AttributeModifier modifier = new AttributeModifier(SPEED_ID, target - attr.getValue(), AttributeModifier.Operation.ADD_VALUE);
        attr.addTransientModifier(modifier);
        OWNED_SPEED.put(player.getUUID(), modifier);
    }
    private static void setCrawling(ServerPlayer player, boolean active) {
        boolean current = Boolean.TRUE.equals(player.getAttached(Attachments.CRAWL));
        if (active != current) {
            if (active) player.setAttached(Attachments.CRAWL, Boolean.TRUE); else player.removeAttached(Attachments.CRAWL);
        }
    }
    /** Also called by the local player: the tiny transient attachment is synchronized to trackers. */
    public static boolean forceCrawling(Player player) {
        if (!Boolean.TRUE.equals(player.getAttached(Attachments.CRAWL)) || !player.isAlive() || player.isPassenger()
            || player.isFallFlying() || player.isInWater() || player.getAbilities().flying) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            ReverseState state = REVERSE_STATE.get(player.getUUID());
            return state != null && player.level().getGameTime() < state.activeUntil
                && sameGear(state.gear, find(serverPlayer, false));
        }
        return true;
    }
    public static boolean muffle(ServerPlayer player, Holder<GameEvent> event) {
        ForwardState state = FORWARD_STATE.get(player.getUUID());
        long now = player.level().getGameTime();
        if (state == null || !sameGear(state.gear, find(player, true)) || !player.isShiftKeyDown()
            || now < state.muffleTick || now - state.muffleTick > 1) return false;
        return event.is(GameEvent.STEP) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SWIM);
    }
    public static void tick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !eligible(player)) { onDisconnect(player); return; }
        UUID id = player.getUUID();
        long now = level.getGameTime();
        boolean soul = soulGround(player), speedOk = speedAvailable(player);
        Gear forwardGear = find(player, true);
        ForwardState forward = FORWARD_STATE.get(id);
        if (!sameGear(forward == null ? null : forward.gear, forwardGear)) {
            FORWARD_STATE.remove(id);
            forward = forwardGear == null ? null : new ForwardState(forwardGear, player.position());
            if (forward != null) FORWARD_STATE.put(id, forward);
        }
        double desiredSpeed = 0.0D;
        if (forward != null) {
            boolean moved = SoulStepMath.actualStep(moved(forward.last, player.position()));
            forward.last = player.position();
            if (now >= forward.expires) forward.energy = 0;
            boolean collect = soul && moved && player.isSprinting() && !player.isShiftKeyDown();
            boolean spend = !soul && player.onGround() && moved && player.isShiftKeyDown() && forward.energy > 0 && speedOk;
            forward.energy = SoulStepMath.energy(forward.energy,
                FusionConfigStore.signatureInt(FORWARD, "energy_capacity_ticks", 200, 1, 1200), collect, spend);
            if (collect) forward.expires = now + FusionConfigStore.signatureInt(FORWARD, "energy_lifetime_ticks", 400, 20, 72000);
            if (spend) {
                desiredSpeed = SoulStepMath.fastSneak(forward.gear.pair().secondaryLevel());
                forward.muffleTick = now;
                CompendiumDiscoveryTracker.recordDiscovery(player, SOUL, SWIFT);
            }
        }
        Gear reverseGear = find(player, false);
        ReverseState reverse = REVERSE_STATE.get(id);
        if (!sameGear(reverse == null ? null : reverse.gear, reverseGear)) {
            REVERSE_STATE.remove(id);
            setCrawling(player, false);
            reverse = reverseGear == null ? null : new ReverseState(reverseGear, player.position());
            if (reverse != null) REVERSE_STATE.put(id, reverse);
        }
        if (reverse != null) {
            boolean moved = SoulStepMath.actualStep(moved(reverse.last, player.position()));
            reverse.last = player.position();
            if (soul && moved && player.isShiftKeyDown() && now >= reverse.activeUntil) {
                reverse.readyUntil = now + FusionConfigStore.signatureInt(REVERSE, "form_window_ticks", 120, 20, 1200);
            }
            boolean active = now < reverse.activeUntil && speedOk;
            if (!active) reverse.activeUntil = 0;
            setCrawling(player, active);
            if (active) desiredSpeed = Math.max(desiredSpeed, SoulStepMath.fastCrawl(reverse.gear.pair().secondaryLevel()));
        } else setCrawling(player, false);
        if (desiredSpeed > 0.0D && speedOk) setSpeed(player, desiredSpeed); else clearSpeed(player);
    }
    private static boolean oneBlockEntrance(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.horizontalDistanceSqr() < 1.0E-6D) return false;
        Vec3 ahead = player.position().add(horizontal.normalize().scale(0.65D));
        ServerLevel level = player.level();
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(ahead))) return false;
        return level.noCollision(player, player.getDimensions(Pose.SWIMMING).makeBoundingBox(ahead).deflate(1.0E-7D))
            && !level.noCollision(player, player.getDimensions(Pose.CROUCHING).makeBoundingBox(ahead).deflate(1.0E-7D));
    }
    public static boolean activateCrawl(ServerPlayer player) {
        ReverseState state = REVERSE_STATE.get(player.getUUID());
        long now = player.level().getGameTime();
        if (state == null || !sameGear(state.gear, find(player, false))) return false;
        if (now < state.activeUntil) {
            state.activeUntil = 0; setCrawling(player, false); clearSpeed(player); return true;
        }
        if (!eligible(player) || !player.onGround() || !speedAvailable(player) || now >= state.readyUntil
            || !oneBlockEntrance(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.enchantment_fusion.soul_crawl_not_ready"), true);
            return false;
        }
        state.readyUntil = 0;
        state.activeUntil = now + FusionConfigStore.signatureInt(REVERSE, "crawl_duration_ticks", 120, 20, 1200);
        setCrawling(player, true);
        setSpeed(player, SoulStepMath.fastCrawl(state.gear.pair().secondaryLevel()));
        CompendiumDiscoveryTracker.recordDiscovery(player, SWIFT, SOUL);
        return true;
    }
    public static void onDisconnect(ServerPlayer player) {
        FORWARD_STATE.remove(player.getUUID()); REVERSE_STATE.remove(player.getUUID());
        clearSpeed(player); setCrawling(player, false);
    }
    public static void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onDisconnect(player);
        FORWARD_STATE.clear(); REVERSE_STATE.clear(); OWNED_SPEED.clear();
    }
}
