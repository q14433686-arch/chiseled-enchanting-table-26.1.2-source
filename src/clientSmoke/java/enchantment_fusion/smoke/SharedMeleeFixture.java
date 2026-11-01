package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mixin.LivingEntityAttackChargeAccessor;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Shared disposable melee setup: real entities and hits, simulated player charge and position. */
final class SharedMeleeFixture implements AutoCloseable {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    final ServerPlayer player; final ServerLevel level;
    private final ItemStack[] previous = new ItemStack[SLOTS.length];
    private final Vec3 position, velocity; private final float yaw, pitch; private final boolean ground;
    private final double fall, attackSpeed; private final int attackCharge; private final GameType mode;
    private final List<LivingEntity> spawned = new ArrayList<>();
    SharedMeleeFixture(ServerPlayer player, BlockPos origin) {
        this.player = player; this.level = player.level();
        require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Disposable fixture space must be loaded");
        for (int i = 0; i < SLOTS.length; i++) previous[i] = player.getItemBySlot(SLOTS[i]);
        position = player.position(); velocity = player.getDeltaMovement(); yaw = player.getYRot(); pitch = player.getXRot();
        ground = player.onGround(); fall = player.fallDistance; mode = player.gameMode.getGameModeForPlayer();
        attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED).getBaseValue();
        attackCharge = ((LivingEntityAttackChargeAccessor) (Object) player).ef$getAttackStrengthTicker();
        player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
        for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
        player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(-90); player.setXRot(15); player.setOnGround(false); player.fallDistance = 0;
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4);
    }
    LivingEntity spawn(boolean spider, double dx, double dz) {
        LivingEntity target = spider ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        return spawn(target, dx, dz);
    }
    <T extends LivingEntity> T spawn(T target, double dx, double dz) {
        if (target instanceof Mob mob) mob.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
        target.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
        require(level.addFreshEntity(target), "The fixture target must enter the world"); spawned.add(target); return target;
    }
    ItemStack equip(Item item, FusedPair pair) {
        ItemStack weapon = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(weapon, pair, level.registryAccess());
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon); return weapon;
    }
    void hit(LivingEntity target, boolean full) {
        target.invulnerableTime = 0;
        ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(full ? 200 : 0);
        player.attack(target);
    }
    static void checkStation(ServerLevel level, FusedPair pair, List<Item> accepted, List<Item> denied) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : accepted) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                require(result.executable(), "Both station paths must accept the compatible carrier " + item);
            }
            for (Item item : denied) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                require(result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station paths must reject the incompatible carrier " + item);
            }
        }
    }
    static void checkMeleeStation(ServerLevel level, FusedPair pair) {
        checkStation(level, pair, List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE),
            List.of(Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW));
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void close() {
        for (LivingEntity target : spawned) target.discard();
        for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], previous[i]);
        ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(attackCharge);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(attackSpeed); player.setGameMode(mode);
        player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
        player.setOnGround(ground); player.fallDistance = fall;
    }
}
