package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.ExperienceOrbCountAccess;
import enchantment_fusion.mechanics.signatures.RangedMendingCapture;
import enchantment_fusion.mechanics.signatures.RangedMendingMath;
import enchantment_fusion.mechanics.signatures.RangedMendingSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Six candidates using actual server arrows and XP orbs; aim, flight and pickups are prepared. */
final class RangedMendingProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private RangedMendingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedMendingSkills.MENDING, 1);
        if (reverse) pair = pair.flipped();
        GrapnelProjectileProbe.checkStation(level, pair, true, true);
        ItemStack noDurability = new ItemStack(Items.BOW); noDurability.remove(DataComponents.MAX_DAMAGE);
        require(DirectionalHostRules.validateNewOrChanged(noDurability, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
            "Even a launcher must actually have durability");
        RangedMendingSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); GameType mode = player.gameMode.getGameModeForPlayer();
        int xpLevel = player.experienceLevel, xpTotal = player.totalExperience, xpDelay = player.takeXpDelay; float xpProgress = player.experienceProgress;
        AABB area = new AABB(origin).inflate(20); Set<UUID> oldArrows = GrapnelProjectileProbe.arrowIds(level, area);
        Set<UUID> oldOrbs = ids(level.getEntitiesOfClass(ExperienceOrb.class, area));
        Set<UUID> oldItems = ids(level.getEntitiesOfClass(ItemEntity.class, area));
        List<LivingEntity> targets = new ArrayList<>(); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture requires loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            if (reverse) reverse(player, pair, theme, area, targets, deny);
            else forward(player, pair, theme, area, targets, deny, oldOrbs);
        } finally {
            deny.clear(); player.stopUsingItem(); RangedMendingSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
            for (LivingEntity target : targets) target.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!oldOrbs.contains(orb.getUUID())) orb.discard();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!oldItems.contains(item.getUUID())) item.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
            player.experienceLevel = xpLevel; player.totalExperience = xpTotal; player.experienceProgress = xpProgress; player.takeXpDelay = xpDelay;
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, String theme, AABB area, List<LivingEntity> targets,
                                Set<UUID> deny, Set<UUID> oldOrbs) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
        bow.remove(DataComponents.ENCHANTMENTS); bow.setDamageValue(40); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Zombie target = zombie(player, targets, 1); Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        require(level.addFreshEntity(fake), "Control arrow enters world"); GrapnelProjectileProbe.hit(fake, target, false);
        require(routes().isEmpty(), "A fabricated unlaunched arrow cannot claim kill XP"); clearNewOrbs(level, area, oldOrbs);
        target = zombie(player, targets, 1); deny.add(target.getUUID());
        GrapnelProjectileProbe.hit(fireBow(player, bow, area), target, true); deny.clear();
        require(routes().isEmpty(), "Cancelled damage cannot claim kill XP"); target.discard();
        target = zombie(player, targets, 1); Arrow real = fireBow(player, bow, area); GrapnelProjectileProbe.hit(real, target, false);
        require(target.isDeadOrDying(), "A real center arrow must actually kill the target");
        ExperienceOrb orb = soleRoute(player); int value = orb.getValue();
        require(value > 0 && ((ExperienceOrbCountAccess) (Object) orb).ef$pickupCount() == 1, "Only a fresh single-count orb is routed");
        Vec3 from = orb.position(); RangedMendingSkills.tickServer(level.getServer());
        require(orb.position().equals(from) && orb.getDeltaMovement().lengthSqr() > 0 && orb.getDeltaMovement().length() <= .55D + 1e-6,
            "Routing accelerates the same orb without teleporting it");
        bow.setDamageValue(4); int beforeXp = player.totalExperience; player.takeXpDelay = 0; orb.playerTouch(player);
        require(bow.getDamageValue() == 0 && player.totalExperience - beforeXp == RangedMendingMath.remainingXp(value, value * 2, 4),
            "The owner picks up the real orb: exact launcher repairs first and only the integer remainder reaches XP");
        require(!routes().containsKey(orb), "Pickup consumes the route only once"); clearNewOrbs(level, area, oldOrbs);
        // Merge through the vanilla method rather than pretending a nearby orb is this kill's XP.
        target = zombie(player, targets, 1); GrapnelProjectileProbe.hit(fireBow(player, bow, area), target, false);
        orb = soleRoute(player); ExperienceOrb other = new ExperienceOrb(level, orb.getX(), orb.getY(), orb.getZ(), orb.getValue());
        require(level.addFreshEntity(other), "Second actual orb enters world");
        Method merge = ExperienceOrb.class.getDeclaredMethod("merge", ExperienceOrb.class); merge.setAccessible(true); merge.invoke(orb, other);
        require(((ExperienceOrbCountAccess) (Object) orb).ef$pickupCount() == 2, "Vanilla merge increases the pickup count");
        RangedMendingSkills.tickServer(level.getServer()); require(!routes().containsKey(orb), "A merged orb must lose special treatment");
        bow.setDamageValue(10); beforeXp = player.totalExperience; player.takeXpDelay = 0; orb.playerTouch(player);
        require(bow.getDamageValue() == 10 && player.totalExperience - beforeXp == orb.getValue(),
            "A merged orb uses ordinary pickup and cannot repair this non-native secondary");
        clearNewOrbs(level, area, oldOrbs);
        // An equal-looking copy is not the actual firing weapon.
        target = zombie(player, targets, 1); GrapnelProjectileProbe.hit(fireBow(player, bow, area), target, false);
        orb = soleRoute(player); ItemStack copy = bow.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy);
        RangedMendingSkills.tickServer(level.getServer()); require(!routes().containsKey(orb), "An equal copy cannot inherit the route");
        clearNewOrbs(level, area, oldOrbs);
        // Prepare a wall after the kill, then simulate the orb reaching the impact node.
        player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        target = zombie(player, targets, 1); GrapnelProjectileProbe.hit(fireBow(player, bow, area), target, false);
        orb = soleRoute(player); Object route = routes().get(orb); Field pathField = route.getClass().getDeclaredField("path"); pathField.setAccessible(true);
        @SuppressWarnings("unchecked") List<Vec3> path = (List<Vec3>) pathField.get(route);
        require(path.size() >= 2, "The source must retain a real multi-node arrow path");
        Vec3 end = path.getLast(), previous = path.get(path.size() - 2); BlockPos wall = BlockPos.containing(end.add(previous).scale(.5D));
        require(level.hasChunkAt(wall) && level.getBlockState(wall).isAir(), "Wall fixture requires loaded open air");
        BlockState oldWall = level.getBlockState(wall);
        try {
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState()); orb.setPos(end);
            RangedMendingSkills.tickServer(level.getServer());
            require(!routes().containsKey(orb) && level.getBlockState(wall).is(Blocks.STONE),
                "A new wall ends the route without being changed or bypassed");
        } finally { level.setBlockAndUpdate(wall, oldWall); }
        clearNewOrbs(level, area, oldOrbs);
        Cow cow = new FixedCow(level); cow.setNoAi(true); cow.setHealth(1);
        cow.setPos(player.getX() + 4, player.getY(), player.getZ()); require(level.addFreshEntity(cow), "Passive target enters world"); targets.add(cow);
        GrapnelProjectileProbe.hit(fireBow(player, bow, area), cow, false);
        require(soleRoute(player).isAlive(), "A legally killed ordinary passive mob's own XP can return");
        clearNewOrbs(level, area, oldOrbs); RangedMendingSkills.tickServer(level.getServer());
        Villager villager = new Villager(EntityType.VILLAGER, level); villager.setNoAi(true); setReward(villager, 3); villager.setHealth(1);
        villager.setPos(player.getX() + 4, player.getY(), player.getZ()); require(level.addFreshEntity(villager), "Protected target enters world"); targets.add(villager);
        GrapnelProjectileProbe.hit(fireBow(player, bow, area), villager, false);
        require(routes().isEmpty(), "A protected villager cannot be taken as a fusion XP source");
        clearNewOrbs(level, area, oldOrbs);
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        crossbow.setDamageValue(40); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        target = zombie(player, targets, 1); Arrow cross = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
        GrapnelProjectileProbe.hit(cross, target, false); require(soleRoute(player).isAlive(), "A real crossbow kill also routes its own fresh XP");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, String theme, AABB area, List<LivingEntity> targets,
                                Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
        bow.remove(DataComponents.ENCHANTMENTS); bow.setDamageValue(40); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Zombie target = zombie(player, targets, 200);
        require(!RangedMendingSkills.activate(player), "No repair cannot prepare a seal");
        bow.setDamageValue(39); require(state(player, bow, "forge") == 0, "A direct damage edit cannot earn a seal");
        Arrow baseline = fireBow(player, bow, area); baseline.setCritArrow(false); float before = target.getHealth();
        target.setDeltaMovement(Vec3.ZERO); GrapnelProjectileProbe.hit(baseline, target, false); float normal = before - target.getHealth();
        target.setHealth(200); target.clearFire(); target.setDeltaMovement(Vec3.ZERO);
        int beforeDamage = bow.getDamageValue(), beforeXp = player.totalExperience; touchOrb(player, 4);
        require(bow.getDamageValue() < beforeDamage && state(player, bow, "forge") > 0 && player.totalExperience == beforeXp,
            "Actual non-native Mending uses the same orb XP and yields attributable repair credit");
        require(RangedMendingSkills.activate(player) && state(player, bow, "armed") > 0, "Actual repair prepares one arrow");
        Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        require(level.addFreshEntity(fake), "Unlaunched control enters world"); int armed = state(player, bow, "armed");
        GrapnelProjectileProbe.hit(fake, target, false); target.setHealth(200); target.clearFire(); target.setDeltaMovement(Vec3.ZERO);
        require(state(player, bow, "armed") == armed && !shots().containsKey(fake), "A fabricated arrow cannot consume a seal");
        Arrow shot = fireBow(player, bow, area); shot.setCritArrow(false); ConfirmedArrow.sample(shot);
        require(state(player, bow, "armed") == 0 && shots().containsKey(shot), "The next actual in-world center arrow consumes exactly one seal");
        before = target.getHealth(); GrapnelProjectileProbe.hit(shot, target, false);
        if (RangedWearSkills.POWER.equals(theme)) require(before - target.getHealth() > normal, "Power seal augments the one real damage call");
        if (RangedWearSkills.PUNCH.equals(theme)) require(target.getDeltaMovement().x > 0, "Punch seal adds actual knockback after damage");
        if (RangedWearSkills.FLAME.equals(theme)) require(target.isOnFire(), "Flame seal ignites only after actual accepted damage");
        target.setHealth(200); target.clearFire(); target.setDeltaMovement(Vec3.ZERO);
        bow.setDamageValue(40); touchOrb(player, 4); require(RangedMendingSkills.activate(player), "Another repair prepares cancellation control");
        deny.add(target.getUUID()); Arrow cancelled = fireBow(player, bow, area); ConfirmedArrow.sample(cancelled);
        GrapnelProjectileProbe.hit(cancelled, target, true); deny.clear();
        require(!target.isOnFire() && target.getDeltaMovement().lengthSqr() < 1.0E-8D,
            "Cancelled damage cannot apply the added fire or knockback");
        // Replacing the physical source after preparation cannot transfer it to a copy.
        bow.setDamageValue(40); touchOrb(player, 4); require(RangedMendingSkills.activate(player), "Another repair prepares identity control");
        ItemStack copy = bow.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy); RangedMendingSkills.tickServer(level.getServer());
        Arrow wrong = fireBow(player, copy, area); ConfirmedArrow.sample(wrong);
        require(!shots().containsKey(wrong), "A copied weapon cannot inherit prepared credit");
        RangedMendingSkills.clearPlayer(player.getUUID());
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.setDamageValue(40);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        var mending = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(RangedMendingSkills.MENDING)));
        require(crossbow.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(mending) > 0,
            "Native Mending control must be present on the crossbow");
        touchOrb(player, 4); require(state(player, crossbow, "forge") > 0 && RangedMendingSkills.activate(player),
            "Native Mending must still earn one attributable repair credit");
        Arrow cross = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); ConfirmedArrow.sample(cross);
        require(shots().containsKey(cross) && state(player, crossbow, "armed") == 0, "A genuine crossbow center arrow receives the seal");
    }
    private static Zombie zombie(ServerPlayer player, List<LivingEntity> targets, float health) throws Exception {
        Zombie target = new Zombie(EntityType.ZOMBIE, player.level()); target.setNoAi(true); setReward(target, 3);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(health);
        target.setPos(player.getX() + 4, player.getY(), player.getZ());
        require(player.level().addFreshEntity(target), "Target must enter world"); targets.add(target); return target;
    }
    private static final class FixedCow extends Cow {
        FixedCow(ServerLevel level) { super(EntityType.COW, level); }
        @Override protected int getBaseExperienceReward(ServerLevel level) { return 3; }
    }
    private static void setReward(Mob mob, int amount) throws Exception {
        // A single vanilla 3-XP orb makes this fixture deterministic; splitting/merging is tested separately.
        Field reward = Mob.class.getDeclaredField("xpReward"); reward.setAccessible(true); reward.setInt(mob, amount);
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow, AABB area) {
        ServerLevel level = player.level(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
        player.stopUsingItem(); var found = level.getEntitiesOfClass(Arrow.class, area,
            arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real arrow must enter the world"); return found.getFirst();
    }
    private static void touchOrb(ServerPlayer player, int amount) {
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), amount);
        try { require(player.level().addFreshEntity(orb), "Real orb must enter the world"); player.takeXpDelay = 0; orb.playerTouch(player); }
        finally { orb.discard(); }
    }
    private static Set<UUID> ids(List<? extends net.minecraft.world.entity.Entity> entities) {
        Set<UUID> result = new HashSet<>(); for (var entity : entities) result.add(entity.getUUID()); return result;
    }
    private static void clearNewOrbs(ServerLevel level, AABB area, Set<UUID> old) {
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!old.contains(orb.getUUID())) orb.discard();
    }
    @SuppressWarnings("unchecked") private static Map<ExperienceOrb, Object> routes() throws Exception {
        Field field = RangedMendingCapture.class.getDeclaredField("ROUTES"); field.setAccessible(true); return (Map<ExperienceOrb, Object>) field.get(null);
    }
    @SuppressWarnings("unchecked") private static Map<AbstractArrow, Object> shots() throws Exception {
        Field field = RangedMendingSkills.class.getDeclaredField("SHOTS"); field.setAccessible(true); return (Map<AbstractArrow, Object>) field.get(null);
    }
    private static ExperienceOrb soleRoute(ServerPlayer player) throws Exception {
        var owned = routes().entrySet().stream().filter(entry -> {
            try { Field field = entry.getValue().getClass().getDeclaredField("owner"); field.setAccessible(true); return player.getUUID().equals(field.get(entry.getValue())); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        }).map(Map.Entry::getKey).toList();
        require(owned.size() == 1, "One unmerged fresh orb from the exact death must be routed"); return owned.getFirst();
    }
    private static int state(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field field = RangedMendingSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        Object raw = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (!(raw instanceof List<?> states)) return 0;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object entry = gear.get(state);
            Method getStack = entry.getClass().getDeclaredMethod("stack"); getStack.setAccessible(true);
            if (getStack.invoke(entry) == stack) { Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.getInt(state); }
        }
        return 0;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
