package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelChanneling;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelTridentFlight;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Prepared weather/collisions in the disposable world; spawned lightning is removed before it can tick. */
final class GrapnelChannelingProbe {
    private GrapnelChannelingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelChanneling.CHANNELING, 1, GrapnelSkills.LURE, 2);
        FusedPair impaling = FusedPair.clean(GrapnelImpact.IMPALING, 4, GrapnelSkills.LURE, 2);
        FusedPair sibling = FusedPair.clean(GrapnelChanneling.CHANNELING, 1, "minecraft:smite", 4);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SPEAR, Items.DIAMOND_SWORD, Items.MACE, Items.BOW, Items.DIAMOND_PICKAXE));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(16);
        Set<UUID> oldTridents = tridentIds(level, area), oldBolts = boltIds(level, area), denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> !denied.contains(target.getUUID()));
        BlockPos rod = origin.offset(4, 1, 0), roof = origin.offset(3, 4, 0);
        require(level.hasChunkAt(rod) && level.isInWorldBounds(rod) && level.hasChunkAt(roof) && level.isInWorldBounds(roof),
            "Disposable rod and roof positions must be loaded and in bounds");
        BlockState oldRod = level.getBlockState(rod), oldRoof = level.getBlockState(roof);
        require(oldRod.isAir() && oldRoof.isAir() && level.canHaveWeather(), "Disposable fixture needs empty space and overworld weather");
        try (Weather weather = new Weather(level); SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            LivingEntity target = f.spawn(false, 2.8D, 0);
            Villager protectedVictim = f.spawn(new Villager(EntityType.VILLAGER, level), 2.8D, 4);
            require(target.blockPosition().above(4).equals(roof) && level.canSeeSky(target.blockPosition()), "Fixture target needs open sky");
            player.setYRot(-90); player.setXRot(15); GrapnelSkills.clearPlayer(player.getUUID());

            weather.clear(); ThrownTrident clear = throwTrident(f, pair, null, false, area);
            hit(clear, target); require(target.getHealth() < 200 && mark(player) == null && takeBolts(level, area, oldBolts) == 0,
                "Clear-weather damage is not evidence of a Channeling strike"); clear.discard();
            LightningBolt unrelated = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            unrelated.setVisualOnly(true); unrelated.setCause(player); unrelated.setPos(target.position());
            require(level.addFreshEntity(unrelated), "Harmless unrelated lightning control must enter the disposable world");
            target.setHealth(200); ThrownTrident nearBolt = throwTrident(f, pair, null, true, area); hit(nearBolt, target);
            require(mark(player) == null && takeBolts(level, area, oldBolts) == 1,
                "Even nearby player-attributed lightning is not evidence of this trident's Channeling effect"); nearBolt.discard();
            weather.thunder(); target.setHealth(200);
            ItemStack copy = weapon(level, pair, null, false); ThrownTrident synthetic = new ThrownTrident(level, player, copy);
            require(level.addFreshEntity(synthetic), "Synthetic control must enter the world"); hit(synthetic, target);
            require(takeBolts(level, area, oldBolts) == 1 && mark(player) == null,
                "An untracked trident keeps its missing primary but cannot create a new mark"); synthetic.discard();

            ThrownTrident protectedThrow = throwTrident(f, pair, null, true, area);
            require(GrapnelChanneling.beginEntity(protectedThrow, protectedVictim) == null && mark(player) == null,
                "Protected villagers cannot become lure targets; the fixture does not hurt them"); protectedThrow.discard();
            ThrownTrident cancelled = throwTrident(f, pair, null, true, area);
            denied.add(target.getUUID()); hit(cancelled, target); denied.clear();
            require(mark(player) == null && takeBolts(level, area, oldBolts) == 0, "Cancelled damage cannot summon or mark"); cancelled.discard();
            level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            ThrownTrident covered = throwTrident(f, pair, null, true, area); target.setHealth(200); hit(covered, target);
            require(mark(player) == null && takeBolts(level, area, oldBolts) == 0, "An overhead roof prevents the default strike");
            covered.discard(); level.setBlockAndUpdate(roof, oldRoof);

            for (boolean nativePrimary : new boolean[]{true, false}) {
                target.setHealth(200); ThrownTrident actual = throwTrident(f, pair, null, nativePrimary, area); hit(actual, target);
                require(takeBolts(level, area, oldBolts) == 1 && target.getHealth() < 200
                    && target.getUUID().equals(markedTarget(player)) && GrapnelSkills.hasEquipped(player),
                    "A real native or projected Channeling strike marks its damaged eligible victim once");
                target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && mark(player) == null,
                    "The original live trident allows one actual pull"); actual.discard();
            }
            ThrownTrident both = throwTrident(f, pair, impaling, false, area);
            require(GrapnelTridentFlight.valid(player, both, both.getWeaponItem(), pair)
                && GrapnelTridentFlight.valid(player, both, both.getWeaponItem(), impaling),
                "One real release preserves both distinct trident fusion sources"); both.discard();
            ThrownTrident competing = throwTrident(f, pair, sibling, false, area); target.setHealth(200); hit(competing, target);
            require(takeBolts(level, area, oldBolts) == 1 && mark(player) == null && MeleeLightningSkills.hasEquipped(player),
                "Both Channeling families are active, but they summon once and ambiguous sources do not claim a lure mark");
            competing.discard(); MeleeLightningSkills.clearPlayer(player.getUUID());

            level.setBlockAndUpdate(rod, Blocks.STONE.defaultBlockState());
            ThrownTrident stone = throwTrident(f, pair, null, false, area); blockHit(stone, rod);
            require(mark(player) == null && takeBolts(level, area, oldBolts) == 0, "Default Channeling does not strike ordinary stone"); stone.discard();
            level.setBlockAndUpdate(rod, Blocks.LIGHTNING_ROD.defaultBlockState());
            ThrownTrident sharedRod = throwTrident(f, pair, sibling, false, area); blockHit(sharedRod, rod);
            require(takeBolts(level, area, oldBolts) == 1 && mark(player) == null,
                "Sibling block effects summon once and cannot claim an ambiguous position"); sharedRod.discard();
            ThrownTrident rodThrow = throwTrident(f, pair, null, false, area); blockHit(rodThrow, rod);
            require(takeBolts(level, area, oldBolts) == 1 && Vec3.atCenterOf(rod).equals(markedPosition(player))
                && GrapnelSkills.hasEquipped(player), "A genuine lightning-rod strike marks the actual block location");
            target.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(15);
            require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && mark(player) == null,
                "An aimed eligible enemy can be pulled once from a valid position mark"); rodThrow.discard();
            ThrownTrident lost = throwTrident(f, pair, null, true, area); blockHit(lost, rod);
            require(takeBolts(level, area, oldBolts) == 1 && markedPosition(player) != null, "A later rod strike may mark again");
            lost.discard(); require(!GrapnelSkills.activate(player), "Losing the original projectile invalidates a position mark");
        } finally {
            denied.clear(); GrapnelSkills.clearPlayer(player.getUUID()); MeleeLightningSkills.clearPlayer(player.getUUID());
            level.setBlockAndUpdate(rod, oldRod); level.setBlockAndUpdate(roof, oldRoof);
            for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, area))
                if (!oldTridents.contains(trident.getUUID())) trident.discard();
            takeBolts(level, area, oldBolts);
        }
    }
    private static ItemStack weapon(ServerLevel level, FusedPair pair, FusedPair second, boolean nativePrimary) {
        ItemStack stack = new ItemStack(Items.TRIDENT); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        if (second != null) stack.set(FusionDataComponents.FUSED_PAIRS,
            stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).append(second));
        if (!nativePrimary) stack.remove(DataComponents.ENCHANTMENTS);
        return stack;
    }
    private static ThrownTrident throwTrident(SharedMeleeFixture f, FusedPair pair, FusedPair second, boolean nativePrimary, AABB area) {
        ItemStack stack = weapon(f.level, pair, second, nativePrimary); f.player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        Set<UUID> before = tridentIds(f.level, area); f.player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((TridentItem) stack.getItem()).releaseUsing(stack, f.level, f.player, stack.getUseDuration(f.player) - 10),
            "The actual trident release must succeed"); f.player.stopUsingItem();
        var thrown = f.level.getEntitiesOfClass(ThrownTrident.class, area, p -> !before.contains(p.getUUID()) && p.getOwner() == f.player);
        require(thrown.size() == 1, "Exactly one actual trident must be created"); return thrown.getFirst();
    }
    private static void hit(ThrownTrident projectile, LivingEntity target) throws Exception {
        target.invulnerableTime = 0;
        Method method = ThrownTrident.class.getDeclaredMethod("onHitEntity", EntityHitResult.class); method.setAccessible(true);
        method.invoke(projectile, new EntityHitResult(target));
    }
    private static void blockHit(ThrownTrident projectile, BlockPos block) throws Exception {
        Method method = AbstractArrow.class.getDeclaredMethod("onHitBlock", BlockHitResult.class); method.setAccessible(true);
        method.invoke(projectile, new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false));
    }
    private static Object mark(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static UUID markedTarget(ServerPlayer player) throws Exception {
        Object mark = mark(player); if (mark == null || !mark.getClass().getSimpleName().equals("TargetMark")) return null;
        Method method = mark.getClass().getDeclaredMethod("target"); method.setAccessible(true); return (UUID) method.invoke(mark);
    }
    private static Vec3 markedPosition(ServerPlayer player) throws Exception {
        Object mark = mark(player); if (mark == null || !mark.getClass().getSimpleName().equals("PositionMark")) return null;
        Method method = mark.getClass().getDeclaredMethod("origin"); method.setAccessible(true); return (Vec3) method.invoke(mark);
    }
    private static Set<UUID> tridentIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (ThrownTrident e : level.getEntitiesOfClass(ThrownTrident.class, area)) ids.add(e.getUUID()); return ids;
    }
    private static Set<UUID> boltIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (LightningBolt e : level.getEntitiesOfClass(LightningBolt.class, area)) ids.add(e.getUUID()); return ids;
    }
    private static int takeBolts(ServerLevel level, AABB area, Set<UUID> existing) {
        int count = 0; for (LightningBolt bolt : level.getEntitiesOfClass(LightningBolt.class, area))
            if (bolt.isAlive() && !existing.contains(bolt.getUUID())) { count++; bolt.discard(); }
        return count;
    }
    private static final class Weather implements AutoCloseable {
        final ServerLevel level; final Field[] fields = new Field[4]; final float[] old = new float[4];
        Weather(ServerLevel level) throws ReflectiveOperationException {
            this.level = level; String[] names = {"oThunderLevel", "thunderLevel", "oRainLevel", "rainLevel"};
            for (int i = 0; i < names.length; i++) { fields[i] = Level.class.getDeclaredField(names[i]); fields[i].setAccessible(true); old[i] = fields[i].getFloat(level); }
        }
        void clear() { level.setRainLevel(0); level.setThunderLevel(0); }
        void thunder() { level.setRainLevel(1); level.setThunderLevel(1); }
        @Override public void close() throws IllegalAccessException { for (int i = 0; i < fields.length; i++) fields[i].setFloat(level, old[i]); }
    }
}
