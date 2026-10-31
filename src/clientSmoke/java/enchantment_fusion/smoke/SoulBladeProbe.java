package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.Collection;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Prepared real-attack fixture; position and attack charge are explicitly simulated. */
final class SoulBladeProbe {
    private static final FusedPair WEAK = FusedPair.clean("minecraft:sharpness", 5, "minecraft:smite", 5);
    private SoulBladeProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = reverse ? WEAK.flipped() : WEAK;
        checkStation(level, pair); SoulBladeSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Zombie first = zombie(level), second = zombie(level), behind = zombie(level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Soul blade fixture needs open disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            first.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            second.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            behind.setPos(origin.getX() + 5D, origin.getY(), origin.getZ() + 0.5D);
            villager.setNoAi(true); villager.setPos(origin.getX() + 4D, origin.getY(), origin.getZ() + 0.5D);
            require(level.addFreshEntity(first) && level.addFreshEntity(second) && level.addFreshEntity(behind) && level.addFreshEntity(villager), "Fixture entities must enter world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair), trident = fused(level, Items.TRIDENT, pair);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, first, level.damageSources().playerAttack(player), 1.0F);
            player.setItemSlot(EquipmentSlot.MAINHAND, trident);
            float replayDamage = EnchantmentHelper.modifyDamage(level, trident, first, level.damageSources().playerAttack(player), 1.0F);
            require(nativeDamage > 1 && Math.abs(nativeDamage - replayDamage) < 0.001F, "Non-native trident must replay the real primary once");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            if (!reverse) {
                second.discard(); behind.discard(); villager.discard();
                hit(player, first); require(size("WEAKPOINTS") == 1, "A full damaging undead hit creates one weakness");
                first.invulnerableTime = 0; float beforeSame = first.getHealth(); player.resetAttackStrengthTicker(); player.attack(first);
                float sameDamage = beforeSame - first.getHealth(); require(size("WEAKPOINTS") == 1, "Same-side strike must not consume the weakness");
                player.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D); player.setYRot(90);
                deny.add(first.getUUID()); hit(player, first); deny.clear();
                require(size("WEAKPOINTS") == 1, "Cancelled opposite hit must not consume the weakness");
                float beforeOpposite = first.getHealth(); hit(player, first); float oppositeDamage = beforeOpposite - first.getHealth();
                require(size("WEAKPOINTS") == 0 && oppositeDamage > sameDamage && first.hasEffect(MobEffects.SLOWNESS),
                    "Opposite-side full hit must consume, add damage and slow the undead");
            } else {
                first.setHealth(1); hit(player, first);
                require(!first.isAlive() && size("CHARGES") == 1, "A real undead kill must store soul charge");
                deny.add(second.getUUID()); hit(player, second); deny.clear();
                require(size("CHARGES") == 1, "Cancelled hit must not spend the charge");
                speed.setBaseValue(1); player.resetAttackStrengthTicker(); second.invulnerableTime = 0; player.attack(second);
                require(size("CHARGES") == 1 && behind.getHealth() == behind.getMaxHealth(), "An uncharged hit must not release the blade");
                speed.setBaseValue(1024); player.resetAttackStrengthTicker(); second.invulnerableTime = 0;
                float protectedHealth = villager.getHealth(); player.attack(second);
                require(size("CHARGES") == 0 && behind.getHealth() < behind.getMaxHealth() && villager.getHealth() == protectedHealth,
                    "A later full hit must release a piercing blade while protecting the villager");
                require(level.getBlockState(origin).isAir(), "Spirit blade must not place blocks");
            }
        } finally {
            deny.clear(); SoulBladeSkills.onDisconnect(player.getUUID()); first.discard(); second.discard(); behind.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground);
        }
    }
    private static void hit(ServerPlayer player, Zombie target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static Zombie zombie(ServerLevel level) {
        Zombie result = new Zombie(EntityType.ZOMBIE, level); result.setNoAi(true);
        result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); result.setHealth(200); return result;
    }
    private static int size(String name) throws Exception {
        Field field = SoulBladeSkills.class.getDeclaredField(name); field.setAccessible(true); Object state = field.get(null);
        return state instanceof Map<?, ?> map ? map.size() : ((Collection<?>) state).size();
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
                    "Both station paths must accept melee weapons and reject bows");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
