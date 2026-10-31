package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.TriggerType;
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
        var target = new Zombie(level);
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        try {
            target.setNoAi(true);
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

            // These are actual server damage callbacks driven by a manually dispatched attack;
            // they do not claim to test a real mouse click or a second client.
            MatrixEffectExecutor.clearCooldowns();
            target.clearFire();
            float before = target.getHealth();
            MatrixEffectExecutor.dispatch(level, player, sword, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
            require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Cold hit must land");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(before - target.getHealth() <= 1.01F, "Cold target must not receive the glaze bonus");

            target.invulnerableTime = 0;
            target.igniteForSeconds(10.0F);
            before = target.getHealth();
            MatrixEffectExecutor.dispatch(level, player, sword, TriggerType.ON_MELEE_STRIKE, target, target.position(), false);
            require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Partial hit must land");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(before - target.getHealth() <= 1.01F, "Partial hit must not receive the glaze bonus");

            target.invulnerableTime = 0;
            denied.add(target.getUUID());
            MatrixEffectExecutor.dispatch(level, player, sword, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
            require(!target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Cancelled hit must be denied");
            GlazedStrikeEffect.tickServer(level.getServer());
            denied.clear();
            target.invulnerableTime = 0;
            before = target.getHealth();
            MatrixEffectExecutor.dispatch(level, player, sword, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
            require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Full hit must land");
            GlazedStrikeEffect.tickServer(level.getServer());
            require(before - target.getHealth() > 1.5F, "Already-burning target must receive the armor-bypassing bonus");
        } finally {
            denied.clear();
            target.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            GlazedStrikeEffect.clear();
            MatrixEffectExecutor.clearCooldowns();
        }
    }
}
