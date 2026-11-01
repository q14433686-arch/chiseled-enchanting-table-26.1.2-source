package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** First real-action adapters for 40.1: attack-theme hits leave a manually reeled target. */
public final class GrapnelSkills {
    public static final String SIGNATURE = "spirit_grapnel", LURE = "minecraft:lure";
    private static final int MAX_OWNERS = 256;
    private static final long MARK_TICKS = 160;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Mark(Gear gear, ResourceKey<Level> dimension, UUID target, long until) {}
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private GrapnelSkills() {}
    public static void init() {
        ConfirmedMelee.register(GrapnelSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("grapnel_attack", GrapnelSkills::applies);
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && LURE.equals(pair.secondaryId())
            && SignatureCatalog.resolve(pair).map(entry -> SIGNATURE.equals(entry.id()) && "*".equals(entry.primaryId())).orElse(false);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair())) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); Gear gear = selected(hit.weapon());
        if (gear == null || !ready(owner) || hit.slot() != EquipmentSlot.MAINHAND || owner.getMainHandItem() != hit.weapon()
            || !hit.eligibleBefore() || !hit.target().isAlive() || !MeleeAttackThemes.matchesTarget(gear.pair().primaryId(), hit.target())
            || (MeleeAttackThemes.SHARP.equals(gear.pair().primaryId()) && !hit.full()) || !owns(owner, gear)
            || (MARKS.size() >= MAX_OWNERS && !MARKS.containsKey(owner.getUUID()))) return;
        MARKS.put(owner.getUUID(), new Mark(gear, owner.level().dimension(), hit.target().getUUID(), owner.level().getGameTime() + MARK_TICKS));
        owner.level().sendParticles(ParticleTypes.FISHING, hit.target().getX(), hit.target().getY(0.6D), hit.target().getZ(),
            8, 0.2D, 0.2D, 0.2D, 0.02D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_marked"), true);
    }
    private static boolean valid(ServerPlayer owner, Mark mark) {
        return ready(owner) && owner.level().dimension().equals(mark.dimension()) && owner.level().getGameTime() < mark.until()
            && owns(owner, mark.gear());
    }
    public static boolean hasEquipped(ServerPlayer player) { return selected(player.getMainHandItem()) != null; }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Mark mark = MARKS.get(owner.getUUID());
        if (mark == null || !valid(owner, mark)) {
            MARKS.remove(owner.getUUID()); owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_need"), true); return false;
        }
        if (owner.getMainHandItem() != mark.gear().stack()) return false;
        ServerLevel level = owner.level();
        if (!(level.getEntity(mark.target()) instanceof LivingEntity target) || !MeleeAttackThemes.eligible(owner, target, mark.gear().pair().primaryId())) return false;
        Vec3 from = owner.getEyePosition(), to = target.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > 12.0D * 12.0D || !VirtualCombatSight.clear(level, owner, from, to)) return false;
        double awayX = target.getX() - owner.getX(), awayZ = target.getZ() - owner.getZ();
        if (awayX * awayX + awayZ * awayZ < 0.01D) return false;
        Vec3 before = target.getDeltaMovement();
        double force = Math.min(1.25D, 0.45D + 0.10D * Math.clamp(mark.gear().pair().secondaryLevel(), 1, 8));
        target.knockback(force, awayX, awayZ); // ordinary knockback resistance and movement collisions still apply
        if (target.getDeltaMovement().distanceToSqr(before) < 1.0E-8D) return false;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        MARKS.remove(owner.getUUID(), mark);
        level.sendParticles(ParticleTypes.FISHING, target.getX(), target.getY(0.6D), target.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.gear().pair().primaryId(), mark.gear().pair().secondaryId());
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(MARKS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !valid(owner, entry.getValue()) || owner.level().getEntity(entry.getValue().target()) == null)
                MARKS.remove(entry.getKey(), entry.getValue());
        }
    }
    public static void clearPlayer(UUID id) {
        MARKS.remove(id);
        MARKS.entrySet().removeIf(entry -> entry.getValue().target().equals(id));
    }
    public static void clearServer() { MARKS.clear(); }
}
