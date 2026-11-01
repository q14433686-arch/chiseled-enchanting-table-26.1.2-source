package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelDefense;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Four bounded server probes: actual hurt/block paths, simulated danger sources, aim and activation. */
final class GrapnelDefenseProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private GrapnelDefenseProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String defense) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(defense, 4, GrapnelSkills.LURE, 2);
        checkStation(level, pair); GrapnelSkills.clearPlayer(player.getUUID());
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR), armorBackup = new AttributeInstance(Attributes.ARMOR, ignored -> {});
        armorBackup.replaceFrom(armor); var max = player.getAttribute(Attributes.MAX_HEALTH); double oldMax = max.getBaseValue();
        List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), headYaw = player.getYHeadRot(), pitch = player.getXRot();
        float health = player.getHealth(), absorption = player.getAbsorptionAmount(); int invulnerability = player.invulnerableTime;
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Arrow arrow = new Arrow(level, enemy, new ItemStack(Items.ARROW), null);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        BlockPos wall = origin.east(2).above(); boolean changedWall = false;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(wall) && level.getBlockState(wall).isAir(), "Fixture needs loaded disposable air");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.removeAllEffects();
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            armor.removeModifiers(); armor.setBaseValue(0); max.setBaseValue(200); player.setHealth(200); player.setAbsorptionAmount(0);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setYHeadRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0;
            enemy.setNoAi(true); villager.setNoAi(true); enemy.setPos(player.getX() + 4, player.getY(), player.getZ());
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            arrow.setPos(player.getX() + 2, player.getY() + 1.0D, player.getZ()); arrow.setDeltaMovement(new Vec3(-1, 0, 0));
            require(level.addFreshEntity(enemy) && level.addFreshEntity(villager) && level.addFreshEntity(arrow), "Fixture entities must enter the world");
            DamageSource source = source(level, defense, arrow, enemy);
            ItemStack chest = fused(level, Items.DIAMOND_CHESTPLATE, pair);
            // Exercise actual fallback of the non-native primary, rather than merely detecting a vanilla enchantment.
            chest.remove(DataComponents.ENCHANTMENTS); player.setItemSlot(EquipmentSlot.CHEST, chest);
            require(GrapnelSkills.hasEquipped(player) && !GrapnelSkills.activate(player), "A worn carrier exposes activation, but no mark cannot pull");
            deny.add(player.getUUID()); hit(player, source); deny.clear(); require(!marked(player), "Cancelled hurt cannot mark a defense");
            if (!GrapnelDefense.PROTECTION.equals(defense)) {
                hit(player, level.damageSources().generic()); require(!marked(player), "The wrong danger type cannot mark");
            }
            ItemStack saturated = new ItemStack(Items.DIAMOND_HELMET);
            var protection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(GrapnelDefense.PROTECTION)));
            saturated.enchant(protection, 20); player.setItemSlot(EquipmentSlot.HEAD, saturated);
            hit(player, source); require(!marked(player), "Other gear already at the vanilla cap leaves no attributable mitigation");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            hit(player, source); require(marked(player), "Accepted real damage with an attributable primary contribution must mark the location");

            villager.setPos(player.getX() + 2, player.getY(), player.getZ()); enemy.setDeltaMovement(Vec3.ZERO);
            require(!GrapnelSkills.activate(player) && marked(player) && enemy.getDeltaMovement().lengthSqr() == 0,
                "A protected body must block the aim ray, not be pulled or penetrated");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            require(level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3), "Fixture wall must be placed"); changedWall = true;
            require(!GrapnelSkills.activate(player) && marked(player), "A wall must block active recall without spending the mark");
            level.removeBlock(wall, false); changedWall = false;
            player.setItemSlot(EquipmentSlot.CHEST, chest.copy());
            require(!GrapnelSkills.activate(player), "A copied armor piece must not spend the original mark");
            player.setItemSlot(EquipmentSlot.CHEST, chest); hit(player, source); require(marked(player), "The original armor can establish a fresh mark");
            enemy.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && !marked(player),
                "Aimed activation must pull the eligible enemy once");
            require(!GrapnelSkills.activate(player), "A spent position mark cannot pull again");

            if (!GrapnelDefense.FIRE.equals(defense)) {
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); GrapnelSkills.clearPlayer(player.getUUID());
                ItemStack shield = fused(level, Items.SHIELD, pair); player.setItemSlot(EquipmentSlot.OFFHAND, shield);
                hit(player, source); require(marked(player), "An actively held non-native shield primary must project actual protection");
                GrapnelSkills.clearPlayer(player.getUUID());
                if (!GrapnelDefense.BLAST.equals(defense)) {
                    player.startUsingItem(InteractionHand.OFF_HAND);
                    Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining"); remaining.setAccessible(true);
                    remaining.setInt(player, shield.getUseDuration(player) - 10); // simulate the native block delay
                    require(player.getItemBlockingWith() == shield, "The same shield must be blocking");
                    DamageSource blockSource = GrapnelDefense.PROJECTILE.equals(defense) ? source : level.damageSources().mobAttack(enemy);
                    player.invulnerableTime = 0; player.setHealth(200); float before = player.getHealth();
                    boolean accepted = player.hurtServer(level, blockSource, 8.0F);
                    require(!accepted && player.getHealth() == before && marked(player),
                        "A fully blocked hit may return false yet must mark only the actual blocking shield");
                    player.stopUsingItem(); GrapnelSkills.clearPlayer(player.getUUID());
                    player.invulnerableTime = 20; player.hurtServer(level, blockSource, 8.0F);
                    require(!marked(player), "A cooldown-rejected hit cannot mark the shield");
                }
            }
        } finally {
            if (changedWall) level.removeBlock(wall, false);
            deny.clear(); GrapnelSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); enemy.discard(); villager.discard(); arrow.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            armor.replaceFrom(armorBackup); max.setBaseValue(oldMax); player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(effect);
            player.setGameMode(mode); player.setHealth(health); player.setAbsorptionAmount(absorption); player.invulnerableTime = invulnerability;
            player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setYHeadRot(headYaw); player.setXRot(pitch);
            player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static DamageSource source(ServerLevel level, String defense, Arrow arrow, Zombie enemy) {
        return switch (defense) {
            case GrapnelDefense.FIRE -> level.damageSources().inFire();
            case GrapnelDefense.BLAST -> level.damageSources().explosion(null, null);
            case GrapnelDefense.PROJECTILE -> level.damageSources().arrow(arrow, enemy);
            default -> level.damageSources().generic();
        };
    }
    private static void hit(ServerPlayer player, DamageSource source) {
        player.invulnerableTime = 0; player.setHealth(200); player.hurtServer(player.level(), source, 8.0F);
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inventory = new SimpleContainer(4);
            if (direct) {
                inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inventory.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.SHIELD, Items.DIAMOND_SWORD, Items.BOW)) {
                inventory.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inventory, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.SHIELD && !GrapnelDefense.FIRE.equals(pair.primaryId());
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must accept only carriers with this directional defense");
            }
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
