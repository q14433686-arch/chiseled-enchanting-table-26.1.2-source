package chiseled_enchanting_table.item;

import chiseled_enchanting_table.registry.SoundRegistry;
import java.util.function.Consumer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Illustrated in-game guide tome ({@code chiseled_enchanting_table:arcane_grimoire}).
 * Keeps {@code src/main} completely free of client-only imports while delegating screen opening
 * through {@link #CLIENT_OPEN_ACTION} registered in {@code ChiseledEnchantingTableClient}.
 */
public class ArcaneGrimoireItem extends Item {
    public static volatile Consumer<Player> CLIENT_OPEN_ACTION = player -> {};

    public ArcaneGrimoireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            level.playSound(
                player,
                player.blockPosition(),
                SoundRegistry.GRIMOIRE_OPEN,
                SoundSource.PLAYERS,
                0.75F,
                0.96F + level.getRandom().nextFloat() * 0.08F
            );
            player.awardStat(Stats.ITEM_USED.get(this));
        } else {
            CLIENT_OPEN_ACTION.accept(player);
        }
        return InteractionResult.SUCCESS;
    }
}
