package enchantment_fusion.smoke;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.RangedBreathSkills;
import enchantment_fusion.mechanics.signatures.RangedWaterDrag;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Six candidate fixtures. Water and shots are prepared directly, not natural input acceptance. */
final class RangedBreathProbe {
    private RangedBreathProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedBreathSkills.RESPIRATION, 2);
        if (reverse) pair = pair.flipped(); checkStation(level, pair, reverse);
        RangedBreathSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem(), helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); int air = player.getAirSupply(); GameType mode = player.gameMode.getGameModeForPlayer();
        AttributeInstance oxygen = player.getAttribute(Attributes.OXYGEN_BONUS);
        AttributeInstance savedOxygen = new AttributeInstance(oxygen.getAttribute(), ignored -> {}); savedOxygen.replaceFrom(oxygen);
        AABB area = new AABB(origin).inflate(24); Set<UUID> existing = GrapnelProjectileProbe.arrowIds(level, area);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        Zombie target = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        try {
            for (int x = -4; x <= 10; x++) for (int y = 0; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                BlockPos cell = origin.offset(x, y, z); BlockState state = level.getBlockState(cell);
                require(level.hasChunkAt(cell) && level.isInWorldBounds(cell) && state.isAir(), "Fixture requires loaded disposable open space");
                saved.put(cell, state);
            }
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setPos(origin.getX() + .5D, origin.getY() + .2D, origin.getZ() + .5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0); player.setOnGround(false);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            target.setNoAi(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            villager.setNoAi(true); villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); villager.setHealth(1000);
            target.setPos(player.getX() + 5, player.getY(), player.getZ());
            villager.setPos(player.getX() + 5, player.getY(), player.getZ() + 2);
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Fixture targets must enter the world");
            refreshWater(player);
            if (reverse) {
                ItemStack dryHelmet = GrapnelProjectileProbe.fused(level, Items.DIAMOND_HELMET, pair);
                player.setItemSlot(EquipmentSlot.HEAD, dryHelmet); player.setAirSupply(150);
                require(!RangedBreathSkills.activate(player) && player.getAirSupply() == 150, "Dry activation cannot spend air");
            }
            for (BlockPos cell : saved.keySet()) level.setBlockAndUpdate(cell, Blocks.WATER.defaultBlockState());
            refreshWater(player); refreshWater(target); refreshWater(villager);
            require(player.isEyeInFluid(FluidTags.WATER) && target.isInWater(), "Fixture must be genuinely underwater");
            if (reverse) reverse(player, pair, area);
            else forward(player, pair, theme, target, villager, area, deny);
            for (BlockPos cell : saved.keySet()) require(level.getFluidState(cell).is(FluidTags.WATER), "Neither ability may replace water");
        } finally {
            deny.clear(); player.stopUsingItem(); RangedBreathSkills.clearPlayer(player.getUUID());
            ConfirmedArrow.clearPlayer(player.getUUID()); UnderwaterMiningSkills.onPlayerDisconnect(player);
            MeleeWorkSkills.clearPlayer(player); oxygen.replaceFrom(savedOxygen);
            target.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!existing.contains(arrow.getUUID())) arrow.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setItemSlot(EquipmentSlot.HEAD, helmet); player.setGameMode(mode); player.setPos(position);
            player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setAirSupply(air);
            refreshWater(player);
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, String theme, Zombie target, Villager villager,
                                AABB area, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
        bow.remove(DataComponents.ENCHANTMENTS); player.setItemSlot(EquipmentSlot.MAINHAND, bow); player.setAirSupply(100);
        Arrow synthetic = new Arrow(level, player, new ItemStack(Items.ARROW), bow); require(level.addFreshEntity(synthetic), "Synthetic control enters world");
        refreshWater(synthetic); GrapnelProjectileProbe.hit(synthetic, target, false);
        require(bubbles(player).isEmpty(), "An arrow not launched from the bow cannot create a bubble");
        deny.add(target.getUUID()); Arrow cancelled = fireBow(player, bow, area); refreshWater(cancelled);
        GrapnelProjectileProbe.hit(cancelled, target, true); deny.clear();
        require(bubbles(player).isEmpty(), "Cancelled damage cannot create a bubble");
        Arrow protectedShot = fireBow(player, bow, area); refreshWater(protectedShot); GrapnelProjectileProbe.hit(protectedShot, villager, false);
        require(bubbles(player).isEmpty(), "Protected villagers cannot be used to create a bubble");
        Arrow real = fireBow(player, bow, area); refreshWater(real); checkPrimary(level, player, real, target, theme);
        GrapnelProjectileProbe.hit(real, target, false); List<?> bubbles = bubbles(player);
        require(bubbles.size() == 1, "A real underwater hit must create one virtual bubble");
        Field position = bubbles.getFirst().getClass().getDeclaredField("position"); position.setAccessible(true);
        Vec3 location = (Vec3) position.get(bubbles.getFirst());
        player.setPos(location.x, location.y - .5D, location.z); refreshWater(player); player.setAirSupply(100);
        UnderwaterMiningSkills.tickServer(level.getServer());
        require(player.getAirSupply() > 100 && bubbles(player).isEmpty(), "Only actual contact restores air and consumes the owner's bubble");
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); Arrow bolt = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
        refreshWater(bolt); checkPrimary(level, player, bolt, target, theme); GrapnelProjectileProbe.hit(bolt, target, false);
        require(bubbles(player).size() == 1, "A genuine crossbow hit uses the same bounded bubble effect");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, AABB area) throws Exception {
        ServerLevel level = player.level(); ItemStack helmet = GrapnelProjectileProbe.fused(level, Items.DIAMOND_HELMET, pair);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        var holder = FusionStationOperations.lookupEnchantment(level.registryAccess(), RangedBreathSkills.RESPIRATION).orElseThrow();
        var enchants = helmet.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        detect(player); helmet.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        Identifier projected = EnchantmentFusion.id("primary_attribute/minecraft/respiration/head/0");
        require(player.getAttribute(Attributes.OXYGEN_BONUS).getModifier(projected) != null, "Missing Respiration primary must be projected");
        helmet.set(DataComponents.ENCHANTMENTS, enchants); detect(player); MeleeWorkSkills.tickServer(level.getServer());
        require(player.getAttribute(Attributes.OXYGEN_BONUS).getModifier(projected) == null, "Native Respiration cannot be doubled");
        require(holder.value().matchingSlot(EquipmentSlot.HEAD), "The datapack must permit the primary on a helmet");
        int cost = UnderwaterMiningSkills.airCost(player.getMaxAirSupply()), reserve = UnderwaterMiningSkills.airReserve(player.getMaxAirSupply());
        player.setAirSupply(cost + reserve - 1); require(!RangedBreathSkills.activate(player), "Insufficient air cannot arm an arrow");
        require(player.getAirSupply() == cost + reserve - 1, "A refused activation must not pay");
        player.setAirSupply(150); require(RangedBreathSkills.activate(player), "Underwater air payment must prepare the next arrow");
        require(player.getAirSupply() == 150 - cost && !RangedBreathSkills.activate(player) && player.getAirSupply() == 150 - cost,
            "Preparation pays once, not on repeat activation");
        ItemStack bow = new ItemStack(Items.BOW); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow absent = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        ConfirmedArrow.launched(absent, player, 0, bow); ConfirmedArrow.sample(absent);
        require(prepared(player), "A projectile absent from the world must not consume preparation"); absent.discard();
        int ammo = player.getOffhandItem().getCount(); Arrow real = fireBow(player, bow, area); ConfirmedArrow.sample(real); refreshWater(real);
        require(!prepared(player) && player.getOffhandItem().getCount() == ammo - 1, "One real arrow spends preparation and normal ammo");
        float inertia = RangedWaterDrag.inertia(real, .6F); require(inertia > .6F && inertia <= .96F, "Water drag must be reduced, not reversed");
        real.setNoGravity(true); real.setDeltaMovement(new Vec3(.25, 0, 0)); real.tick();
        require(real.isAlive() && real.getDeltaMovement().x > .25D * .6D, "Actual arrow tick must invoke the reduced water inertia");
        player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
        require(RangedWaterDrag.inertia(real, .6F) == .6F, "An equal copied helmet cannot inherit the flight");
        player.setItemSlot(EquipmentSlot.HEAD, helmet); real.discard();
        player.setAirSupply(150); require(RangedBreathSkills.activate(player), "The original helmet can prepare again");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        Arrow bolt = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); ConfirmedArrow.sample(bolt); refreshWater(bolt);
        require(!prepared(player) && RangedWaterDrag.inertia(bolt, .6F) > .6F, "A genuine crossbow arrow receives the shared effect");
    }
    private static List<?> bubbles(ServerPlayer owner) throws Exception {
        Field field = UnderwaterMiningSkills.class.getDeclaredField("BUBBLES"); field.setAccessible(true);
        var result = new java.util.ArrayList<>();
        for (Object bubble : (List<?>) field.get(null)) {
            Field id = bubble.getClass().getDeclaredField("owner"); id.setAccessible(true);
            if (owner.getUUID().equals(id.get(bubble))) result.add(bubble);
        }
        return result;
    }
    private static boolean prepared(ServerPlayer owner) throws Exception {
        Field field = RangedBreathSkills.class.getDeclaredField("PREPARED"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(owner.getUUID());
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow, AABB area) {
        ServerLevel level = player.level(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "A real bow must release");
        player.stopUsingItem(); var found = level.getEntitiesOfClass(Arrow.class, area,
            arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real bow arrow enters the world"); return found.getFirst();
    }
    private static void checkPrimary(ServerLevel level, ServerPlayer player, Arrow arrow, LivingEntity target, String theme) {
        if (RangedWearSkills.POWER.equals(theme)) require(EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), target,
            level.damageSources().arrow(arrow, player), 2.0F) > 2, "Missing Power must project its datapack effect");
        if (RangedWearSkills.FLAME.equals(theme)) require(arrow.isOnFire(), "Missing Flame must affect the real spawned arrow");
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
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
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS,
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.SHIELD)) {
                ItemStack gear = new ItemStack(item); inventory.setItem(FusionStationOperations.SLOT_TARGET, gear);
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean allowed = reverse ? item == Items.DIAMOND_HELMET : item == Items.BOW || item == Items.CROSSBOW;
                require(allowed ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use the ordered action carrier");
                if (!allowed) {
                    FusionStationOperations.applyFusedPairToGear(gear, pair, level.registryAccess());
                    require(!RangedBreathSkills.applies(pair, gear) && gear.getOrDefault(FusionDataComponents.FUSED_PAIRS,
                        FusedEnchantmentList.EMPTY).pairs().contains(pair), "Unsupported old gear must retain its data and old path");
                }
            }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
