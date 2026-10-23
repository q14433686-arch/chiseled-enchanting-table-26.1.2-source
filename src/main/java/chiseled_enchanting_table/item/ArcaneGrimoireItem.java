package chiseled_enchanting_table.item;

import java.util.function.Consumer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Illustrated in-game guide tome ({@code chiseled_enchanting_table:arcane_grimoire}).
 * Uses native Minecraft {@link SoundEvents#BOOK_PAGE_TURN} audio and delegates client screen
 * opening via {@link #CLIENT_OPEN_ACTION} without introducing client imports into {@code src/main}.
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
                SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS,
                0.85F,
                0.95F + level.getRandom().nextFloat() * 0.10F
            );
            player.awardStat(Stats.ITEM_USED.get(this));
        } else {
            CLIENT_OPEN_ACTION.accept(player);
        }
        return InteractionResult.SUCCESS;
    }
}
