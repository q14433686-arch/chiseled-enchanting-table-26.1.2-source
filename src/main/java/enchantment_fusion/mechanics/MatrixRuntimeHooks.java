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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Wires Fabric server lifecycle, damage/death interception, combat, mining, and passive world ticks
 * into {@link SignaturePairs}, {@link AberrantRegistry}, and {@link MatrixEffectExecutor}.
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

    private MatrixRuntimeHooks() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MatrixEffectExecutor.clearCooldowns();
            PEAK_FALL_DISTANCE.clear();
        });

        // Pre-damage interception: [时运 ▷ 保护] 招财金身 (chance to negate damage at 2x durability cost) & 琉璃圣盾
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer serverPlayer) {
                return SignaturePairs.allowIncomingDamage(serverPlayer, amount);
            }
            return true;
        });

        // Lethal interception: [保护 ▷ 时运] 吉星高照 (lucky survival at 1 HP) & [保护 ▷ 忠诚] 归元护主 (void save)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer serverPlayer) {
                return SignaturePairs.allowPlayerDeath(serverPlayer);
            }
            return true;
        });

        // Melee Strike + Sweep/Cleave (checks full charge 90%+ for ranged sword-waves & lightning iaido)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && entity instanceof LivingEntity target) {
                ItemStack weapon = serverPlayer.getItemInHand(hand);
                boolean fullCharge = serverPlayer.getAttackStrengthScale(0.5F) >= 0.90F;
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, weapon, TriggerType.ON_MELEE_STRIKE, target, target.position(), fullCharge);
                if (fullCharge) {
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, weapon, TriggerType.ON_SWEEP_OR_CLEAVE, target, target.position(), true);
                }
            }
            return InteractionResult.PASS;
        });

        // Block Harvest (Signature ore geode, molten smelting, 3x1 rock-splitter, magnet, etc. + Matrix)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_BLOCK_HARVEST, null, Vec3.atCenterOf(pos));
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_UTILITY_CYCLE, null, Vec3.atCenterOf(pos));
            }
        });

        // Post-Damage Counter & Blood Vengeance tracking
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
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
            }
        });

        // Entity Kill hooks (Pristine Trophy mob heads, Vanish-on-Kill invisibility, Abyssal breath heal)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SignaturePairs.onEntityKilled(serverLevel, serverPlayer, entity);
                ItemStack main = serverPlayer.getMainHandItem();
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, main, TriggerType.ON_UTILITY_CYCLE, entity, entity.position());
            }
        });

        // Movement / Landing + Passive Signature Ticks + Soul-Fire / Skyfire DoTs
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel world : server.getAllLevels()) {
                SignaturePairs.tickWorldDots(world);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.level() instanceof ServerLevel serverLevel)) continue;
                SignaturePairs.tickServerPlayer(serverLevel, player);

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
