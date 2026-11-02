package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelLoyalty;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Disposable wall and simulated charge/ticks; no fixture result is a full gameplay acceptance. */
final class GrapnelLoyaltyProbe {
    private GrapnelLoyaltyProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelLoyalty.LOYALTY, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SPEAR, Items.DIAMOND_SWORD, Items.BOW, Items.FISHING_ROD, Items.DIAMOND_BOOTS));
        ServerLevel level = player.level(); Inventory inventory = player.getInventory();
        ItemStack[] saved = new ItemStack[inventory.getContainerSize()];
        for (int i = 0; i < saved.length; i++) saved[i] = inventory.getItem(i);
        int selected = inventory.getSelectedSlot(); AABB area = new AABB(origin).inflate(16);
        Set<UUID> existing = tridentIds(level, area); Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (int y = 0; y <= 3; y++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = origin.offset(5, y, z); BlockState old = level.getBlockState(pos);
            require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && old.isAir(), "Temporary test wall needs loaded air");
            wall.put(pos, old);
        }
        try (SharedMeleeFixture fixture = new SharedMeleeFixture(player, origin)) {
            clearInventory(inventory); inventory.setSelectedSlot(0); player.setYRot(-90); player.setXRot(0);
            for (BlockPos pos : wall.keySet()) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            LivingEntity target = fixture.spawn(false, 4, 5);
            Villager villager = fixture.spawn(new Villager(EntityType.VILLAGER, level), 2, 5);

            // Just toggling noPhysics, before any real return tick, is not a Loyalty return.
            clear(player, inventory); ThrownTrident flag = throwHeld(fixture, pair, true, area);
            flag.setNoPhysics(true); flag.playerTouch(player);
            require(!marked(player), "No-physics alone cannot earn a return mark"); flag.discard();

            // Calling releaseUsing with a made-up elapsed time still spawns a normal trident, but not evidence of held use.
            clear(player, inventory); ThrownTrident direct = throwDirect(fixture, pair, area);
            require(touchAfterReturn(direct, player), "Direct-release control should still return normally");
            require(direct.isRemoved() && !marked(player), "A fabricated elapsed-use argument cannot earn a mark");

            // A real item manually picked up from the ground before returning is not a Loyalty return.
            clear(player, inventory); ThrownTrident grounded = throwHeld(fixture, pair, true, area);
            Method hitBlock = AbstractArrow.class.getDeclaredMethod("onHitBlock", BlockHitResult.class); hitBlock.setAccessible(true);
            BlockPos contact = origin.offset(5, 1, 0);
            hitBlock.invoke(grounded, new BlockHitResult(Vec3.atCenterOf(contact), Direction.WEST, contact, false));
            grounded.setPos(player.position()); grounded.shakeTime = 0; grounded.playerTouch(player);
            require(grounded.isRemoved() && !marked(player), "A manually picked up grounded trident cannot mark before flight back");

            // An equal pre-existing stack must not be mistaken for the one actually inserted.
            for (boolean nativePrimary : new boolean[]{true, false}) {
                clear(player, inventory); ThrownTrident trident = throwHeld(fixture, pair, nativePrimary, area);
                ItemStack expected = trident.getWeaponItem().copy(); ItemStack equalTwin = expected.copy(); inventory.setItem(1, equalTwin);
                require(touchAfterReturn(trident, player) && trident.isRemoved(), "A real native or projected Loyalty trident must return and be picked up");
                ItemStack returned = inventory.getItem(0);
                require(marked(player) && returned != equalTwin && ItemStack.matches(returned, expected)
                    && inventory.getItem(1) == equalTwin && GrapnelSkills.hasEquipped(player),
                    "The newly inserted original components, not the existing equal trident, own the mark");
                inventory.setItem(0, returned.copy());
                require(!GrapnelSkills.activate(player), "A copied returned item cannot spend the original object's mark");
            }

            // A full inventory cannot claim a successful pickup; after a slot opens the same entity may return normally.
            clear(player, inventory); ThrownTrident full = throwHeld(fixture, pair, false, area);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            require(touchAfterReturn(full, player) && full.isAlive() && !marked(player), "Full inventory must leave the original entity without a mark");
            inventory.setItem(0, ItemStack.EMPTY); full.playerTouch(player);
            require(full.isRemoved() && marked(player), "A later successful pickup of that same returning entity may mark");

            // Two Loyalty-bearing fused sources make attribution ambiguous but do not disable vanilla return.
            clear(player, inventory); ItemStack ambiguous = weapon(level, pair, false);
            ambiguous.set(FusionDataComponents.FUSED_PAIRS, ambiguous.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
                .append(FusedPair.clean(GrapnelLoyalty.LOYALTY, 1, "minecraft:sharpness", 1)));
            ThrownTrident multiple = throwHeld(fixture, ambiguous, area);
            require(touchAfterReturn(multiple, player) && multiple.isRemoved() && !marked(player),
                "Multiple possible Loyalty sources retain normal return but cannot claim a mark");

            // The real returned object allows one safe pull; a protected body in the ray blocks it without consumption.
            clear(player, inventory); ThrownTrident usable = throwHeld(fixture, pair, true, area);
            require(touchAfterReturn(usable, player) && usable.isRemoved() && marked(player), "A real return should prepare an active mark");
            for (var entry : wall.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            target.setPos(player.getX() + 4, player.getY(), player.getZ());
            villager.setPos(player.getX() + 2, player.getY(), player.getZ());
            require(!GrapnelSkills.activate(player) && marked(player), "Protected villagers block aim without spending the mark");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 5); target.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && !marked(player),
                "The original returned trident pulls one eligible enemy once");
            require(!GrapnelSkills.activate(player), "A consumed position mark cannot be reused");
        } finally {
            GrapnelSkills.clearPlayer(player.getUUID()); player.stopUsingItem();
            for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, area)) if (!existing.contains(trident.getUUID())) trident.discard();
            for (var entry : wall.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < saved.length; i++) inventory.setItem(i, saved[i]);
            inventory.setSelectedSlot(selected);
        }
    }
    private static ItemStack weapon(ServerLevel level, FusedPair pair, boolean nativePrimary) {
        ItemStack stack = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("return-identity-fixture"));
        if (!nativePrimary) stack.remove(DataComponents.ENCHANTMENTS);
        return stack;
    }
    private static ThrownTrident throwHeld(SharedMeleeFixture fixture, FusedPair pair, boolean nativePrimary, AABB area) throws Exception {
        return throwHeld(fixture, weapon(fixture.level, pair, nativePrimary), area);
    }
    private static ThrownTrident throwHeld(SharedMeleeFixture fixture, ItemStack stack, AABB area) throws Exception {
        ServerPlayer player = fixture.player; player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        Set<UUID> before = tridentIds(fixture.level, area);
        require(((TridentItem) stack.getItem()).use(fixture.level, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
            "The real trident use must begin");
        Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining"); remaining.setAccessible(true);
        remaining.setInt(player, stack.getUseDuration(player) - 10); player.releaseUsingItem();
        return oneTrident(fixture.level, area, before, player);
    }
    private static ThrownTrident throwDirect(SharedMeleeFixture fixture, FusedPair pair, AABB area) {
        ServerPlayer player = fixture.player; ItemStack stack = weapon(fixture.level, pair, true);
        player.setItemSlot(EquipmentSlot.MAINHAND, stack); Set<UUID> before = tridentIds(fixture.level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((TridentItem) stack.getItem()).releaseUsing(stack, fixture.level, player, stack.getUseDuration(player) - 10),
            "The direct-release control should still follow the ordinary engine path");
        player.stopUsingItem(); return oneTrident(fixture.level, area, before, player);
    }
    private static boolean touchAfterReturn(ThrownTrident trident, ServerPlayer player) {
        for (int i = 0; i < 150 && trident.isAlive(); i++) {
            trident.tick();
            if (trident.isNoPhysics() && trident.clientSideReturnTridentTickCount > 0 && trident.shakeTime <= 0
                && player.getBoundingBox().inflate(1, .5, 1).intersects(trident.getBoundingBox())) {
                trident.playerTouch(player); return true;
            }
        }
        return false;
    }
    private static Set<UUID> tridentIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>();
        for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, area)) result.add(trident.getUUID());
        return result;
    }
    private static ThrownTrident oneTrident(ServerLevel level, AABB area, Set<UUID> before, ServerPlayer owner) {
        var found = level.getEntitiesOfClass(ThrownTrident.class, area, trident -> !before.contains(trident.getUUID()) && trident.getOwner() == owner);
        require(found.size() == 1, "Exactly one actual projectile must enter the disposable world"); return found.getFirst();
    }
    private static void clear(ServerPlayer player, Inventory inventory) {
        GrapnelSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); clearInventory(inventory);
    }
    private static void clearInventory(Inventory inventory) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
}
