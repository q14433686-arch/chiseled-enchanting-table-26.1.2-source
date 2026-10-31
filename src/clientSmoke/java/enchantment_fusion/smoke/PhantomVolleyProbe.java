package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Prepared, not run: physical server arrow ticks, but no real crossbow input or multiplayer client. */
final class PhantomVolleyProbe {
    private static final FusedPair VOLLEY = FusedPair.clean("minecraft:multishot", 1, "minecraft:piercing", 3);
    private static final FusedPair CLUSTER = VOLLEY.flipped();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private PhantomVolleyProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.level();
        exerciseCarriers(level);
        ItemStack[] savedSlots = new ItemStack[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) savedSlots[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 oldPosition = player.position();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        List<AbstractArrow> arrows = new ArrayList<>();
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Cow cow = new Cow(EntityType.COW, level);
        Wolf pet = new Wolf(EntityType.WOLF, level), neutral = new Wolf(EntityType.WOLF, level);
        Zombie first = new Zombie(level), other = new Zombie(level);
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            for (int x = 0; x <= 14; x++) for (int y = 0; y <= 3; y++) for (int z = -1; z <= 3; z++) {
                BlockPos pos = origin.offset(x, y, z);
                savedBlocks.put(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            villager.setNoAi(true);
            cow.setNoAi(true);
            pet.setNoAi(true);
            neutral.setNoAi(true);
            first.setNoAi(true);
            other.setNoAi(true);
            villager.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D);
            cow.setPos(origin.getX() + 6.5D, origin.getY(), origin.getZ() + 6.5D);
            pet.setPos(origin.getX() + 7.5D, origin.getY(), origin.getZ() + 6.5D);
            neutral.setPos(origin.getX() + 8.5D, origin.getY(), origin.getZ() + 6.5D);
            first.setPos(origin.getX() + 12.5D, origin.getY(), origin.getZ() + 6.5D);
            other.setPos(origin.getX() + 10.5D, origin.getY(), origin.getZ() + 2.5D);
            for (var fixture : List.of(villager, cow, pet, neutral, first, other)) {
                require(level.addFreshEntity(fixture), "Fixture entity must enter the level");
            }
            pet.setTame(true, false);
            require(PhantomVolley.isProtected(player, player) && PhantomVolley.isProtected(player, villager)
                && PhantomVolley.isProtected(player, pet), "Players, villagers and tamed animals are protected");
            require(!PhantomVolley.eligibleHomingTarget(player, cow) && !PhantomVolley.eligibleHomingTarget(player, neutral)
                && PhantomVolley.eligibleHomingTarget(player, first), "Only hostiles or actively attacking neutrals are selected");
            neutral.setTarget(player);
            require(PhantomVolley.eligibleHomingTarget(player, neutral), "An attacking neutral may be selected");
            neutral.setTarget(null);

            ItemStack crossbow = fused(level, new ItemStack(Items.CROSSBOW), VOLLEY);
            Arrow center = new Arrow(level, player, new ItemStack(Items.ARROW), crossbow);
            Arrow flank = new Arrow(level, player, new ItemStack(Items.ARROW), crossbow);
            arrows.add(center); arrows.add(flank);
            PhantomVolley.configureCrossbow(center, player, 0);
            PhantomVolley.configureCrossbow(flank, player, 1);
            require(!PhantomVolley.isGhost(center) && center.getPierceLevel() >= 3,
                "The real crossbow center arrow remains normal and uses secondary Piercing");
            require(PhantomVolley.isGhost(flank) && flank.getPierceLevel() >= 3 && flank.pickup == AbstractArrow.Pickup.DISALLOWED,
                "A crossbow side arrow is non-recoverable and has real piercing");
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            flank.saveWithoutId(output);
            Arrow restored = new Arrow(EntityType.ARROW, level);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
            arrows.add(restored);
            require(PhantomVolley.isGhost(restored) && restored.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Serialization must retain ghost identity and no-pickup mode");

            ItemStack volleyBow = fused(level, new ItemStack(Items.BOW), VOLLEY);
            player.setItemSlot(EquipmentSlot.MAINHAND, volleyBow);
            AABB region = new AABB(origin).inflate(18);
            Set<java.util.UUID> beforeVolley = level.getEntitiesOfClass(Arrow.class, region).stream()
                .map(Arrow::getUUID).collect(Collectors.toSet());
            Arrow bowCenter = new Arrow(level, player, new ItemStack(Items.ARROW), volleyBow);
            arrows.add(bowCenter);
            bowCenter.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            List<Arrow> bowSides = level.getEntitiesOfClass(Arrow.class, region,
                a -> !beforeVolley.contains(a.getUUID()) && PhantomVolley.isGhost(a));
            require(!PhantomVolley.isGhost(bowCenter) && bowCenter.getPierceLevel() >= 3 && bowSides.size() == 2
                && bowSides.stream().allMatch(a -> a.getPierceLevel() >= 3 && a.pickup == AbstractArrow.Pickup.DISALLOWED),
                "A real bow launch must generate two non-recoverable piercing side arrows");
            for (Arrow side : bowSides) side.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            Arrow ghost = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            arrows.add(ghost);
            ghost.setPos(origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            PhantomVolley.markGhost(ghost, player, null);
            ghost.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            level.addFreshEntity(ghost);
            float villagerBefore = villager.getHealth();
            for (int i = 0; i < 3 && ghost.isAlive(); i++) ghost.tick();
            require(!ghost.isAlive() && villager.getHealth() == villagerBefore,
                "Real phantom collision with a villager must vanish before damage");

            Arrow real = new Arrow(level, player, new ItemStack(Items.ARROW), crossbow);
            arrows.add(real);
            PhantomVolley.configureCrossbow(real, player, 0);
            real.setPos(origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            real.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            level.addFreshEntity(real);
            for (int i = 0; i < 3 && real.isAlive() && villager.getHealth() == villagerBefore; i++) real.tick();
            require(villager.getHealth() < villagerBefore, "The real center arrow still follows normal damage rules");
            real.discard();

            villager.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 6.5D);
            BlockPos wall = origin.offset(4, 1, 0);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            Arrow blocked = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            arrows.add(blocked);
            blocked.setPos(origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            PhantomVolley.markGhost(blocked, player, null);
            blocked.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            level.addFreshEntity(blocked);
            for (int i = 0; i < 3 && blocked.isAlive(); i++) blocked.tick();
            require(!blocked.isAlive() && level.getBlockState(wall).is(Blocks.STONE),
                "A phantom stops at a solid block without damaging terrain");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());

            first.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D);
            ItemStack bow = fused(level, new ItemStack(Items.BOW), CLUSTER);
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
            Arrow cluster = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
            arrows.add(cluster);
            cluster.setPos(origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            PhantomVolley.markCluster(cluster, player, true); // deterministic hit fixture, not a probability test
            cluster.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            level.addFreshEntity(cluster);
            float firstBefore = first.getHealth();
            for (int i = 0; i < 3 && cluster.isAlive() && first.getHealth() == firstBefore; i++) cluster.tick();
            require(first.getHealth() < firstBefore, "The cluster carrier must make a real successful hit");
            List<Arrow> children = level.getEntitiesOfClass(Arrow.class, new AABB(origin).inflate(18), PhantomVolley::isGhost);
            require(children.size() == 2 && children.stream().allMatch(a -> a.pickup == AbstractArrow.Pickup.DISALLOWED),
                "A successful armed hit must produce exactly two non-recoverable ghost arrows");
            arrows.addAll(children);
            Arrow away = children.stream().min(java.util.Comparator.comparingDouble(a -> a.getDeltaMovement().z)).orElseThrow();
            double oldZ = away.getDeltaMovement().normalize().z;
            away.tick();
            require(away.getDeltaMovement().normalize().z > oldZ,
                "A splitting ghost should turn toward the closest eligible hostile rather than a neutral or villager");
        } finally {
            for (AbstractArrow arrow : arrows) arrow.discard();
            for (var fixture : List.of(villager, cow, pet, neutral, first, other)) fixture.discard();
            for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], savedSlots[i]);
            player.setGameMode(oldMode);
            player.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
        }
    }

    private static void exerciseCarriers(ServerLevel level) {
        Holder<Enchantment> multi = enchant(level, "multishot"), pierce = enchant(level, "piercing");
        for (FusedPair pair : List.of(VOLLEY, CLUSTER)) {
            for (ItemStack weapon : List.of(new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW))) {
                require(DirectionalHostRules.validateNewOrChanged(weapon, pair) == RejectReason.NONE, "Bow and crossbow must be supported");
                for (boolean direct : List.of(false, true)) {
                    var station = new SimpleContainer(4);
                    station.setItem(FusionStationOperations.SLOT_TARGET, weapon.copy());
                    if (direct) {
                        station.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(
                            PhantomVolley.isVolley(pair) ? multi : pierce, pair.primaryLevel()));
                        station.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(
                            PhantomVolley.isVolley(pair) ? pierce : multi, pair.secondaryLevel()));
                    } else station.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
                    int first = direct ? 0 : -1, second = direct ? 1 : -1;
                    require(FusionStationOperations.inspect(station, level.registryAccess(), FusionStationOperations.TAB_SOCKET, first, second)
                        .executable(), "Both book and direct fusion should accept the firing weapon");
                    station.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.TRIDENT));
                    require(FusionStationOperations.inspect(station, level.registryAccess(), FusionStationOperations.TAB_SOCKET, first, second)
                        .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station paths must reject a new trident");
                }
            }
        }
    }
    private static ItemStack fused(ServerLevel level, ItemStack stack, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
    }
    private static Holder<Enchantment> enchant(ServerLevel level, String path) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
