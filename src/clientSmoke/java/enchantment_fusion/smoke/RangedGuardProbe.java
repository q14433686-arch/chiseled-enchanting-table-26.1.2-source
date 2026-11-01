package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.MeleeGuardSkills;
import enchantment_fusion.mechanics.signatures.RangedGuardSkills;
import enchantment_fusion.mechanics.signatures.RangedPrimary;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
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
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Candidate: real damage and projectile movement, but prepared entities and simulated item use. */
final class RangedGuardProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private RangedGuardProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean protectionFirst) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedGuardSkills.PROTECTION, 4);
        if (protectionFirst) pair = pair.flipped();
        checkStation(level, pair, protectionFirst); RangedGuardSkills.clearPlayer(player.getUUID());
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        int ammoSlot = 8; ItemStack oldAmmo = player.getInventory().getItem(ammoSlot);
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH), armor = player.getAttribute(Attributes.ARMOR);
        double maxValue = max.getBaseValue(); AttributeInstance armorBackup = new AttributeInstance(Attributes.ARMOR, ignored -> {}); armorBackup.replaceFrom(armor);
        List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        float health = player.getHealth(), absorption = player.getAbsorptionAmount(), yaw = player.getYRot(), pitch = player.getXRot(), headYaw = player.getYHeadRot();
        int invulnerability = player.invulnerableTime; Vec3 position = player.position(), velocity = player.getDeltaMovement();
        boolean ground = player.onGround(); GameType mode = player.gameMode.getGameModeForPlayer();
        Zombie target = new Zombie(EntityType.ZOMBIE, level), shooter = new Zombie(EntityType.ZOMBIE, level);
        Villager villager = new Villager(EntityType.VILLAGER, level); List<Projectile> projectiles = new ArrayList<>(); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.removeAllEffects();
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.getInventory().setItem(ammoSlot, new ItemStack(Items.ARROW, 64));
            armor.removeModifiers(); armor.setBaseValue(0); max.setBaseValue(200); player.setHealth(200); player.setAbsorptionAmount(0); player.invulnerableTime = 0;
            player.setPos(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setYHeadRot(-90); player.setXRot(0); player.setOnGround(true);
            for (LivingEntity entity : List.of(target, shooter, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(player.getX() + 5, player.getY(), player.getZ());
            shooter.setPos(player.getX() + 9, player.getY(), player.getZ() + 4);
            villager.setPos(player.getX() + 5, player.getY(), player.getZ() + 5);
            require(level.addFreshEntity(target) && level.addFreshEntity(shooter) && level.addFreshEntity(villager), "Targets must enter world");
            if (protectionFirst) exerciseArrow(player, target, shooter, pair, deny, armor, projectiles);
            else exerciseScreen(player, target, shooter, villager, pair, deny, projectiles);
        } finally {
            deny.clear(); RangedGuardSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player); player.stopUsingItem();
            target.discard(); shooter.discard(); villager.discard(); for (Projectile projectile : projectiles) projectile.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]); player.getInventory().setItem(ammoSlot, oldAmmo);
            armor.replaceFrom(armorBackup); max.setBaseValue(maxValue); player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(effect);
            player.setGameMode(mode); player.setHealth(health); player.setAbsorptionAmount(absorption); player.invulnerableTime = invulnerability;
            player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setYHeadRot(headYaw); player.setXRot(pitch); player.setOnGround(ground);
        }
    }
    private static void exerciseScreen(ServerPlayer player, Zombie target, Zombie shooter, Villager villager, FusedPair pair,
                                       Set<UUID> deny, List<Projectile> projectiles) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = fused(level, Items.BOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow); projectiles.add(fake);
        GrapnelProjectileProbe.hit(fake, target, false); require(screens().isEmpty(), "A fabricated unlaunched arrow cannot establish a screen");
        deny.add(target.getUUID()); AbstractArrow cancelled = fireBow(player, bow); projectiles.add(cancelled);
        fly(cancelled, target); deny.clear(); require(screens().isEmpty(), "Cancelled damage cannot establish a screen");
        AbstractArrow valid = fireBow(player, bow); projectiles.add(valid); fly(valid, target);
        require(screens().containsKey(player.getUUID()), "A true eligible arrow impact creates a screen");
        checkPrimary(player, target, bow, pair.primaryId());
        Vec3 shootFrom = player.position(), targetPosition = target.position();
        player.setPos(target.getX() - .8D, target.getY(), target.getZ()); target.setPos(target.getX() + 10, target.getY(), target.getZ() + 10);
        Arrow friendly = new Arrow(level, villager, new ItemStack(Items.ARROW), null); projectiles.add(friendly);
        friendly.setPos(player.getX() + 1, player.getY() + 1, player.getZ()); friendly.setDeltaMovement(new Vec3(-1, 0, 0));
        require(level.addFreshEntity(friendly), "Friendly arrow must be real");
        require(!RangedGuardSkills.intercept(friendly, new EntityHitResult(player, player.getBoundingBox().getCenter())),
            "An ally or villager projectile cannot be taken as hostile");
        Arrow hostile = new Arrow(level, shooter, new ItemStack(Items.ARROW), null); projectiles.add(hostile);
        hostile.setPos(player.getX() + 1.2D, player.getY() + 1, player.getZ()); hostile.setDeltaMovement(new Vec3(-1.6D, 0, 0));
        var pickup = hostile.pickup; require(level.addFreshEntity(hostile), "Hostile arrow must enter world");
        player.invulnerableTime = 0; float before = player.getHealth(); hostile.tick();
        require(player.getHealth() == before && hostile.isAlive() && hostile.getDeltaMovement().x > 0
            && hostile.getOwner() == shooter && hostile.pickup == pickup,
            "Real impact on owner is deflected without deleting or transferring the arrow");
        villager.setPos(player.position()); player.setPos(player.getX(), player.getY(), player.getZ() + 4);
        Arrow villagerThreat = new Arrow(level, shooter, new ItemStack(Items.ARROW), null); projectiles.add(villagerThreat);
        villagerThreat.setPos(villager.getX() + 1.2D, villager.getY() + 1, villager.getZ()); villagerThreat.setDeltaMovement(new Vec3(-1.6D, 0, 0));
        require(level.addFreshEntity(villagerThreat), "Villager threat must enter world");
        float villageHealth = villager.getHealth(); villagerThreat.tick();
        require(villager.getHealth() == villageHealth && villagerThreat.isAlive() && villagerThreat.getDeltaMovement().x > 0
            && villagerThreat.getOwner() == shooter, "A villager in range also benefits from actual hostile impact deflection");
        villager.setPos(villager.getX(), villager.getY(), villager.getZ() + 5); target.setPos(targetPosition);
        player.setPos(shootFrom); player.setItemSlot(EquipmentSlot.MAINHAND, bow.copy()); RangedGuardSkills.tickServer(level.getServer());
        require(!screens().containsKey(player.getUUID()), "An equal-looking copied launcher cannot inherit a screen");
        ItemStack crossbow = fused(level, Items.CROSSBOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        AbstractArrow cross = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, new AABB(player.blockPosition()).inflate(48)); projectiles.add(cross); fly(cross, target);
        require(screens().containsKey(player.getUUID()), "A real crossbow hit also establishes a screen");
        checkPrimary(player, target, crossbow, pair.primaryId());
    }
    private static void checkPrimary(ServerPlayer player, Zombie target, ItemStack weapon, String theme) {
        ServerLevel level = player.level();
        Holder<Enchantment> nativePrimary = FusionStationOperations.lookupEnchantment(level.registryAccess(), theme).orElseThrow();
        boolean nativeOnItem = weapon.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(nativePrimary) > 0;
        Arrow probe = new Arrow(level, player, new ItemStack(Items.ARROW), weapon); var source = level.damageSources().arrow(probe, player);
        if (RangedWearSkills.POWER.equals(theme)) {
            float modified = RangedPrimary.damage(level, weapon, target, source, 4.0F);
            require(nativeOnItem ? modified == 4.0F : modified > 4.0F, "Power is native or projected once");
        } else if (RangedWearSkills.FLAME.equals(theme)) {
            RangedPrimary.spawned(probe);
            require(nativeOnItem ? !probe.isOnFire() : probe.isOnFire(), "Flame is native or projected once");
        } else {
            float knockback = RangedPrimary.knockback(level, weapon, target, source, 0);
            require(nativeOnItem ? knockback == 0 : knockback > 0, "Punch is native or projected once");
        }
    }
    private static void exerciseArrow(ServerPlayer player, Zombie target, Zombie shooter, FusedPair pair, Set<UUID> deny,
                                      AttributeInstance armor, List<Projectile> projectiles) throws Exception {
        ServerLevel level = player.level(); ItemStack chest = fused(level, Items.DIAMOND_CHESTPLATE, pair);
        player.setItemSlot(EquipmentSlot.CHEST, chest); installArmor(armor, chest, EquipmentSlot.CHEST);
        deny.add(player.getUUID()); player.invulnerableTime = 0; player.hurtServer(level, level.damageSources().generic(), 30); deny.clear();
        require(charge(player) == 0 && !RangedGuardSkills.activate(player), "Cancelled damage cannot prepare arrow protection");
        player.invulnerableTime = 0; require(player.hurtServer(level, level.damageSources().generic(), 30), "Real damage must be accepted");
        require(charge(player) >= 2 && RangedGuardSkills.activate(player), "Actual item-attributable mitigation prepares protection");
        ItemStack bow = new ItemStack(Items.BOW); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow); projectiles.add(fake);
        GrapnelProjectileProbe.hit(fake, target, false); require(prepared().containsKey(player.getUUID()), "An unlaunched arrow cannot consume preparation");
        AbstractArrow real = fireBow(player, bow); projectiles.add(real); ConfirmedArrow.sample(real);
        require(!prepared().containsKey(player.getUUID()) && guarded().containsKey(real), "Only a real launched arrow receives the protection once");
        LargeFireball incoming = new LargeFireball(level, shooter, new Vec3(-1, 0, 0), 0); projectiles.add(incoming);
        incoming.setPos(player.getX() + 2.5D, player.getEyeY() - .1D, player.getZ()); incoming.setDeltaMovement(new Vec3(-1, 0, 0));
        require(level.addFreshEntity(incoming), "Hostile redirectable projectile must enter world");
        real.setPos(player.getX() + .8D, player.getEyeY() - .1D, player.getZ()); real.setDeltaMovement(new Vec3(3, 0, 0)); real.tick();
        require(real.isAlive() && incoming.isAlive() && incoming.getDeltaMovement().x > 0 && incoming.getOwner() == shooter && !guarded().containsKey(real),
            "Real collision with a hostile redirectable projectile spends protection, preserving both entities and ownership");
        incoming.discard();
        player.invulnerableTime = 0; player.hurtServer(level, level.damageSources().generic(), 30);
        require(RangedGuardSkills.activate(player), "A second real defense can prepare a crossbow shot");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        AbstractArrow cross = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, new AABB(player.blockPosition()).inflate(48)); projectiles.add(cross); ConfirmedArrow.sample(cross);
        require(guarded().containsKey(cross) && !prepared().containsKey(player.getUUID()), "A real crossbow arrow receives protection");
        player.setItemSlot(EquipmentSlot.CHEST, chest.copy()); RangedGuardSkills.tickServer(level.getServer());
        require(!guarded().containsKey(cross), "Replacing protective gear with a copy invalidates arrow protection");
        RangedGuardSkills.clearPlayer(player.getUUID()); armor.removeModifiers(); armor.setBaseValue(0);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.OFFHAND, fused(level, Items.SHIELD, pair));
        player.invulnerableTime = 0; player.setHealth(200); require(player.hurtServer(level, level.damageSources().generic(), 30), "Non-native shield damage accepted");
        require(charge(player) >= 2, "Held shield replays and attributes its Protection once");
        RangedGuardSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); player.setHealth(200); player.invulnerableTime = 0;
        ItemStack shield = player.getOffhandItem(); player.startUsingItem(InteractionHand.OFF_HAND);
        Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining"); remaining.setAccessible(true); remaining.setInt(player, shield.getUseDuration(player) - 10);
        deny.add(player.getUUID()); player.hurtServer(level, level.damageSources().mobAttack(shooter), 8); deny.clear();
        require(charge(player) == 0, "Cancelled shield damage does not create credit");
        player.invulnerableTime = 0; player.setYRot(-90); shooter.setPos(player.getX() + 2, player.getY(), player.getZ());
        float before = player.getHealth(); boolean accepted = player.hurtServer(level, level.damageSources().mobAttack(shooter), 8);
        require(!accepted && player.getHealth() == before && charge(player) > 0 && charge(player) <= 4, "Real complete blocking is credited once");
        player.stopUsingItem();
    }
    private static void fly(AbstractArrow arrow, Zombie target) {
        target.invulnerableTime = 0; arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0);
        for (int i = 0; i < 4 && arrow.isAlive(); i++) arrow.tick();
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow) {
        ServerLevel level = player.level(); AABB area = new AABB(player.blockPosition()).inflate(48);
        Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release"); player.stopUsingItem();
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real bow arrow must enter the world"); return found.getFirst();
    }
    private static void installArmor(AttributeInstance armor, ItemStack stack, EquipmentSlot slot) {
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) armor.addTransientModifier(modifier);
        });
    }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> screens() throws Exception { return map("SCREENS"); }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> prepared() throws Exception { return map("PREPARED"); }
    @SuppressWarnings("unchecked") private static Map<AbstractArrow, Object> guarded() throws Exception { return map("GUARDED"); }
    @SuppressWarnings("unchecked") private static <K> Map<K, Object> map(String name) throws Exception {
        Field field = RangedGuardSkills.class.getDeclaredField(name); field.setAccessible(true); return (Map<K, Object>) field.get(null);
    }
    @SuppressWarnings("unchecked") private static float charge(ServerPlayer player) throws Exception {
        Field field = RangedGuardSkills.class.getDeclaredField("CHARGES"); field.setAccessible(true);
        List<Object> values = ((Map<UUID, List<Object>>) field.get(null)).getOrDefault(player.getUUID(), List.of());
        float total = 0; for (Object value : values) { Field amount = value.getClass().getDeclaredField("amount"); amount.setAccessible(true); total += amount.getFloat(value); }
        return total;
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean protectionFirst) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inventory = new SimpleContainer(4);
            if (direct) {
                inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inventory.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.SHIELD, Items.DIAMOND_SWORD)) {
                inventory.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = protectionFirst ? item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.SHIELD
                    : item == Items.BOW || item == Items.CROSSBOW;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station paths check this direction's carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
