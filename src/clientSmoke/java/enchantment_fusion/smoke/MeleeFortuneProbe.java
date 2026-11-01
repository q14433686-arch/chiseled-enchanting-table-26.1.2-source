package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeFortuneSkills;
import enchantment_fusion.mixin.LivingEntityAttackChargeAccessor;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real hit and item paths; random outcome coverage uses bounded attempts and simulated cooldown. */
final class MeleeFortuneProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private MeleeFortuneProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean reverse) throws Exception {
        FusedPair forward = FusedPair.clean(attack, 4, MeleeFortuneSkills.FORTUNE, 3);
        FusedPair pair = reverse ? forward.flipped() : forward; ServerLevel level = player.level(); checkStation(level, pair);
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        int oldCharge = ((LivingEntityAttackChargeAccessor) (Object) player).ef$getAttackStrengthTicker();
        boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity other = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            MeleeFortuneSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(15); player.setOnGround(true); player.fallDistance = 0; speed.setBaseValue(4);
            for (LivingEntity entity : List.of(target, other, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); entity.setHealth(1000);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ()); other.setPos(player.getX() + 3.5D, player.getY(), player.getZ());
            wrong.setPos(player.getX() + 2, player.getY(), player.getZ() + 3); villager.setPos(player.getX() + 2, player.getY(), player.getZ() - 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(other) && level.addFreshEntity(wrong) && level.addFreshEntity(villager), "Fixture targets must enter world");
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD); FusionStationOperations.applyFusedPairToGear(sword, pair, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            if (reverse) exerciseStance(player, target, other, wrong, villager, sharp, deny);
            else exerciseRefresh(player, target, wrong, villager, sharp, deny);
        } finally {
            deny.clear(); MeleeFortuneSkills.clearPlayer(player.getUUID()); target.discard(); other.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(oldCharge);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static void exerciseRefresh(ServerPlayer player, LivingEntity target, LivingEntity wrong, Villager villager,
                                        boolean sharp, Set<UUID> deny) {
        deny.add(target.getUUID()); hit(player, target); deny.clear(); require(charge(player) == 0, "Cancelled hits cannot refresh charge");
        if (!sharp) { hit(player, wrong); require(charge(player) == 0, "Wrong target type cannot refresh charge"); }
        hit(player, villager); require(charge(player) == 0, "Protected targets cannot refresh charge");
        boolean refreshed = false;
        for (int i = 0; i < 64 && !refreshed; i++) { target.setHealth(1000); hit(player, target); refreshed = charge(player) > 0; }
        require(refreshed, "An eligible real full hit must eventually refresh charge");
    }
    private static void exerciseStance(ServerPlayer player, LivingEntity target, LivingEntity other, LivingEntity wrong, Villager villager,
                                       boolean sharp, Set<UUID> deny) throws Exception {
        target.setHealth(1000); hit(player, target); float baseline = 1000 - target.getHealth();
        require(baseline > 0, "Baseline real damage is required");
        require(MeleeFortuneSkills.hasEquipped(player) && MeleeFortuneSkills.activate(player), "The reverse direction must arm");
        Object saved = armed(player); require(saved != null && !MeleeFortuneSkills.activate(player), "An armed stance cannot be rerolled");
        deny.add(target.getUUID()); hit(player, target); deny.clear(); require(armed(player) == saved, "Cancelled damage preserves the same roll");
        if (!sharp) { hit(player, wrong); require(armed(player) == saved, "Wrong targets preserve the stance"); }
        hit(player, villager); require(armed(player) == saved, "Protected targets preserve the stance");
        target.setHealth(1000); hit(player, target); require(armed(player) == null, "The next eligible hit consumes exactly one stance");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 96 && seen.size() < 4; i++) {
            clearCooldown(player); require(MeleeFortuneSkills.activate(player), "Simulated cooldown must allow a new stance");
            Object state = armed(player); Method outcome = state.getClass().getDeclaredMethod("outcome"); outcome.setAccessible(true);
            String selected = outcome.invoke(state).toString(); target.setHealth(1000); other.setHealth(1000); other.invulnerableTime = 0;
            hit(player, target); float loss = 1000 - target.getHealth();
            require(armed(player) == null, "Every real matching hit consumes the stance even without an outcome");
            if (selected.equals("HEAVY")) require(loss > baseline * 1.20F, "Heavy stance increases real matching damage");
            else if (selected.equals("SCATTER")) require(other.getHealth() < 1000, "Scatter must hit a second eligible creature along a clear line");
            else if (selected.equals("REFRESH")) require(charge(player) > 0, "Refresh outcome restores real server attack charge");
            else require(selected.equals("NONE") && Math.abs(loss - baseline) < 0.01F, "No-outcome stance retains ordinary damage");
            seen.add(selected);
        }
        require(seen.size() == 4, "All four outcomes must be observed within bounded random attempts");
    }
    private static void hit(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0; ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(200); player.attack(target);
    }
    private static int charge(ServerPlayer player) { return ((LivingEntityAttackChargeAccessor) (Object) player).ef$getAttackStrengthTicker(); }
    private static Object armed(ServerPlayer player) throws Exception {
        Field field = MeleeFortuneSkills.class.getDeclaredField("ARMED"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static void clearCooldown(ServerPlayer player) throws Exception {
        Field field = MeleeFortuneSkills.class.getDeclaredField("NEXT_ARM"); field.setAccessible(true); ((Map<?, ?>) field.get(null)).remove(player.getUUID());
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.MACE;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths require a real compatible melee carrier");
            }
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
