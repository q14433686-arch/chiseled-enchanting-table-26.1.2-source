package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.RangedWearMath;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Six recorded fixtures; real releases/rolls, but movement, timing and activation are simulated. */
final class RangedWearProbe {
    private RangedWearProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level();
        FusedPair forward = FusedPair.clean(theme, 2, RangedWearSkills.UNBREAKING, 3);
        FusedPair pair = reverse ? forward.flipped() : forward;
        GrapnelProjectileProbe.checkStation(level, pair, true, true);
        checkNoDurability(level, pair);
        RangedWearSkills.clearPlayer(player.getUUID()); ConfirmedArrow.clearPlayer(player.getUUID());
        EchoPathSkills.onDisconnect(player.getUUID());
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20.0D); Set<UUID> existing = GrapnelProjectileProbe.arrowIds(level, area);
        Zombie target = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            for (LivingEntity entity : List.of(target, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); entity.setHealth(1000);
            }
            target.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            villager.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 3.5D);
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Fixture entities must enter the world");
            ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
            bow.remove(DataComponents.ENCHANTMENTS); // Exercise the missing-primary data-pack projection.
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
            if (reverse) exerciseEcho(player, target, villager, bow, pair, area, origin);
            else exerciseGuard(player, target, villager, bow, pair, theme, area, deny);
            // Both launchers are real supported carriers, not just station previews.
            ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair);
            crossbow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
            Arrow arrow = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            if (!reverse) checkPrimary(level, player, arrow, target, theme);
            GrapnelProjectileProbe.hit(arrow, target, false);
            if (reverse) {
                require(!ConfirmedArrow.latestPath(player, crossbow, pair).isEmpty(), "A real crossbow must record its own path");
                fund(player, crossbow); require(value(player, crossbow, "charges") > 0, "Crossbow actual wear must fund an echo");
                require(RangedWearSkills.activate(player, false), "Crossbow path and actual saving must release an echo");
            } else require(value(player, crossbow, "streak") == 1, "A real crossbow hit must count for its own launcher");
        } finally {
            deny.clear(); player.stopUsingItem(); RangedWearSkills.clearPlayer(player.getUUID());
            ConfirmedArrow.clearPlayer(player.getUUID()); EchoPathSkills.onDisconnect(player.getUUID());
            target.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area))
                if (!existing.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain); player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity);
            player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static void exerciseGuard(ServerPlayer player, Zombie target, Villager villager, ItemStack bow,
                                      FusedPair pair, String theme, AABB area, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level();
        require(!RangedWearSkills.activate(player, true), "No real hit cannot fund guard");
        Arrow synthetic = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        require(level.addFreshEntity(synthetic), "Synthetic control must enter world");
        GrapnelProjectileProbe.hit(synthetic, target, false);
        require(value(player, bow, "streak") == 0, "A synthetic arrow cannot earn a real-shot streak");
        deny.add(target.getUUID()); GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), target, true); deny.clear();
        require(value(player, bow, "streak") == 0, "Cancelled damage cannot earn a streak");
        for (int i = 0; i < 2; i++) {
            Arrow arrow = fireBow(level, player, bow, area); checkPrimary(level, player, arrow, target, theme);
            target.setDeltaMovement(Vec3.ZERO);
            GrapnelProjectileProbe.hit(arrow, target, false);
            if (RangedWearSkills.PUNCH.equals(theme))
                require(target.getDeltaMovement().x > 0, "Missing Punch must apply real data-pack knockback once");
        }
        require(value(player, bow, "streak") == 2 && !RangedWearSkills.activate(player, true), "Two hits are insufficient");
        GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), villager, false);
        require(value(player, bow, "streak") == 0, "A protected-target shot must interrupt the streak");
        for (int i = 0; i < 3; i++) GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), target, false);
        require(value(player, bow, "streak") == 3 && RangedWearSkills.activate(player, true), "Three real hits must arm guard");
        int guard = value(player, bow, "guard"), damage = bow.getDamageValue();
        require(guard == RangedWearMath.guard(3, pair.secondaryLevel()), "Guard must derive from this streak and rank");
        player.setGameMode(GameType.CREATIVE); bow.hurtAndBreak(2, player, EquipmentSlot.MAINHAND); player.setGameMode(GameType.SURVIVAL);
        require(value(player, bow, "guard") == guard && bow.getDamageValue() == damage, "Creative non-wear cannot spend guard");
        bow.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
        require(value(player, bow, "guard") == guard - 2 && bow.getDamageValue() == damage, "Real wear must spend matching guard");
        bow.hurtAndBreak(guard, player, EquipmentSlot.MAINHAND);
        require(value(player, bow, "guard") == 0 && bow.getDamageValue() == damage + 2, "Excess wear remains normal");
        for (int i = 0; i < 3; i++) GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), target, false);
        require(RangedWearSkills.activate(player, true), "A second streak can prepare another guard");
        ItemStack copy = bow.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy);
        RangedWearSkills.tickServer(level.getServer());
        require(value(player, copy, "guard") == 0 && !RangedWearSkills.activate(player, true), "An equal copy cannot inherit the guard");
    }
    private static void exerciseEcho(ServerPlayer player, Zombie target, Villager villager, ItemStack bow,
                                     FusedPair pair, AABB area, BlockPos origin) throws Exception {
        ServerLevel level = player.level();
        require(!RangedWearSkills.activate(player, false), "No actual savings cannot fund an echo");
        ItemStack copy = bow.copy();
        for (int i = 0; i < 12; i++) copy.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        require(value(player, copy, "charges") == 0, "An unequipped copy cannot earn charges");
        fund(player, bow);
        int charges = value(player, bow, "charges");
        require(charges > 0 && !RangedWearSkills.activate(player, false), "Real savings without a real arrow path cannot release");
        player.setGameMode(GameType.CREATIVE); bow.hurtAndBreak(1, player, EquipmentSlot.MAINHAND); player.setGameMode(GameType.SURVIVAL);
        require(value(player, bow, "charges") == charges, "Creative non-wear cannot earn charges");
        Arrow arrow = fireBow(level, player, bow, area);
        GrapnelProjectileProbe.hit(arrow, target, false);
        require(!ConfirmedArrow.latestPath(player, bow, pair).isEmpty(), "Real launched arrow path must be recorded");
        fund(player, bow);
        villager.setPos(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
        target.invulnerableTime = 0; float targetHealth = target.getHealth(), protectedHealth = villager.getHealth();
        int ammo = player.getOffhandItem().getCount(); Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        charges = value(player, bow, "charges");
        require(RangedWearSkills.activate(player, false) && value(player, bow, "charges") == charges - 1, "One real saving funds one echo");
        for (int i = 0; i < 8; i++) EchoPathSkills.tickServer(level.getServer());
        require(target.getHealth() == targetHealth && villager.getHealth() == protectedHealth,
            "A protected villager must stop the echo without damage to itself or a target behind");
        require(player.getOffhandItem().getCount() == ammo && GrapnelProjectileProbe.arrowIds(level, area).equals(before),
            "A virtual echo cannot create or consume ammunition");
        villager.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 3.5D);
        target.invulnerableTime = 0;
        Arrow newer = fireBow(level, player, bow, area);
        require(ConfirmedArrow.latestPath(player, bow, pair).isEmpty(),
            "A newer inserted arrow must prevent replaying the old path before the new path is sampled");
        GrapnelProjectileProbe.hit(newer, target, false); fund(player, bow);
        target.invulnerableTime = 0; target.clearFire(); target.setDeltaMovement(Vec3.ZERO); targetHealth = target.getHealth();
        require(RangedWearSkills.activate(player, false), "Another real saving and path can release an echo");
        for (int i = 0; i < 8; i++) EchoPathSkills.tickServer(level.getServer());
        require(target.getHealth() < targetHealth, "Virtual echo must damage an eligible target on the actual path");
        if (RangedWearSkills.FLAME.equals(pair.secondaryId())) require(target.isOnFire(), "Flame echo must ignite only the eligible target");
        if (RangedWearSkills.PUNCH.equals(pair.secondaryId())) require(target.getDeltaMovement().x > 0, "Punch echo must use real knockback");
        ItemStack replacement = bow.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, replacement);
        ConfirmedArrow.tickServer(level.getServer()); RangedWearSkills.tickServer(level.getServer());
        require(ConfirmedArrow.latestPath(player, replacement, pair).isEmpty() && value(player, replacement, "charges") == 0,
            "A copied launcher cannot inherit a path or savings");
    }
    private static void checkPrimary(ServerLevel level, ServerPlayer player, Arrow arrow, LivingEntity target, String theme) {
        if (RangedWearSkills.POWER.equals(theme)) {
            float value = EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), target, level.damageSources().arrow(arrow, player), 2.0F);
            require(value > 2.0F, "Missing Power must replay the current data-pack effect");
        } else if (RangedWearSkills.FLAME.equals(theme)) require(arrow.isOnFire(), "Missing Flame must ignite the real arrow");
    }
    private static void fund(ServerPlayer player, ItemStack stack) throws Exception {
        for (int i = 0; i < 100 && value(player, stack, "charges") < 1; i++) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        require(value(player, stack, "charges") > 0, "Real Unbreaking roll must save at least one durability point");
    }
    private static Arrow fireBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area) {
        Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
        player.stopUsingItem();
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real center arrow must launch"); return found.getFirst();
    }
    private static int value(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field field = RangedWearSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        Object raw = ((Map<?, ?>) field.get(null)).get(player.getUUID());
        if (!(raw instanceof List<?> states)) return 0;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object entry = gear.get(state);
            var method = entry.getClass().getDeclaredMethod("stack"); method.setAccessible(true);
            if (method.invoke(entry) != stack) continue;
            Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.getInt(state);
        }
        return 0;
    }
    private static void checkNoDurability(ServerLevel level, FusedPair pair) {
        ItemStack bow = new ItemStack(Items.BOW); bow.remove(DataComponents.MAX_DAMAGE);
        SimpleContainer inv = new SimpleContainer(4);
        inv.setItem(FusionStationOperations.SLOT_PRIMARY, enchantment_fusion.item.FusedEnchantedBookItem.createForPair(pair));
        inv.setItem(FusionStationOperations.SLOT_TARGET, bow);
        require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1).rejectReason()
            == RejectReason.UNSUPPORTED_DIRECTION_HOST, "A launcher without real durability must not accept the new direction");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
