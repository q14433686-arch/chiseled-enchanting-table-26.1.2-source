package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearMath;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Six individually recorded server fixtures; real methods, but input and timing are simulated. */
final class MeleeWearProbe {
    private MeleeWearProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean unbreakingFirst) throws Exception {
        ServerLevel level = player.level();
        FusedPair pair = FusedPair.clean(attack, 4, MeleeWearSkills.UNBREAKING, 3);
        if (unbreakingFirst) pair = pair.flipped();
        checkStation(level, pair); MeleeWearSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(); double fall = player.fallDistance;
        GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0;
            speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            for (LivingEntity entity : List.of(target, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            wrong.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 2.5D);
            villager.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() - 1.5D);
            require(level.addFreshEntity(target) && level.addFreshEntity(wrong) && level.addFreshEntity(villager),
                "Fixture targets must enter the disposable world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            if (unbreakingFirst) exerciseEdge(player, target, wrong, villager, sword, attack, deny);
            else exerciseGuard(player, target, wrong, villager, sword, pair, attack, deny);
        } finally {
            deny.clear(); MeleeWearSkills.clearPlayer(player.getUUID());
            target.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static void exerciseGuard(ServerPlayer player, LivingEntity target, LivingEntity wrong, Villager villager,
                                      ItemStack sword, FusedPair pair, String attack, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level();
        ItemStack cross = fused(level, Items.TRIDENT, pair);
        float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, target, level.damageSources().playerAttack(player), 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, cross);
        float crossDamage = EnchantmentHelper.modifyDamage(level, cross, target, level.damageSources().playerAttack(player), 1);
        require(nativeDamage > 1 && Math.abs(nativeDamage - crossDamage) < 0.001F, "Missing native attack primary must be replayed once");
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        require(!MeleeWearSkills.activate(player, true), "No confirmed hits cannot fund protection");
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(value(player, sword, "streak") == 0, "A cancelled attack must not count");
        hit(player, target); hit(player, target);
        require(value(player, sword, "streak") == 2 && !MeleeWearSkills.activate(player, true), "Two real hits are insufficient");
        hit(player, MeleeAttackThemes.SHARP.equals(attack) ? villager : wrong);
        require(value(player, sword, "streak") == 0, "A nonqualifying hit must break the observed combo");
        hit(player, target); hit(player, target); hit(player, target);
        require(value(player, sword, "streak") == 3 && MeleeWearSkills.activate(player, true), "Three confirmed hits must arm protection");
        int guard = value(player, sword, "guard"), before = sword.getDamageValue(), maximum = sword.getMaxDamage();
        require(guard == MeleeWearMath.guardFor(3, pair.secondaryLevel()), "Guard must be based on the actual combo");
        sword.hurtAndBreak(0, player, EquipmentSlot.MAINHAND);
        player.setGameMode(GameType.CREATIVE); sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND); player.setGameMode(GameType.SURVIVAL);
        require(value(player, sword, "guard") == guard && sword.getDamageValue() == before, "Zero or creative wear cannot consume guard");
        sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
        require(sword.getDamageValue() == before && value(player, sword, "guard") == guard - 2, "Real wear must spend only its matching guard");
        sword.hurtAndBreak(guard, player, EquipmentSlot.MAINHAND);
        require(sword.getDamageValue() == before + 2 && value(player, sword, "guard") == 0 && sword.getMaxDamage() == maximum,
            "Excess wear must remain normal and maximum durability unchanged");
        hit(player, target); hit(player, target); hit(player, target);
        require(MeleeWearSkills.activate(player, true), "A later real combo can prepare protection again");
        ItemStack copy = sword.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy); MeleeWearSkills.tickServer(level.getServer());
        require(value(player, copy, "guard") == 0 && !MeleeWearSkills.activate(player, true), "A copy cannot inherit another stack's guard");
    }
    private static void exerciseEdge(ServerPlayer player, LivingEntity target, LivingEntity wrong, Villager villager,
                                     ItemStack sword, String attack, Set<UUID> deny) throws Exception {
        require(!MeleeWearSkills.activate(player, false), "No real Unbreaking savings cannot arm an enhanced hit");
        float baseBefore = target.getHealth(); hit(player, target); float baseline = baseBefore - target.getHealth();
        require(baseline > 0, "Baseline hit must really damage target");
        MeleeWearSkills.clearPlayer(player.getUUID());
        int saved = fund(player, sword);
        require(saved >= 2 && value(player, sword, "shards") >= 2, "Real Unbreaking savings must fund shards");
        int shards = value(player, sword, "shards");
        player.setGameMode(GameType.CREATIVE); sword.hurtAndBreak(1, player, EquipmentSlot.MAINHAND); player.setGameMode(GameType.SURVIVAL);
        require(value(player, sword, "shards") == shards, "Creative non-wear must not earn shards");
        ItemStack unowned = sword.copy();
        for (int i = 0; i < 12; i++) unowned.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        require(value(player, unowned, "shards") == 0, "An unequipped copy cannot earn this player's shards");
        require(MeleeWearSkills.activate(player, false) && value(player, sword, "armedShards") >= 2, "Activation must really spend shards");
        int armed = value(player, sword, "armedShards");
        hit(player, MeleeAttackThemes.SHARP.equals(attack) ? villager : wrong);
        require(value(player, sword, "armedShards") == armed, "A protected or wrong-category target cannot spend the prepared hit");
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(value(player, sword, "armedShards") == armed, "Cancelled damage cannot spend the prepared hit");
        float before = target.getHealth(); hit(player, target); float enhanced = before - target.getHealth();
        require(enhanced > baseline + 0.5F && value(player, sword, "armedShards") == 0, "A real matching hit must deal and consume its bonus");
        before = target.getHealth(); hit(player, target);
        require(Math.abs((before - target.getHealth()) - baseline) < 0.01F, "Later unarmed hits must not repeat the bonus");

        // A valid weapon with its native enchantment component absent exercises the missing-primary replay.
        MeleeWearSkills.clearPlayer(player.getUUID());
        ItemStack replay = sword.copy(); replay.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, replay);
        require(fund(player, replay) >= 2 && value(player, replay, "shards") >= 2, "Non-native Unbreaking replay must use real prevented wear");
        require(MeleeWearSkills.activate(player, false), "Replay savings can be spent once");
        ItemStack copy = replay.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy); MeleeWearSkills.tickServer(player.level().getServer());
        require(value(player, copy, "armedShards") == 0 && !MeleeWearSkills.activate(player, false), "A replacement stack cannot inherit a prepared hit");
    }
    private static int fund(ServerPlayer player, ItemStack stack) throws Exception {
        int saved = 0;
        for (int i = 0; i < 300 && value(player, stack, "shards") < 2; i++) {
            int before = stack.getDamageValue(); stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            if (stack.getDamageValue() == before) saved++;
        }
        return saved;
    }
    private static void hit(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target);
    }
    private static int value(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field map = MeleeWearSkills.class.getDeclaredField("STATES"); map.setAccessible(true);
        Object raw = ((Map<?, ?>) map.get(null)).get(player.getUUID());
        if (!(raw instanceof List<?> states)) return 0;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object object = gear.get(state);
            var method = object.getClass().getDeclaredMethod("stack"); method.setAccessible(true);
            if (method.invoke(object) != stack) continue;
            Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.getInt(state);
        }
        return 0;
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.TRIDENT;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must validate durable melee carriers");
            }
            ItemStack noWear = new ItemStack(Items.DIAMOND_SWORD); noWear.remove(DataComponents.MAX_DAMAGE);
            inv.setItem(FusionStationOperations.SLOT_TARGET, noWear);
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "A melee item without durability cannot host the new direction");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
