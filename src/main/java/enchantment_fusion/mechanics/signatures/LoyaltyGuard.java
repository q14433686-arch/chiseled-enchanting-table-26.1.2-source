package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.FusionNetworking.LoyaltyGuardActivationPayload;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** The explicitly selected Loyalty ▷ Protection rescue; the opposite direction is untouched. */
public final class LoyaltyGuard {
    public static final String PRIMARY = "minecraft:loyalty";
    public static final String SECONDARY = "minecraft:protection";
    public static final String SIGNATURE = "loyalty_guard";
    private static final ResourceKey<DamageType> EXECUTION = ResourceKey.create(Registries.DAMAGE_TYPE, EnchantmentFusion.id(SIGNATURE));
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    // A save can trigger another player's save. Prevent retaliation chains from recurring indefinitely.
    private static final ThreadLocal<List<UUID>> ACTIVE = new ThreadLocal<>();

    private record Candidate(EquipmentSlot slot, ItemStack stack, int pairIndex, FusedPair pair) {}
    private LoyaltyGuard() {}

    public static boolean isExecuting() {
        List<UUID> active = ACTIVE.get();
        return active != null && !active.isEmpty();
    }

    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && PRIMARY.equals(pair.primaryId()) && SECONDARY.equals(pair.secondaryId());
    }

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return isPair(pair) && SIGNATURE.equals(signature);
    }

    /** Chosen radius: 3 + 2 per Loyalty level + 0.5 per Protection level, capped at 24 blocks. */
    public static double radius(int loyalty, int protection) {
        return Math.min(24.0D, 3.0D + 2.0D * Math.max(1, loyalty) + 0.5D * Math.max(1, protection));
    }

    /** False means the lethal damage has been intercepted and the fusion has paid exactly once. */
    public static boolean allowDeath(ServerLevel level, ServerPlayer player, DamageSource source) {
        if (source == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.isSpectator()) return true;
        List<UUID> active = ACTIVE.get();
        if (active != null && active.contains(player.getUUID())) return true;
        List<Candidate> candidates = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!VesselCapacityRules.isPotentialEquipmentHost(stack)) continue;
            var pairs = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int i = 0; i < pairs.size(); i++) {
                FusedPair pair = pairs.pairs().get(i);
                if (isPair(pair) && SignatureCatalog.resolve(pair).map(e -> SIGNATURE.equals(e.id())).orElse(false)) {
                    candidates.add(new Candidate(slot, stack, i, pair));
                }
            }
        }
        candidates.sort(Comparator.<Candidate>comparingInt(c -> c.pair().primaryLevel()).reversed()
            .thenComparing(Comparator.comparingInt((Candidate c) -> c.pair().secondaryLevel()).reversed()));
        boolean cannotPreserve = false;
        for (Candidate candidate : candidates) {
            // Build the full post-payment stack before changing the player's actual equipment.
            ItemStack paid = afterUse(candidate.stack(), candidate.pairIndex(), level.registryAccess());
            if (paid == null) { cannotPreserve = true; continue; }
            if (player.getItemBySlot(candidate.slot()) != candidate.stack()) continue;
            ItemStack visual = candidate.stack().copyWithCount(1);
            boolean nested = active != null && !active.isEmpty();
            if (active == null) { active = new ArrayList<>(); ACTIVE.set(active); }
            active.add(player.getUUID());
            try {
                player.setItemSlot(candidate.slot(), paid);
                player.setHealth(1.0F);
                DeathProtection.TOTEM_OF_UNDYING.applyEffects(visual, player);
                CompendiumDiscoveryTracker.recordDiscovery(player, PRIMARY, SECONDARY);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(),
                    32, 0.45D, 0.5D, 0.45D, 0.12D);
                level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (ServerPlayNetworking.canSend(player, LoyaltyGuardActivationPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new LoyaltyGuardActivationPayload(visual));
                }
                if (!nested) executeNearby(level, player, candidate.pair());
                return false;
            } finally {
                active.removeLast();
                if (active.isEmpty()) ACTIVE.remove();
            }
        }
        if (cannotPreserve) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.guard_retention_full"), false);
        }
        return true;
    }

    /** Only mod-created, unchanged, unshared projections may be removed during station management. */
    public static boolean hasUnambiguousProjection(ItemStack stack, FusedPair pair, FusedEnchantmentList others) {
        if (!isPair(pair) || others.containsEnchantmentId(PRIMARY)) return false;
        int marker = stack.getOrDefault(FusionDataComponents.LOYALTY_GUARD_PROJECTION, 0);
        if (marker <= 0 || marker != pair.primaryLevel()) return false;
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.unwrapKey().map(key -> PRIMARY.equals(key.identifier().toString())).orElse(false)) {
                return enchants.getLevel(holder) == marker;
            }
        }
        return false;
    }

    /** Returns a paid copy or null if preserving the secondary is impossible; never mutates input. */
    public static @Nullable ItemStack afterUse(ItemStack source, int index, HolderLookup.Provider registries) {
        if (source == null || source.isEmpty()) return null;
        FusedEnchantmentList list = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (index < 0 || index >= list.size()) return null;
        FusedPair pair = list.pairs().get(index);
        if (!isPair(pair)) return null;
        boolean exhausted = pair.primaryLevel() == 1;
        FusedEnchantmentList remaining = exhausted ? list.removeAt(index)
            : list.replaceAt(index, pair.withPrimaryLevel(pair.primaryLevel() - 1));
        ItemStack paid = source.copy();
        ItemEnchantments current = paid.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(current);
        boolean changedVanilla = false;

        // Only a recorded, still-unmodified projection created by this mod may be adjusted.
        int marker = paid.getOrDefault(FusionDataComponents.LOYALTY_GUARD_PROJECTION, 0);
        Holder<Enchantment> loyalty = FusionStationOperations.lookupEnchantment(registries, PRIMARY).orElse(null);
        boolean sharedLoyalty = false;
        for (int i = 0; i < list.size(); i++) {
            if (i != index && list.pairs().get(i).involves(PRIMARY)) sharedLoyalty = true;
        }
        if (marker > 0) {
            if (!sharedLoyalty && loyalty != null && marker == pair.primaryLevel() && current.getLevel(loyalty) == marker) {
                enchants.set(loyalty, marker - 1);
                changedVanilla = true;
                if (marker > 1) paid.set(FusionDataComponents.LOYALTY_GUARD_PROJECTION, marker - 1);
                else paid.remove(FusionDataComponents.LOYALTY_GUARD_PROJECTION);
            } else {
                // Ambiguous legacy/external edits are deliberately left untouched.
                paid.remove(FusionDataComponents.LOYALTY_GUARD_PROJECTION);
            }
        }

        if (exhausted) {
            Holder<Enchantment> protection = FusionStationOperations.lookupEnchantment(registries, SECONDARY).orElse(null);
            if (protection != null && current.getLevel(protection) == 0 && !remaining.containsEnchantmentId(SECONDARY)) {
                // Store the exact level even on a non-native carrier; never infer a new cross-domain effect.
                enchants.set(protection, pair.secondaryLevel());
                changedVanilla = true;
            } else {
                List<RetainedEnchant> old = paid.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.of());
                if (old.size() >= FusionDataComponents.MAX_RETAINED_ENCHANTS) return null;
                List<RetainedEnchant> retained = new ArrayList<>(old);
                retained.add(new RetainedEnchant(SECONDARY, pair.secondaryLevel()));
                paid.set(FusionDataComponents.RETAINED_ENCHANTS, List.copyOf(retained));
            }
        }
        if (remaining.isEmpty()) paid.remove(FusionDataComponents.FUSED_PAIRS);
        else paid.set(FusionDataComponents.FUSED_PAIRS, remaining);
        if (changedVanilla) EnchantmentHelper.setEnchantments(paid, enchants.toImmutable());
        FusionStationOperations.syncGlintOverride(paid);
        return paid;
    }

    /** Expose previously shadowed levels when their independent same-name enchantment is removed. */
    public static boolean materializeRetained(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return false;
        List<RetainedEnchant> old = stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.of());
        if (old.isEmpty()) return false;
        FusedEnchantmentList pairs = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        List<RetainedEnchant> remaining = new ArrayList<>();
        boolean changed = false;
        for (RetainedEnchant entry : old) {
            Optional<Holder<Enchantment>> holder = FusionStationOperations.lookupEnchantment(registries, entry.id());
            if (holder.isPresent() && !pairs.containsEnchantmentId(entry.id()) && enchants.getLevel(holder.get()) == 0) {
                enchants.set(holder.get(), entry.level());
                changed = true;
            } else remaining.add(entry);
        }
        if (!changed) return false;
        EnchantmentHelper.setEnchantments(stack, enchants.toImmutable());
        if (remaining.isEmpty()) stack.remove(FusionDataComponents.RETAINED_ENCHANTS);
        else stack.set(FusionDataComponents.RETAINED_ENCHANTS, List.copyOf(remaining));
        FusionStationOperations.syncGlintOverride(stack);
        return true;
    }

    private static void executeNearby(ServerLevel level, ServerPlayer owner, FusedPair pair) {
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(EXECUTION);
        if (holder.isEmpty()) {
            EnchantmentFusion.LOGGER.error("Loyalty guard damage type is missing; the rescue succeeded but its execution could not run");
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.guard_damage_unavailable"), false);
            return;
        }
        DamageSource source = new DamageSource(holder.get(), owner);
        double radius = radius(pair.primaryLevel(), pair.secondaryLevel());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(owner.blockPosition()).inflate(radius),
            entity -> eligibleTarget(owner, entity))) {
            if (target.distanceToSqr(owner) > radius * radius) continue;
            // Armor is not damaged by this custom source; normal damage events can still cancel it.
            double health = (double) target.getHealth() + target.getAbsorptionAmount();
            float damage = (float) Math.min(Float.MAX_VALUE / 1024.0D, Math.max(1000.0D, health * 8.0D + 16.0D));
            try { target.hurtServer(level, source, damage); }
            catch (RuntimeException ex) {
                EnchantmentFusion.LOGGER.error("Loyalty guard could not affect {}", target.getUUID(), ex);
            }
        }
    }

    public static boolean eligibleTarget(ServerPlayer owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(owner) || owner.isAlliedTo(target) || target instanceof AbstractVillager) return false;
        if (target instanceof TamableAnimal tame && tame.isTame()) return false;
        if (target instanceof AbstractHorse horse && horse.isTamed()) return false;
        if (target instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            LivingEntity theirOwner = ownable.getOwner();
            if (theirOwner == null || theirOwner instanceof Player || theirOwner.isAlliedTo(owner)) return false;
        }
        if (target instanceof Player other) {
            return !other.getAbilities().invulnerable && owner.canHarmPlayer(other);
        }
        if (target instanceof NeutralMob) return target instanceof Mob mob && mob.getTarget() == owner;
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == owner);
    }
}
