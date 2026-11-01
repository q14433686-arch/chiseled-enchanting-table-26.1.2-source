package enchantment_fusion.smoke;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.RangedSneakMath;
import enchantment_fusion.mechanics.signatures.RangedSneakSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Candidate fixture: real launcher actions with manually prepared pose, input and projectile ticks. */
final class RangedSneakProbe {
    private RangedSneakProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedSneakSkills.SWIFT, 2);
        if (reverse) pair = pair.flipped(); checkStation(level, pair, reverse);
        RangedSneakSkills.clearPlayer(player); ConfirmedArrow.clearPlayer(player.getUUID());
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem(), legs = player.getItemBySlot(EquipmentSlot.LEGS);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(), sneaking = player.isShiftKeyDown(); Input input = player.getLastClientInput();
        GameType mode = player.gameMode.getGameModeForPlayer();
        AttributeInstance attribute = player.getAttribute(Attributes.SNEAKING_SPEED);
        AttributeInstance savedAttribute = new AttributeInstance(attribute.getAttribute(), ignored -> {}); savedAttribute.replaceFrom(attribute);
        AABB area = new AABB(origin).inflate(24); Set<UUID> existing = GrapnelProjectileProbe.arrowIds(level, area);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        Zombie target = new Zombie(EntityType.ZOMBIE, level);
        try {
            for (int x = -3; x <= 8; x++) for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++) {
                BlockPos cell = origin.offset(x, y, z); BlockState state = level.getBlockState(cell);
                require(level.hasChunkAt(cell) && level.isInWorldBounds(cell) && state.isAir(), "Fixture needs loaded disposable space"); saved.put(cell, state);
            }
            for (int x = -3; x <= 8; x++) for (int z = -2; z <= 2; z++)
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.setPos(origin.getX() + .5D, origin.getY() + 1, origin.getZ() + .5D); player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true); player.setLastClientInput(Input.EMPTY); player.setYRot(-90); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            target.setNoAi(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            target.setPos(player.getX() + 5, player.getY(), player.getZ()); require(level.addFreshEntity(target), "Target must enter the world");
            if (reverse) reverse(player, pair, target, origin, area); else forward(player, pair, theme, target, area, deny);
        } finally {
            deny.clear(); player.stopUsingItem(); RangedSneakSkills.clearPlayer(player); ConfirmedArrow.clearPlayer(player.getUUID());
            MeleeWorkSkills.clearPlayer(player); attribute.replaceFrom(savedAttribute); target.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!existing.contains(arrow.getUUID())) arrow.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off); player.setItemSlot(EquipmentSlot.LEGS, legs);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setLastClientInput(input); player.setShiftKeyDown(sneaking); detect(player);
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, String theme, Zombie target, AABB area, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair); bow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, bow); player.setShiftKeyDown(false); aim(player, target);
        target.invulnerableTime = 0; Arrow standing = fireBow(player, bow, area, 20); fly(standing);
        require(!state(player, "AIMS"), "A standing shot cannot create sneak aim");
        player.setShiftKeyDown(true); aim(player, target); target.invulnerableTime = 0;
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow); require(level.addFreshEntity(fake), "Control enters world");
        GrapnelProjectileProbe.hit(fake, target, false); require(!state(player, "AIMS"), "Forged arrow cannot earn aim");
        target.invulnerableTime = 0; deny.add(target.getUUID()); Arrow cancelled = fireBow(player, bow, area, 20); fly(cancelled); deny.clear(); cancelled.discard();
        require(!state(player, "AIMS"), "Cancelled damage cannot earn aim");
        target.invulnerableTime = 0; Arrow shot = fireBow(player, bow, area, 20); checkPrimary(level, player, shot, target, theme); fly(shot);
        require(state(player, "AIMS") && RangedSneakSkills.bowTicks(player, bow, 10) == 10 + RangedSneakMath.credit(pair.secondaryLevel()),
            "Actual sneak hit and aim accelerate the next bow draw");
        player.setYRot(90); require(RangedSneakSkills.bowTicks(player, bow, 10) == 10, "Looking away removes the acceleration immediately");
        RangedSneakSkills.tickServer(level.getServer()); require(!state(player, "AIMS"), "A lost aim cannot resume later");
        aim(player, target); target.invulnerableTime = 0; fly(fireBow(player, bow, area, 20)); require(state(player, "AIMS"), "A new hit can prepare again");
        player.setShiftKeyDown(false); RangedSneakSkills.tickServer(level.getServer()); require(!state(player, "AIMS"), "Standing cancels the aim");
        player.setShiftKeyDown(true); aim(player, target); target.invulnerableTime = 0; fly(fireBow(player, bow, area, 20));
        player.setItemSlot(EquipmentSlot.MAINHAND, bow.copy()); RangedSneakSkills.tickServer(level.getServer());
        require(!state(player, "AIMS"), "An equal launcher copy cannot inherit aim"); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        aim(player, target); target.invulnerableTime = 0; fly(fireBow(player, bow, area, 20)); require(state(player, "AIMS"), "Original weapon can build new aim");
        int ammo = player.getOffhandItem().getCount(); Arrow accelerated = fireBow(player, bow, area, 20 - RangedSneakMath.credit(pair.secondaryLevel()));
        ConfirmedArrow.sample(accelerated);
        require(accelerated.isCritArrow() && !state(player, "AIMS") && player.getOffhandItem().getCount() == ammo - 1,
            "A genuine accelerated bow shot spends one aim and ordinary ammunition"); accelerated.discard();
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); aim(player, target); target.invulnerableTime = 0;
        fly(GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area)); require(state(player, "AIMS"), "A real crossbow hit earns aim");
        int duration = CrossbowItem.getChargeDuration(crossbow, player);
        require(duration < 25, "Aimed arrow loading must be shorter than the normal crossbow duration");
        player.startUsingItem(InteractionHand.MAIN_HAND);
        ((CrossbowItem) crossbow.getItem()).onUseTick(level, player, crossbow, crossbow.getUseDuration(player) - duration);
        require(CrossbowItem.isCharged(crossbow) && !state(player, "AIMS"), "Only a successful real load spends aim");
        require(CrossbowItem.getChargeDuration(crossbow, player) == duration
            && ((CrossbowItem) crossbow.getItem()).releaseUsing(crossbow, level, player, crossbow.getUseDuration(player) - duration),
            "The completed accelerated load remains valid through release");
        player.stopUsingItem(); RangedSneakSkills.tickServer(level.getServer());
        require(CrossbowItem.getChargeDuration(crossbow, player) >= duration, "Load credit ends with that use action");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, Zombie target, BlockPos origin, AABB area) throws Exception {
        ServerLevel level = player.level(); ItemStack legs = GrapnelProjectileProbe.fused(level, Items.DIAMOND_LEGGINGS, pair);
        player.setItemSlot(EquipmentSlot.LEGS, legs); detect(player); ItemEnchantments nativeEnchants = legs.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        legs.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        Identifier projected = EnchantmentFusion.id("primary_attribute/minecraft/swift_sneak/legs/0");
        require(player.getAttribute(Attributes.SNEAKING_SPEED).getModifier(projected) != null, "Missing Swift Sneak must project once");
        legs.set(DataComponents.ENCHANTMENTS, nativeEnchants); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        require(player.getAttribute(Attributes.SNEAKING_SPEED).getModifier(projected) == null, "Native Swift Sneak cannot double");
        ItemStack bow = new ItemStack(Items.BOW); player.setItemSlot(EquipmentSlot.MAINHAND, bow); player.setShiftKeyDown(false);
        Arrow standing = fireBow(player, bow, area, 20); ConfirmedArrow.sample(standing); standing.discard();
        require(!state(player, "SLIPS"), "Standing shot cannot prepare a retreat");
        player.setShiftKeyDown(true); player.setYRot(-90); player.setXRot(0);
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow); require(level.addFreshEntity(fake), "Control enters world");
        ConfirmedArrow.sample(fake); fake.discard(); require(!state(player, "SLIPS"), "A forged arrow cannot prepare retreat");
        Arrow real = fireBow(player, bow, area, 20); ConfirmedArrow.sample(real); real.discard(); require(state(player, "SLIPS"), "A real crouched arrow prepares retreat");
        player.setOnGround(false); require(!RangedSneakSkills.activate(player) && state(player, "SLIPS"), "Airborne attempt preserves retreat");
        player.setOnGround(true); player.setLastClientInput(new Input(true, false, false, false, false, false, false));
        BlockPos wall = origin.offset(1, 1, 0); level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        require(!RangedSneakSkills.activate(player) && state(player, "SLIPS"), "An immediate wall cannot consume or bypass retreat");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); Vec3 start = player.position(); player.setDeltaMovement(Vec3.ZERO);
        require(RangedSneakSkills.activate(player) && !state(player, "SLIPS") && player.position().equals(start) && player.getDeltaMovement().x > 0,
            "Chosen direction grants one velocity impulse, never a teleport");
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState()); player.move(MoverType.SELF, new Vec3(.8D, 0, 0));
        require(player.getX() < origin.getX() + 1, "Normal movement remains blocked by a real wall");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); player.setPos(start); player.setDeltaMovement(Vec3.ZERO);
        require(!RangedSneakSkills.activate(player), "A retreat can be spent only once");
        Arrow next = fireBow(player, bow, area, 20); ConfirmedArrow.sample(next); next.discard();
        player.setItemSlot(EquipmentSlot.LEGS, legs.copy()); RangedSneakSkills.tickServer(level.getServer());
        require(!state(player, "SLIPS"), "An equal leggings copy cannot inherit retreat"); player.setItemSlot(EquipmentSlot.LEGS, legs);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        Arrow bolt = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); ConfirmedArrow.sample(bolt); bolt.discard();
        require(state(player, "SLIPS"), "A real crossbow arrow can prepare retreat");
        player.setLastClientInput(Input.EMPTY); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
        require(RangedSneakSkills.activate(player) && player.getDeltaMovement().x < 0, "Without input retreat is opposite the view");
    }
    private static void aim(ServerPlayer player, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(player.getEyePosition()); player.setYRot(-90);
        player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }
    private static void fly(Arrow arrow) {
        arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0); for (int i = 0; i < 4 && arrow.isAlive(); i++) arrow.tick();
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow, AABB area, int ticks) {
        ServerLevel level = player.level(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - ticks), "Bow must release"); player.stopUsingItem();
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real bow arrow must enter the world"); return found.getFirst();
    }
    private static boolean state(ServerPlayer player, String name) throws Exception {
        Field field = RangedSneakSkills.class.getDeclaredField(name); field.setAccessible(true); return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static void checkPrimary(ServerLevel level, ServerPlayer player, Arrow arrow, LivingEntity target, String theme) {
        if (RangedWearSkills.POWER.equals(theme)) require(EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), target,
            level.damageSources().arrow(arrow, player), 2.0F) > 2, "Missing Power must project once");
        if (RangedWearSkills.FLAME.equals(theme)) require(arrow.isOnFire(), "Missing Flame must affect the real arrow");
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean reverse) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var a = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var b = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inventory = new SimpleContainer(4);
            if (direct) {
                inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(a, pair.primaryLevel()));
                inventory.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(b, pair.secondaryLevel()));
            } else inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.SHIELD)) {
                ItemStack gear = new ItemStack(item); inventory.setItem(FusionStationOperations.SLOT_TARGET, gear);
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean allowed = reverse ? item == Items.DIAMOND_LEGGINGS : item == Items.BOW || item == Items.CROSSBOW;
                require(allowed ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use actual ordered carrier capability");
                if (!allowed) {
                    FusionStationOperations.applyFusedPairToGear(gear, pair, level.registryAccess());
                    require(!RangedSneakSkills.applies(pair, gear) && gear.getOrDefault(FusionDataComponents.FUSED_PAIRS,
                        FusedEnchantmentList.EMPTY).pairs().contains(pair), "Old inapplicable gear remains intact on its old path");
                }
            }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
