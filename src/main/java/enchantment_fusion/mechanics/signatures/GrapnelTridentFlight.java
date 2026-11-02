package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
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

/** Runtime provenance for a real consumed trident, not an equal item or a nearby projectile. */
public final class GrapnelTridentFlight {
    private static final int MAX_FLIGHTS = 4096;
    private record Flight(UUID owner, ResourceKey<Level> dimension, ItemStack stack, List<FusedPair> pairs, long expires) {}
    private static final Map<ThrownTrident, Flight> FLIGHTS = new WeakHashMap<>();
    private GrapnelTridentFlight() {}

    public static void launched(Projectile projectile, LivingEntity user, ItemStack consumed) {
        if (!(projectile instanceof ThrownTrident trident) || !(user instanceof ServerPlayer owner)
            || !ready(owner) || trident.getOwner() != owner || trident.level() != owner.level()
            || owner.level().getEntity(trident.getUUID()) != trident || FLIGHTS.size() >= MAX_FLIGHTS) return;
        ItemStack stack = trident.getWeaponItem();
        if (stack == null || stack.isEmpty() || consumed == null || !ItemStack.matches(stack, consumed)) return;
        List<FusedPair> pairs = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> (GrapnelImpact.IMPALING.equals(pair.primaryId()) || GrapnelChanneling.isPair(pair)
                || GrapnelLoyalty.isPair(pair)) && GrapnelSkills.applies(pair, stack)).toList();
        if (!pairs.isEmpty()) FLIGHTS.put(trident, new Flight(owner.getUUID(), owner.level().dimension(), stack, pairs,
            owner.level().getGameTime() + 1200L));
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    public static boolean valid(ServerPlayer owner, ThrownTrident projectile, ItemStack stack, FusedPair pair) {
        Flight flight = FLIGHTS.get(projectile);
        return flight != null && ready(owner) && flight.owner().equals(owner.getUUID())
            && flight.dimension().equals(owner.level().dimension()) && owner.level().getGameTime() < flight.expires()
            && projectile.isAlive() && projectile.level() == owner.level() && owner.level().getEntity(projectile.getUUID()) == projectile
            && projectile.getOwner() == owner && projectile.getWeaponItem() == stack && flight.stack() == stack
            && flight.pairs().contains(pair) && GrapnelSkills.applies(pair, stack)
            && stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    public static boolean valid(ServerPlayer owner, ThrownTrident projectile, ItemStack stack) {
        Flight flight = FLIGHTS.get(projectile);
        return flight != null && flight.pairs().stream().anyMatch(pair -> valid(owner, projectile, stack, pair));
    }
    public static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight flight = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner());
            return owner == null || !valid(owner, entry.getKey(), flight.stack());
        });
    }
    public static void clearPlayer(UUID owner) { FLIGHTS.values().removeIf(flight -> flight.owner().equals(owner)); }
    public static void clearServer() { FLIGHTS.clear(); }
}
