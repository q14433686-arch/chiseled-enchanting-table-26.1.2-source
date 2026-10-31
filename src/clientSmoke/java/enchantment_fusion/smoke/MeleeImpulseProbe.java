package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Six server fixtures: real damage/knockback, with explicitly simulated motion and mark age. */
final class MeleeImpulseProbe {
    private MeleeImpulseProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean knockbackFirst) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(attack, 4, MeleeImpulseSkills.KNOCKBACK, 2);
        if (knockbackFirst) pair = pair.flipped();
        checkStation(level, pair); MeleeImpulseSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(), sprint = player.isSprinting(); double fall = player.fallDistance;
        GameType mode = player.gameMode.getGameModeForPlayer(); var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = enemy(level, bane), neighbor = enemy(level, bane), wrong = enemy(level, !bane);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(bane ? 15 : 0); player.setOnGround(false); player.setSprinting(false); player.fallDistance = 0;
            speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            for (LivingEntity entity : List.of(target, neighbor, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            neighbor.setPos(origin.getX() + 4.8D, origin.getY(), origin.getZ() + 0.5D);
            wrong.setPos(origin.getX() + 4.8D, origin.getY(), origin.getZ() + 0.5D);
            villager.setPos(origin.getX() + 4.8D, origin.getY(), origin.getZ() + 3.5D);
            require(level.addFreshEntity(target) && level.addFreshEntity(neighbor) && level.addFreshEntity(wrong) && level.addFreshEntity(villager),
                "Fixture targets must enter the disposable world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            deny.add(target.getUUID()); hit(player, target); deny.clear();
            require(marks() == 0, "Cancelled damage cannot create a knockback trace");
            var resistance = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE); double oldResistance = resistance.getBaseValue();
            resistance.setBaseValue(1); target.setDeltaMovement(Vec3.ZERO); hit(player, target);
            require(marks() == 0, "A fully resisted knockback cannot create a trace"); resistance.setBaseValue(oldResistance);
            if (knockbackFirst) exerciseTransfer(player, origin, target, neighbor, wrong, villager, sword, pair, attack);
            else exerciseChase(player, origin, target, neighbor, wrong, villager, sword, pair, attack, deny);
        } finally {
            deny.clear(); MeleeImpulseSkills.clearPlayer(player.getUUID()); target.discard(); neighbor.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setSprinting(sprint); player.fallDistance = fall;
        }
    }
    private static void exerciseChase(ServerPlayer player, BlockPos origin, LivingEntity target, LivingEntity neighbor,
                                      LivingEntity wrong, Villager villager, ItemStack sword, FusedPair pair,
                                      String attack, Set<UUID> deny) throws Exception {
        ServerLevel level = player.level(); ItemStack cross = fused(level, Items.TRIDENT, pair);
        float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, target, level.damageSources().playerAttack(player), 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, cross);
        float crossDamage = EnchantmentHelper.modifyDamage(level, cross, target, level.damageSources().playerAttack(player), 1);
        require(nativeDamage > 1 && Math.abs(nativeDamage - crossDamage) < 0.001F, "A missing attack primary must replay once");
        player.setItemSlot(EquipmentSlot.MAINHAND, sword); target.setDeltaMovement(Vec3.ZERO); hit(player, target);
        require(marks() == 1 && target.getDeltaMovement().x > 0, "A real hit must create a resisted knockback trace");
        age(player, target); target.move(MoverType.SELF, new Vec3(0.5D, 0, 0)); player.move(MoverType.PLAYER, new Vec3(0.4D, 0, 0));
        MeleeImpulseSkills.tickServer(level.getServer());
        deny.add(target.getUUID()); hit(player, target); deny.clear();
        require(marks() == 1, "A cancelled chase hit must not release the trace");
        float before = neighbor.getHealth(), wrongHealth = wrong.getHealth(), protectedHealth = villager.getHealth();
        hit(player, target);
        require(neighbor.getHealth() < before, "A real follow-up after simulated pursuit must release a directional blade");
        require(villager.getHealth() == protectedHealth, "The blade must protect villagers");
        if (!MeleeAttackThemes.SHARP.equals(attack)) require(wrong.getHealth() == wrongHealth, "The blade must keep its target category");
    }
    private static void exerciseTransfer(ServerPlayer player, BlockPos origin, LivingEntity target, LivingEntity neighbor,
                                         LivingEntity wrong, Villager villager, ItemStack sword, FusedPair pair, String attack) throws Exception {
        ServerLevel level = player.level();
        double start = origin.getX() + 2.5D, z = origin.getZ() + 0.5D;
        neighbor.setPos(start + (target.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.7D, origin.getY(), z);
        neighbor.setOldPosAndRot();
        wrong.setPos(origin.getX() + 4.8D, origin.getY(), origin.getZ() + 3.5D);
        target.setDeltaMovement(Vec3.ZERO); neighbor.setDeltaMovement(Vec3.ZERO); hit(player, target);
        require(marks() == 1 && target.getDeltaMovement().x > 0, "A real hit must create a resisted knockback trace");
        age(player, target); target.move(MoverType.SELF, new Vec3(0.2D, 0, 0)); MeleeImpulseSkills.tickServer(level.getServer());
        require(neighbor.getDeltaMovement().horizontalDistanceSqr() == 0 && marks() == 1, "A merely nearby enemy cannot receive momentum");
        target.move(MoverType.SELF, new Vec3(0.8D, 0, 0)); MeleeImpulseSkills.tickServer(level.getServer());
        require(neighbor.getDeltaMovement().x > 0 && marks() == 0, "A swept contact can transmit real momentum once");

        MeleeImpulseSkills.clearPlayer(player.getUUID());
        target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D); target.setDeltaMovement(Vec3.ZERO);
        neighbor.setDeltaMovement(Vec3.ZERO); villager.setDeltaMovement(Vec3.ZERO);
        villager.setPos(start + (target.getBbWidth() + villager.getBbWidth()) / 2 + 0.3D, origin.getY(), z);
        neighbor.setPos(villager.getX() + (villager.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.3D, origin.getY(), z);
        hit(player, target); require(marks() == 1, "A second real knockback can create a trace"); age(player, target);
        target.move(MoverType.SELF, new Vec3(1.0D, 0, 0)); MeleeImpulseSkills.tickServer(level.getServer());
        require(villager.getDeltaMovement().horizontalDistanceSqr() == 0 && neighbor.getDeltaMovement().horizontalDistanceSqr() == 0 && marks() == 0,
            "A protected first contact must stop transfer to everything behind it");
        if (!MeleeAttackThemes.SHARP.equals(attack)) {
            MeleeImpulseSkills.clearPlayer(player.getUUID());
            target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D); target.setDeltaMovement(Vec3.ZERO);
            villager.setPos(origin.getX() + 4.8D, origin.getY(), origin.getZ() + 3.5D);
            wrong.setPos(start + (target.getBbWidth() + wrong.getBbWidth()) / 2 + 0.3D, origin.getY(), z); wrong.setDeltaMovement(Vec3.ZERO);
            hit(player, target); age(player, target); target.move(MoverType.SELF, new Vec3(1.0D, 0, 0)); MeleeImpulseSkills.tickServer(level.getServer());
            require(wrong.getDeltaMovement().horizontalDistanceSqr() == 0 && marks() == 0, "A wrong-category contact must stop transfer");
        }

        // Position changes without Entity.move are not accepted as knockback motion.
        MeleeImpulseSkills.clearPlayer(player.getUUID());
        target.setPos(start, origin.getY(), z); target.setDeltaMovement(Vec3.ZERO);
        villager.setPos(start + 3, origin.getY(), z + 3); wrong.setPos(start + 3, origin.getY(), z + 3);
        neighbor.setPos(start + (target.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.3D, origin.getY(), z);
        neighbor.setOldPosAndRot(); neighbor.setDeltaMovement(Vec3.ZERO);
        hit(player, target); age(player, target); target.setPos(target.position().add(1, 0, 0));
        MeleeImpulseSkills.tickServer(level.getServer());
        require(marks() == 0 && neighbor.getDeltaMovement().horizontalDistanceSqr() == 0,
            "A teleport cannot masquerade as swept contact");

        // An already-overlapping protected entity blocks attribution to a target farther away.
        MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(start, origin.getY(), z); target.setDeltaMovement(Vec3.ZERO);
        villager.setPos(start + (target.getBbWidth() + villager.getBbWidth()) / 2 - 0.05D, origin.getY(), z);
        villager.setDeltaMovement(Vec3.ZERO);
        neighbor.setPos(start + (target.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.8D, origin.getY(), z);
        neighbor.setOldPosAndRot(); neighbor.setDeltaMovement(Vec3.ZERO);
        hit(player, target); age(player, target); target.move(MoverType.SELF, new Vec3(1, 0, 0));
        MeleeImpulseSkills.tickServer(level.getServer());
        require(marks() == 0 && villager.getDeltaMovement().horizontalDistanceSqr() == 0
            && neighbor.getDeltaMovement().horizontalDistanceSqr() == 0, "Initial protected overlap must block attribution");

        // A quickly moving candidate is ambiguous, even if its current box intersects the sweep.
        MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(start, origin.getY(), z); target.setDeltaMovement(Vec3.ZERO);
        villager.setPos(start + 3, origin.getY(), z + 3);
        neighbor.setPos(start + (target.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.3D, origin.getY(), z);
        neighbor.setOldPosAndRot(); neighbor.setDeltaMovement(new Vec3(0.3D, 0, 0));
        hit(player, target); age(player, target); target.move(MoverType.SELF, new Vec3(1, 0, 0));
        MeleeImpulseSkills.tickServer(level.getServer());
        require(marks() == 0 && Math.abs(neighbor.getDeltaMovement().x - 0.3D) < 0.0001D,
            "Fast candidate motion must be skipped, not guessed");

        MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(start, origin.getY(), z); target.setDeltaMovement(Vec3.ZERO);
        neighbor.setPos(start + (target.getBbWidth() + neighbor.getBbWidth()) / 2 + 0.3D, origin.getY(), z);
        neighbor.setOldPosAndRot(); neighbor.setDeltaMovement(Vec3.ZERO);
        hit(player, target); age(player, target); target.move(MoverType.SELF, new Vec3(1, 0, 0));
        neighbor.setPos(neighbor.position().add(0.1D, 0, 0)); MeleeImpulseSkills.tickServer(level.getServer());
        require(marks() == 0 && neighbor.getDeltaMovement().horizontalDistanceSqr() == 0,
            "A candidate moving after the source must not be guessed as a simultaneous collision");

        // World collision clips the real path before any entity behind a wall.
        MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(start, origin.getY(), z); target.setDeltaMovement(Vec3.ZERO);
        neighbor.setPos(origin.getX() + 5.2D, origin.getY(), z); neighbor.setOldPosAndRot(); neighbor.setDeltaMovement(Vec3.ZERO);
        BlockPos wall = origin.offset(4, 0, 0); var wallBefore = level.getBlockState(wall); var upperBefore = level.getBlockState(wall.above());
        try {
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3); level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
            hit(player, target); age(player, target); target.move(MoverType.SELF, new Vec3(1.8D, 0, 0));
            MeleeImpulseSkills.tickServer(level.getServer());
            require(target.getBoundingBox().maxX <= wall.getX() + 0.0001D && neighbor.getDeltaMovement().horizontalDistanceSqr() == 0,
                "A solid wall must clip the path before transfer");
        } finally {
            MeleeImpulseSkills.clearPlayer(player.getUUID()); level.setBlock(wall, wallBefore, 3); level.setBlock(wall.above(), upperBefore, 3);
        }

        MeleeImpulseSkills.clearPlayer(player.getUUID());
        target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D); target.setDeltaMovement(Vec3.ZERO);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        float health = target.getHealth(); player.stabAttack(EquipmentSlot.MAINHAND, target, 0, false, true, false);
        double normal = target.getDeltaMovement().horizontalDistanceSqr(); target.setDeltaMovement(Vec3.ZERO);
        ItemStack replay = fused(level, Items.TRIDENT, pair); replay.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, replay); player.stabAttack(EquipmentSlot.MAINHAND, target, 0, false, true, false);
        require(target.getHealth() == health && target.getDeltaMovement().horizontalDistanceSqr() > normal && marks() == 0,
            "Missing primary Knockback must replay on a non-damaging stab without creating a skill trace");
    }
    private static LivingEntity enemy(ServerLevel level, boolean spider) {
        return spider ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
    }
    private static void hit(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target);
    }
    @SuppressWarnings("unchecked")
    private static List<Object> state() throws Exception {
        Field field = MeleeImpulseSkills.class.getDeclaredField("MARKS"); field.setAccessible(true); return (List<Object>) field.get(null);
    }
    private static int marks() throws Exception { return state().size(); }
    /** Rebuild a mark with a test-only earlier creation time; do not alter the world's clock. */
    private static void age(ServerPlayer player, LivingEntity target) throws Exception {
        List<Object> marks = state();
        for (int i = 0; i < marks.size(); i++) {
            Object mark = marks.get(i); Class<?> type = mark.getClass();
            Field owner = type.getDeclaredField("owner"), victim = type.getDeclaredField("target"), gear = type.getDeclaredField("gear"), direction = type.getDeclaredField("direction");
            for (Field field : List.of(owner, victim, gear, direction)) field.setAccessible(true);
            if (!owner.get(mark).equals(player.getUUID()) || !victim.get(mark).equals(target.getUUID())) continue;
            var ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            marks.set(i, ctor.newInstance(player, target, gear.get(mark), direction.get(mark), player.level().getGameTime() - 1)); return;
        }
        throw new AssertionError("Expected real knockback trace to age");
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
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.TRIDENT;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must validate melee carriers");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
