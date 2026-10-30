package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Explicit two-press active skill; ordinary trident and spear use is left untouched. */
public final class MirageSnapbackSkill {
    public static final int ANCHOR_TICKS = 80;
    public static final int INPUT_COOLDOWN_TICKS = 8;
    private record Anchor(ResourceKey<Level> dimension, Vec3 position, long expires, String pairFingerprint) {}
    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();

    private MirageSnapbackSkill() {}

    private static @Nullable FusedPair pair(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedEnchantmentList pairs = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair pair : pairs.pairs()) {
            if (SignaturePairs.resolve(pair).map(entry -> entry.id().equals("mirage_snapback")).orElse(false)) return pair;
        }
        return null;
    }

    public static boolean canUse(Player player, ItemStack stack) {
        return player.isShiftKeyDown() && !player.getCooldowns().isOnCooldown(stack) && pair(stack) != null;
    }

    public static boolean use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!canUse(player, stack)) return false;
        FusedPair pair = pair(stack);
        if (pair == null) return false;
        long now = level.getGameTime();
        Anchor anchor = ANCHORS.get(player.getUUID());
        boolean canReturn = anchor != null && now <= anchor.expires()
            && level.dimension().equals(anchor.dimension()) && pair.fingerprint().equals(anchor.pairFingerprint());
        if (canReturn) {
            Vec3 offset = anchor.position().subtract(player.position());
            BlockPos destination = BlockPos.containing(anchor.position());
            // Never force-load a chunk or teleport into a wall/closed door.
            if (!level.isLoaded(destination) || !level.noCollision(player, player.getBoundingBox().move(offset))) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.mirage_blocked"), true);
                player.getCooldowns().addCooldown(stack, INPUT_COOLDOWN_TICKS);
                return true;
            }
            Vec3 departure = player.position();
            ANCHORS.remove(player.getUUID());
            player.teleportTo(anchor.position().x, anchor.position().y, anchor.position().z);
            player.resetFallDistance();
            for (LivingEntity mob : SignatureRuntimeSupport.nearbyHostiles(level, player, departure, 4.0D, null)) {
                mob.hurtServer(level, level.damageSources().magic(), 3.2F + 0.6F * pair.primaryLevel());
                Vec3 pull = anchor.position().subtract(mob.position()).normalize().scale(0.55D);
                mob.push(pull.x, 0.25D, pull.z);
                mob.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.SPLASH, departure.x, departure.y + 0.8D, departure.z,
                24, 0.5D, 0.4D, 0.5D, 0.1D);
            level.sendParticles(ParticleTypes.SPLASH, anchor.position().x, anchor.position().y + 0.8D,
                anchor.position().z, 20, 0.4D, 0.4D, 0.4D, 0.08D);
            level.playSound(null, destination, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.35F);
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.mirage_return"), true);
        } else {
            ANCHORS.put(player.getUUID(), new Anchor(level.dimension(), player.position(), now + ANCHOR_TICKS, pair.fingerprint()));
            Vec3 dash = player.getLookAngle().normalize().scale(player.isInWaterOrRain() ? 1.3D : 0.85D);
            player.setDeltaMovement(dash.x, Math.max(0.18D, dash.y * 0.7D), dash.z);
            player.hurtMarked = true;
            player.resetFallDistance();
            level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.6D, player.getZ(),
                20, 0.4D, 0.3D, 0.4D, 0.08D);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.mirage_anchor"), true);
        }
        player.getCooldowns().addCooldown(stack, INPUT_COOLDOWN_TICKS);
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
        return true;
    }

    public static void onPlayerDisconnect(UUID playerId) { ANCHORS.remove(playerId); }
    public static void clearRuntimeState() { ANCHORS.clear(); }
}
