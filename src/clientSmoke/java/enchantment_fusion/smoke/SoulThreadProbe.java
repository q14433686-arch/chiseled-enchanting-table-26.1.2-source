package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.List;
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

/** Prepared fixture uses real Player.attack and resets invulnerability/attack speed, not actual input timing. */
final class SoulThreadProbe {
    private static final FusedPair WEB = FusedPair.clean("minecraft:smite", 5, "minecraft:bane_of_arthropods", 5);
    private SoulThreadProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level(); FusedPair pair = reverse ? WEB.flipped() : WEB;
        checkStation(level, pair);
        SoulThreadSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] oldGear = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) oldGear[i] = player.getItemBySlot(slots[i]);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot(); GameType oldMode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Zombie undead = new Zombie(EntityType.ZOMBIE, level), neighbor = new Zombie(EntityType.ZOMBIE, level);
        Spider spider = new Spider(EntityType.SPIDER, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Soul fixture needs disposable open space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            speed.setBaseValue(1024.0D); player.resetAttackStrengthTicker();
            undead.setNoAi(true); undead.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            neighbor.setNoAi(true); neighbor.setPos(origin.getX() + 3.2D, origin.getY(), origin.getZ() + 0.5D);
            villager.setNoAi(true); villager.setPos(origin.getX() + 3.2D, origin.getY(), origin.getZ() + 1.0D);
            spider.setNoAi(true); spider.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D); spider.setHealth(200.0F);
            require(level.addFreshEntity(neighbor) && level.addFreshEntity(villager), "Soul fixture observers must enter the world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            if (!reverse) {
                require(level.addFreshEntity(undead), "Undead fixture target must enter the world");
                undead.setHealth(1.0F); undead.invulnerableTime = 0; player.attack(undead);
                require(!undead.isAlive(), "A real direct strike must kill the undead target");
                SoulThreadSkills.tickServer(level.getServer());
                require(neighbor.hasEffect(MobEffects.SLOWNESS) && !villager.hasEffect(MobEffects.SLOWNESS),
                    "The virtual web must restrain an enemy but protect the villager");
                require(level.getBlockState(origin).isAir(), "A virtual web must not place cobweb blocks");
                ItemStack trident = fused(level, Items.TRIDENT, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, neighbor, level.damageSources().playerAttack(player), 1.0F);
                player.setItemSlot(EquipmentSlot.MAINHAND, trident);
                float tridentDamage = EnchantmentHelper.modifyDamage(level, trident, neighbor, level.damageSources().playerAttack(player), 1.0F);
                require(Math.abs(nativeDamage - tridentDamage) < 0.001F && nativeDamage > 1.0F,
                    "Non-native trident must replay the real Smite damage effect exactly once");
            } else {
                require(level.addFreshEntity(spider), "Arthropod fixture target must enter the world");
                hit(player, spider); // mark, zero pressure
                deny.add(spider.getUUID()); hit(player, spider); deny.clear(); // cancelled hit must not add pressure
                hit(player, spider); // one pressure
                require(!neighbor.hasEffect(MobEffects.SLOWNESS), "A cancelled hit must not secretly complete the cocoon");
                speed.setBaseValue(1.0D); player.resetAttackStrengthTicker();
                hit(player, spider); // reaches threshold but is not a fully charged hit
                require(!neighbor.hasEffect(MobEffects.SLOWNESS), "An uncharged hit must not release ready pressure");
                speed.setBaseValue(1024.0D); player.resetAttackStrengthTicker(); hit(player, spider);
                require(neighbor.hasEffect(MobEffects.SLOWNESS) && !villager.hasEffect(MobEffects.SLOWNESS),
                    "A later full hit must release restraint at enemies while protecting villagers");
                ItemStack trident = fused(level, Items.TRIDENT, pair); player.setItemSlot(EquipmentSlot.MAINHAND, trident);
                spider.removeEffect(MobEffects.SLOWNESS); hit(player, spider);
                require(spider.hasEffect(MobEffects.SLOWNESS), "Non-native trident must invoke Bane's real post-attack effect");
                // A charged finishing blow still releases at the death position rather than losing the cocoon.
                player.setItemSlot(EquipmentSlot.MAINHAND, sword); neighbor.removeEffect(MobEffects.SLOWNESS);
                hit(player, spider); hit(player, spider); spider.setHealth(1.0F); hit(player, spider);
                require(!spider.isAlive() && neighbor.hasEffect(MobEffects.SLOWNESS),
                    "A fully charged lethal hit must release an already built cocoon");
            }
        } finally {
            deny.clear(); SoulThreadSkills.onDisconnect(player.getUUID());
            undead.discard(); neighbor.discard(); spider.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], oldGear[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(oldMode); player.setPos(oldPos);
            player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static void hit(ServerPlayer player, Spider target) { target.invulnerableTime = 0; player.attack(target); }
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
