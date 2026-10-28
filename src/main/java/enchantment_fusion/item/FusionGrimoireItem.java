package enchantment_fusion.item;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.menu.FusionNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * {@code 《融合秘典》 (Fusion Grimoire)} — handheld double-page arcane manual for querying
 * binary Signature Pairs, Cross-Domain Transmutations, Aberrant Mutations, and progressive
 * three-stage discoveries.
 */
public final class FusionGrimoireItem extends Item {
    public FusionGrimoireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            CompendiumDiscoveryTracker.scanPlayerInventory(serverPlayer);
            FusionNetworking.sendCompendiumSync(
                serverPlayer,
                CompendiumDiscoveryTracker.encodeStates(serverPlayer.getUUID())
            );
            FusionNetworking.sendOpenGrimoire(serverPlayer, 0);
            level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.85F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
