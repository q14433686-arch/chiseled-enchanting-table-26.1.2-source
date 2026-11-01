package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Runtime provenance for a real consumed trident, not an equal item or a nearby projectile. */
public final class GrapnelTridentFlight {
    private static final int MAX_FLIGHTS = 4096;
    private record Flight(UUID owner, ResourceKey<Level> dimension, ItemStack stack, FusedPair pair, long expires) {}
    private static final Map<ThrownTrident, Flight> FLIGHTS = new WeakHashMap<>();
    private GrapnelTridentFlight() {}

    public static void launched(Projectile projectile, LivingEntity user, ItemStack consumed) {
        if (!(projectile instanceof ThrownTrident trident) || !(user instanceof ServerPlayer owner)
            || !ready(owner) || trident.getOwner() != owner || trident.level() != owner.level()
            || owner.level().getEntity(trident.getUUID()) != trident || FLIGHTS.size() >= MAX_FLIGHTS) return;
        ItemStack stack = trident.getWeaponItem(); FusedPair pair = select(stack);
        if (pair == null || consumed == null || !ItemStack.matches(stack, consumed)) return;
        FLIGHTS.put(trident, new Flight(owner.getUUID(), owner.level().dimension(), stack, pair, owner.level().getGameTime() + 1200L));
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static @Nullable FusedPair select(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (GrapnelImpact.IMPALING.equals(pair.primaryId()) && GrapnelSkills.applies(pair, stack)
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    public static boolean valid(ServerPlayer owner, ThrownTrident projectile, ItemStack stack, FusedPair pair) {
        Flight flight = FLIGHTS.get(projectile);
        return flight != null && ready(owner) && flight.owner().equals(owner.getUUID())
            && flight.dimension().equals(owner.level().dimension()) && owner.level().getGameTime() < flight.expires()
            && projectile.isAlive() && projectile.level() == owner.level() && owner.level().getEntity(projectile.getUUID()) == projectile
            && projectile.getOwner() == owner && projectile.getWeaponItem() == stack && flight.stack() == stack
            && flight.pair().equals(pair) && GrapnelSkills.applies(pair, stack)
            && stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    public static boolean valid(ServerPlayer owner, ThrownTrident projectile, ItemStack stack) {
        Flight flight = FLIGHTS.get(projectile);
        return flight != null && valid(owner, projectile, stack, flight.pair());
    }
    public static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight flight = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner());
            return owner == null || !valid(owner, entry.getKey(), flight.stack(), flight.pair());
        });
    }
    public static void clearPlayer(UUID owner) { FLIGHTS.values().removeIf(flight -> flight.owner().equals(owner)); }
    public static void clearServer() { FLIGHTS.clear(); }
}
