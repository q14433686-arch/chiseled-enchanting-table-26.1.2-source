package enchantment_fusion.smoke;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.ConfirmedMining;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.RangedWorkSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Six candidate fixtures: real breaks and arrows, but activation and flight are deliberately prepared. */
final class RangedWorkProbe {
    private RangedWorkProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String theme, boolean reverse) throws Exception {
        ServerLevel level = player.level();
        FusedPair forward = FusedPair.clean(theme, 2, RangedWorkSkills.EFFICIENCY, 3), pair = reverse ? forward.flipped() : forward;
        checkStation(level, pair, reverse);
        RangedWorkSkills.clearPlayer(player); ConfirmedArrow.clearPlayer(player.getUUID());
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem(), savedSlot = player.getInventory().getItem(20);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot(); GameType mode = player.gameMode.getGameModeForPlayer();
        AABB area = new AABB(origin).inflate(24.0D); Set<UUID> existing = GrapnelProjectileProbe.arrowIds(level, area);
        Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        BlockPos mine = origin.offset(-1, 0, -2);
        Zombie target = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> denyDamage = new HashSet<>(); Set<BlockPos> denyBlocks = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denyDamage.contains(entity.getUUID()));
        PlayerBlockBreakEvents.BEFORE.register((world, who, pos, state, entity) -> !denyBlocks.contains(pos));
        try {
            for (int x = -2; x <= 14; x++) for (int y = 0; y <= 4; y++) for (int z = -2; z <= 3; z++) {
                BlockPos pos = origin.offset(x, y, z); BlockState state = level.getBlockState(pos);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && state.isAir(), "Fixture needs loaded disposable open space");
                savedBlocks.put(pos, state);
            }
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
            for (LivingEntity entity : List.of(target, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); entity.setHealth(1000);
            }
            target.setPos(player.getX() + 4.0D, player.getY(), player.getZ());
            villager.setPos(player.getX() + 4.0D, player.getY(), player.getZ() + 4.0D);
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Targets must enter the world");
            if (reverse) reverse(player, pair, theme, mine, target, villager, area, denyDamage, denyBlocks, savedBlocks);
            else forward(player, pair, theme, mine, target, villager, area, denyDamage, denyBlocks);
        } finally {
            denyDamage.clear(); denyBlocks.clear(); player.stopUsingItem();
            RangedWorkSkills.clearPlayer(player); ConfirmedArrow.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            target.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!existing.contains(arrow.getUUID())) arrow.discard();
            for (var entry : savedBlocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.getInventory().setItem(20, savedSlot); player.setItemSlot(EquipmentSlot.MAINHAND, main);
            player.setItemSlot(EquipmentSlot.OFFHAND, off); player.setGameMode(mode); player.setPos(oldPos);
            player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, String theme, BlockPos mine, Zombie target, Villager villager,
                                AABB area, Set<UUID> denyDamage, Set<BlockPos> denyBlocks) throws Exception {
        ServerLevel level = player.level(); ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, pair);
        bow.remove(DataComponents.ENCHANTMENTS); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        require(!RangedWorkSkills.activate(player, true), "No hit cannot prepare mining acceleration");
        Arrow synthetic = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        require(level.addFreshEntity(synthetic), "Synthetic control enters the world"); GrapnelProjectileProbe.hit(synthetic, target, false);
        require(value(player, bow, "streak") == 0, "An arrow not fired from the launcher cannot earn a streak");
        denyDamage.add(target.getUUID()); GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), target, true); denyDamage.clear();
        require(value(player, bow, "streak") == 0, "Cancelled arrow damage cannot earn a streak");
        Arrow older = fireBow(level, player, bow, area); ConfirmedArrow.sample(older);
        Arrow newer = fireBow(level, player, bow, area); ConfirmedArrow.sample(newer);
        GrapnelProjectileProbe.hit(newer, target, false); GrapnelProjectileProbe.hit(older, target, false);
        require(value(player, bow, "streak") == 1, "A late impact from an older overlapping shot cannot extend the streak");
        RangedWorkSkills.clearPlayer(player); ConfirmedArrow.clearPlayer(player.getUUID());
        for (int i = 0; i < 2; i++) {
            Arrow arrow = fireBow(level, player, bow, area); checkPrimary(level, player, arrow, target, theme);
            target.setDeltaMovement(Vec3.ZERO); GrapnelProjectileProbe.hit(arrow, target, false);
            if (RangedWearSkills.PUNCH.equals(theme)) require(target.getDeltaMovement().x > 0, "Missing Punch must keep its real effect");
        }
        require(value(player, bow, "streak") == 2 && !RangedWorkSkills.activate(player, true), "Two hits cannot arm the boost");
        GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), villager, false);
        require(value(player, bow, "streak") == 0, "A protected target cannot build a streak");
        for (int i = 0; i < 3; i++) GrapnelProjectileProbe.hit(fireBow(level, player, bow, area), target, false);
        require(RangedWorkSkills.activate(player, true) && present("BOOSTS", player), "Three real hits prepare one boost");
        player.getInventory().setItem(20, bow); player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        RangedWorkSkills.tickServer(level.getServer());
        require(player.getAttribute(Attributes.BLOCK_BREAK_SPEED).hasModifier(EnchantmentFusion.id("ranged_work_speed")), "Tool swap enables synced speed");
        level.setBlockAndUpdate(mine, Blocks.GLASS.defaultBlockState()); denyBlocks.add(mine);
        require(!player.gameMode.destroyBlock(mine) && present("BOOSTS", player), "Cancelled break must not spend the boost");
        denyBlocks.clear(); require(player.gameMode.destroyBlock(mine) && !present("BOOSTS", player), "One genuine break consumes the boost");
        require(!player.getAttribute(Attributes.BLOCK_BREAK_SPEED).hasModifier(EnchantmentFusion.id("ranged_work_speed")), "Consumed speed must be removed");
        ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        Arrow shot = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); checkPrimary(level, player, shot, target, theme);
        GrapnelProjectileProbe.hit(shot, target, false);
        require(value(player, crossbow, "streak") == 1, "A genuine crossbow has its own independent streak");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, String theme, BlockPos mine, Zombie target, Villager villager,
                                AABB area, Set<UUID> denyDamage, Set<BlockPos> denyBlocks, Map<BlockPos, BlockState> savedBlocks) throws Exception {
        ServerLevel level = player.level(); ItemStack tool = GrapnelProjectileProbe.fused(level, Items.DIAMOND_PICKAXE, pair);
        player.setItemSlot(EquipmentSlot.MAINHAND, tool); checkEfficiencyPrimary(player, tool, pair);
        require(!RangedWorkSkills.activate(player, false), "No real break cannot prepare a blade");
        level.setBlockAndUpdate(mine, Blocks.GLASS.defaultBlockState());
        ConfirmedMining.after(level, player, mine, level.getBlockState(mine));
        require(value(player, tool, "edges") == 0, "A naked event without an actual break cannot earn progress");
        denyBlocks.add(mine); require(!player.gameMode.destroyBlock(mine) && value(player, tool, "edges") == 0,
            "Cancelled mining cannot earn a blade"); denyBlocks.clear();
        require(player.gameMode.destroyBlock(mine) && value(player, tool, "edges") == 1, "One real break earns one edge");
        for (int i = 0; i < 4; i++) mine(player, mine);
        require(value(player, tool, "edges") == 3, "Mining edges saturate at three");
        require(RangedWorkSkills.activate(player, false) && present("ARMED", player), "The mined tool must actively prepare a shot");
        player.getInventory().setItem(20, tool); ItemStack bow = new ItemStack(Items.BOW); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Arrow absent = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        ConfirmedArrow.launched(absent, player, 0, bow); ConfirmedArrow.sample(absent);
        require(present("ARMED", player), "A tentative arrow never inserted into the world cannot spend the edge"); absent.discard();
        target.setPos(player.getX() + 5.0D, player.getEyeY() - 0.8D, player.getZ() + 1.0D);
        villager.setPos(player.getX() + 2.8D, player.getEyeY() - 0.8D, player.getZ() + 1.0D);
        float before = target.getHealth(), protectedHealth = villager.getHealth(); int ammo = player.getOffhandItem().getCount();
        Arrow protectedShot = fireBow(level, player, bow, area); ConfirmedArrow.sample(protectedShot);
        require(!present("ARMED", player), "Only an inserted real center arrow consumes the preparation");
        fly(protectedShot, 5); require(target.getHealth() == before && villager.getHealth() == protectedHealth,
            "A protected body must stop the virtual blade without harming itself or the target behind");
        require(player.getOffhandItem().getCount() == ammo - 1, "The actual bow consumes one arrow, not extra ammunition");
        protectedShot.discard(); villager.setPos(player.getX() + 4.0D, player.getY(), player.getZ() + 4.0D);
        // Re-arm using the same tool, then exercise the other genuine launcher and the side of its path.
        player.setItemSlot(EquipmentSlot.MAINHAND, tool); player.getInventory().setItem(20, ItemStack.EMPTY);
        mine(player, mine); require(RangedWorkSkills.activate(player, false), "A new real break can prepare another blade");
        player.getInventory().setItem(20, tool); ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); target.invulnerableTime = 0; target.clearFire(); target.setDeltaMovement(Vec3.ZERO);
        before = target.getHealth(); ammo = player.getOffhandItem().getCount();
        Arrow real = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); ConfirmedArrow.sample(real);
        require(!present("ARMED", player), "A real crossbow center arrow consumes the prepared blade");
        Set<UUID> launched = GrapnelProjectileProbe.arrowIds(level, area); fly(real, 5);
        require(target.getHealth() < before, "An eligible side target on the observed arrow path takes blade damage");
        if (RangedWearSkills.PUNCH.equals(theme)) require(target.getDeltaMovement().x > 0, "Punch applies to an accepted blade hit");
        if (RangedWearSkills.FLAME.equals(theme)) require(target.isOnFire(), "Flame applies only after accepted blade damage");
        require(player.getOffhandItem().getCount() == ammo - 1 && GrapnelProjectileProbe.arrowIds(level, area).equals(launched),
            "The virtual blade cannot create or consume extra ammunition"); real.discard();
        // A wall on the actual path cannot be bypassed by a later virtual slash.
        player.setItemSlot(EquipmentSlot.MAINHAND, tool); player.getInventory().setItem(20, ItemStack.EMPTY);
        mine(player, mine); require(RangedWorkSkills.activate(player, false), "A third real break can prepare a wall test");
        player.getInventory().setItem(20, tool); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        target.invulnerableTime = 0; before = target.getHealth(); Arrow wallShot = fireBow(level, player, bow, area);
        ConfirmedArrow.sample(wallShot); BlockPos wall = BlockPos.containing(wallShot.position().add(2.0D, 0.0D, 0.0D));
        require(savedBlocks.containsKey(wall), "Wall must be in disposable space"); level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        fly(wallShot, 5); require(target.getHealth() == before, "A wall stops both real path and the virtual blade");
        level.setBlockAndUpdate(wall, savedBlocks.get(wall)); wallShot.discard();
        // A cancelled virtual hit must not ignite or push a target, nor retry it along the same flight.
        player.setItemSlot(EquipmentSlot.MAINHAND, tool); player.getInventory().setItem(20, ItemStack.EMPTY);
        mine(player, mine); require(RangedWorkSkills.activate(player, false), "A real break prepares the cancellation test");
        player.getInventory().setItem(20, tool); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        target.clearFire(); target.setDeltaMovement(Vec3.ZERO); target.invulnerableTime = 0; before = target.getHealth();
        denyDamage.add(target.getUUID()); Arrow denied = fireBow(level, player, bow, area); ConfirmedArrow.sample(denied); fly(denied, 5);
        denyDamage.clear(); require(target.getHealth() == before && !target.isOnFire() && target.getDeltaMovement().lengthSqr() < 1.0E-8D,
            "A cancelled virtual hit must have no damage, fire or knockback"); denied.discard();
        // A copied or transferred source is not the tool that earned the preparation.
        player.setItemSlot(EquipmentSlot.MAINHAND, tool); player.getInventory().setItem(20, ItemStack.EMPTY);
        mine(player, mine); require(RangedWorkSkills.activate(player, false), "A later break prepares the identity test");
        player.getInventory().setItem(20, tool.copy()); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        RangedWorkSkills.tickServer(level.getServer()); require(!present("ARMED", player), "An equal copy cannot inherit another tool's progress");
    }
    private static void mine(ServerPlayer player, BlockPos pos) {
        player.level().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        require(player.gameMode.destroyBlock(pos), "A real legal fixture block must be removed");
    }
    private static void fly(Arrow arrow, int ticks) {
        arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0);
        for (int i = 0; i < ticks && arrow.isAlive(); i++) arrow.tick();
    }
    private static Arrow fireBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area) {
        Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
        player.stopUsingItem(); var found = level.getEntitiesOfClass(Arrow.class, area,
            arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one actual bow arrow must enter the world"); return found.getFirst();
    }
    private static void checkPrimary(ServerLevel level, ServerPlayer player, Arrow arrow, LivingEntity target, String theme) {
        if (RangedWearSkills.POWER.equals(theme)) {
            float value = EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), target, level.damageSources().arrow(arrow, player), 2.0F);
            require(value > 2.0F, "Missing Power must project the current data-pack effect");
        } else if (RangedWearSkills.FLAME.equals(theme)) require(arrow.isOnFire(), "Missing Flame must affect the real arrow");
    }
    private static void checkEfficiencyPrimary(ServerPlayer player, ItemStack tool, FusedPair pair) {
        var enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), pair.primaryId()).orElseThrow();
        var effects = enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES); int index = -1;
        for (int i = 0; i < effects.size(); i++) if (effects.get(i).attribute().equals(Attributes.MINING_EFFICIENCY)) { index = i; break; }
        require(index >= 0, "Efficiency primary must expose its real data-pack attribute");
        AttributeModifier vanilla = effects.get(index).getModifier(pair.primaryLevel(), EquipmentSlot.MAINHAND);
        Identifier id = EnchantmentFusion.id("primary_attribute/" + pair.primaryId().replace(':', '/') + "/mainhand/" + index);
        AttributeInstance attribute = player.getAttribute(Attributes.MINING_EFFICIENCY);
        AttributeInstance saved = new AttributeInstance(attribute.getAttribute(), ignored -> {}); saved.replaceFrom(attribute);
        ItemEnchantments enchants = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        try {
            tool.remove(DataComponents.ENCHANTMENTS); attribute.removeModifier(vanilla.id()); MeleeWorkSkills.tickServer(player.level().getServer());
            AttributeModifier projected = attribute.getModifier(id);
            require(projected != null && projected.amount() == vanilla.amount() && projected.operation() == vanilla.operation(),
                "Missing efficiency must use the existing shared primary projection");
            tool.set(DataComponents.ENCHANTMENTS, enchants); MeleeWorkSkills.tickServer(player.level().getServer());
            require(attribute.getModifier(id) == null, "Native efficiency cannot be applied twice");
        } finally { tool.set(DataComponents.ENCHANTMENTS, enchants); MeleeWorkSkills.clearPlayer(player); attribute.replaceFrom(saved); }
    }
    private static boolean present(String fieldName, ServerPlayer player) throws Exception {
        Field field = RangedWorkSkills.class.getDeclaredField(fieldName); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static int value(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field field = RangedWorkSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        Object raw = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (!(raw instanceof List<?> states)) return 0;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object entry = gear.get(state);
            var getStack = entry.getClass().getDeclaredMethod("stack"); getStack.setAccessible(true);
            if (getStack.invoke(entry) == stack) { Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.getInt(state); }
        }
        return 0;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean reverse) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_SWORD, Items.TRIDENT, Items.DIAMOND_HELMET)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean allowed = reverse ? item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE
                    : item == Items.BOW || item == Items.CROSSBOW;
                require(allowed ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both fusion paths must use the actual ordered action carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
