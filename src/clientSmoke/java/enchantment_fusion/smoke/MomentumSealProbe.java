package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real server attack calls in the disposable world, not physical keypress or multiplayer testing. */
final class MomentumSealProbe {
    private static final FusedPair FORWARD = FusedPair.clean("minecraft:knockback", 2, "minecraft:silk_touch", 1);
    private MomentumSealProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? FORWARD.flipped() : FORWARD;
        checkStation(level, pair);
        MomentumSealSkills.onDisconnect(player);
        Vec3 savedPosition = player.position(), savedVelocity = player.getDeltaMovement();
        float savedYaw = player.getYRot(), savedPitch = player.getXRot();
        GameType savedMode = player.gameMode.getGameModeForPlayer();
        ItemStack savedMain = player.getMainHandItem(), savedOff = player.getOffhandItem();
        var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        double oldSpeed = attackSpeed.getBaseValue();
        Zombie target = new Zombie(level), other = new Zombie(level);
        Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(),
                "Momentum fixture needs empty loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            attackSpeed.setBaseValue(1024.0D); player.resetAttackStrengthTicker();
            for (Zombie zombie : List.of(target, other)) {
                zombie.setNoAi(true); zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D); zombie.setHealth(200.0F);
                zombie.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
                require(level.addFreshEntity(zombie), "Momentum targets must enter the disposable world");
            }
            other.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 4.5D);
            for (ItemStack weapon : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE))) {
                FusionStationOperations.applyFusedPairToGear(weapon, pair, level.registryAccess());
                player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                target.invulnerableTime = 0; target.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F);
                float health = target.getHealth();
                player.attack(target);
                require(target.getHealth() < health, "The direct vanilla hit must actually damage the target");
                if (!reverse) {
                    Vec3 moving = target.getDeltaMovement();
                    require(moving.x > 0.01D, "The real hit must push the target before activation");
                    require(MomentumSealSkills.activate(player), "The original pushed target must be capturable");
                }
                require(target.getDeltaMovement().horizontalDistanceSqr() < 0.0001D
                    && target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < 0.0001D,
                    "A captured target must have horizontal movement held without stopping gravity");
                // A second target cannot steal an already controlled target's reservation.
                other.invulnerableTime = 0; other.setDeltaMovement(Vec3.ZERO);
                player.attack(other);
                require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < 0.0001D,
                    "Another hit must not replace an existing controlled target");
                if (reverse) player.setYRot(0.0F); // +Z, not the original +X.
                require(MomentumSealSkills.activate(player), "A held target must release its stored movement");
                Vec3 released = target.getDeltaMovement();
                require(reverse ? released.z > 0.01D && Math.abs(released.x) < 0.001D
                                : released.x > 0.01D && Math.abs(released.z) < 0.001D,
                    "Each order must use its designated original or chosen release direction");
                require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > 0.0D,
                    "Release must remove the transient control modifier");
                require(!MomentumSealSkills.activate(player), "A sealed movement cannot be released twice");
            }

            ItemStack spear = new ItemStack(Items.DIAMOND_SPEAR);
            FusionStationOperations.applyFusedPairToGear(spear, pair, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, spear); player.setYRot(-90.0F);
            target.invulnerableTime = 0; target.setDeltaMovement(Vec3.ZERO);
            require(player.stabAttack(EquipmentSlot.MAINHAND, target, 4.0F, true, true, false),
                "A real spear stab must hit the target");
            if (!reverse) require(MomentumSealSkills.activate(player), "A real spear's push must be capturable");
            require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < 0.0001D,
                "A real spear stab must reach the same motion control path");
            player.setItemSlot(EquipmentSlot.MAINHAND, spear.copy());
            MomentumSealSkills.tickServer(level.getServer());
            require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > 0.0D,
                "Replacing the weapon object must remove transient control and restore ordinary motion");
            require(!MomentumSealSkills.activate(player), "A copied weapon must not inherit another item's state");

            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            FusionStationOperations.applyFusedPairToGear(sword, pair, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            target.invulnerableTime = 0; target.setDeltaMovement(Vec3.ZERO);
            deny.add(target.getUUID()); player.attack(target); deny.clear();
            require(!MomentumSealSkills.activate(player), "A cancelled damage event must not create a usable seal");
            target.invulnerableTime = 0; target.setDeltaMovement(Vec3.ZERO);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            player.attack(target);
            require(!MomentumSealSkills.activate(player), "A fully knockback-resistant target must not acquire a fake seal");
            require(level.getBlockState(origin).isAir(), "The motion effect must not create ice, water or other blocks");
        } finally {
            deny.clear(); MomentumSealSkills.onDisconnect(player);
            target.discard(); other.discard(); player.stopUsingItem();
            player.setItemSlot(EquipmentSlot.MAINHAND, savedMain); player.setItemSlot(EquipmentSlot.OFFHAND, savedOff);
            attackSpeed.setBaseValue(oldSpeed); player.setGameMode(savedMode); player.setPos(savedPosition);
            player.setDeltaMovement(savedVelocity); player.setYRot(savedYaw); player.setXRot(savedPitch);
        }
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
            for (ItemStack item : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE),
                    new ItemStack(Items.DIAMOND_SPEAR), new ItemStack(Items.MACE))) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, item);
                require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).executable(),
                    "Both station routes must accept each actual melee carrier");
            }
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.BOW));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station routes must reject a bow");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
