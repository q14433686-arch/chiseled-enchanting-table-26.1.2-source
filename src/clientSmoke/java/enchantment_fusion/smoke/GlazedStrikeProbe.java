package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Prepared integrated-server fixture. It is not a replacement for the requested full playtest. */
final class GlazedStrikeProbe {
    private static final FusedPair PAIR = FusedPair.clean("minecraft:fire_aspect", 2, "minecraft:silk_touch", 1);

    private GlazedStrikeProbe() {}

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void exercise(ServerPlayer player) {
        var level = player.level();
        var oldMain = player.getMainHandItem();
        var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        double oldSpeed = attackSpeed.getBaseValue();
        var target = new Zombie(level);
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        try {
            target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            target.setPos(player.getX() + 2.0D, player.getY(), player.getZ());
            level.addFreshEntity(target);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            FusionStationOperations.applyFusedPairToGear(sword, PAIR, level.registryAccess());
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var fire = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
            var silk = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("silk_touch")));
            require(sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fire) == 2,
                "Burning glaze must preserve the native Fire Aspect primary");
            require(DirectionalHostRules.validateNewOrChanged(sword, PAIR) == RejectReason.NONE,
                "A sword must support the selected ordered direction");
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            require(DirectionalHostRules.validateNewOrChanged(pick, PAIR) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "A pickaxe must not accept the new weapon-only direction");

            var socket = new SimpleContainer(4);
            socket.setItem(FusionStationOperations.SLOT_TARGET, pick.copy());
            socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(PAIR));
            var preview = FusionStationOperations.inspect(socket, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1);
            require(!preview.executable() && preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Fused-book socketing must report the unsupported carrier");
            var direct = new SimpleContainer(4);
            direct.setItem(FusionStationOperations.SLOT_TARGET, pick.copy());
            direct.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(fire, 2));
            direct.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(silk, 1));
            var directPreview = FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1);
            require(!directPreview.executable() && directPreview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Direct fusion must report the same carrier rejection, not substitute a single inscription");
            direct.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_SWORD));
            require(FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1).executable(),
                "Direct fusion must accept the selected sword carrier");

            ItemStack legacyPick = new ItemStack(Items.DIAMOND_PICKAXE);
            legacyPick.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PAIR));
            require(!GlazedStrikeEffect.applies(PAIR, legacyPick, GlazedStrikeEffect.SIGNATURE),
                "Existing non-weapon gear must not be redirected to the new weapon ability");
            var topUp = new SimpleContainer(4);
            ItemStack lowerLegacy = legacyPick.copy();
            lowerLegacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PAIR.withPrimaryLevel(1)));
            topUp.setItem(FusionStationOperations.SLOT_TARGET, lowerLegacy);
            topUp.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(fire, 2));
            var topUpPreview = FusionStationOperations.inspect(topUp, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1);
            require(!topUpPreview.executable() && topUpPreview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Upgrading an old pair on an unsupported carrier must be rejected without removing it");
            var flip = new SimpleContainer(4);
            ItemStack reversed = new ItemStack(Items.DIAMOND_PICKAXE);
            reversed.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PAIR.flipped()));
            flip.setItem(FusionStationOperations.SLOT_TARGET, reversed);
            var flipResult = FusionStationOperations.executeFlipSocketWithReason(flip, player, level.registryAccess(), 0, "");
            require(!flipResult.success() && flipResult.reason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Flipping a legacy pair into the new weapon direction must be rejected without changing the item");
            require(reversed.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(PAIR.flipped()),
                "A failed flip must retain the original pair");

            // Real server Player.attack / stabAttack calls; there is no client mouse, kinetic movement,
            // shield, multiplayer, or physical line-of-sight simulation in this fixture.
            MatrixEffectExecutor.clearCooldowns();
            GlazedStrikeEffect.clear();
            attackSpeed.setBaseValue(1024.0D); // Full charge with no need to tick the whole world here.
            player.resetAttackStrengthTicker();
            target.clearFire();
            target.invulnerableTime = 0;
            float before = target.getHealth();
            player.attack(target);
            float afterDirect = target.getHealth();
            require(afterDirect < before, "The cold sword hit must actually damage the target");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(Math.abs(target.getHealth() - afterDirect) < 0.001F,
                "A cold target must not receive the glaze bonus from the same hit's ignition");

            // A failed full hit and a subsequent partial hit in the same tick must not share a pending attempt.
            target.setHealth(100.0F);
            target.setRemainingFireTicks(150);
            target.invulnerableTime = 0;
            player.resetAttackStrengthTicker();
            denied.add(target.getUUID());
            player.attack(target);
            denied.clear();
            require(target.getHealth() == 100.0F, "The cancelled full hit must not deal damage");
            attackSpeed.setBaseValue(0.1D);
            player.resetAttackStrengthTicker();
            target.invulnerableTime = 0;
            player.attack(target);
            afterDirect = target.getHealth();
            require(afterDirect < 100.0F, "The subsequent partial hit must actually land");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(Math.abs(target.getHealth() - afterDirect) < 0.001F,
                "A partial hit must not consume a failed full attempt's pending glaze");

            attackSpeed.setBaseValue(1024.0D);
            player.resetAttackStrengthTicker();
            target.setHealth(100.0F);
            target.setRemainingFireTicks(150);
            target.invulnerableTime = 0;
            player.attack(target);
            afterDirect = target.getHealth();
            GlazedStrikeEffect.tickServer(level.getServer());
            require(afterDirect - target.getHealth() > 1.0F,
                "An already-burning target must receive the armor-bypassing bonus from a real full hit");

            ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
            FusionStationOperations.applyFusedPairToGear(axe, PAIR, level.registryAccess());
            require(axe.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fire) == 0,
                "The fixture must exercise a carrier without native Fire Aspect projection");
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            MatrixEffectExecutor.clearCooldowns();
            target.clearFire();
            target.setHealth(100.0F);
            target.invulnerableTime = 0;
            require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Unframed direct damage must land");
            require(!target.isOnFire(), "Damage outside the exact weapon attack must not grant fallback fire");
            target.invulnerableTime = 0;
            player.resetAttackStrengthTicker();
            player.attack(target);
            afterDirect = target.getHealth();
            require(target.isOnFire(), "A confirmed non-native axe hit must supply the Fire Aspect primary's ignition");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(Math.abs(target.getHealth() - afterDirect) < 0.001F,
                "Fallback ignition must not turn the same cold hit into a glaze hit");
            target.invulnerableTime = 0;
            player.resetAttackStrengthTicker();
            player.attack(target);
            afterDirect = target.getHealth();
            GlazedStrikeEffect.tickServer(level.getServer());
            require(afterDirect - target.getHealth() > 1.0F, "A later full axe hit must shatter its own prior fire");

            ItemStack spear = new ItemStack(Items.DIAMOND_SPEAR);
            FusionStationOperations.applyFusedPairToGear(spear, PAIR, level.registryAccess());
            player.setItemInHand(InteractionHand.MAIN_HAND, spear);
            MatrixEffectExecutor.clearCooldowns();
            target.setHealth(100.0F);
            target.setRemainingFireTicks(150);
            target.invulnerableTime = 0;
            attackSpeed.setBaseValue(0.1D);
            player.resetAttackStrengthTicker();
            require(player.stabAttack(EquipmentSlot.MAINHAND, target, 2.0F, true, false, false), "Partial spear stab must land");
            afterDirect = target.getHealth();
            GlazedStrikeEffect.tickServer(level.getServer());
            require(Math.abs(target.getHealth() - afterDirect) < 0.001F, "A partial spear stab must not shatter");
            attackSpeed.setBaseValue(1024.0D);
            player.resetAttackStrengthTicker();
            target.invulnerableTime = 0;
            require(player.stabAttack(EquipmentSlot.MAINHAND, target, 2.0F, true, false, false), "Full spear stab must land");
            afterDirect = target.getHealth();
            GlazedStrikeEffect.tickServer(level.getServer());
            require(afterDirect - target.getHealth() > 1.0F, "A full main-hand spear stab must receive the shatter bonus");
        } finally {
            denied.clear();
            target.discard();
            attackSpeed.setBaseValue(oldSpeed);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            GlazedStrikeEffect.clear();
            MatrixEffectExecutor.clearCooldowns();
        }
    }
}
