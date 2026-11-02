package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The 08 Looting-primary disarm: one real item, no copied equipment or player targets. */
public final class MeleeDisarmSkills {
    public static final String LOOTING = "minecraft:looting", KNOCKBACK = "minecraft:knockback", SIGNATURE = "trophy_shockwave";
    private static final String BINDING = "minecraft:binding_curse";
    private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, EnchantmentFusion.id("disarm_immune"));
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Set<FusionCarrier> EQUIPMENT = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR,
        FusionCarrier.MACE, FusionCarrier.TRIDENT, FusionCarrier.BOW, FusionCarrier.CROSSBOW, FusionCarrier.SHIELD,
        FusionCarrier.PICKAXE, FusionCarrier.SHOVEL, FusionCarrier.HOE, FusionCarrier.HEAD_ARMOR,
        FusionCarrier.CHEST_ARMOR, FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR);
    private record Cooldown(ResourceKey<Level> dimension, long until) {}
    private static final Map<UUID, Cooldown> COOLDOWNS = new HashMap<>();
    private static final ThreadLocal<Boolean> TRANSFERRING = ThreadLocal.withInitial(() -> false);
    static record Claim(ServerPlayer owner, Mob target, ItemStack weapon, FusedPair pair, EquipmentSlot slot,
                        ItemStack actual, ItemStack snapshot) {}
    private MeleeDisarmSkills() {}

    public static boolean selectedSignature(@Nullable SignatureEntry entry) {
        return entry != null && SIGNATURE.equals(entry.id()) && LOOTING.equals(entry.primaryId()) && KNOCKBACK.equals(entry.secondaryId());
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && LOOTING.equals(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId())
            && SignatureCatalog.resolve(pair).map(MeleeDisarmSkills::selectedSignature).orElse(false);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return isPair(pair) && stack != null && !stack.isEmpty() && MeleeAttackThemes.supportsWeapon(FusionCarrier.classify(stack));
    }
    private static @Nullable FusedPair selected(ItemStack weapon) {
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, weapon)) return pair;
        return null;
    }
    private static boolean removable(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1 || FusionCarrier.classify(stack).stream().noneMatch(EQUIPMENT::contains)
            || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
        if (stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream().anyMatch(p -> p.involves(BINDING))
            || stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.<String, Integer>of()).getOrDefault(BINDING, 0) > 0) return false;
        return stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream().noneMatch(e -> BINDING.equals(e.id()));
    }
    private static boolean eligible(ServerPlayer owner, Mob target) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return id != null && "minecraft".equals(id.getNamespace()) && !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(IMMUNE)
            && target.getType() != EntityType.ENDER_DRAGON && target.getType() != EntityType.WITHER
            && target.getType() != EntityType.WARDEN && target.getType() != EntityType.ELDER_GUARDIAN
            && target.getMaxHealth() <= 100.0F && target.isAlive() && owner.level().getEntity(target.getUUID()) == target
            && owner.distanceToSqr(target) <= 36.0D && MeleeAttackThemes.eligible(owner, target, MeleeAttackThemes.SHARP);
    }
    static @Nullable Claim prepare(MeleeImpulseSkills.Attack frame, ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player();
        if (frame.ambiguous || Boolean.TRUE.equals(TRANSFERRING.get()) || !hit.full() || !(hit.target() instanceof Mob target) || !eligible(owner, target)
            || owner.getMainHandItem() != hit.weapon() || COOLDOWNS.containsKey(target.getUUID())) return null;
        FusedPair pair = selected(hit.weapon());
        if (pair == null) return null;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack actual = target.getItemBySlot(slot);
            if (removable(actual)) return new Claim(owner, target, hit.weapon(), pair, slot, actual, actual.copy());
        }
        return null;
    }
    private static boolean unchanged(Claim claim) {
        return claim.owner().isAlive() && !claim.owner().isSpectator() && claim.owner().getMainHandItem() == claim.weapon()
            && claim.weapon().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(claim.pair())
            && applies(claim.pair(), claim.weapon()) && eligible(claim.owner(), claim.target())
            && claim.target().getItemBySlot(claim.slot()) == claim.actual() && ItemStack.matches(claim.actual(), claim.snapshot())
            && removable(claim.actual());
    }
    private static boolean presentIdentity(ServerLevel level, ItemEntity drop, Claim claim) {
        return drop.isAlive() && level.getEntity(drop.getUUID()) == drop && drop.getItem() == claim.actual();
    }
    private static boolean present(ServerLevel level, ItemEntity drop, Claim claim) {
        return presentIdentity(level, drop, claim) && ItemStack.matches(drop.getItem(), claim.snapshot());
    }
    private static boolean stillEquipped(Claim claim) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) if (claim.target().getItemBySlot(slot) == claim.actual()) return true;
        return false;
    }
    /** Spawn a temporarily unpickable exact stack first; only clear the still-identical slot after registration. */
    static void settle(Claim claim) {
        if (Boolean.TRUE.equals(TRANSFERRING.get())) return;
        TRANSFERRING.set(true);
        try { transfer(claim); }
        catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Unable to complete real disarm transfer", error); }
        finally { TRANSFERRING.remove(); }
    }
    private static void transfer(Claim claim) {
        ServerPlayer owner = claim.owner(); Mob target = claim.target(); ServerLevel level = owner.level();
        if (!unchanged(claim) || COOLDOWNS.containsKey(target.getUUID()) || COOLDOWNS.size() >= 256) return;
        Vec3 towardOwner = owner.position().subtract(target.position());
        if (!Double.isFinite(towardOwner.x) || !Double.isFinite(towardOwner.z) || towardOwner.horizontalDistanceSqr() < 1.0E-6D) return;
        towardOwner = new Vec3(towardOwner.x, 0, towardOwner.z).normalize();
        ItemEntity drop = new ItemEntity(level, target.getX(), target.getY(0.65D), target.getZ(), claim.actual());
        drop.setNeverPickUp();
        Cooldown reservation = new Cooldown(level.dimension(), level.getGameTime() + 100L);
        COOLDOWNS.put(target.getUUID(), reservation);
        boolean committed = false;
        try {
            if (!level.addFreshEntity(drop) || !present(level, drop, claim) || !unchanged(claim)) return;
            target.setItemSlot(claim.slot(), ItemStack.EMPTY);
            ItemStack now = target.getItemBySlot(claim.slot());
            if (!present(level, drop, claim)) {
                if (now.isEmpty() && ItemStack.matches(claim.actual(), claim.snapshot())) target.setItemSlot(claim.slot(), claim.actual());
                return;
            }
            // A handler may have moved the exact object to another slot during removal.
            if (stillEquipped(claim)) return;
            committed = true;
            drop.setDefaultPickUpDelay();
            drop.setDeltaMovement(towardOwner.x * 0.35D, 0.24D, towardOwner.z * 0.35D);
            target.knockback(0.45D + 0.08D * Math.clamp(claim.pair().secondaryLevel(), 1, 6), towardOwner.x, towardOwner.z);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.65D), target.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
            CompendiumDiscoveryTracker.recordDiscovery(owner, claim.pair().primaryId(), claim.pair().secondaryId());
        } finally {
            if (!committed) {
                if (presentIdentity(level, drop, claim)) {
                    if (stillEquipped(claim)) drop.discard();
                    else drop.setDefaultPickUpDelay(); // never discard the only proven surviving object
                }
                COOLDOWNS.remove(target.getUUID(), reservation);
            }
        }
    }
    static void tickServer(MinecraftServer server) {
        COOLDOWNS.entrySet().removeIf(e -> {
            ServerLevel level = server.getLevel(e.getValue().dimension());
            return level == null || level.getGameTime() >= e.getValue().until();
        });
    }
    static void clearServer() { COOLDOWNS.clear(); TRANSFERRING.remove(); }
}
