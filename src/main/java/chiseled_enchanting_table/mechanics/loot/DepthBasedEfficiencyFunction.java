package chiseled_enchanting_table.mechanics.loot;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

/**
 * Scales an injected Abandoned Mineshaft Efficiency book from II (near sea level) up to V
 * (near {@code level.getMinY()}), preserving upstream's {@code DepthBasedEfficiency} formula.
 */
public class DepthBasedEfficiencyFunction extends LootItemConditionalFunction {
    public static final MapCodec<DepthBasedEfficiencyFunction> MAP_CODEC =
        MapCodec.unit(DepthBasedEfficiencyFunction::new);

    private static final ResourceKey<Enchantment> EFFICIENCY_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("efficiency"));

    public DepthBasedEfficiencyFunction() {
        this(List.of());
    }

    public DepthBasedEfficiencyFunction(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends LootItemConditionalFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return stack;
        ServerLevel level = context.getLevel();
        Holder<Enchantment> efficiency = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .get(EFFICIENCY_KEY)
            .orElse(null);
        if (efficiency == null) return stack;

        int levelValue = 2;
        Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        int topY = level.getSeaLevel();
        int bottomY = level.getMinY();
        if (origin != null && topY > bottomY) {
            int rounded = Math.round(2.0F + (float)(topY - origin.y) / (float)(topY - bottomY) * 3.0F);
            levelValue = Math.max(2, Math.min(5, rounded));
        }

        int finalLevel = levelValue;
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(efficiency, finalLevel));
        return stack;
    }
}
