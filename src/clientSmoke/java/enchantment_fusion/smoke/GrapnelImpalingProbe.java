package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Real trident release and damage methods; the target collision itself is prepared directly. */
final class GrapnelImpalingProbe {
    private GrapnelImpalingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelImpact.IMPALING, 4, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SPEAR, Items.DIAMOND_SWORD, Items.MACE, Items.BOW, Items.DIAMOND_PICKAXE));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(16); Set<UUID> existing = ids(level, area);
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> !denied.contains(target.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            Guardian target = f.spawn(new Guardian(EntityType.GUARDIAN, level), 2.8, 0);
            Method moving = Guardian.class.getDeclaredMethod("setMoving", boolean.class); moving.setAccessible(true); moving.invoke(target, true);
            LivingEntity wrong = f.spawn(false, 2.8, 4);
            for (boolean nativePrimary : new boolean[]{true, false}) {
                ItemStack trident = f.equip(Items.TRIDENT, pair); if (!nativePrimary) trident.remove(DataComponents.ENCHANTMENTS);
                detect(player); GrapnelSkills.clearPlayer(player.getUUID());
                f.hit(wrong, true); require(marked(player) == null, "A target without an Impaling bonus cannot be marked");
                denied.add(target.getUUID()); f.hit(target, true); denied.clear();
                require(marked(player) == null, "Cancelled melee damage cannot mark");
                target.setHealth(200); f.hit(target, true);
                require(target.getUUID().equals(marked(player)) && target.getHealth() < 200,
                    "A real hit with an effective Impaling bonus must mark the eligible aquatic enemy");
                float singleRankLoss = 200 - target.getHealth();
                player.setItemSlot(EquipmentSlot.OFFHAND, trident); player.setItemSlot(EquipmentSlot.MAINHAND, trident.copy());
                require(!GrapnelSkills.activate(player), "A different but equal trident cannot spend the melee mark");
                player.setItemSlot(EquipmentSlot.MAINHAND, trident); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && marked(player) == null,
                    "The same held trident must pull once");
                if (!nativePrimary) {
                    FusedEnchantmentList pairs = trident.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                    trident.set(FusionDataComponents.FUSED_PAIRS, pairs.append(FusedPair.clean(GrapnelImpact.IMPALING, 5, GrapnelSkills.LURE, 2)));
                    target.setHealth(200); f.hit(target, true);
                    require(marked(player) == null && 200 - target.getHealth() > singleRankLoss + 1,
                        "Ambiguous old sources do not mark, but the highest missing Impaling primary still applies");
                }
                ItemStack copy = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(copy, pair, level.registryAccess());
                if (!nativePrimary) copy.remove(DataComponents.ENCHANTMENTS);
                ThrownTrident synthetic = new ThrownTrident(level, player, copy);
                require(level.addFreshEntity(synthetic), "Synthetic control must enter the world");
                target.setHealth(200); hit(synthetic, target); synthetic.discard();
                require(marked(player) == null && target.getHealth() < 191,
                    "An untracked copy preserves its Impaling damage, but cannot create a new mark");
                ThrownTrident cancelled = throwTrident(f, pair, nativePrimary, area);
                denied.add(target.getUUID()); hit(cancelled, target); denied.clear(); cancelled.discard();
                require(marked(player) == null, "Cancelled thrown damage cannot mark");
                ThrownTrident actual = throwTrident(f, pair, nativePrimary, area);
                target.setHealth(200); target.setPos(player.getX() + 2.8, player.getY(), player.getZ()); hit(actual, target);
                require(target.getUUID().equals(marked(player)) && GrapnelSkills.hasEquipped(player),
                    "A real thrown trident with effective Impaling must mark its actual target");
                target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && marked(player) == null,
                    "While the original trident remains in the world, recall pulls once without copying it");
                actual.discard();
                ThrownTrident lost = throwTrident(f, pair, nativePrimary, area);
                target.setHealth(200); hit(lost, target);
                require(target.getUUID().equals(marked(player)), "A later real throw may create a fresh mark");
                lost.discard(); require(!GrapnelSkills.activate(player), "Losing the original projectile invalidates its mark");
            }
        } finally {
            denied.clear(); GrapnelSkills.clearPlayer(player.getUUID());
            for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, area))
                if (!existing.contains(trident.getUUID())) trident.discard();
            detect(player);
        }
    }
    private static ThrownTrident throwTrident(SharedMeleeFixture f, FusedPair pair, boolean nativePrimary, AABB area) {
        ItemStack stack = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(stack, pair, f.level.registryAccess());
        if (!nativePrimary) stack.remove(DataComponents.ENCHANTMENTS);
        f.player.setItemSlot(EquipmentSlot.MAINHAND, stack); Set<UUID> before = ids(f.level, area);
        f.player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((TridentItem) stack.getItem()).releaseUsing(stack, f.level, f.player, stack.getUseDuration(f.player) - 10),
            "Actual trident release must succeed");
        f.player.stopUsingItem();
        var thrown = f.level.getEntitiesOfClass(ThrownTrident.class, area, p -> !before.contains(p.getUUID()) && p.getOwner() == f.player);
        require(thrown.size() == 1, "Exactly one real trident must be created"); return thrown.getFirst();
    }
    private static void hit(ThrownTrident projectile, LivingEntity target) throws Exception {
        target.invulnerableTime = 0;
        Method method = ThrownTrident.class.getDeclaredMethod("onHitEntity", EntityHitResult.class); method.setAccessible(true);
        method.invoke(projectile, new EntityHitResult(target));
    }
    private static Set<UUID> ids(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>(); for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, area)) result.add(trident.getUUID()); return result;
    }
    private static UUID marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method target = mark.getClass().getDeclaredMethod("target"); target.setAccessible(true); return (UUID) target.invoke(mark);
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
}
