package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeGuardSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Six bounded probes: real damage/armor and projectile impact, simulated input and mark age. */
final class MeleeGuardProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private MeleeGuardProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean defenseFirst) throws Exception {
        ServerLevel level = player.level();
        FusedPair pair = FusedPair.clean(attack, 4, MeleeGuardSkills.PROTECTION, 4);
        if (defenseFirst) pair = pair.flipped();
        checkStation(level, pair, defenseFirst);
        MeleeGuardSkills.clearPlayer(player.getUUID());
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        var max = player.getAttribute(Attributes.MAX_HEALTH); double maxValue = max.getBaseValue();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double speedValue = speed.getBaseValue();
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR), armorBackup = new AttributeInstance(Attributes.ARMOR, ignored -> {});
        armorBackup.replaceFrom(armor);
        List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        float health = player.getHealth(), absorption = player.getAbsorptionAmount(), yaw = player.getYRot(), pitch = player.getXRot();
        float headYaw = player.getYHeadRot();
        int invulnerability = player.invulnerableTime; Vec3 position = player.position(), velocity = player.getDeltaMovement();
        boolean ground = player.onGround(), sprint = player.isSprinting(); double fall = player.fallDistance;
        GameType mode = player.gameMode.getGameModeForPlayer();
        boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = enemy(level, bane), neighbor = enemy(level, bane), wrong = enemy(level, !bane);
        Set<UUID> deny = new HashSet<>(); List<Arrow> arrows = new ArrayList<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.removeAllEffects();
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            armor.removeModifiers(); armor.setBaseValue(0); max.setBaseValue(200); speed.setBaseValue(1024);
            player.setHealth(200); player.setAbsorptionAmount(0); player.invulnerableTime = 0;
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setYHeadRot(-90); player.setXRot(bane ? 15 : 0); player.setOnGround(false); player.setSprinting(false); player.fallDistance = 0;
            for (LivingEntity entity : List.of(target, neighbor, wrong)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ());
            neighbor.setPos(player.getX() + 4.2, player.getY(), player.getZ());
            wrong.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(neighbor) && level.addFreshEntity(wrong), "Targets must enter the disposable world");
            if (defenseFirst) exerciseCounter(player, target, neighbor, wrong, pair, attack, deny, armor);
            else exerciseBlade(player, target, wrong, pair, attack, deny, arrows);
        } finally {
            deny.clear(); MeleeGuardSkills.clearPlayer(player.getUUID()); player.stopUsingItem();
            target.discard(); neighbor.discard(); wrong.discard(); for (Arrow arrow : arrows) arrow.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            armor.replaceFrom(armorBackup); max.setBaseValue(maxValue); speed.setBaseValue(speedValue);
            player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(effect);
            player.setGameMode(mode); player.setHealth(health); player.setAbsorptionAmount(absorption); player.invulnerableTime = invulnerability;
            player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setYHeadRot(headYaw); player.setXRot(pitch);
            player.setOnGround(ground); player.setSprinting(sprint); player.fallDistance = fall;
        }
    }
    private static void exerciseBlade(ServerPlayer player, LivingEntity target, LivingEntity wrong, FusedPair pair,
                                      String attack, Set<UUID> deny, List<Arrow> arrows) throws Exception {
        ServerLevel level = player.level(); ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(guards().isEmpty(), "Cancelled damage cannot form a guard");
        if (!MeleeAttackThemes.SHARP.equals(attack)) {
            hit(player, wrong); require(guards().isEmpty(), "Wrong-category damage cannot form a guard");
        }
        hit(player, target); require(guards().containsKey(player.getUUID()), "A real qualified hit forms the guard");
        Arrow friendly = new Arrow(level, player, new ItemStack(Items.ARROW), null); arrows.add(friendly);
        friendly.setDeltaMovement(new Vec3(-1, 0, 0));
        require(!MeleeGuardSkills.deflect(friendly, player, new EntityHitResult(player, player.getBoundingBox().getCenter().add(0.3D, 0, 0))),
            "An owner's own projectile must not be deflected");
        Arrow hostile = new Arrow(level, target, new ItemStack(Items.ARROW), null); arrows.add(hostile);
        hostile.setPos(player.getX() + 1.25D, player.getY() + 1.0D, player.getZ()); hostile.setDeltaMovement(new Vec3(-1.6D, 0, 0));
        var pickup = hostile.pickup; require(level.addFreshEntity(hostile), "Hostile arrow must enter the world");
        float before = player.getHealth(); hostile.tick();
        require(player.getHealth() == before && hostile.isAlive() && hostile.getDeltaMovement().x > 0
            && hostile.getOwner() == target && hostile.pickup == pickup,
            "A real front impact must deflect the same hostile arrow without changing its owner or pickup");
        ageGuard(player); MeleeGuardSkills.onSwing(player, InteractionHand.MAIN_HAND);
        require(!guards().containsKey(player.getUUID()), "A subsequent simulated main-hand swing recalls the guard");
    }
    private static void exerciseCounter(ServerPlayer player, LivingEntity target, LivingEntity neighbor, LivingEntity wrong,
                                        FusedPair pair, String attack, Set<UUID> deny, AttributeInstance armor) throws Exception {
        ServerLevel level = player.level(); ItemStack chest = fused(level, Items.DIAMOND_CHESTPLATE, pair);
        player.setItemSlot(EquipmentSlot.CHEST, chest); installArmor(armor, chest, EquipmentSlot.CHEST);
        deny.add(player.getUUID()); player.invulnerableTime = 0; player.hurtServer(level, level.damageSources().generic(), 30); deny.clear();
        require(charge(player) == 0, "Cancelled damage cannot credit a protective item");
        player.invulnerableTime = 0; require(player.hurtServer(level, level.damageSources().generic(), 30), "Armor fixture damage must be accepted");
        float first = charge(player); require(first >= 2 && first <= 12, "Real protection by this chest must create bounded credit");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        if (!MeleeAttackThemes.SHARP.equals(attack)) {
            hit(player, wrong); require(charge(player) == first, "Wrong-category melee cannot spend the stored counter");
        }
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(charge(player) == first, "Cancelled melee cannot spend the stored counter");
        float before = neighbor.getHealth(); hit(player, target);
        require(charge(player) < first && neighbor.getHealth() < before, "A real eligible melee releases the aimed counterblade");

        MeleeGuardSkills.clearPlayer(player.getUUID()); armor.removeModifiers(); armor.setBaseValue(0);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.OFFHAND, fused(level, Items.SHIELD, pair));
        player.invulnerableTime = 0; player.setHealth(200);
        require(player.hurtServer(level, level.damageSources().generic(), 30), "Shield fixture damage must be accepted");
        require(charge(player) >= 2, "A held non-native shield must replay actual Protection once and earn its own credit");
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); MeleeGuardSkills.tickServer(level.getServer());
        require(charge(player) == 0, "Removing the exact protective item invalidates its credit");

        ItemStack shield = fused(level, Items.SHIELD, pair);
        player.setItemSlot(EquipmentSlot.OFFHAND, shield); player.setHealth(200); player.invulnerableTime = 0;
        player.startUsingItem(InteractionHand.OFF_HAND);
        Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining"); remaining.setAccessible(true);
        remaining.setInt(player, shield.getUseDuration(player) - 10); // simulate the real block-delay time
        require(player.getItemBlockingWith() == shield, "The original shield must be actively blocking");
        deny.add(player.getUUID()); player.hurtServer(level, level.damageSources().mobAttack(target), 8); deny.clear();
        require(charge(player) == 0, "Damage cancelled before the shield cannot earn block credit");
        player.invulnerableTime = 0; float beforeBlock = player.getHealth();
        boolean accepted = player.hurtServer(level, level.damageSources().mobAttack(target), 8);
        require(!accepted && player.getHealth() == beforeBlock && charge(player) > 0 && charge(player) <= 4,
            "A fully blocked real hit earns capped shield credit even if vanilla returns false");
        MeleeGuardSkills.clearPlayer(player.getUUID()); player.invulnerableTime = 20;
        player.hurtServer(level, level.damageSources().mobAttack(target), 8);
        require(charge(player) == 0, "A cooldown-rejected block cannot earn new shield credit");
        player.stopUsingItem();
    }
    private static LivingEntity enemy(ServerLevel level, boolean spider) {
        return spider ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
    }
    private static void hit(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target);
    }
    private static void installArmor(AttributeInstance armor, ItemStack stack, EquipmentSlot slot) {
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) armor.addTransientModifier(modifier);
        });
    }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> guards() throws Exception {
        Field field = MeleeGuardSkills.class.getDeclaredField("GUARDS"); field.setAccessible(true); return (Map<UUID, Object>) field.get(null);
    }
    @SuppressWarnings("unchecked") private static float charge(ServerPlayer player) throws Exception {
        Field field = MeleeGuardSkills.class.getDeclaredField("CHARGES"); field.setAccessible(true);
        List<Object> values = ((Map<UUID, List<Object>>) field.get(null)).getOrDefault(player.getUUID(), List.of());
        float total = 0; for (Object value : values) { Field amount = value.getClass().getDeclaredField("amount"); amount.setAccessible(true); total += amount.getFloat(value); }
        return total;
    }
    private static void ageGuard(ServerPlayer player) throws Exception {
        Object current = guards().get(player.getUUID()); require(current != null, "Guard must exist before aging");
        Field weapon = current.getClass().getDeclaredField("weapon"), pair = current.getClass().getDeclaredField("pair");
        weapon.setAccessible(true); pair.setAccessible(true);
        var constructor = current.getClass().getDeclaredConstructors()[0]; constructor.setAccessible(true);
        guards().put(player.getUUID(), constructor.newInstance(player, weapon.get(current), pair.get(current), player.level().getGameTime() - 5));
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean defenseFirst) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inventory = new SimpleContainer(4);
            if (direct) {
                inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inventory.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.SHIELD, Items.BOW, Items.DIAMOND_PICKAXE)) {
                inventory.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = defenseFirst ? item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.SHIELD
                    : item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must reject unsupported directional hosts");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
