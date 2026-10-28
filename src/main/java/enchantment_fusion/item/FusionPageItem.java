package enchantment_fusion.item;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * {@code 《融合残页》 (Tattered Fusion Page)} — world chest & Librarian trade research scroll
 * modeled after Herbcraft's {@code TatteredPageItem}, unlocking progressive Grimoire entries:
 * <ul>
 *   <li>Tier 0 ({@code COMMON}): Unlocks an unknown entry to Stage 1 ({@code 初识})</li>
 *   <li>Tier 1 ({@code RARE}): Masters a Stage 1 / unknown entry to Stage 2 ({@code 通晓})</li>
 *   <li>Tier 2 ({@code ABERRANT}): Masters an unstable Aberrant entry</li>
 * </ul>
 */
public final class FusionPageItem extends Item {
    public FusionPageItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createStack(int tier, int count) {
        int clamped = Math.max(0, Math.min(2, tier));
        ItemStack st = new ItemStack(FusionItems.FUSION_TATTERED_PAGE, count);
        if (clamped > 0) {
            st.set(FusionDataComponents.PAGE_TIER, clamped);
            st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return st;
    }

    @Override
    public Component getName(ItemStack stack) {
        int tier = stack.getOrDefault(FusionDataComponents.PAGE_TIER, 0);
        return switch (tier) {
            case 1 -> Component.translatable("item.enchantment_fusion.fusion_tattered_page.rare");
            case 2 -> Component.translatable("item.enchantment_fusion.fusion_tattered_page.aberrant");
            default -> super.getName(stack);
        };
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        int tier = stack.getOrDefault(FusionDataComponents.PAGE_TIER, 0);
        if (!CompendiumDiscoveryTracker.consumeTatteredPage(serverPlayer, tier)) {
            return InteractionResult.FAIL;
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.15F);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.45F, 1.35F);
        return InteractionResult.SUCCESS;
    }
}
