package enchantment_fusion.mechanics;

import enchantment_fusion.model.TriggerType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/**
 * Registers Fabric gameplay hooks so both binary Fused Pairs ({@code [A ▷ B]} / {@code [B ▷ A]})
 * and Phase 3 Cross-Domain Single Transmutations execute in real combat, mining, ranged shots,
 * item usage, defensive triggers, and world item survival.
 */
public final class MatrixRuntimeHooks {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };
    private static final Map<UUID, ItemStack> BLOCK_BREAK_TOOL_SNAPSHOT = new ConcurrentHashMap<>();

    private MatrixRuntimeHooks() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BLOCK_BREAK_TOOL_SNAPSHOT.clear();
            SignaturePairs.clearRuntimeState();
            CrossDomainTransmutations.clearRuntimeState();
        });

        // 1. Pre-Break Smelting / Wood Charcoal-Ash & Post-Break Block Harvest
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                BLOCK_BREAK_TOOL_SNAPSHOT.put(serverPlayer.getUUID(), tool.copy());
                if (!CrossDomainTransmutations.onBeforeBlockBreak(serverLevel, serverPlayer, pos, state)) {
                    // Handled directly (e.g., Auto-Smelt Pickaxe or Auto-Smelt Axe Charcoal/Ash); still run binary fusion hooks!
                    SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                    MatrixEffectExecutor.dispatch(
                        serverLevel,
                        serverPlayer,
                        tool,
                        TriggerType.ON_BLOCK_BREAK,
                        null,
                        pos.getCenter()
                    );
                    return false;
                }
            }
            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                if (tool.isEmpty()) {
                    tool = BLOCK_BREAK_TOOL_SNAPSHOT.getOrDefault(serverPlayer.getUUID(), ItemStack.EMPTY);
                }
                SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                MatrixEffectExecutor.dispatch(
                    serverLevel,
                    serverPlayer,
                    tool,
                    TriggerType.ON_BLOCK_BREAK,
                    null,
                    pos.getCenter()
                );
                CrossDomainTransmutations.onAfterBlockBreak(serverLevel, serverPlayer, pos, state);
            }
        });

        // 2. Melee Hit: Pet-Safeguard (完壁) + Binary Fusions + Cross-Domain Melee Transmutations
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.isSpectator()
                && hand == InteractionHand.MAIN_HAND
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && entity instanceof LivingEntity target) {
                if (!CrossDomainTransmutations.allowAttackTarget(serverPlayer, target)) {
                    return InteractionResult.FAIL;
                }
                ItemStack weapon = serverPlayer.getMainHandItem();
                boolean fullCharge = serverPlayer.getAttackStrengthScale(0.5F) > 0.9F;
                MatrixEffectExecutor.dispatch(
                    serverLevel,
                    serverPlayer,
                    weapon,
                    TriggerType.ON_MELEE_HIT,
                    target,
                    target.position()
                );
                CrossDomainTransmutations.onMeleeAttack(serverLevel, serverPlayer, target, fullCharge);
            }
            return InteractionResult.PASS;
        });

        // 3. Right-Click Item Use (飞剑掷斧, 水龙突刺, 驭浪盾冲, 幻影连投, 经久省用 Consumables)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!player.isSpectator()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
                CrossDomainTransmutations.onUseItem(serverLevel, serverPlayer, hand, serverPlayer.getItemInHand(hand));
            }
            return InteractionResult.PASS;
        });

        // 4. Pre-Damage Immunity Interception (Lucky Golden Body, Glass Holy Shield, 避雷充能)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer serverPlayer && entity.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowIncomingDamage(serverPlayer, amount)) {
                    return false;
                }
                return CrossDomainTransmutations.allowIncomingDamage(serverLevel, serverPlayer, source, amount);
            }
            return true;
        });

        // 5. Post-Damage Reactive Hooks (Armor + Shield + Weapon Bracer)
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer serverPlayer && entity.level() instanceof ServerLevel serverLevel) {
                LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack piece = serverPlayer.getItemBySlot(slot);
                    if (!piece.isEmpty()) {
                        MatrixEffectExecutor.dispatch(
                            serverLevel,
                            serverPlayer,
                            piece,
                            TriggerType.ON_DAMAGED,
                            attacker,
                            serverPlayer.position()
                        );
                    }
                }
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack held = serverPlayer.getItemInHand(hand);
                    if (!held.isEmpty() && blocked) {
                        MatrixEffectExecutor.dispatch(
                            serverLevel,
                            serverPlayer,
                            held,
                            TriggerType.ON_DAMAGED,
                            attacker,
                            serverPlayer.position()
                        );
                    }
                }
                CrossDomainTransmutations.onAfterDamage(serverLevel, serverPlayer, source, damageTaken, blocked);
            }
        });

        // 6. Fatal Damage Interception (Phoenix Nirvana + 裂而不碎 Totem Survival)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer serverPlayer && entity.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowPlayerDeath(serverPlayer)) {
                    return false;
                }
                return CrossDomainTransmutations.allowPlayerDeath(serverLevel, serverPlayer);
            }
            return true;
        });

        // 7. Entity Kill Hooks (Kill Plunder, Soul Siphon, 猎宝, 海猎, 完壁)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.level() instanceof ServerLevel serverLevel
                && damageSource.getEntity() instanceof ServerPlayer killer) {
                SignaturePairs.onEntityKilledByPlayer(serverLevel, killer, entity, killer.getMainHandItem());
                CrossDomainTransmutations.onEntityKilled(serverLevel, killer, entity);
            }
        });

        // 8. Server Tick: Player Passives, In-Flight Cross-Domain Projectiles, and World Item Survival / Sticky Curse
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || !(player.level() instanceof ServerLevel serverLevel)) {
                    continue;
                }
                SignaturePairs.tickServerPlayer(player);
                CrossDomainTransmutations.tickServerPlayer(serverLevel, player);

                // Tick active player-owned projectiles within 72 blocks for cross-domain flight behaviors
                for (Projectile proj : serverLevel.getEntitiesOfClass(
                    Projectile.class,
                    player.getBoundingBox().inflate(72.0D),
                    p -> p.isAlive() && p.getOwner() == player && p.tickCount <= 160
                )) {
                    ItemStack weapon = proj.getWeaponItem();
                    if (weapon == null || weapon.isEmpty()) {
                        weapon = player.getMainHandItem();
                    }
                    if (!weapon.isEmpty()) {
                        CrossDomainTransmutations.onProjectileTick(serverLevel, proj, player, weapon);
                    }
                }

                if (serverLevel.getGameTime() % 40L == 0L) {
                    for (EquipmentSlot slot : ARMOR_SLOTS) {
                        ItemStack piece = player.getItemBySlot(slot);
                        if (!piece.isEmpty()) {
                            MatrixEffectExecutor.dispatch(
                                serverLevel,
                                player,
                                piece,
                                TriggerType.ON_ARMOR_TICK,
                                null,
                                player.position()
                            );
                        }
                    }
                }
            }
            for (ServerLevel worldLevel : server.getAllLevels()) {
                CrossDomainTransmutations.tickWorldItems(worldLevel);
            }
        });
    }
}
