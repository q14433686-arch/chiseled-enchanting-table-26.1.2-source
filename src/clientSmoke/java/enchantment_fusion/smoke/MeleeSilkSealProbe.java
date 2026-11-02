package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeSilkSealSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Candidate real-hit paths; artificial charge/ticking are not complete gameplay acceptance. */
final class MeleeSilkSealProbe {
    private MeleeSilkSealProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack) throws Exception {
        FusedPair pair = FusedPair.clean(MeleeSilkSealSkills.SILK, 1, attack, 4);
        SharedMeleeFixture.checkMeleeStation(player.level(), pair);
        boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
        Set<UUID> denied = new HashSet<>();
        float[] permissionAmount = {0.0F}; boolean[] observe = {false}, nest = {false}, nestAfter = {false};
        LivingEntity[] nestedTarget = {null}, nestedTrigger = {null};
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (observe[0] && source.getEntity() == player) permissionAmount[0] = amount;
            if (nest[0] && source.getEntity() == player && entity == nestedTrigger[0]) {
                nest[0] = false;
                nestedTarget[0].invulnerableTime = 0;
                player.attack(nestedTarget[0]);
                nestedTarget[0].invulnerableTime = 0;
            }
            return !denied.contains(entity.getUUID());
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, damage, blocked) -> {
            if (nestAfter[0] && entity == nestedTrigger[0] && MeleeSilkSealSkills.isDeferredHit(entity, source)) {
                nestAfter[0] = false;
                nestedTarget[0].invulnerableTime = 0;
                player.attack(nestedTarget[0]);
            }
        });
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            MeleeSilkSealSkills.clearPlayer(player.getUUID());
            LivingEntity target = f.spawn(bane, 2, 0), other = f.spawn(bane, 3, 0), wrong = f.spawn(!bane, 2, 3);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, -3);
            ItemStack weapon = f.equip(Items.DIAMOND_SWORD, pair);
            f.hit(target, true); float baseline = 200 - target.getHealth(); require(baseline > 0, "Ordinary real hit required");
            target.setHealth(200); double speed = target.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            require(MeleeSilkSealSkills.hasEquipped(player) && MeleeSilkSealSkills.activate(player), "Seal must be activatable");
            Object armed = state(player); require(armed != null && target(armed) == null, "One pending preparation required");
            require(!MeleeSilkSealSkills.activate(player), "Preparation cannot be rerolled");
            denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(target.getHealth() == 200 && state(player) == armed && target(armed) == null, "Denied damage cannot be sealed");
            if (!sharp) { f.hit(wrong, true); require(state(player) == armed && target(armed) == null, "Wrong creature type cannot be sealed"); }
            f.hit(villager, true); require(state(player) == armed && target(armed) == null, "Villagers cannot be sealed");
            f.hit(target, false); require(state(player) == armed && target(armed) == null && target.getHealth() < 200, "Partial hit remains ordinary");
            target.setHealth(200); observe[0] = true; permissionAmount[0] = 0; f.hit(target, true); observe[0] = false;
            require(permissionAmount[0] > 0, "Damage permission must see the original positive amount");
            require(state(player) == armed && target(armed) == target && target.getHealth() == 200, "Accepted full hit is deferred, not a synthetic hit");
            require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < speed, "The sealed target is actually slowed");
            f.hit(other, true); require(state(player) == armed && target(armed) == target, "Another creature cannot release the stored hit");
            denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(state(player) == armed && target.getHealth() == 200, "Denied release cannot consume or deal the stored hit");
            f.hit(target, true);
            require(state(player) == null && target.getHealth() < 200 - baseline * 1.5F, "Same weapon releases one combined real hit");
            require(Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed) < 0.0001D, "Release restores owned slowdown");
            target.setHealth(200); f.hit(target, true);
            require(Math.abs((200 - target.getHealth()) - baseline) < 0.01F, "Later hits cannot repeat the stored damage");

            target.setHealth(200); require(MeleeSilkSealSkills.activate(player), "Rearm for explicit cancellation"); f.hit(target, true);
            Object cancelled = state(player);
            require(target(cancelled) == target && MeleeSilkSealSkills.activate(player), "Second activation dissipates an existing seal");
            require(state(player) == null && target.getHealth() == 200, "Cancellation cannot damage target");
            require(Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed) < 0.0001D, "Cancellation restores slowdown");

            require(MeleeSilkSealSkills.activate(player), "Rearm for equipment identity"); f.hit(target, true);
            Object replacement = state(player); require(target(replacement) == target, "Identity test needs a real seal");
            clearStale(cancelled);
            require(state(player) == replacement && target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < speed,
                "Stale cleanup cannot remove another live seal's identical slowdown");
            player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy()); MeleeSilkSealSkills.tickServer(f.level.getServer());
            require(state(player) == null && target.getHealth() == 200, "Equal but different equipment cannot claim the seal");
            require(Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed) < 0.0001D, "Equipment change restores slowdown");
            player.setItemSlot(EquipmentSlot.MAINHAND, weapon);

            require(MeleeSilkSealSkills.activate(player), "Rearm for expiry"); f.hit(target, true); Object expiry = state(player);
            setExpires(expiry, f.level.getGameTime()); MeleeSilkSealSkills.tickServer(f.level.getServer());
            require(state(player) == null && target.getHealth() == 200, "Expiry dissipates damage instead of applying it");
            require(Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed) < 0.0001D, "Expiry restores slowdown");

            require(MeleeSilkSealSkills.activate(player), "Rearm for actual shield");
            ItemStack shield = new ItemStack(Items.SHIELD); target.setItemSlot(EquipmentSlot.OFFHAND, shield);
            target.setYRot(90); target.setYHeadRot(90); target.setXRot(0); startBlocking(target, shield);
            Object shieldPreparation = state(player); f.hit(target, true);
            require(target.getHealth() == 200 && state(player) == shieldPreparation && target(shieldPreparation) == null,
                "A shielded hit cannot be captured");
            target.stopUsingItem(); f.hit(target, true); require(target(state(player)) == target, "Unblocked hit can be sealed");
            int shieldWear = shield.getDamageValue(); startBlocking(target, shield); f.hit(target, true);
            require(state(player) == null && target.getHealth() == 200 && shield.getDamageValue() > shieldWear,
                "A real blocked release spends the seal exactly once and respects shield durability");
            target.stopUsingItem(); target.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

            require(MeleeSilkSealSkills.activate(player), "Rearm for nested attack");
            nestedTrigger[0] = target; nestedTarget[0] = other; nest[0] = true;
            f.hit(target, true); nest[0] = false;
            require(state(player) != null && target(state(player)) == null && target.getHealth() < 200,
                "Nesting before capture must conservatively remain ordinary and cannot create a seal");
            target.setHealth(200); nestedTrigger[0] = target; nestAfter[0] = true; f.hit(target, true);
            require(!nestAfter[0] && state(player) != null && target(state(player)) == target && target.getHealth() == 200,
                "A later nested attack on a different creature must not silently discard the captured damage");
            require(MeleeSilkSealSkills.activate(player), "Dissipate the stored hit after the nested case");
            target.setHealth(200); require(MeleeSilkSealSkills.activate(player), "Rearm for nested release"); f.hit(target, true);
            require(target(state(player)) == target, "Nested release needs a real seal");
            nestedTrigger[0] = target; nestedTarget[0] = target; nest[0] = true; f.hit(target, true);
            float nestedReleaseLoss = 200 - target.getHealth();
            require(!nest[0] && state(player) == null && nestedReleaseLoss > baseline * 1.5F && nestedReleaseLoss < baseline * 2.8F,
                "A nested same-target hit cannot add the stored damage twice");
        } finally { denied.clear(); observe[0] = false; nest[0] = false; nestAfter[0] = false; MeleeSilkSealSkills.clearPlayer(player.getUUID()); }
    }
    private static void startBlocking(LivingEntity target, ItemStack shield) throws Exception {
        target.startUsingItem(InteractionHand.OFF_HAND);
        Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining"); remaining.setAccessible(true);
        remaining.setInt(target, shield.getUseDuration(target) - 10);
        require(target.isBlocking(), "Shield must actually be blocking");
    }
    private static Object state(ServerPlayer player) throws Exception {
        Field field = MeleeSilkSealSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static Object target(Object state) throws Exception {
        require(state != null, "Expected a pending or sealed state");
        Field field = state.getClass().getDeclaredField("target"); field.setAccessible(true); return field.get(state);
    }
    private static void clearStale(Object state) throws Exception {
        var clear = MeleeSilkSealSkills.class.getDeclaredMethod("clear", state.getClass()); clear.setAccessible(true);
        clear.invoke(null, state);
    }
    private static void setExpires(Object state, long tick) throws Exception {
        require(state != null, "Expected a state for expiry");
        Field field = state.getClass().getDeclaredField("expires"); field.setAccessible(true); field.setLong(state, tick);
    }
}
