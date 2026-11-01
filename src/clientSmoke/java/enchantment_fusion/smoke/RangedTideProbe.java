package enchantment_fusion.smoke;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.RangedBreathSkills;
import enchantment_fusion.mechanics.signatures.RangedTideSkills;
import enchantment_fusion.mechanics.signatures.RangedWaterDrag;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
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

/** Prepared candidate fixtures; actual shots and collision-clipped movement, but artificial input and water. */
final class RangedTideProbe {
    private RangedTideProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedTideSkills.DEPTH, 2);
        if (reverse) pair = pair.flipped(); checkStation(level, pair, reverse);
        RangedTideSkills.clearPlayer(player.getUUID()); RangedBreathSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET), helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); int air = player.getAirSupply(); Input input = player.getLastClientInput();
        GameType mode = player.gameMode.getGameModeForPlayer();
        AttributeInstance attribute = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        AttributeInstance savedAttribute = new AttributeInstance(attribute.getAttribute(), ignored -> {}); savedAttribute.replaceFrom(attribute);
        AABB area = new AABB(origin).inflate(24); Set<UUID> existing = GrapnelProjectileProbe.arrowIds(level, area);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        Zombie target = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        try {
            for (int x = -4; x <= 12; x++) for (int y = 0; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                BlockPos cell = origin.offset(x, y, z); BlockState state = level.getBlockState(cell);
                require(level.hasChunkAt(cell) && level.isInWorldBounds(cell) && state.isAir(), "Fixture needs loaded disposable space");
                saved.put(cell, state);
            }
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); player.setPos(origin.getX() + .5D, origin.getY() + .2D, origin.getZ() + .5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0); player.setOnGround(false);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            target.setNoAi(true); villager.setNoAi(true);
            for (LivingEntity mob : List.of(target, villager)) { mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); mob.setHealth(1000); }
            target.setPos(player.getX() + 5, player.getY(), player.getZ()); villager.setPos(player.getX() + 5, player.getY(), player.getZ() + 2);
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Targets must enter the world");
            refreshWater(player);
            if (reverse) {
                player.setItemSlot(EquipmentSlot.FEET, GrapnelProjectileProbe.fused(level, Items.DIAMOND_BOOTS, pair));
                require(!RangedTideSkills.activate(player), "Dry activation must fail");
            }
            for (BlockPos cell : saved.keySet()) level.setBlockAndUpdate(cell, Blocks.WATER.defaultBlockState());
            refreshWater(player); refreshWater(target); refreshWater(villager);
            require(player.isInWater() && player.isEyeInFluid(FluidTags.WATER) && target.isInWater(), "Fixture must be underwater");
            if (reverse) reverse(player, pair, target, area);
            else forward(player, pair, theme, target, villager, area, deny);
            for (BlockPos cell : saved.keySet()) require(level.getFluidState(cell).is(FluidTags.WATER), "Water must never be replaced by a skill");
        } finally {
            deny.clear(); player.stopUsingItem(); RangedTideSkills.clearPlayer(player.getUUID()); RangedBreathSkills.clearPlayer(player.getUUID());
            ConfirmedArrow.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player); attribute.replaceFrom(savedAttribute);
            target.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!existing.contains(arrow.getUUID())) arrow.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setItemSlot(EquipmentSlot.FEET, boots); player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setAirSupply(air); player.setLastClientInput(input);
            refreshWater(player); detect(player);
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, String theme, Zombie target, Villager villager,
                                AABB area, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); Vec3 start = player.position();
        ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair); bow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow); require(level.addFreshEntity(fake), "Fake control enters world");
        refreshWater(fake); GrapnelProjectileProbe.hit(fake, target, false); require(!current(player), "A forged projectile cannot create a current");
        target.invulnerableTime = 0; float unchanged = target.getHealth(); deny.add(target.getUUID());
        Arrow cancelled = fireBow(player, bow, area); refreshWater(cancelled); fly(cancelled, 6); deny.clear();
        require(target.getHealth() == unchanged && !current(player), "A cancelled real underwater hit cannot create a current"); cancelled.discard();
        target.setPos(start.x + 5, start.y, start.z + 2); villager.setPos(start.x + 5, start.y, start.z);
        Arrow protectedShot = fireBow(player, bow, area); refreshWater(protectedShot); float health = villager.getHealth(); fly(protectedShot, 6);
        require(villager.getHealth() < health && !current(player), "A real hit on a protected villager cannot create a current");
        target.setPos(start.x + 5, start.y, start.z); villager.setPos(start.x + 5, start.y, start.z + 2); target.invulnerableTime = 0;
        Arrow arrow = fireBow(player, bow, area); refreshWater(arrow); checkPrimary(level, player, arrow, target, theme);
        health = target.getHealth(); fly(arrow, 6);
        require(target.getHealth() < health && current(player), "A real confirmed water hit creates a current along observed steps");
        player.setLastClientInput(Input.EMPTY); player.move(MoverType.SELF, new Vec3(.5, 0, 0)); RangedTideSkills.tickServer(level.getServer());
        require(current(player), "Unrequested displacement cannot ride a current"); player.setPos(start);
        player.setLastClientInput(new Input(true, false, false, false, false, false, false));
        player.move(MoverType.SELF, new Vec3(.5, 0, 0)); player.setPos(player.position().add(3, 0, 0)); RangedTideSkills.tickServer(level.getServer());
        require(current(player), "An unobserved correction cannot consume a current");
        player.setPos(start.add(1, 0, 0)); player.setYRot(90); player.setDeltaMovement(Vec3.ZERO);
        player.move(MoverType.SELF, new Vec3(-.5, 0, 0)); RangedTideSkills.tickServer(level.getServer());
        require(current(player) && player.getDeltaMovement().x == 0, "Swimming against the flow cannot claim it");
        BlockPos interrupted = BlockPos.containing(start.add(2, 1, 0)); level.setBlockAndUpdate(interrupted, Blocks.AIR.defaultBlockState());
        player.setPos(start); player.setYRot(-90); refreshWater(player);
        player.move(MoverType.SELF, new Vec3(.5, 0, 0)); RangedTideSkills.tickServer(level.getServer());
        require(current(player) && player.getDeltaMovement().x == 0, "A path that is no longer entirely water cannot push");
        level.setBlockAndUpdate(interrupted, Blocks.WATER.defaultBlockState());
        player.setPos(start); player.setDeltaMovement(Vec3.ZERO); refreshWater(player);
        player.move(MoverType.SELF, new Vec3(.5, 0, 0)); RangedTideSkills.tickServer(level.getServer());
        require(!current(player) && player.getDeltaMovement().x > 0, "Actual input-directed swimming takes one forward push");
        player.setPos(start); player.setDeltaMovement(Vec3.ZERO); target.invulnerableTime = 0;
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); Arrow bolt = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
        refreshWater(bolt); checkPrimary(level, player, bolt, target, theme); health = target.getHealth(); fly(bolt, 6);
        require(target.getHealth() < health && current(player), "Real crossbow movement can also leave one current");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, Zombie target, AABB area) throws Exception {
        ServerLevel level = player.level(); target.setPos(target.getX(), target.getY(), target.getZ() + 2);
        ItemStack boots = GrapnelProjectileProbe.fused(level, Items.DIAMOND_BOOTS, pair); player.setItemSlot(EquipmentSlot.FEET, boots); detect(player);
        ItemEnchantments nativeEnchants = boots.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        boots.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        Identifier projected = EnchantmentFusion.id("primary_attribute/minecraft/depth_strider/feet/0");
        require(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY).getModifier(projected) != null, "Missing Depth Strider must project once");
        boots.set(DataComponents.ENCHANTMENTS, nativeEnchants); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        require(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY).getModifier(projected) == null, "Restoring native Depth Strider cannot double it");
        Vec3 start = player.position(); player.setLastClientInput(Input.EMPTY); swim(player, .5D);
        require(!RangedTideSkills.activate(player), "Movement without actual input cannot charge"); player.setPos(start);
        player.setLastClientInput(new Input(true, false, false, false, false, false, false));
        swim(player, 1); swim(player, 1); swim(player, 1);
        require(RangedTideSkills.activate(player) && prepared(player), "Three actual input-aligned water blocks prepare an arrow");
        FusedPair breath = FusedPair.clean(RangedBreathSkills.RESPIRATION, 2, RangedWearSkills.POWER, 2);
        player.setItemSlot(EquipmentSlot.HEAD, GrapnelProjectileProbe.fused(level, Items.DIAMOND_HELMET, breath)); player.setAirSupply(150);
        require(!RangedBreathSkills.activate(player) && player.getAirSupply() == 150, "Different water-arrow preparations cannot double-charge");
        ItemStack bow = new ItemStack(Items.BOW); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow absent = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        ConfirmedArrow.launched(absent, player, 0, bow); ConfirmedArrow.sample(absent);
        require(prepared(player), "An arrow absent from the world cannot spend flow"); absent.discard();
        int ammo = player.getOffhandItem().getCount(); Arrow real = fireBow(player, bow, area); ConfirmedArrow.sample(real); refreshWater(real);
        require(!prepared(player) && player.getOffhandItem().getCount() == ammo - 1 && RangedWaterDrag.inertia(real, .6F) > .6F,
            "One genuine bow arrow consumes flow, normal ammo and gains less water drag");
        player.setItemSlot(EquipmentSlot.FEET, boots.copy()); require(RangedWaterDrag.inertia(real, .6F) == .6F,
            "An equal copy cannot inherit the original boot's flight"); real.discard(); player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setYRot(90); swim(player, -1); swim(player, -1); swim(player, -1); player.setYRot(-90);
        require(RangedTideSkills.activate(player), "A later real swim can prepare a crossbow arrow");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        Arrow bolt = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); ConfirmedArrow.sample(bolt); refreshWater(bolt);
        require(!prepared(player) && RangedWaterDrag.inertia(bolt, .6F) > .6F, "Genuine crossbow arrows use the same water drag lease");
    }
    private static void swim(ServerPlayer player, double x) {
        player.move(MoverType.SELF, new Vec3(x, 0, 0)); RangedTideSkills.tickServer(player.level().getServer());
    }
    private static void fly(Arrow arrow, int steps) {
        arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0);
        for (int i = 0; i < steps && arrow.isAlive(); i++) arrow.tick();
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow, AABB area) {
        ServerLevel level = player.level(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must really release");
        player.stopUsingItem(); var found = level.getEntitiesOfClass(Arrow.class, area,
            arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real bow arrow must enter the world"); return found.getFirst();
    }
    private static boolean prepared(ServerPlayer player) throws Exception {
        Field field = RangedTideSkills.class.getDeclaredField("PREPARED"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static boolean current(ServerPlayer player) throws Exception {
        Class<?> type = Class.forName("enchantment_fusion.mechanics.signatures.RangedTideTrail");
        Field field = type.getDeclaredField("CURRENTS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static void checkPrimary(ServerLevel level, ServerPlayer player, Arrow arrow, LivingEntity target, String theme) {
        if (RangedWearSkills.POWER.equals(theme)) require(EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), target,
            level.damageSources().arrow(arrow, player), 2.0F) > 2, "Missing Power must project once");
        if (RangedWearSkills.FLAME.equals(theme)) require(arrow.isOnFire(), "Missing Flame must affect the real arrow");
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
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
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_BOOTS, Items.DIAMOND_HELMET,
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.SHIELD)) {
                ItemStack gear = new ItemStack(item); inventory.setItem(FusionStationOperations.SLOT_TARGET, gear);
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean allowed = reverse ? item == Items.DIAMOND_BOOTS : item == Items.BOW || item == Items.CROSSBOW;
                require(allowed ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use actual ordered carrier capability");
                if (!allowed) {
                    FusionStationOperations.applyFusedPairToGear(gear, pair, level.registryAccess());
                    require(!RangedTideSkills.applies(pair, gear) && gear.getOrDefault(FusionDataComponents.FUSED_PAIRS,
                        FusedEnchantmentList.EMPTY).pairs().contains(pair), "Old inapplicable gear remains intact on its old path");
                }
            }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
