package enchantment_fusion.mechanics.transmute;

import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getStandaloneEnchantLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getTransmutedLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.hasAnyTransmutedEnchants;
import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.resolve;
import static enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport.*;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Relic &amp; Consumable Cross-Domain Transmutations (Totem of Undying save, Glass Bottle XP
 * condensing, Ration conservation) and Dropped World-Item Physics (lava/blast/void homing).
 */
public final class RelicWorldItemTransmutes {
    private RelicWorldItemTransmutes() {}

    public static boolean allowPlayerDeath(ServerLevel level, ServerPlayer player, DamageSource source) {
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            int unbTotem = getTransmutedLevel(held, "unbreaking_totem");
            if (unbTotem > 0) {
                float keepChance = switch (unbTotem) {
                    case 1 -> 0.10F;
                    case 2 -> 0.18F;
                    default -> 0.25F;
                };
                if (level.getRandom().nextFloat() < keepChance) {
                    player.setHealth(1.0F);
                    player.removeAllEffects();
                    grantEffect(player, "regeneration", 900, 1);
                    grantEffect(player, "absorption", 100, 1);
                    grantEffect(player, "fire_resistance", 800, 0);
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 32, 0.5D, 0.5D, 0.5D, 0.2D);
                    level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.15F);
                    return false;
                }
            }
        }
        return true;
    }

    public static void onConsumableFinished(ServerLevel level, ServerPlayer player, ItemStack snapshotBeforeConsume) {
        if (snapshotBeforeConsume == null || snapshotBeforeConsume.isEmpty()) return;
        ItemStack singleCopy = snapshotBeforeConsume.copyWithCount(1);
        level.getServer().execute(() -> {
            if (player.isAlive()) tryRefundRationItemImmediate(level, player, singleCopy);
        });
    }

    public static void tryRefundRationItemImmediate(ServerLevel level, ServerPlayer player, ItemStack singleSnapshot) {
        int rationLv = getTransmutedLevel(singleSnapshot, "unbreaking_ration");
        if (rationLv > 0 && !player.getAbilities().instabuild) {
            CompendiumDiscoveryTracker.recordTransmuteProgress(player, "unbreaking_ration", CompendiumDiscoveryTracker.STATE_MASTERED);
            float saveChance = switch (rationLv) {
                case 1 -> 0.22F;
                case 2 -> 0.35F;
                default -> 0.48F;
            };
            if (level.getRandom().nextFloat() < saveChance) {
                player.getInventory().placeItemBackInInventory(singleSnapshot.copyWithCount(1));
            }
        }
    }

    public static void handleSpecialMendingXpGain(ServerPlayer player, int gained) {
        UUID pid = player.getUUID();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = player.getItemInHand(hand);
            if (st.isEmpty()) continue;

            if (getTransmutedLevel(st, "mending_xp_bottle") > 0) {
                int buf = BOTTLE_XP_BUFFER.merge(pid, gained, Integer::sum);
                int converted = 0;
                while (buf >= 8 && !st.isEmpty() && converted < 8) {
                    buf -= 8;
                    converted++;
                    if (player.totalExperience >= 8) player.giveExperiencePoints(-8);
                    int plainSlot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
                    if (plainSlot >= 0 && player.getInventory().getItem(plainSlot) != st) {
                        player.getInventory().getItem(plainSlot).shrink(1);
                    } else {
                        st.shrink(1);
                    }
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.EXPERIENCE_BOTTLE, 1));
                }
                BOTTLE_XP_BUFFER.put(pid, Math.max(0, buf));
                LAST_PLAYER_XP.put(pid, player.totalExperience);
                return;
            }
            if (getTransmutedLevel(st, "mending_vitality") > 0) {
                player.heal(Math.min(4.0F, 0.35F * gained));
                return;
            }
            if (getTransmutedLevel(st, "mending_replenish") > 0 && st.getCount() < st.getMaxStackSize()) {
                int buf = REPLENISH_XP_BUFFER.merge(pid, gained, Integer::sum);
                if (buf >= 22) {
                    REPLENISH_XP_BUFFER.put(pid, buf - 22);
                    st.grow(1);
                }
                return;
            }
        }
    }

    public static void tickWorldItems(ServerLevel level) {
        if (level.getGameTime() % 10L != 0L || level.players().isEmpty()) return;
        Set<Integer> visitedIds = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB columnBox = new AABB(
                player.getX() - 96.0D, level.getMinY() - 96.0D, player.getZ() - 96.0D,
                player.getX() + 96.0D, player.getY() + 64.0D, player.getZ() + 96.0D
            );
            List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, columnBox, Entity::isAlive);
            for (ItemEntity ie : nearby) {
                if (!visitedIds.add(ie.getId())) continue;
                ItemStack st = ie.getItem();
                if (st.isEmpty() || !hasAnyTransmutedEnchants(st)) continue;

                if (getStandaloneEnchantLevel(st, "minecraft:fire_protection") > 0 && resolve("minecraft:fire_protection", st).isPresent()) {
                    ie.setInvulnerable(true);
                    ie.clearFire();
                    if (ie.isInLava()) ie.setDeltaMovement(ie.getDeltaMovement().x * 0.8D, 0.12D, ie.getDeltaMovement().z * 0.8D);
                }
                if (getStandaloneEnchantLevel(st, "minecraft:blast_protection") > 0 && resolve("minecraft:blast_protection", st).isPresent()) {
                    ie.setInvulnerable(true);
                }
                if (getStandaloneEnchantLevel(st, "minecraft:loyalty") > 0 && resolve("minecraft:loyalty", st).isPresent()) {
                    ie.setInvulnerable(true);
                    if (ie.getY() <= level.getMinY() + 6 || ie.isInLava()) {
                        ServerPlayer targetOwner = (ie.getOwner() instanceof ServerPlayer sp && sp.isAlive()) ? sp : player;
                        ie.setNoPickUpDelay();
                        ie.setNoGravity(true);
                        if (targetOwner.getInventory().add(st.copy())) {
                            ie.discard();
                            level.playSound(null, targetOwner.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.7F, 1.2F);
                            continue;
                        } else {
                            ie.setPos(targetOwner.getX(), targetOwner.getY() + 0.5D, targetOwner.getZ());
                            ie.setDeltaMovement(Vec3.ZERO);
                        }
                    }
                }
                if (getTransmutedLevel(st, "sticky_root_curse") > 0 && ie.getOwner() instanceof ServerPlayer owner && owner.isAlive()) {
                    if (owner.getInventory().add(st.copy())) {
                        ie.discard();
                        level.playSound(null, owner.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 0.7F);
                    }
                }
            }
        }
    }
}
