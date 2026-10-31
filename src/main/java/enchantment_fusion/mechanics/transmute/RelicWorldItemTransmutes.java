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
                int buf = (int) Math.min(64L, (long) BOTTLE_XP_BUFFER.getOrDefault(pid, 0) + gained);
                int converted = 0;
                while (buf >= 8 && !st.isEmpty() && converted < 8 && hasAtLeastEightSpendableXp(player)) {
                    var inventory = player.getInventory();
                    int plainSlot = inventory.findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
                    ItemStack bottle = plainSlot >= 0 && inventory.getItem(plainSlot) != st ? inventory.getItem(plainSlot) : st;
                    ItemStack output = new ItemStack(Items.EXPERIENCE_BOTTLE);
                    boolean freesInventorySlot = bottle.getCount() == 1 && (bottle != st || hand == InteractionHand.MAIN_HAND);
                    if (inventory.getSlotWithRemainingSpace(output) < 0 && inventory.getFreeSlot() < 0 && !freesInventorySlot) break;
                    // Each conversion pays both actual spendable XP and one real glass bottle;
                    // no output is created if either resource or inventory space is missing.
                    player.giveExperiencePoints(-8);
                    bottle.shrink(1);
                    inventory.add(output);
                    buf -= 8;
                    converted++;
                }
                BOTTLE_XP_BUFFER.put(pid, Math.max(0, buf));
                return;
            }
            if (getTransmutedLevel(st, "mending_vitality") > 0) {
                player.heal(Math.min(4.0F, 0.35F * gained));
                return;
            }
            // Replenishment is handled at the actual XP-credit hook, not by comparing a
            // player's total XP on a later tick. Keep hand priority over bottle/vitality.
            if (getTransmutedLevel(st, "mending_replenish") > 0) return;
        }
    }

    /** Vanilla totalExperience is not the spendable balance after levels are spent. */
    static boolean hasAtLeastEightSpendableXp(ServerPlayer player) {
        if (player.experienceLevel >= 2) return true;
        if (player.experienceLevel < 1) return false;
        int pointsIntoLevel = (int) Math.floor(player.experienceProgress * player.getXpNeededForNextLevel() + 0.0001D);
        return 7 + pointsIntoLevel >= 8;
    }

    /** Only the hazard named by the enchantment is blocked; unrelated damage remains vanilla. */
    public static boolean protectsDroppedItem(ItemStack stack, DamageSource source) {
        if (stack == null || stack.isEmpty() || source == null || !hasAnyTransmutedEnchants(stack)
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypeTags.IS_FIRE) && getStandaloneEnchantLevel(stack, "minecraft:fire_protection") > 0
            && resolve("minecraft:fire_protection", stack).isPresent()) return true;
        return source.is(DamageTypeTags.IS_EXPLOSION) && getStandaloneEnchantLevel(stack, "minecraft:blast_protection") > 0
            && resolve("minecraft:blast_protection", stack).isPresent();
    }

    private static ServerPlayer verifiedOwner(ItemEntity item) {
        if (!(item.getOwner() instanceof ServerPlayer owner) || !owner.isAlive()) return null;
        UUID pickupTarget = ((DroppedItemOwnerAccess) (Object) item).ef$pickupTarget();
        return pickupTarget == null || pickupTarget.equals(owner.getUUID()) ? owner : null;
    }

    /** Never discard an uninserted remainder, including partial adds or creative full inventories. */
    private static void returnToOwner(ServerLevel level, ItemEntity item, ServerPlayer owner, boolean rescue) {
        ItemStack original = item.getItem();
        int before = countMatching(owner, original);
        ItemStack offered = original.copy();
        owner.getInventory().add(offered);
        int inserted = Math.clamp(countMatching(owner, original) - before, 0, original.getCount());
        if (inserted > 0) {
            ItemStack remaining = original.copyWithCount(original.getCount() - inserted);
            if (remaining.isEmpty()) item.discard();
            else item.setItem(remaining);
            level.playSound(null, owner.blockPosition(), rescue ? SoundEvents.TRIDENT_RETURN : SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, rescue ? 0.7F : 0.5F, rescue ? 1.2F : 0.7F);
        }
        if (rescue && item.isAlive()) {
            // Retain a real entity for the uninserted remainder and prevent bystanders taking it.
            item.setTarget(owner.getUUID());
            item.setNoPickUpDelay();
            item.setPos(owner.getX(), owner.getY() + 0.5D, owner.getZ());
            item.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static int countMatching(ServerPlayer player, ItemStack sample) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, sample)) count += item.getCount();
        }
        return count;
    }

    /** Process one real, already-spawned item; no nearby player's identity is used as a fallback. */
    public static void processWorldItem(ServerLevel level, ItemEntity item) {
        if (item == null || !item.isAlive() || item.level() != level) return;
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !hasAnyTransmutedEnchants(stack)) return;
        if (getStandaloneEnchantLevel(stack, "minecraft:fire_protection") > 0
            && resolve("minecraft:fire_protection", stack).isPresent()) {
            item.clearFire();
            if (item.isInLava()) item.setDeltaMovement(item.getDeltaMovement().x * 0.8D, 0.12D, item.getDeltaMovement().z * 0.8D);
        }
        ServerPlayer owner = verifiedOwner(item);
        if (owner != null && getStandaloneEnchantLevel(stack, "minecraft:loyalty") > 0
            && resolve("minecraft:loyalty", stack).isPresent()
            && (item.getY() <= level.getMinY() + 6 || item.isInLava())) {
            returnToOwner(level, item, owner, true);
            return;
        }
        if (owner != null && getTransmutedLevel(stack, "sticky_root_curse") > 0) {
            returnToOwner(level, item, owner, false);
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
                processWorldItem(level, ie);
            }
        }
    }
}
