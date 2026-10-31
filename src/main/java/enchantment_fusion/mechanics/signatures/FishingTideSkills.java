package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Directional fishing choices. Real vanilla loot is generated once; the virtual school owns no items. */
public final class FishingTideSkills {
    public static final String LUCK = "minecraft:luck_of_the_sea", LURE = "minecraft:lure";
    public static final String CHOICE = "tidal_double_catch", SCHOOL = "raging_hook";
    private static final int MAX_SCHOOLS = 256;
    private static final class Flight {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack rod;
        final FusedPair pair;
        int biteTicks;
        boolean bite, extended, schoolSpent, schoolMade;
        Flight(ServerPlayer owner, ItemStack rod, FusedPair pair) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.rod = rod; this.pair = pair;
        }
    }
    private static final class School {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack rod;
        final FusedPair pair;
        final long expires;
        Vec3 position, direction;
        School(ServerPlayer player, ItemStack rod, FusedPair pair, Vec3 position, long expires) {
            this.owner = player.getUUID(); this.dimension = player.level().dimension(); this.rod = rod;
            this.pair = pair; this.position = position; this.expires = expires;
            double angle = Math.floorMod(player.getUUID().hashCode(), 360) * Math.PI / 180.0D;
            this.direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }
    }
    // Fishing hooks do not persist in vanilla; weak keys avoid retaining an unloaded hook.
    private static final Map<FishingHook, Flight> FLIGHTS = new WeakHashMap<>();
    private static final Map<UUID, School> SCHOOLS = new HashMap<>();
    private FishingTideSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        if (pair == null) return false;
        return LUCK.equals(pair.primaryId()) && LURE.equals(pair.secondaryId()) && CHOICE.equals(signature)
            || LURE.equals(pair.primaryId()) && LUCK.equals(pair.secondaryId()) && SCHOOL.equals(signature);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && carriers.contains(FusionCarrier.FISHING_ROD);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack rod) {
        if (rod == null || rod.isEmpty() || !supportsCarrier(FusionCarrier.classify(rod))) return null;
        for (FusedPair pair : rod.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, rod, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    /** Called after the actual rod use creates this owner's vanilla bobber, not by guessing the hand. */
    public static void bind(FishingHook hook, ServerPlayer owner, ItemStack rod) {
        FusedPair pair = selected(rod);
        if (pair != null && hook.getOwner() == owner) FLIGHTS.put(hook, new Flight(owner, rod, pair));
    }
    private static @Nullable ServerPlayer valid(FishingHook hook, @Nullable Flight flight) {
        if (flight == null || !(hook.getOwner() instanceof ServerPlayer player) || !player.isAlive()
            || player.isSpectator() || hook.level() != player.level() || !flight.owner.equals(player.getUUID())
            || !flight.dimension.equals(player.level().dimension()) || player.fishing != hook
            || (player.getMainHandItem() != flight.rod && player.getOffhandItem() != flight.rod)
            || !flight.pair.equals(selected(flight.rod))) return null;
        return player;
    }
    public static int beforeFishingTick(FishingHook hook, int nibble) {
        Flight flight = FLIGHTS.get(hook);
        if (valid(hook, flight) == null || flight == null || !LUCK.equals(flight.pair.primaryId())) return nibble;
        if (flight.bite && nibble == 1 && !flight.extended) {
            flight.extended = true;
            return nibble + FusionConfigStore.signatureInt(CHOICE, "bite_extension_ticks", 20, 0, 100);
        }
        return nibble;
    }
    /** Called after one vanilla bite/wait update; return a shortened real waiting counter if a school was followed. */
    public static int afterFishingTick(FishingHook hook, int beforeNibble, int nibble, int waiting) {
        Flight flight = FLIGHTS.get(hook);
        ServerPlayer player = valid(hook, flight);
        if (player == null || flight == null || !(hook.level() instanceof ServerLevel level)) return waiting;
        if (LUCK.equals(flight.pair.primaryId())) {
            if (beforeNibble <= 0 && nibble > 0) {
                flight.bite = true; flight.extended = false; flight.biteTicks = 0;
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.tidal_bite"), true);
                level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY(), hook.getZ(), 12, 0.35D, 0.05D, 0.35D, 0.02D);
            } else if (flight.bite && nibble > 0) {
                flight.biteTicks++;
                if (flight.biteTicks % 8 == 0) level.sendParticles(ParticleTypes.FISHING,
                    hook.getX(), hook.getY(), hook.getZ(), 5, 0.28D, 0.05D, 0.28D, 0.01D);
            } else if (nibble <= 0) { flight.bite = false; flight.biteTicks = 0; }
        } else if (!flight.schoolSpent && waiting > 0 && nibble <= 0) {
            School school = SCHOOLS.get(player.getUUID());
            if (school != null && school.rod == flight.rod && school.pair.equals(flight.pair)
                && school.dimension.equals(level.dimension()) && level.getGameTime() < school.expires
                && hook.position().distanceToSqr(school.position) <= 36.0D
                && player.position().distanceToSqr(school.position) <= 100.0D && isWater(level, hook.position())) {
                int reduced = FishingTideMath.shortenedWait(waiting, flight.pair.secondaryLevel(),
                    FusionConfigStore.signatureInt(SCHOOL, "school_base_reduction_percent", 15, 0, 75),
                    FusionConfigStore.signatureInt(SCHOOL, "school_reduction_per_level_percent", 5, 0, 25),
                    FusionConfigStore.signatureInt(SCHOOL, "school_max_reduction_percent", 50, 0, 75));
                if (reduced < waiting) {
                    flight.schoolSpent = true; SCHOOLS.remove(player.getUUID());
                    CompendiumDiscoveryTracker.recordDiscovery(player, LURE, LUCK);
                    player.sendSystemMessage(Component.translatable("message.enchantment_fusion.tidal_school_followed"), true);
                    return reduced;
                }
            }
        }
        return waiting;
    }
    /** Amend vanilla LootParams luck only during the actual reel; no extra draw or duplicated items. */
    public static float lootLuck(FishingHook hook, ItemStack rod, float original, int nibble) {
        Flight flight = FLIGHTS.get(hook);
        ServerPlayer owner = valid(hook, flight);
        if (owner == null || flight == null || flight.rod != rod || !flight.bite || nibble <= 0
            || !LUCK.equals(flight.pair.primaryId())) return original;
        float bonus = FishingTideMath.waitingLuck(flight.biteTicks, flight.pair.secondaryLevel());
        if (bonus > 0) CompendiumDiscoveryTracker.recordDiscovery(owner, LUCK, LURE);
        return original + bonus;
    }
    /** Only a successfully spawned nonempty native fishing drop can create one virtual school. */
    public static void afterCatch(FishingHook hook, ItemStack rod, Entity entity, boolean added) {
        Flight flight = FLIGHTS.get(hook);
        ServerPlayer owner = valid(hook, flight);
        if (!added || !(entity instanceof ItemEntity item) || item.getItem().isEmpty() || owner == null
            || flight == null || flight.rod != rod || flight.schoolMade || !LURE.equals(flight.pair.primaryId())
            || !(hook.level() instanceof ServerLevel level) || !isWater(level, hook.position())) return;
        if (!SCHOOLS.containsKey(owner.getUUID()) && SCHOOLS.size() >= MAX_SCHOOLS) return;
        flight.schoolMade = true;
        long lifetime = FusionConfigStore.signatureInt(SCHOOL, "school_lifetime_ticks", 400, 20, 1200);
        SCHOOLS.put(owner.getUUID(), new School(owner, rod, flight.pair, hook.position(), level.getGameTime() + lifetime));
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY(), hook.getZ(), 14, 0.35D, 0.05D, 0.35D, 0.02D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.tidal_school_created"), true);
    }
    private static boolean isWater(ServerLevel level, Vec3 pos) {
        BlockPos at = BlockPos.containing(pos);
        return level.hasChunkAt(at) && (level.getFluidState(at).is(FluidTags.WATER)
            || level.getFluidState(at.below()).is(FluidTags.WATER));
    }
    private static boolean owns(ServerPlayer player, ItemStack rod) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == rod) return true;
        return false;
    }
    public static void tickServer(MinecraftServer server) {
        Iterator<School> it = SCHOOLS.values().iterator();
        while (it.hasNext()) {
            School school = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(school.owner);
            if (player == null || !player.isAlive() || player.isSpectator() || !school.dimension.equals(player.level().dimension())
                || !owns(player, school.rod) || !school.pair.equals(selected(school.rod))) { it.remove(); continue; }
            ServerLevel level = player.level();
            long now = level.getGameTime();
            if (now >= school.expires || !isWater(level, school.position)) { it.remove(); continue; }
            Vec3 next = school.position.add(school.direction.scale(0.035D));
            if (isWater(level, next)) school.position = next; else school.direction = school.direction.scale(-1.0D);
            if (now % 8L == 0L) level.sendParticles(ParticleTypes.FISHING, school.position.x, school.position.y,
                school.position.z, 4, 0.24D, 0.05D, 0.24D, 0.01D);
        }
    }
    public static void onDisconnect(UUID owner) {
        SCHOOLS.remove(owner); FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
    }
    public static void clear() { SCHOOLS.clear(); FLIGHTS.clear(); }
}
