package enchantment_fusion.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin that gates optional sister-mod {@code @Pseudo} mixins targeting
 * {@code 纂刻附魔台 (chiseled-enchanting-table)} via an explicit allowlist ({@link #CET_GATED_MIXINS})
 * rather than brittle substring matching (MIX-4), and tracks applied bridge count for startup diagnostics (MIX-5).
 * <p>
 * Note (MIX-6): {@code onLoad} runs during early Mixin bootstrap before Minecraft registries initialize,
 * so sister-mod IDs are intentionally inlined here rather than referencing {@code CetSoftBridge} (which touches
 * {@code BuiltInRegistries} / {@code FusionItems}). {@code scripts/ci_static_checks.py} verifies parity.
 */
public final class FusionMixinConfigPlugin implements IMixinConfigPlugin {
    public static final Set<String> CET_GATED_MIXINS = Set.of(
        "CetCleanseMechanicsBridgeMixin",
        "CetCleanseQuotesBridgeMixin",
        "CetDisenchantBridgeMixin",
        "CetMaterialQuotesBridgeMixin",
        "CetQuickMoveBridgeMixin",
        "CetXpQuotesBridgeMixin",
        "CetCleanseListPopulateMixin",
        "CetCleanseOptionModelMixin",
        "CetEnchantmentDisplayNamesMixin"
    );

    private static final AtomicInteger APPLIED_CET_MIXINS = new AtomicInteger(0);
    private boolean cetLoaded;

    public static int appliedCetMixinCount() {
        return APPLIED_CET_MIXINS.get();
    }

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        this.cetLoaded = loader.isModLoaded("chiseled-enchanting-table")
            || loader.isModLoaded("chiseled_enchanting_table");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    private static String extractSimpleName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    public static boolean isCetGatedMixinName(String mixinClassName) {
        return CET_GATED_MIXINS.contains(extractSimpleName(mixinClassName));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (isCetGatedMixinName(mixinClassName)) {
            return this.cetLoaded;
        }
        if (targetClassName != null && targetClassName.startsWith("chiseled_enchanting_table.")) {
            return this.cetLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (isCetGatedMixinName(mixinClassName)) {
            APPLIED_CET_MIXINS.incrementAndGet();
        }
    }
}
