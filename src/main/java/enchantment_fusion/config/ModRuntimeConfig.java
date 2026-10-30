package enchantment_fusion.config;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.model.TriggerType;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe runtime configuration hub for Enchantment Fusion, supporting atomic JSON persistence,
 * dedicated-server S2C/C2S live synchronization, and per-entry Signature / Transmutation switches.
 */
public final class ModRuntimeConfig {

    public record Snapshot(
        boolean overrideDatapacks,
        boolean frostedIceNoWater,
        boolean backgroundRitualEnabled,
        int ritualNormalTicks,
        int ritualHighRiskTicks,
        boolean tridentFusedLoyaltyReturn,
        boolean blockVanillaAnvilCrossDomain,
        EconomyConfig economy,
        Set<String> disabledSignatures,
        Set<String> disabledTransmutations
    ) {
        public static final Snapshot DEFAULT = new Snapshot(
            true,
            true,
            true,
            26,
            36,
            true,
            true,
            EconomyConfig.DEFAULT,
            Set.of(),
            Set.of()
        );

        public Snapshot {
            ritualNormalTicks = Math.clamp(ritualNormalTicks, 6, 120);
            ritualHighRiskTicks = Math.clamp(ritualHighRiskTicks, 8, 160);
            economy = economy != null ? economy : EconomyConfig.DEFAULT;
            disabledSignatures = disabledSignatures != null ? Set.copyOf(disabledSignatures) : Set.of();
            disabledTransmutations = disabledTransmutations != null ? Set.copyOf(disabledTransmutations) : Set.of();
        }
    }

    /** Mutable editor buffer used by the in-game config screen before saving or sending to server. */
    public static final class Draft {
        public boolean overrideDatapacks;
        public boolean frostedIceNoWater;
        public boolean backgroundRitualEnabled;
        public int ritualNormalTicks;
        public int ritualHighRiskTicks;
        public boolean tridentFusedLoyaltyReturn;
        public boolean blockVanillaAnvilCrossDomain;

        public double lowFailChance;
        public double midFailChance;
        public double highFailChance;
        public double repulsiveFailChance;
        public double repulsiveMinFloor;
        public double aberrantSocketFailChance;
        public double failCeilingLow;
        public double failCeilingMid;
        public double failCeilingHigh;
        public double failCeilingRepulsive;
        public double failCeilingConversion;
        public double failCeilingSocket;
        public double stabilizerReductionRepulsive;
        public double stabilizerReductionNormal;
        public int maxRepulsiveStabilizers;
        public int maxNormalStabilizers;
        public int maxLowStabilizers;
        public double normalFailFeeRatio;
        public int repulsiveBaseXp;
        public int repulsiveMaxBonusXp;
        public int repulsiveMaxPairReagents;
        public int repulsiveDefaultReagents;
        public int highBaseXp;
        public int highMaxXp;
        public int highReagents;
        public int midBaseXp;
        public int midMinXp;
        public int midMaxXp;
        public int midMaxReagents;
        public int lowBaseXp;
        public int lowMinXp;
        public int lowMaxReagents;
        public int socketBookXp;
        public int topUpXp;
        public int expandVesselXp;
        public int manageFlipSplitXp;
        public int purifyAberrantXp;
        public int purifyAberrantStabilizers;
        public double meleeMinCharge;
        public double meleeFullCharge;
        public final Map<TriggerType, Integer> triggerCooldowns = new EnumMap<>(TriggerType.class);
        public final Set<String> disabledSignatures = new LinkedHashSet<>();
        public final Set<String> disabledTransmutations = new LinkedHashSet<>();

        public Draft(Snapshot src) {
            copyFrom(src);
        }

        public void copyFrom(Snapshot s) {
            this.overrideDatapacks = s.overrideDatapacks();
            this.frostedIceNoWater = s.frostedIceNoWater();
            this.backgroundRitualEnabled = s.backgroundRitualEnabled();
            this.ritualNormalTicks = s.ritualNormalTicks();
            this.ritualHighRiskTicks = s.ritualHighRiskTicks();
            this.tridentFusedLoyaltyReturn = s.tridentFusedLoyaltyReturn();
            this.blockVanillaAnvilCrossDomain = s.blockVanillaAnvilCrossDomain();

            EconomyConfig ec = s.economy();
            this.lowFailChance = ec.lowFailChance();
            this.midFailChance = ec.midFailChance();
            this.highFailChance = ec.highFailChance();
            this.repulsiveFailChance = ec.repulsiveFailChance();
            this.repulsiveMinFloor = ec.repulsiveMinFloor();
            this.aberrantSocketFailChance = ec.aberrantSocketFailChance();
            this.failCeilingLow = ec.failCeilingLow();
            this.failCeilingMid = ec.failCeilingMid();
            this.failCeilingHigh = ec.failCeilingHigh();
            this.failCeilingRepulsive = ec.failCeilingRepulsive();
            this.failCeilingConversion = ec.failCeilingConversion();
            this.failCeilingSocket = ec.failCeilingSocket();
            this.stabilizerReductionRepulsive = ec.stabilizerReductionRepulsive();
            this.stabilizerReductionNormal = ec.stabilizerReductionNormal();
            this.maxRepulsiveStabilizers = ec.maxRepulsiveStabilizers();
            this.maxNormalStabilizers = ec.maxNormalStabilizers();
            this.maxLowStabilizers = ec.maxLowStabilizers();
            this.normalFailFeeRatio = ec.normalFailFeeRatio();
            this.repulsiveBaseXp = ec.repulsiveBaseXp();
            this.repulsiveMaxBonusXp = ec.repulsiveMaxBonusXp();
            this.repulsiveMaxPairReagents = ec.repulsiveMaxPairReagents();
            this.repulsiveDefaultReagents = ec.repulsiveDefaultReagents();
            this.highBaseXp = ec.highBaseXp();
            this.highMaxXp = ec.highMaxXp();
            this.highReagents = ec.highReagents();
            this.midBaseXp = ec.midBaseXp();
            this.midMinXp = ec.midMinXp();
            this.midMaxXp = ec.midMaxXp();
            this.midMaxReagents = ec.midMaxReagents();
            this.lowBaseXp = ec.lowBaseXp();
            this.lowMinXp = ec.lowMinXp();
            this.lowMaxReagents = ec.lowMaxReagents();
            this.socketBookXp = ec.socketBookXp();
            this.topUpXp = ec.topUpXp();
            this.expandVesselXp = ec.expandVesselXp();
            this.manageFlipSplitXp = ec.manageFlipSplitXp();
            this.purifyAberrantXp = ec.purifyAberrantXp();
            this.purifyAberrantStabilizers = ec.purifyAberrantStabilizers();
            this.meleeMinCharge = ec.meleeMinCharge();
            this.meleeFullCharge = ec.meleeFullCharge();

            this.triggerCooldowns.clear();
            for (TriggerType t : TriggerType.values()) {
                this.triggerCooldowns.put(t, ec.cooldownTicksFor(t));
            }
            this.disabledSignatures.clear();
            this.disabledSignatures.addAll(s.disabledSignatures());
            this.disabledTransmutations.clear();
            this.disabledTransmutations.addAll(s.disabledTransmutations());
        }

        public Snapshot toSnapshot() {
            double safeFull = Math.max(meleeMinCharge, meleeFullCharge);
            EconomyConfig ec = new EconomyConfig(
                lowFailChance, midFailChance, highFailChance, repulsiveFailChance,
                repulsiveMinFloor, aberrantSocketFailChance,
                failCeilingLow, failCeilingMid, failCeilingHigh,
                failCeilingRepulsive, failCeilingConversion, failCeilingSocket,
                stabilizerReductionRepulsive, stabilizerReductionNormal,
                maxRepulsiveStabilizers, maxNormalStabilizers, maxLowStabilizers,
                normalFailFeeRatio,
                repulsiveBaseXp, repulsiveMaxBonusXp, repulsiveMaxPairReagents, repulsiveDefaultReagents,
                highBaseXp, highMaxXp, highReagents,
                midBaseXp, midMinXp, midMaxXp, midMaxReagents,
                lowBaseXp, lowMinXp, lowMaxReagents,
                socketBookXp, topUpXp, expandVesselXp, manageFlipSplitXp,
                purifyAberrantXp, purifyAberrantStabilizers,
                meleeMinCharge, safeFull,
                triggerCooldowns
            );
            return new Snapshot(
                overrideDatapacks,
                frostedIceNoWater,
                backgroundRitualEnabled,
                ritualNormalTicks,
                ritualHighRiskTicks,
                tridentFusedLoyaltyReturn,
                blockVanillaAnvilCrossDomain,
                ec,
                disabledSignatures,
                disabledTransmutations
            );
        }
    }

    private static volatile Snapshot localSnapshot = Snapshot.DEFAULT;
    private static volatile @Nullable Snapshot clientOverride = null;

    private ModRuntimeConfig() {}

    public static void bootstrap() {
        localSnapshot = ConfigFileStore.loadOrCreate();
        FusionMatrixRegistry.refreshRuntimeConfigOverlay();
    }

    public static Snapshot localSnapshot() {
        return localSnapshot;
    }

    public static Snapshot clientView() {
        Snapshot remote = clientOverride;
        return remote != null ? remote : localSnapshot;
    }

    public static Snapshot effectiveFor(boolean clientSide) {
        return clientSide ? clientView() : localSnapshot;
    }

    /** Active snapshot for shared catalog lookups (synced on dedicated clients, authoritative on server). */
    public static Snapshot active() {
        Snapshot remote = clientOverride;
        return remote != null ? remote : localSnapshot;
    }

    public static void setClientOverride(@Nullable Snapshot synced) {
        clientOverride = synced;
    }

    public static void applyAndSave(Snapshot next) {
        if (next == null) {
            return;
        }
        localSnapshot = next;
        ConfigFileStore.save(next);
        FusionMatrixRegistry.refreshRuntimeConfigOverlay();
    }

    /** Testing hook that swaps the in-memory snapshot without touching disk unless explicitly saved. */
    public static void setInMemoryForTesting(Snapshot next) {
        localSnapshot = next != null ? next : Snapshot.DEFAULT;
        clientOverride = null;
        FusionMatrixRegistry.refreshRuntimeConfigOverlay();
    }

    public static boolean isSignatureDisabled(String sigId) {
        return sigId != null && active().disabledSignatures().contains(sigId);
    }

    public static boolean isTransmutationDisabled(String variantId) {
        return variantId != null && active().disabledTransmutations().contains(variantId);
    }
}
