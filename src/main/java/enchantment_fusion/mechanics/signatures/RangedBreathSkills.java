package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Shooting/Respiration: one real underwater hit creates an owner-only bubble; air arms one real arrow. */
public final class RangedBreathSkills {
    public static final String RESPIRATION = "minecraft:respiration";
    private static final int MAX_OWNERS = 256;
    private static final long DURATION = 200;
    private record Gear(ItemStack item, FusedPair pair) {}
    private record Prepared(Gear gear, ResourceKey<Level> dimension, long born, long until) {}
    private static final Map<UUID, Prepared> PREPARED = new HashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) {
            return shootingFirst(pair) && RangedBreathSkills.applies(pair, launcher);
        }
        @Override public void hit(ConfirmedArrow.Hit hit) { onHit(hit); }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            Prepared prepared = PREPARED.get(owner.getUUID());
            return RangedWearSkills.supportsCarrier(FusionCarrier.classify(launcher)) && valid(owner, prepared) ? prepared : null;
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object captured) {
            if (!(captured instanceof Prepared prepared) || PREPARED.get(owner.getUUID()) != prepared || !valid(owner, prepared)) return;
            // The first genuine inserted arrow consumes the preparation even if a bounded resource limit rejects it.
            PREPARED.remove(owner.getUUID(), prepared);
            if (RangedWaterDrag.arm(owner, arrow, EquipmentSlot.HEAD, prepared.gear().item(), prepared.gear().pair(), RangedBreathSkills::source)) {
                CompendiumDiscoveryTracker.recordDiscovery(owner, prepared.gear().pair().primaryId(), prepared.gear().pair().secondaryId());
            } else owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_water_full"), true);
        }
    };
    private RangedBreathSkills() {}
    public static void init() { ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS); }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && RESPIRATION.equals(pair.secondaryId());
    }
    public static boolean breathFirst(@Nullable FusedPair pair) {
        return pair != null && RESPIRATION.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    /** User-selected exact/wildcard data-pack signatures always take precedence over this template. */
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || breathFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        return roles != null && (shootingFirst(pair) ? RangedWearSkills.supportsCarrier(roles)
            : breathFirst(pair) && roles.contains(FusionCarrier.HEAD_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack item) {
        return matches(pair) && item != null && !item.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(item));
    }
    public static int primaryLevel(@Nullable ItemStack item, String id) {
        if (item == null || item.isEmpty()) return 0;
        int level = 0;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (id.equals(pair.primaryId()) && shootingFirst(pair) && applies(pair, item)) level = Math.max(level, pair.primaryLevel());
        return level;
    }
    private static @Nullable Gear worn(ServerPlayer owner) {
        ItemStack item = owner.getItemBySlot(EquipmentSlot.HEAD);
        if (item.isEmpty() || item.isBroken()) return null;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (breathFirst(pair) && applies(pair, item)) return new Gear(item, pair);
        return null;
    }
    public static boolean hasEquipped(ServerPlayer owner) { return worn(owner) != null; }
    private static boolean source(ServerPlayer owner, EquipmentSlot slot, ItemStack item, FusedPair pair) {
        return slot == EquipmentSlot.HEAD && owner.getItemBySlot(slot) == item && !item.isBroken()
            && breathFirst(pair) && applies(pair, item)
            && item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    private static boolean valid(ServerPlayer owner, @Nullable Prepared prepared) {
        if (prepared == null || !owner.isAlive() || owner.isSpectator() || owner.isCreative()
            || !owner.level().dimension().equals(prepared.dimension())) return false;
        long now = owner.level().getGameTime();
        return now >= prepared.born() && now < prepared.until()
            && source(owner, EquipmentSlot.HEAD, prepared.gear().item(), prepared.gear().pair());
    }
    public static boolean hasPreparation(ServerPlayer owner) {
        Prepared prepared = PREPARED.get(owner.getUUID());
        if (prepared != null && !valid(owner, prepared)) { PREPARED.remove(owner.getUUID(), prepared); return false; }
        return prepared != null;
    }
    /** Paying air is an explicit choice: cancellation, timeout or changing equipment does not refund it. */
    public static boolean activate(ServerPlayer owner) {
        if (!owner.isAlive() || owner.isSpectator() || owner.isCreative() || owner.containerMenu != owner.inventoryMenu
            || !owner.isEyeInFluid(FluidTags.WATER)) return false;
        Gear gear = worn(owner); if (gear == null) return false;
        if (hasPreparation(owner) || RangedTideSkills.hasPreparation(owner)) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_water_prepared"), true); return false;
        }
        if (PREPARED.size() >= MAX_OWNERS) return false;
        int cost = UnderwaterMiningSkills.airCost(owner.getMaxAirSupply());
        if (owner.getAirSupply() < cost + UnderwaterMiningSkills.airReserve(owner.getMaxAirSupply())) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.breath_not_enough"), true); return false;
        }
        long now = owner.level().getGameTime();
        PREPARED.put(owner.getUUID(), new Prepared(gear, owner.level().dimension(), now, now + DURATION));
        owner.setAirSupply(owner.getAirSupply() - cost);
        owner.level().sendParticles(ParticleTypes.BUBBLE, owner.getX(), owner.getEyeY(), owner.getZ(), 8, .2, .2, .2, .02);
        return true;
    }
    private static void onHit(ConfirmedArrow.Hit hit) {
        ServerPlayer owner = hit.owner(); BlockPos cell = BlockPos.containing(hit.location());
        if (!owner.isEyeInFluid(FluidTags.WATER) || !owner.level().hasChunkAt(cell)
            || !owner.level().getFluidState(cell).is(FluidTags.WATER)) return;
        int air = UnderwaterMiningSkills.bubbleAir(hit.pair().secondaryLevel());
        if (UnderwaterMiningSkills.spawnBubble(owner, hit.location(), air))
            CompendiumDiscoveryTracker.recordDiscovery(owner, hit.pair().primaryId(), hit.pair().secondaryId());
    }
    public static void tickServer(MinecraftServer server) {
        PREPARED.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
        RangedWaterDrag.tickServer(server);
    }
    public static void clearPlayer(UUID id) { PREPARED.remove(id); RangedWaterDrag.clearPlayer(id); }
    public static void clearServer() { PREPARED.clear(); RangedWaterDrag.clearServer(); }
}
