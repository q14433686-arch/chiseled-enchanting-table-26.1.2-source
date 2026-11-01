package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
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
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Three bounded server probes: real attacks and server activation, with simulated timing and aim. */
final class GrapnelProbe {
    private GrapnelProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(attack, 4, GrapnelSkills.LURE, 2);
        checkStation(level, pair); GrapnelSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        BlockPos wall = origin.east().above(); boolean changedWall = false;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(wall) && level.getBlockState(wall).isAir(), "Fixture needs loaded disposable air");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0; speed.setBaseValue(1024);
            for (LivingEntity entity : List.of(target, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ());
            wrong.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() - 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(wrong) && level.addFreshEntity(villager), "Fixture targets must enter the world");
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD); FusionStationOperations.applyFusedPairToGear(sword, pair, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            require(!GrapnelSkills.activate(player), "No mark cannot pull");
            if (!sharp) hit(player, wrong);
            hit(player, villager); require(marked(player) == null, "Wrong or protected targets cannot be marked");
            deny.add(target.getUUID()); hit(player, target); deny.clear(); require(marked(player) == null, "Cancelled damage cannot mark");
            if (sharp) {
                speed.setBaseValue(0.1D); hit(player, target); require(marked(player) == null, "Sharpness requires a full hit"); speed.setBaseValue(1024);
            }
            hit(player, target); require(target.getUUID().equals(marked(player)), "An eligible real hit must mark its target");
            target.setPos(player.getX() + 20, player.getY(), player.getZ());
            require(!GrapnelSkills.activate(player) && target.getUUID().equals(marked(player)), "Range failure cannot consume the mark");
            target.setPos(player.getX() + 2, player.getY(), player.getZ());
            require(level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3), "Fixture wall must be placed"); changedWall = true;
            require(!GrapnelSkills.activate(player) && target.getUUID().equals(marked(player)), "Blocked sight cannot consume the mark");
            level.removeBlock(wall, false); changedWall = false;
            player.setItemSlot(EquipmentSlot.OFFHAND, sword); player.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
            require(!GrapnelSkills.activate(player) && target.getUUID().equals(marked(player)), "A copied weapon cannot spend the original mark");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            target.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && marked(player) == null,
                "Active recall must pull the marked target toward the player exactly once");
            require(!GrapnelSkills.activate(player), "An already spent mark cannot pull again");
        } finally {
            if (changedWall) level.removeBlock(wall, false);
            deny.clear(); GrapnelSkills.clearPlayer(player.getUUID()); target.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]);
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
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.TRIDENT;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use the actual melee carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
