package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/** Four candidate probes use real melee and trident settlement; aim and activation are simulated. */
final class MeleeLightningProbe {
    private MeleeLightningProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean reverse) throws Exception {
        FusedPair pair = FusedPair.clean(attack, 4, MeleeLightningSkills.CHANNELING, 1); if (reverse) pair = pair.flipped();
        if (reverse) SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        else SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE),
            List.of(Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW));
        MeleeLightningSkills.clearPlayer(player.getUUID()); Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(16); Set<UUID> existing = ids(level, area);
        BlockPos roof = origin.offset(2, 4, 0);
        require(!reverse || level.hasChunkAt(roof) && level.isInWorldBounds(roof), "Fixture roof must be loaded and in bounds");
        BlockState oldRoof = reverse ? level.getBlockState(roof) : Blocks.AIR.defaultBlockState();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            boolean bane = MeleeAttackThemes.BANE.equals(attack);
            LivingEntity target = f.spawn(bane, 2, 0), other = f.spawn(bane, 4, 0), wrong = f.spawn(!bane, 2, 3);
            if (reverse) {
                require(level.hasChunkAt(roof) && level.isInWorldBounds(roof) && oldRoof.isAir(), "Fixture roof must be empty and loaded");
                // Keep vanilla Channeling from summoning a real lightning bolt even if the disposable world is thundering.
                level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
                reverse(f, pair, target, wrong, denied, area);
            } else forward(f, pair, target, other, wrong, denied);
        } finally {
            denied.clear(); MeleeLightningSkills.clearPlayer(player.getUUID());
            if (reverse) level.setBlockAndUpdate(roof, oldRoof);
            for (ThrownTrident projectile : level.getEntitiesOfClass(ThrownTrident.class, area))
                if (!existing.contains(projectile.getUUID())) projectile.discard();
        }
    }
    private static void forward(SharedMeleeFixture f, FusedPair pair, LivingEntity target, LivingEntity other,
                                LivingEntity wrong, Set<UUID> denied) {
        f.equip(Items.DIAMOND_AXE, pair);
        f.hit(target, false); f.hit(target, false); require(other.getHealth() == 200, "Partial hits cannot create a full-hit mark");
        denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        f.hit(wrong, true); require(other.getHealth() == 200, "Cancelled or wrong-category hits cannot create an electric mark");
        f.hit(target, true); denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        require(other.getHealth() == 200, "Cancelled follow-up cannot discharge the mark");
        f.hit(wrong, true); f.hit(target, true);
        require(other.getHealth() < 200, "A second real matching hit must arc into a nearby matching enemy");
        other.setHealth(200); other.invulnerableTime = 0; f.hit(target, false);
        require(other.getHealth() == 200, "A consumed electric mark cannot arc twice");
    }
    private static void reverse(SharedMeleeFixture f, FusedPair pair, LivingEntity target, LivingEntity wrong,
                                Set<UUID> denied, AABB area) throws Exception {
        ItemStack copied = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(copied, pair, f.level.registryAccess());
        ThrownTrident synthetic = new ThrownTrident(f.level, f.player, copied);
        require(f.level.addFreshEntity(synthetic), "Synthetic control must enter the world"); hit(synthetic, target);
        require(!MeleeLightningSkills.hasEquipped(f.player), "A synthetic copied projectile without real release cannot establish a trace");
        synthetic.discard(); target.setHealth(200);
        ThrownTrident cancelled = throwTrident(f, pair, area); denied.add(target.getUUID()); hit(cancelled, target); denied.clear();
        require(!MeleeLightningSkills.hasEquipped(f.player), "Cancelled trident damage cannot leave a trace"); cancelled.discard();
        ThrownTrident projectile = throwTrident(f, pair, area); hit(projectile, target);
        require(MeleeLightningSkills.hasEquipped(f.player), "A real thrown trident's valid hit leaves an active trace");
        f.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        target.setHealth(200); f.hit(target, true); float baseline = 200 - target.getHealth();
        require(baseline > 0 && MeleeLightningSkills.activate(f.player), "The player may prepare the nearby trace");
        denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        require(!MeleeLightningSkills.activate(f.player), "Cancellation cannot consume a prepared trace");
        f.hit(wrong, true); require(!MeleeLightningSkills.activate(f.player), "Wrong-category hit cannot consume the trace");
        target.setHealth(200); f.hit(target, true);
        require(200 - target.getHealth() > baseline + 1 && !MeleeLightningSkills.hasEquipped(f.player),
            "A real matching follow-up adds the trace to the same damage settlement exactly once");
        ThrownTrident lost = throwTrident(f, pair, area); target.setHealth(200); hit(lost, target);
        require(MeleeLightningSkills.hasEquipped(f.player), "A later real throw may leave a new trace");
        lost.discard(); require(!MeleeLightningSkills.hasEquipped(f.player), "Removing the actual trident invalidates its trace");
    }
    private static ThrownTrident throwTrident(SharedMeleeFixture f, FusedPair pair, AABB area) {
        ItemStack weapon = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(weapon, pair, f.level.registryAccess());
        f.player.setItemSlot(EquipmentSlot.MAINHAND, weapon); Set<UUID> before = ids(f.level, area);
        f.player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((TridentItem) weapon.getItem()).releaseUsing(weapon, f.level, f.player, weapon.getUseDuration(f.player) - 10),
            "The actual trident release must succeed");
        f.player.stopUsingItem();
        var thrown = f.level.getEntitiesOfClass(ThrownTrident.class, area,
            p -> !before.contains(p.getUUID()) && p.getOwner() == f.player);
        require(thrown.size() == 1, "Exactly one real trident entity must be created"); return thrown.getFirst();
    }
    private static void hit(ThrownTrident trident, LivingEntity target) throws Exception {
        target.invulnerableTime = 0;
        Method onHit = ThrownTrident.class.getDeclaredMethod("onHitEntity", EntityHitResult.class); onHit.setAccessible(true);
        onHit.invoke(trident, new EntityHitResult(target));
    }
    private static Set<UUID> ids(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>(); for (ThrownTrident p : level.getEntitiesOfClass(ThrownTrident.class, area)) result.add(p.getUUID()); return result;
    }
}
