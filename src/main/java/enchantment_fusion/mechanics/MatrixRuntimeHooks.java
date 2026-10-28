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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Wires Fabric server lifecycle, damage/death interception, combat, mining, item usage,
 * and passive world ticks into {@link SignaturePairs}, {@link AberrantRegistry},
 * {@link MatrixEffectExecutor}, and {@link CrossDomainTransmutations}.
 */
public final class MatrixRuntimeHooks {
    private static final EquipmentSlot[] ARMOR_AND_HAND_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.MAINHAND
    };

    private static final Map<UUID, Float> PEAK_FALL_DISTANCE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IN_AFTER_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MatrixRuntimeHooks() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MatrixEffectExecutor.clearCooldowns();
            SignaturePairs.clearRuntimeState();
            CrossDomainTransmutations.clearRuntimeState();
            PEAK_FALL_DISTANCE.clear();
        });

        // Pre-damage interception: [时运 ▷ 保护] 招财金身, 琉璃圣盾 & Cross-Domain 避雷充能
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowIncomingDamage(serverPlayer, amount)) {
                    return false;
                }
                return CrossDomainTransmutations.allowIncomingDamage(serverLevel, serverPlayer, source, amount);
            }
            return true;
        });

        // Lethal interception: [保护 ▷ 时运] 吉星高照, [保护 ▷ 忠诚] 归元护主 & Cross-Domain 裂而不碎 Totem survival
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowPlayerDeath(serverPlayer)) {
                    return false;
                }
                return CrossDomainTransmutations.allowPlayerDeath(serverLevel, serverPlayer);
            }
            return true;
        });

        // Melee Strike + Pet Protection (完壁) + Sweep/Cleave + Cross-Domain Melee Transmutations
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && entity instanceof LivingEntity target) {
                if (!CrossDomainTransmutations.allowAttackTarget(serverPlayer, target)) {
                    return InteractionResult.FAIL;
                }
                ItemStack weapon = serverPlayer.getItemInHand(hand);
                boolean fullCharge = serverPlayer.getAttackStrengthScale(0.5F) >= 0.90F;
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, weapon, TriggerType.ON_MELEE_STRIKE, target, target.position(), fullCharge);
                if (fullCharge) {
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, weapon, TriggerType.ON_SWEEP_OR_CLEAVE, target, target.position(), true);
                }
                CrossDomainTransmutations.onMeleeAttack(serverLevel, serverPlayer, target, fullCharge);
            }
            return InteractionResult.PASS;
        });

        // Right-Click Item Use (飞剑掷斧, 水龙突刺, 驭浪盾冲, 幻影连投, 经久省用 Consumables)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
                CrossDomainTransmutations.onUseItem(serverLevel, serverPlayer, hand, serverPlayer.getItemInHand(hand));
            }
            return InteractionResult.PASS;
        });

        // Pre-Break Interception (Auto-Smelt Pickaxe, Charcoal/Ash Axe, Nest Purger)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                if (!CrossDomainTransmutations.onBeforeBlockBreak(serverLevel, serverPlayer, pos, state)) {
                    SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_BLOCK_HARVEST, null, Vec3.atCenterOf(pos));
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_UTILITY_CYCLE, null, Vec3.atCenterOf(pos));
                    return false;
                }
            }
            return true;
        });

        // Block Harvest (Signature ore geode, molten smelting, 3x1 rock-splitter, magnet, etc. + Matrix + Cross-Domain)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_BLOCK_HARVEST, null, Vec3.atCenterOf(pos));
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_UTILITY_CYCLE, null, Vec3.atCenterOf(pos));
                CrossDomainTransmutations.onAfterBlockBreak(serverLevel, serverPlayer, pos, state);
            }
        });

        // Post-Damage Counter, Blood Vengeance & Cross-Domain Armor/Shield Reactions
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (Boolean.TRUE.equals(IN_AFTER_DAMAGE.get())) {
                return;
            }
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                IN_AFTER_DAMAGE.set(Boolean.TRUE);
                try {
                    SignaturePairs.recordIncomingDamage(serverPlayer, damageTaken, serverLevel.getGameTime());
                    LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
                    for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
                        ItemStack equipped = serverPlayer.getItemBySlot(slot);
                        if (!equipped.isEmpty()) {
                            MatrixEffectExecutor.dispatch(
                                serverLevel, serverPlayer, equipped, TriggerType.ON_DAMAGE_TAKEN, attacker, serverPlayer.position()
                            );
                        }
                    }
                    CrossDomainTransmutations.onAfterDamage(serverLevel, serverPlayer, source, damageTaken, blocked);
                } finally {
                    IN_AFTER_DAMAGE.set(Boolean.FALSE);
                }
            }
        });

        // Entity Kill hooks (Pristine Trophy mob heads, Vanish-on-Kill invisibility, Abyssal breath heal, 猎宝, 海猎, 完壁)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SignaturePairs.onEntityKilled(serverLevel, serverPlayer, entity);
                ItemStack main = serverPlayer.getMainHandItem();
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, main, TriggerType.ON_UTILITY_CYCLE, entity, entity.position());
                CrossDomainTransmutations.onEntityKilled(serverLevel, serverPlayer, entity);
            }
        });

        // Movement / Landing + Passive Signature Ticks + Cross-Domain Passives / Projectiles / World Items
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SignaturePairs.tickWorldDots(server);
            for (ServerLevel world : server.getAllLevels()) {
                CrossDomainTransmutations.tickWorldItems(world);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.level() instanceof ServerLevel serverLevel)) continue;
                SignaturePairs.tickServerPlayer(serverLevel, player);
                CrossDomainTransmutations.tickServerPlayer(serverLevel, player);

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

                UUID id = player.getUUID();
                if (!player.onGround() && player.fallDistance > 1.5F) {
                    PEAK_FALL_DISTANCE.merge(id, (float) player.fallDistance, Math::max);
                } else if (player.onGround()) {
                    Float peak = PEAK_FALL_DISTANCE.remove(id);
                    if (peak != null && peak >= 3.2F) {
                        for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
                            ItemStack gear = player.getItemBySlot(slot);
                            if (!gear.isEmpty()) {
                                MatrixEffectExecutor.dispatch(
                                    serverLevel, player, gear, TriggerType.ON_MOVEMENT_OR_LAND, null, player.position()
                                );
                            }
                        }
                    }
                }
            }
        });
    }
}
