package chiseled_enchanting_table.mixin.villager;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Audit follow-up: the Infinity trade restriction used to be hooked only to the
 * right-click interaction ({@code UseEntityCallback}), so offers generated at spawn
 * (wandering traders) or opened programmatically by other mods could expose an
 * Infinity book before any interaction happened. Sanitizing on every server-side
 * {@code getOffers()} return covers offer creation itself — generation happens
 * inside this method — which makes the restriction hold on every path that can
 * display or serialize the offers. The rewrite is idempotent and gated by
 * {@code restrict_villager_infinity_books} inside the sanitize method.
 */
@Mixin(AbstractVillager.class)
public abstract class VillagerInfinitySanitizeMixin {

    @Inject(method = "getOffers", at = @At("RETURN"))
    private void chiseledEnchantingTable$sanitizeInfinityOffers(
        CallbackInfoReturnable<MerchantOffers> cir
    ) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        Level level = self.level();
        if (level != null && !level.isClientSide()) {
            ChiseledEnchantingTable.sanitizeVillagerInfinityOffers(level, cir.getReturnValue());
        }
    }
}
