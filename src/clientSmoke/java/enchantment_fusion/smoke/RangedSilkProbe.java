package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.RangedSilkSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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

/** Candidate only: real shots and pickup, manually positioned targets, arrows, wall and inventory. */
final class RangedSilkProbe {
    private RangedSilkProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(theme, 3, RangedSilkSkills.SILK, 1);
        if (reverse) pair = pair.flipped(); GrapnelProjectileProbe.checkStation(level, pair, true, true);
        RangedSilkSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
        int size = player.getInventory().getContainerSize(); ItemStack[] inventory = new ItemStack[size];
        for (int i = 0; i < size; i++) inventory[i] = player.getInventory().getItem(i);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer(); AABB area = new AABB(origin).inflate(20);
        Set<UUID> oldArrows = GrapnelProjectileProbe.arrowIds(level, area); Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Zombie a = new Zombie(EntityType.ZOMBIE, level), b = new Zombie(EntityType.ZOMBIE, level);
        Villager villager = new Villager(EntityType.VILLAGER, level); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, damage) -> !deny.contains(target.getUUID()));
        try {
            for (int x = 0; x <= 7; x++) for (int y = 0; y <= 4; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = origin.offset(x, y, z); BlockState state = level.getBlockState(pos);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && state.isAir(), "Fixture needs loaded disposable air");
                blocks.put(pos, state);
            }
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
            player.setPos(origin.getX() + .5D, origin.getY() + 1, origin.getZ() + .5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            int lane = 0;
            for (var target : List.of(a, b, villager)) {
                target.setNoAi(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
                target.setPos(player.getX() + 5, player.getY(), player.getZ() + 3 * ++lane);
                require(level.hasChunkAt(target.blockPosition()) && level.addFreshEntity(target),
                    "Separate target lanes must enter loaded chunks");
            }
            if (reverse) recovery(player, pair, origin, area); else mark(player, pair, theme, area, a, b, villager, deny);
        } finally {
            deny.clear(); player.stopUsingItem(); RangedSilkSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
            a.discard(); b.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            for (var entry : blocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, inventory[i]);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(yaw); player.setXRot(pitch);
        }
    }
    private record Result(float damage, double push, int fire) {}
    private static Result result(Arrow arrow, Zombie target) {
        target.setDeltaMovement(Vec3.ZERO); target.clearFire(); float before = target.getHealth();
        arrow.setCritArrow(false); GrapnelProjectileProbe.hit(arrow, target, false);
        return new Result(before - target.getHealth(), target.getDeltaMovement().x, target.getRemainingFireTicks());
    }
    private static void mark(ServerPlayer player, FusedPair pair, String theme, AABB area, Zombie a, Zombie b,
                             Villager villager, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
        bow.remove(DataComponents.ENCHANTMENTS); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow forged = new Arrow(level, player, new ItemStack(Items.ARROW), bow); require(level.addFreshEntity(forged), "Control arrow enters world");
        GrapnelProjectileProbe.hit(forged, a, false); require(marked(player) == null, "An unlaunched arrow cannot mark");
        deny.add(a.getUUID()); GrapnelProjectileProbe.hit(fireBow(player, bow, area), a, true); deny.clear();
        require(marked(player) == null, "Cancelled damage cannot mark");
        GrapnelProjectileProbe.hit(fireBow(player, bow, area), villager, false);
        require(marked(player) == null, "Villagers cannot be targeted by the new effect");
        Result first = result(fireBow(player, bow, area), a);
        require(a.getUUID().equals(marked(player)), "The first confirmed eligible hit marks that target");
        Result follow = result(fireBow(player, bow, area), a);
        require(a.getUUID().equals(marked(player)), "A second real arrow retains the target mark");
        checkTheme(theme, first, follow);
        Result other = result(fireBow(player, bow, area), b);
        require(b.getUUID().equals(marked(player)), "A hit on another target replaces the mark only after that hit");
        if (RangedWearSkills.POWER.equals(theme)) require(Math.abs(other.damage() - first.damage()) < .01F, "Unmarked target receives no Power follow-up");
        if (RangedWearSkills.PUNCH.equals(theme)) require(Math.abs(other.push() - first.push()) < .01D, "Unmarked target receives no Punch follow-up");
        if (RangedWearSkills.FLAME.equals(theme)) require(other.fire() == first.fire(), "Unmarked target receives only native Flame");
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); RangedSilkSkills.clearPlayer(player.getUUID());
        Result crossFirst = result(GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area), a);
        Result crossFollow = result(GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area), a);
        checkTheme(theme, crossFirst, crossFollow);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow.copy()); RangedSilkSkills.tickServer(level.getServer());
        require(marked(player) == null, "An equal launcher copy cannot retain the old target lock");
    }
    private static void checkTheme(String theme, Result first, Result follow) {
        if (RangedWearSkills.POWER.equals(theme)) require(follow.damage() > first.damage(), "Only follow-up Power adds damage");
        if (RangedWearSkills.PUNCH.equals(theme)) require(first.push() > 0 && follow.push() > first.push(), "Only follow-up Punch adds real knockback");
        if (RangedWearSkills.FLAME.equals(theme)) require(first.fire() >= 100 && follow.fire() > first.fire(), "Only follow-up Flame extends vanilla burning");
    }
    private static void recovery(ServerPlayer player, FusedPair pair, BlockPos origin, AABB area) {
        ServerLevel level = player.level();
        for (int y = 1; y <= 3; y++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(origin.offset(6, y, z), Blocks.STONE.defaultBlockState());
        for (Item type : List.of(Items.BOW, Items.CROSSBOW)) {
            ItemStack launcher = GrapnelProjectileProbe.fused(level, type, pair); player.setItemSlot(EquipmentSlot.MAINHAND, launcher);
            ItemStack named = new ItemStack(Items.TIPPED_ARROW, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Original silk arrow"));
            ItemStack expected = named.copyWithCount(1); player.setItemSlot(EquipmentSlot.OFFHAND, named);
            RangedSilkSkills.clearPlayer(player.getUUID());
            Arrow forged = new Arrow(level, player, expected, launcher); require(level.addFreshEntity(forged), "Control arrow enters the world");
            ground(forged, player); require(!RangedSilkSkills.activate(player) && forged.isAlive(), "Unlaunched control arrows cannot be recovered");
            Arrow arrow = fire(player, launcher, area); ground(arrow, player); require(arrow.pickup == AbstractArrow.Pickup.ALLOWED, "Real consumed arrow must allow normal pickup");
            int before = player.getOffhandItem().getCount();
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            require(!RangedSilkSkills.activate(player) && arrow.isAlive(), "A disallowed original arrow cannot be converted to an item");
            arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            level.setBlockAndUpdate(origin.offset(3, 2, 0), Blocks.STONE.defaultBlockState());
            require(!RangedSilkSkills.activate(player) && arrow.isAlive(), "A wall cannot be bypassed or consume the arrow");
            level.setBlockAndUpdate(origin.offset(3, 2, 0), Blocks.AIR.defaultBlockState());
            require(RangedSilkSkills.activate(player) && arrow.isRemoved() && player.getOffhandItem().getCount() == before + 1
                && ItemStack.isSameItemSameComponents(expected, player.getOffhandItem()), "Normal pickup returns exactly the original named tipped arrow");
            require(!RangedSilkSkills.activate(player), "The same arrow cannot be collected twice");
            // Full inventory: even a legitimate arrow remains in the world, then normal pickup succeeds after space is freed.
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 8));
            arrow = fire(player, launcher, area); ground(arrow, player);
            for (int i = 0; i < 36; i++) if (i != player.getInventory().getSelectedSlot()) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE, 64));
            require(!RangedSilkSkills.activate(player) && arrow.isAlive(), "Full inventory must not discard the arrow");
            int free = player.getInventory().getSelectedSlot() == 1 ? 2 : 1; player.getInventory().setItem(free, ItemStack.EMPTY);
            require(RangedSilkSkills.activate(player) && arrow.isRemoved() && player.getInventory().getItem(free).is(Items.ARROW),
                "Freeing a slot allows the original arrow's normal pickup");
            for (int i = 0; i < 36; i++) if (i != player.getInventory().getSelectedSlot()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        // Infinity: newest free arrow must prevent falling back to the earlier real arrow.
        ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 8)); Arrow paid = fireBow(player, bow, area); ground(paid, player);
        var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("minecraft:infinity")));
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(bow.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchants.set(holder, 1); EnchantmentHelper.setEnchantments(bow, enchants.toImmutable());
        int count = player.getOffhandItem().getCount(); Arrow infinite = fireBow(player, bow, area); ground(infinite, player);
        require(infinite.pickup != AbstractArrow.Pickup.ALLOWED && player.getOffhandItem().getCount() == count,
            "A genuine Infinity shot is free but not survival-pickable");
        require(!RangedSilkSkills.activate(player) && infinite.isAlive() && paid.isAlive(), "Never recover an Infinity arrow or fall back to an older arrow");
    }
    private static Arrow fire(ServerPlayer player, ItemStack launcher, AABB area) {
        return launcher.is(Items.BOW) ? fireBow(player, launcher, area) : GrapnelProjectileProbe.fireCrossbow(player.level(), player, launcher, area);
    }
    private static Arrow fireBow(ServerPlayer player, ItemStack bow, AABB area) {
        ServerLevel level = player.level(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release"); player.stopUsingItem();
        var shots = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(shots.size() == 1, "Exactly one real bow arrow must enter the world"); return shots.getFirst();
    }
    private static void ground(Arrow arrow, ServerPlayer player) {
        arrow.setPos(player.getX() + 2, player.getEyeY(), player.getZ()); arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0);
        for (int i = 0; i < 16 && arrow.isAlive() && (!((AbstractArrowAccessor) arrow).ef$isInGround() || arrow.shakeTime > 0); i++) arrow.tick();
        require(arrow.isAlive() && ((AbstractArrowAccessor) arrow).ef$isInGround() && arrow.shakeTime <= 0, "The actual arrow must stick in the fixture wall and settle");
    }
    private static UUID marked(ServerPlayer player) throws Exception {
        Field field = RangedSilkSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object value = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (!(value instanceof List<?> marks) || marks.isEmpty()) return null;
        Method target = marks.getFirst().getClass().getDeclaredMethod("target"); target.setAccessible(true); return (UUID) target.invoke(marks.getFirst());
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
