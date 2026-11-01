package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual attacks, value effects and impulse, with deliberately simulated fall and timing. */
final class GrapnelImpactProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private GrapnelImpactProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String primary) throws Exception {
        boolean knockback = GrapnelImpact.KNOCKBACK.equals(primary), breach = GrapnelImpact.BREACH.equals(primary);
        FusedPair pair = FusedPair.clean(primary, knockback ? 2 : 4, GrapnelSkills.LURE, 2);
        ServerLevel level = player.level(); checkStation(level, pair);
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Zombie target = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        BlockPos wall = origin.east().above(); boolean changedWall = false;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(wall) && level.getBlockState(wall).isAir(), "Fixture needs loaded disposable air");
            GrapnelSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0; speed.setBaseValue(1024);
            for (LivingEntity entity : List.of(target, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ()); villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Fixture targets must enter the world");
            for (boolean nativePrimary : new boolean[]{false, true}) {
                Item item = knockback ? Items.DIAMOND_SWORD : Items.MACE;
                ItemStack weapon = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(weapon, pair, level.registryAccess());
                if (!nativePrimary) weapon.remove(DataComponents.ENCHANTMENTS);
                player.setItemSlot(EquipmentSlot.MAINHAND, weapon); GrapnelSkills.clearPlayer(player.getUUID());
                target.setPos(player.getX() + 2, player.getY(), player.getZ()); target.setDeltaMovement(Vec3.ZERO);
                target.getAttribute(Attributes.ARMOR).setBaseValue(breach ? 10 : 0);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                player.fallDistance = GrapnelImpact.DENSITY.equals(primary) ? 4 : 0;
                deny.add(target.getUUID()); hit(player, target); deny.clear();
                require(marked(player) == null, "Cancelled damage cannot mark");
                if (knockback) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                else if (breach) target.getAttribute(Attributes.ARMOR).setBaseValue(0);
                else player.fallDistance = 0;
                hit(player, target); require(marked(player) == null, "No real primary contribution cannot mark");
                target.setHealth(200); target.setDeltaMovement(Vec3.ZERO);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                target.getAttribute(Attributes.ARMOR).setBaseValue(breach ? 10 : 0);
                player.fallDistance = GrapnelImpact.DENSITY.equals(primary) ? 4 : 0;
                hit(player, villager); require(marked(player) == null, "A protected villager cannot be marked");
                player.fallDistance = GrapnelImpact.DENSITY.equals(primary) ? 4 : 0;
                hit(player, target); require(target.getUUID().equals(marked(player)), "A real contributed impact must mark the hit enemy");
                target.setPos(player.getX() + 20, player.getY(), player.getZ());
                require(!GrapnelSkills.activate(player) && target.getUUID().equals(marked(player)), "Range failure cannot consume the mark");
                target.setPos(player.getX() + 2, player.getY(), player.getZ());
                require(level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3), "Fixture wall must be placed"); changedWall = true;
                require(!GrapnelSkills.activate(player) && target.getUUID().equals(marked(player)), "Blocked sight cannot consume the mark");
                level.removeBlock(wall, false); changedWall = false;
                player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy()); require(!GrapnelSkills.activate(player), "A copy cannot spend the original mark");
                player.setItemSlot(EquipmentSlot.MAINHAND, weapon); target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && marked(player) == null,
                    "Active recall must pull the marked target exactly once");
            }
        } finally {
            if (changedWall) level.removeBlock(wall, false);
            deny.clear(); GrapnelSkills.clearPlayer(player.getUUID()); target.discard(); villager.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static void hit(ServerPlayer player, LivingEntity target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static UUID marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method getter = mark.getClass().getDeclaredMethod("target"); getter.setAccessible(true); return (UUID) getter.invoke(mark);
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.MACE, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.MACE || (!GrapnelImpact.DENSITY.equals(pair.primaryId()) && (item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE));
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use this primary's actual melee carrier");
            }
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
