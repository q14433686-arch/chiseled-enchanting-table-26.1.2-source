package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The two selected respiration/aqua-affinity directions. All mutable state is server-thread-only. */
public final class UnderwaterMiningSkills {
    public static final String RESPIRATION = "minecraft:respiration";
    public static final String AQUA = "minecraft:aqua_affinity";
    public static final String SPEND_SIGNATURE = "abyssal_sight";
    public static final String BUBBLE_SIGNATURE = "ocean_breath_mine";
    private static final Set<FusionCarrier> TOOLS = Set.of(
        FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE);
    private static final Identifier SUBMERGED_MODIFIER = EnchantmentFusion.id("breath_mining_submerged");
    private static final Identifier SPEED_MODIFIER = EnchantmentFusion.id("breath_mining_speed");
    private static final int MAX_BUBBLES_TOTAL = 256;
    private static final int MAX_BUBBLES_PER_PLAYER = 8;
    private static final int BUBBLE_LIFETIME = 100;
    private static final int BUBBLE_INTERVAL = 6;

    private record Candidate(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Boost(ResourceKey<Level> dimension, EquipmentSlot slot, ItemStack stack,
                         FusedPair pair, long untilTick) {}
    private static final class Bubble {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final int air;
        Vec3 position;
        int age;
        Bubble(UUID owner, ResourceKey<Level> dimension, Vec3 position, int air) {
            this.owner = owner;
            this.dimension = dimension;
            this.position = position;
            this.air = air;
        }
    }

    private static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_BUBBLE = new HashMap<>();
    private static final List<Bubble> BUBBLES = new ArrayList<>();

    private UnderwaterMiningSkills() {}

    public static boolean isSpendPair(@Nullable FusedPair pair) {
        return pair != null && RESPIRATION.equals(pair.primaryId()) && AQUA.equals(pair.secondaryId());
    }

    public static boolean isBubblePair(@Nullable FusedPair pair) {
        return pair != null && AQUA.equals(pair.primaryId()) && RESPIRATION.equals(pair.secondaryId());
    }

    /** Both actions operate through a worn helmet or the actual mining tool, not an enchantability intersection. */
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && (carriers.contains(FusionCarrier.HEAD_ARMOR)
            || carriers.stream().anyMatch(TOOLS::contains));
    }

    public static boolean applies(@Nullable FusedPair pair, ItemStack stack, String signature) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return false;
        return (isSpendPair(pair) && SPEND_SIGNATURE.equals(signature))
            || (isBubblePair(pair) && BUBBLE_SIGNATURE.equals(signature));
    }

    /** Default maximum air is 300 ticks; retain a reserve and adapt to smaller modded air pools. */
    public static int airCost(int maximumAir) {
        return Math.max(1, Math.min(40, Math.max(1, maximumAir) / 5));
    }

    public static int airReserve(int maximumAir) {
        return Math.max(1, Math.min(20, Math.max(1, maximumAir) / 10));
    }

    public static int boostDuration(int secondaryLevel) {
        return 80 + 20 * Math.clamp(secondaryLevel, 1, 6);
    }

    public static int bubbleAir(int secondaryLevel) {
        return 30 + 15 * Math.clamp(secondaryLevel, 1, 10);
    }

    private static boolean worksInSlot(ItemStack stack, EquipmentSlot slot) {
        Set<FusionCarrier> roles = FusionCarrier.classify(stack);
        return slot == EquipmentSlot.HEAD ? roles.contains(FusionCarrier.HEAD_ARMOR)
            : slot == EquipmentSlot.MAINHAND && roles.stream().anyMatch(TOOLS::contains);
    }

    @Nullable
    private static Candidate find(ServerPlayer player, boolean spend) {
        Candidate best = null;
        // Tie-break in favour of the held tool; multiple copies never multiply the same action.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !worksInSlot(stack, slot)) continue;
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                if (!(spend ? isSpendPair(pair) : isBubblePair(pair))) continue;
                if (!SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) continue;
                if (best == null || pair.primaryLevel() + (long) pair.secondaryLevel()
                    > best.pair().primaryLevel() + (long) best.pair().secondaryLevel()) {
                    best = new Candidate(slot, stack, pair);
                }
            }
        }
        return best;
    }

    /** Invoked from a server-validated key packet. Returns false without paying when it cannot start. */
    public static boolean activate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
            || !player.isEyeInFluid(FluidTags.WATER) || player.containerMenu != player.inventoryMenu) return false;
        Candidate candidate = find(player, true);
        if (candidate == null || BOOSTS.containsKey(player.getUUID())) return false;
        int cost = airCost(player.getMaxAirSupply());
        if (player.getAirSupply() < cost + airReserve(player.getMaxAirSupply())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.enchantment_fusion.breath_not_enough"), true);
            return false;
        }
        AttributeInstance submerged = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        // Never overwrite or later remove a modifier we did not create.
        if (submerged == null || speed == null || submerged.hasModifier(SUBMERGED_MODIFIER)
            || speed.hasModifier(SPEED_MODIFIER)) return false;
        Boost boost = new Boost(level.dimension(), candidate.slot(), candidate.stack(), candidate.pair(),
            level.getGameTime() + boostDuration(candidate.pair().secondaryLevel()));
        BOOSTS.put(player.getUUID(), boost);
        if (!refreshModifiers(player, boost)) {
            BOOSTS.remove(player.getUUID());
            removeOurModifiers(player, boost);
            return false;
        }
        player.setAirSupply(player.getAirSupply() - cost);
        level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getEyeY(), player.getZ(),
            10, 0.25D, 0.2D, 0.25D, 0.03D);
        level.playSound(null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
            SoundSource.PLAYERS, 0.6F, 1.15F);
        CompendiumDiscoveryTracker.recordDiscovery(player, candidate.pair().primaryId(), candidate.pair().secondaryId());
        runAberration(level, player, candidate.pair(), TriggerType.ON_UTILITY_CYCLE, player.position());
        return true;
    }

    /** Called only by Fabric's successful AFTER block-break event, not a BEFORE cancellation. */
    public static void onMined(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || ExtraBlockHarvest.isActive()
            || MatrixEffectExecutor.isExecuting() || !player.isEyeInFluid(FluidTags.WATER)) return;
        Candidate candidate = find(player, false);
        if (candidate == null) return;
        long now = level.getGameTime();
        long last = LAST_BUBBLE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now >= last && now - last < BUBBLE_INTERVAL) return;
        if (BUBBLES.size() >= MAX_BUBBLES_TOTAL) return;
        long owned = BUBBLES.stream().filter(bubble -> bubble.owner.equals(player.getUUID())).count();
        if (owned >= MAX_BUBBLES_PER_PLAYER) return;
        BUBBLES.add(new Bubble(player.getUUID(), level.dimension(), Vec3.atCenterOf(pos),
            bubbleAir(candidate.pair().secondaryLevel())));
        LAST_BUBBLE.put(player.getUUID(), now);
        CompendiumDiscoveryTracker.recordDiscovery(player, candidate.pair().primaryId(), candidate.pair().secondaryId());
        runAberration(level, player, candidate.pair(), TriggerType.ON_BLOCK_HARVEST, Vec3.atCenterOf(pos));
    }

    private static void runAberration(ServerLevel level, ServerPlayer player, FusedPair pair, TriggerType trigger, Vec3 pos) {
        if (!pair.isAberrant() || !MatrixEffectExecutor.acquireDeferredCooldown(level, player, pair, trigger)) return;
        MatrixEffectExecutor.runDeferredAberration(level, player, pair, pos);
    }

    private static AttributeModifier submergedModifier() {
        return new AttributeModifier(SUBMERGED_MODIFIER, 4.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static AttributeModifier speedModifier(Boost boost, boolean onGround) {
        double rate = 1.5D + 0.15D * Math.clamp(boost.pair().primaryLevel(), 1, 10);
        return new AttributeModifier(SPEED_MODIFIER, rate * (onGround ? 1.0D : 5.0D) - 1.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static boolean ownedSpeed(@Nullable AttributeModifier current, Boost boost) {
        return current == null || current.equals(speedModifier(boost, true)) || current.equals(speedModifier(boost, false));
    }

    /** Return false if another integration has taken either of our identifiers. */
    private static boolean refreshModifiers(ServerPlayer player, Boost boost) {
        AttributeInstance submerged = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (submerged == null || speed == null) return false;
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            removeOurModifiers(player, boost);
            return true;
        }
        AttributeModifier currentWater = submerged.getModifier(SUBMERGED_MODIFIER);
        AttributeModifier currentSpeed = speed.getModifier(SPEED_MODIFIER);
        if ((currentWater != null && (!currentWater.equals(submergedModifier()) || submerged.getPermanentModifiers().contains(currentWater)))
            || !ownedSpeed(currentSpeed, boost)
            || (currentSpeed != null && speed.getPermanentModifiers().contains(currentSpeed))) return false;
        // The water penalty is offset; when swimming, offset the separate airborne penalty too.
        // ADD_MULTIPLIED_TOTAL composes with vanilla and other mods rather than replacing them.
        setModifier(submerged, submergedModifier());
        setModifier(speed, speedModifier(boost, player.onGround()));
        return true;
    }

    private static void setModifier(AttributeInstance attribute, AttributeModifier wanted) {
        AttributeModifier current = attribute.getModifier(wanted.id());
        if (wanted.equals(current)) return;
        if (current != null) attribute.removeModifier(wanted.id());
        attribute.addTransientModifier(wanted);
    }

    private static void removeOurModifiers(ServerPlayer player, Boost boost) {
        AttributeInstance submerged = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        AttributeModifier currentWater = submerged == null ? null : submerged.getModifier(SUBMERGED_MODIFIER);
        AttributeModifier currentSpeed = speed == null ? null : speed.getModifier(SPEED_MODIFIER);
        if (currentWater != null && currentWater.equals(submergedModifier())
            && !submerged.getPermanentModifiers().contains(currentWater)) submerged.removeModifier(SUBMERGED_MODIFIER);
        if (currentSpeed != null && ownedSpeed(currentSpeed, boost)
            && !speed.getPermanentModifiers().contains(currentSpeed)) speed.removeModifier(SPEED_MODIFIER);
    }

    public static void tickServer(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Boost>> it = BOOSTS.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Boost boost = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) { it.remove(); continue; }
            if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(boost.dimension())
                || !player.isAlive() || player.isSpectator() || level.getGameTime() >= boost.untilTick()
                || player.getItemBySlot(boost.slot()) != boost.stack()
                || !boost.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(boost.pair())
                || !SignatureCatalog.resolve(boost.pair()).map(e -> applies(boost.pair(), boost.stack(), e.id())).orElse(false)) {
                removeOurModifiers(player, boost);
                it.remove();
            } else if (!refreshModifiers(player, boost)) {
                removeOurModifiers(player, boost);
                it.remove();
            }
        }
        for (Iterator<Bubble> it = BUBBLES.iterator(); it.hasNext();) {
            Bubble bubble = it.next();
            ServerLevel level = server.getLevel(bubble.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(bubble.owner);
            if (level == null || owner == null || owner.level() != level || !owner.isAlive() || owner.isSpectator()
                || ++bubble.age > BUBBLE_LIFETIME) { it.remove(); continue; }
            BlockPos cell = BlockPos.containing(bubble.position);
            if (!level.isInWorldBounds(cell) || !level.hasChunkAt(cell)
                || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) { it.remove(); continue; }
            // The mined cell may take a few ticks to fill; once in water the bubble disappears at the surface.
            if (bubble.age > 10 && !level.getFluidState(cell).is(FluidTags.WATER)) { it.remove(); continue; }
            if (owner.isEyeInFluid(FluidTags.WATER) && owner.getAirSupply() < owner.getMaxAirSupply()
                && owner.getBoundingBox().inflate(0.35D).contains(bubble.position)) {
                owner.setAirSupply((int) Math.min((long) owner.getMaxAirSupply(), (long) owner.getAirSupply() + bubble.air));
                level.sendParticles(ParticleTypes.BUBBLE, bubble.position.x, bubble.position.y, bubble.position.z,
                    6, 0.12D, 0.12D, 0.12D, 0.02D);
                it.remove();
                continue;
            }
            if ((bubble.age & 1) == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, bubble.position.x, bubble.position.y, bubble.position.z,
                    1, 0.03D, 0.02D, 0.03D, 0.0D);
            }
            bubble.position = bubble.position.add(0.0D, 0.04D, 0.0D);
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        Boost boost = BOOSTS.remove(id);
        if (boost != null) removeOurModifiers(player, boost);
        LAST_BUBBLE.remove(id);
        BUBBLES.removeIf(bubble -> bubble.owner.equals(id));
    }

    public static void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Boost boost = BOOSTS.get(player.getUUID());
            if (boost != null) removeOurModifiers(player, boost);
        }
        BOOSTS.clear();
        LAST_BUBBLE.clear();
        BUBBLES.clear();
    }
}
