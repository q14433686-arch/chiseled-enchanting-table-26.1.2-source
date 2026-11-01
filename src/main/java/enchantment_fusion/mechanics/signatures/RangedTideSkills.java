package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** The six ordered shooting/Depth Strider directions share real movement and arrow evidence. */
public final class RangedTideSkills {
    public static final String DEPTH = "minecraft:depth_strider";
    private static final int MAX_OWNERS = 256;
    private static final long DURATION = 200;
    record Gear(ItemStack item, FusedPair pair) {}
    private record Prepared(Gear gear, ResourceKey<Level> dimension, long born, long until) {}
    private record Ticket(@Nullable Gear arrowGear, @Nullable Prepared prepared) {}
    private static final Map<UUID, Prepared> PREPARED = new HashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return shootingFirst(pair) && RangedTideSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) { RangedTideTrail.hit(hit); }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            if (!RangedWearSkills.supportsCarrier(FusionCarrier.classify(launcher))) return null;
            Gear gear = selected(launcher, true); Prepared prepared = PREPARED.get(owner.getUUID());
            if (!valid(owner, prepared)) prepared = null;
            return gear == null && prepared == null ? null : new Ticket(gear, prepared);
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object raw) {
            if (!(raw instanceof Ticket ticket) || arrow.getOwner() != owner || owner.level().getEntity(arrow.getUUID()) != arrow) return;
            if (ticket.arrowGear() != null) RangedTideTrail.launch(owner, arrow, ticket.arrowGear());
            Prepared prepared = ticket.prepared();
            if (prepared == null || PREPARED.get(owner.getUUID()) != prepared || !valid(owner, prepared)) return;
            PREPARED.remove(owner.getUUID(), prepared);
            if (RangedWaterDrag.arm(owner, arrow, EquipmentSlot.FEET, prepared.gear().item(), prepared.gear().pair(), RangedTideSkills::source))
                CompendiumDiscoveryTracker.recordDiscovery(owner, prepared.gear().pair().primaryId(), prepared.gear().pair().secondaryId());
            else owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_water_full"), true);
        }
    };
    private RangedTideSkills() {}
    public static void init() {
        ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS);
        PrimaryAttributeProjection.register(RangedTideSkills::primarySources); RangedTideMovement.init();
    }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && DEPTH.equals(pair.secondaryId());
    }
    public static boolean depthFirst(@Nullable FusedPair pair) {
        return pair != null && DEPTH.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || depthFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        return roles != null && (shootingFirst(pair) ? RangedWearSkills.supportsCarrier(roles)
            : depthFirst(pair) && roles.contains(FusionCarrier.FOOT_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack item) {
        return matches(pair) && item != null && !item.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(item));
    }
    public static int primaryLevel(@Nullable ItemStack item, String id) {
        if (item == null || item.isEmpty()) return 0; int result = 0;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (shootingFirst(pair) && id.equals(pair.primaryId()) && applies(pair, item)) result = Math.max(result, pair.primaryLevel());
        return result;
    }
    private static @Nullable Gear selected(ItemStack item, boolean shooting) {
        if (item == null || item.isEmpty() || item.isBroken()) return null;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((shooting ? shootingFirst(pair) : depthFirst(pair)) && applies(pair, item)) return new Gear(item, pair);
        return null;
    }
    static @Nullable Gear worn(ServerPlayer owner) { return selected(owner.getItemBySlot(EquipmentSlot.FEET), false); }
    public static boolean hasEquipped(ServerPlayer owner) { return worn(owner) != null; }
    static boolean same(@Nullable Gear a, @Nullable Gear b) {
        return a == null ? b == null : b != null && a.item() == b.item() && a.pair().equals(b.pair());
    }
    static boolean owns(ServerPlayer owner, Gear gear) {
        if (!applies(gear.pair(), gear.item()) || gear.item().isBroken()
            || !gear.item().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.item()) return true;
        return false;
    }
    static boolean wearing(ServerPlayer owner, Gear gear) {
        return gear != null && owner.getItemBySlot(EquipmentSlot.FEET) == gear.item() && depthFirst(gear.pair()) && owns(owner, gear);
    }
    private static boolean source(ServerPlayer owner, EquipmentSlot slot, ItemStack item, FusedPair pair) {
        return slot == EquipmentSlot.FEET && wearing(owner, new Gear(item, pair));
    }
    private static boolean valid(ServerPlayer owner, @Nullable Prepared prepared) {
        if (prepared == null || !RangedTideMovement.ready(owner) || !owner.level().dimension().equals(prepared.dimension())
            || !wearing(owner, prepared.gear())) return false;
        long now = owner.level().getGameTime(); return now >= prepared.born() && now < prepared.until();
    }
    public static boolean hasPreparation(ServerPlayer owner) {
        Prepared prepared = PREPARED.get(owner.getUUID());
        if (prepared != null && !valid(owner, prepared)) { PREPARED.remove(owner.getUUID(), prepared); return false; }
        return prepared != null;
    }
    public static boolean activate(ServerPlayer owner) {
        if (!RangedTideMovement.ready(owner) || !owner.isInWater() || owner.containerMenu != owner.inventoryMenu) return false;
        Gear gear = worn(owner); if (gear == null) return false;
        if (hasPreparation(owner) || RangedBreathSkills.hasPreparation(owner)) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_water_prepared"), true); return false;
        }
        if (PREPARED.size() >= MAX_OWNERS) return false;
        if (!RangedTideMovement.take(owner, gear)) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_tide_need_swim"), true); return false;
        }
        long now = owner.level().getGameTime(); PREPARED.put(owner.getUUID(), new Prepared(gear, owner.level().dimension(), now, now + DURATION));
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_tide_set"), true);
        return true;
    }
    private static List<PrimaryAttributeProjection.Source> primarySources(ServerPlayer owner) {
        ItemStack boots = owner.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty() || boots.isBroken() || !FusionCarrier.classify(boots).contains(FusionCarrier.FOOT_ARMOR)
            || !FusionStationOperations.lookupEnchantment(owner.registryAccess(), DEPTH)
                .map(holder -> holder.value().matchingSlot(EquipmentSlot.FEET)).orElse(false)) return List.of();
        List<PrimaryAttributeProjection.Source> result = new ArrayList<>();
        for (FusedPair pair : boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (depthFirst(pair) && applies(pair, boots)) result.add(new PrimaryAttributeProjection.Source(EquipmentSlot.FEET, boots, pair));
        return List.copyOf(result);
    }
    public static void tickServer(MinecraftServer server) {
        RangedTideMovement.tickServer(server); RangedTideTrail.tickServer(server);
        PREPARED.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
    }
    public static void clearPlayer(UUID id) { PREPARED.remove(id); RangedTideMovement.clearPlayer(id); RangedTideTrail.clearPlayer(id); }
    public static void clearServer() { PREPARED.clear(); RangedTideMovement.clearServer(); RangedTideTrail.clearServer(); }
}
