package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real attack calls with prepared positions and forced mark age; not natural input or tick timing. */
final class SegmentThreadProbe {
    private static final FusedPair TRIP = FusedPair.clean("minecraft:sharpness", 5, "minecraft:bane_of_arthropods", 5);
    private SegmentThreadProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = reverse ? TRIP.flipped() : TRIP;
        checkStation(level, pair); SegmentThreadSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(); double fall = player.fallDistance;
        GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Spider spider = new Spider(EntityType.SPIDER, level); Zombie enemy = new Zombie(EntityType.ZOMBIE, level);
        Villager villager = new Villager(EntityType.VILLAGER, level); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Fixture needs open disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0;
            speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            spider.setNoAi(true); spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); spider.setHealth(200);
            enemy.setNoAi(true); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200); villager.setNoAi(true);
            spider.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 1.5D);
            enemy.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 1.5D);
            villager.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 1.5D);
            require(level.addFreshEntity(spider) && level.addFreshEntity(enemy) && level.addFreshEntity(villager), "Fixture targets must enter world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair), trident = fused(level, Items.TRIDENT, pair);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, spider, level.damageSources().playerAttack(player), 1.0F);
            player.setItemSlot(EquipmentSlot.MAINHAND, trident);
            float replayDamage = EnchantmentHelper.modifyDamage(level, trident, spider, level.damageSources().playerAttack(player), 1.0F);
            require(nativeDamage > 1 && Math.abs(nativeDamage - replayDamage) < 0.001F, "Non-native trident replays its primary once");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            deny.add(spider.getUUID()); hit(player, spider); deny.clear();
            require(size(reverse ? "THREADS" : "MARKS") == 0, "Cancelled damage cannot create state");
            float beforeFirst = spider.getHealth(); hit(player, spider); float firstDamage = beforeFirst - spider.getHealth();
            if (!reverse) {
                require(size("MARKS") == 1, "Full damaging hit must mark the arthropod");
                forceMarkAge(3); // The probe does not advance actual server time.
                spider.setDeltaMovement(0.4D, 0.25D, 0.2D); spider.setPos(spider.getX() + 0.2D, spider.getY(), spider.getZ());
                SegmentThreadSkills.tickServer(level.getServer());
                require(spider.hasEffect(MobEffects.SLOWNESS) && spider.getDeltaMovement().horizontalDistanceSqr() == 0,
                    "Moving arthropod must trip and slow");
                deny.add(spider.getUUID()); hit(player, spider); deny.clear();
                require(size("MARKS") == 1, "Cancelled follow-up cannot consume the opening");
                float beforeSecond = spider.getHealth(); hit(player, spider);
                require(size("MARKS") == 0 && beforeSecond - spider.getHealth() > firstDamage,
                    "A later full damaging hit consumes the opening for bonus damage");
            } else {
                require(size("THREADS") == 1, "A damaging arthropod hit must create a thread");
                float enemyHealth = enemy.getHealth(), protectedHealth = villager.getHealth();
                deny.add(enemy.getUUID()); player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D);
                SegmentThreadSkills.tickServer(level.getServer()); deny.clear();
                require(enemy.getHealth() == enemyHealth && villager.getHealth() == protectedHealth,
                    "Cancelled thread damage and protected villagers must remain unharmed");
                player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
                SegmentThreadSkills.tickServer(level.getServer());
                require(enemy.getHealth() < enemyHealth && villager.getHealth() == protectedHealth,
                    "Circling sweeps the thread over an enemy, not the villager");
                enemyHealth = enemy.getHealth(); player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D);
                SegmentThreadSkills.tickServer(level.getServer());
                require(enemy.getHealth() == enemyHealth, "A single thread cannot repeatedly hurt the same target");
            }
            require(level.getBlockState(origin).isAir(), "The skills must not create terrain");
        } finally {
            deny.clear(); SegmentThreadSkills.onDisconnect(player.getUUID()); spider.discard(); enemy.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static void hit(ServerPlayer player, Spider target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static int size(String name) throws Exception { return state(name).size(); }
    private static Map<?, ?> state(String name) throws Exception {
        Field field = SegmentThreadSkills.class.getDeclaredField(name); field.setAccessible(true); return (Map<?, ?>) field.get(null);
    }
    private static void forceMarkAge(int ticks) throws Exception {
        Object mark = state("MARKS").values().iterator().next(); Field created = mark.getClass().getDeclaredField("created");
        created.setAccessible(true); created.setLong(mark, created.getLong(mark) - ticks);
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
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.TRIDENT, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                require(item == Items.BOW ? preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST : preview.executable(),
                    "Both station paths accept melee weapons and reject bows");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
