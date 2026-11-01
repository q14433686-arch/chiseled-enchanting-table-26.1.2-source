package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeMendingSkills;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
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

/** Four server fixtures: real attacks and orb touches, with simulated aim and attack timing. */
final class MeleeMendingProbe {
    private MeleeMendingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean mendingFirst) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(attack, 4, DurabilityEconomy.MENDING, 1);
        if (mendingFirst) pair = pair.flipped(); checkStation(level, pair); MeleeMendingSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        int xpLevel = player.experienceLevel, xpTotal = player.totalExperience, xpDelay = player.takeXpDelay; float xpProgress = player.experienceProgress;
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue(); boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity neighbor = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(bane ? 15 : 0); player.setOnGround(false); player.fallDistance = 0; speed.setBaseValue(1024);
            for (LivingEntity entity : List.of(target, neighbor, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ()); neighbor.setPos(player.getX() + 4.2, player.getY(), player.getZ());
            wrong.setPos(player.getX() + 2, player.getY(), player.getZ() + 3); villager.setPos(player.getX() + 2, player.getY(), player.getZ() - 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(neighbor) && level.addFreshEntity(wrong) && level.addFreshEntity(villager), "Fixture targets must enter the world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair); sword.setDamageValue(100); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            if (mendingFirst) reverse(player, sword, target, neighbor, wrong, deny);
            else forward(player, sword, target, wrong, villager, deny);
        } finally {
            deny.clear(); MeleeMendingSkills.clearPlayer(player.getUUID()); target.discard(); neighbor.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
            player.experienceLevel = xpLevel; player.totalExperience = xpTotal; player.experienceProgress = xpProgress; player.takeXpDelay = xpDelay;
        }
    }
    private static void forward(ServerPlayer player, ItemStack sword, LivingEntity target, LivingEntity wrong, Villager villager, Set<UUID> deny) throws Exception {
        hit(player, wrong); hit(player, villager); require(value(player, sword, "target") == null, "Wrong and protected targets cannot be marked");
        deny.add(target.getUUID()); hit(player, target); deny.clear(); require(value(player, sword, "target") == null, "Cancelled damage cannot mark");
        float before = target.getHealth(); hit(player, target); float baseline = before - target.getHealth();
        require(baseline > 0 && target.getUUID().equals(value(player, sword, "target")), "A real full hit must mark its target");
        int wear = sword.getDamageValue(); deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(sword.getDamageValue() == wear && target.getUUID().equals(value(player, sword, "target")), "Cancelled follow-up cannot consume or repair");
        before = target.getHealth(); hit(player, target); float diverted = before - target.getHealth();
        require(diverted > 0 && diverted < baseline && sword.getDamageValue() < wear && value(player, sword, "target") == null,
            "The actual follow-up must deal less and repair this same weapon once");
    }
    private static void reverse(ServerPlayer player, ItemStack sword, LivingEntity target, LivingEntity neighbor, LivingEntity wrong, Set<UUID> deny) throws Exception {
        require(!MeleeMendingSkills.activate(player), "Unpaid blade cannot be prepared");
        sword.setDamageValue(90); require(intValue(player, sword, "forge") == 0, "Direct durability edits cannot earn marks");
        int beforeDamage = sword.getDamageValue(), beforeXp = player.totalExperience; touchOrb(player, 4);
        require(sword.getDamageValue() < beforeDamage && intValue(player, sword, "forge") > 0 && player.totalExperience == beforeXp,
            "Actual orb repair must earn marks without duplicating the used XP");
        require(MeleeMendingSkills.activate(player) && intValue(player, sword, "armed") > 0, "Real repair can prepare one edge");
        int armed = intValue(player, sword, "armed"); hit(player, wrong);
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(intValue(player, sword, "armed") == armed, "Wrong or cancelled hits cannot spend the prepared edge");
        float before = neighbor.getHealth(); hit(player, target);
        require(intValue(player, sword, "armed") == 0 && neighbor.getHealth() < before, "A real full hit must release the blade once");
        MeleeMendingSkills.clearPlayer(player.getUUID()); ItemStack cross = sword.copy(); cross.remove(DataComponents.ENCHANTMENTS);
        cross.setDamageValue(50); player.setItemSlot(EquipmentSlot.MAINHAND, cross); beforeXp = player.totalExperience; touchOrb(player, 2);
        require(cross.getDamageValue() < 50 && intValue(player, cross, "forge") > 0 && player.totalExperience == beforeXp,
            "Missing native Mending must use actual orb XP and earn marks once");
        MeleeMendingSkills.clearPlayer(player.getUUID()); cross.setDamageValue(50);
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.setDamageValue(1);
        var nativeMending = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(DurabilityEconomy.MENDING)));
        helmet.enchant(nativeMending, 1); player.setItemSlot(EquipmentSlot.HEAD, helmet);
        beforeXp = player.totalExperience; touchOrb(player, 2);
        require(helmet.getDamageValue() == 0 && cross.getDamageValue() == 46 && intValue(player, cross, "forge") == 4
            && player.totalExperience == beforeXp, "Native recursive repair and the non-native pass must conserve the same orb XP");
    }
    private static void touchOrb(ServerPlayer player, int value) {
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), value);
        try { require(player.level().addFreshEntity(orb), "Orb must enter the world"); player.takeXpDelay = 0; orb.playerTouch(player); }
        finally { orb.discard(); }
    }
    private static void hit(ServerPlayer player, LivingEntity target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static int intValue(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Object value = value(player, stack, name); return value instanceof Integer number ? number : 0;
    }
    private static Object value(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field field = MeleeMendingSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        Object raw = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (!(raw instanceof List<?> states)) return null;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object object = gear.get(state);
            Method getter = object.getClass().getDeclaredMethod("stack"); getter.setAccessible(true);
            if (getter.invoke(object) == stack) { Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(state); }
        }
        return null;
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
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.TRIDENT;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths require a durable melee carrier");
            }
            ItemStack noWear = new ItemStack(Items.DIAMOND_SWORD); noWear.remove(DataComponents.MAX_DAMAGE);
            inv.setItem(FusionStationOperations.SLOT_TARGET, noWear);
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "A weapon without durability cannot host this direction");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
