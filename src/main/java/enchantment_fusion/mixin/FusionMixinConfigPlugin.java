package enchantment_fusion.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin that gates optional sister-mod {@code @Pseudo} mixins targeting
 * {@code 纂刻附魔台 (chiseled-enchanting-table)} so standalone startup has zero missing-class chatter.
 */
public final class FusionMixinConfigPlugin implements IMixinConfigPlugin {
    private boolean cetLoaded;

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

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName != null && targetClassName.startsWith("chiseled_enchanting_table.")) {
            return this.cetLoaded;
        }
        if (mixinClassName != null && mixinClassName.contains(".Cet")) {
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
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
