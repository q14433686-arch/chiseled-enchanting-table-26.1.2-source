package enchantment_fusion.smoke;

import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Prepared server-side XP/stack fixture, including an orb touch; not a multiplayer playtest. */
final class ReplenishProbe {
    private ReplenishProbe() {}

    static void exercise(ServerPlayer player) {
        Inventory inv = player.getInventory();
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) saved.add(inv.getItem(i).copy());
        int savedLevel = player.experienceLevel, savedTotal = player.totalExperience, savedDelay = player.takeXpDelay;
        float savedProgress = player.experienceProgress;
        try {
            for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, ItemStack.EMPTY);
            player.experienceLevel = 0; player.totalExperience = 0; player.experienceProgress = 0;
            ItemStack bread = transmuted(Items.BREAD);
            player.setItemInHand(InteractionHand.MAIN_HAND, bread);
            ServerLevel level = (ServerLevel) player.level();
            ExperienceOrb orb = new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), 22);
            level.addFreshEntity(orb);
            player.takeXpDelay = 0;
            orb.playerTouch(player);
            require(player.totalExperience == 22 && bread.getCount() == 2,
                "A real XP orb must credit all 22 XP and replenish one matching item");

            player.giveExperiencePoints(11);
            ItemStack split = bread.split(1);
            player.setItemInHand(InteractionHand.OFF_HAND, split);
            player.giveExperiencePoints(11);
            require(bread.getCount() + split.getCount() == 3,
                "Splitting the same component group must not duplicate progress");
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            ItemStack apples = transmuted(Items.APPLE);
            player.setItemInHand(InteractionHand.MAIN_HAND, apples);
            player.giveExperiencePoints(21);
            ItemStack fish = transmuted(Items.COOKED_COD);
            player.setItemInHand(InteractionHand.MAIN_HAND, fish);
            player.giveExperiencePoints(1);
            require(fish.getCount() == 1, "Different item groups must not share progress");
            player.setItemInHand(InteractionHand.MAIN_HAND, apples);
            player.giveExperiencePoints(1);
            require(apples.getCount() == 2, "The original group must retain its own progress");

            ItemStack potion = transmuted(Items.POTION);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion);
            player.giveExperiencePoints(44);
            require(countMatching(inv, potion) == 2, "An unstackable potion must create an identical separate item");

            ItemStack capped = transmuted(Items.COOKED_BEEF);
            capped.setCount(64);
            player.setItemInHand(InteractionHand.MAIN_HAND, capped);
            player.giveExperiencePoints(100);
            capped.setCount(63);
            player.giveExperiencePoints(1);
            require(capped.getCount() == 63, "XP earned at the group cap must not be banked for later");

            // A bottle spends actual XP and an actual bottle; a paid amount can never also remain
            // fully in the player's XP balance. First earn four, spend them, then earn four more.
            for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, ItemStack.EMPTY);
            player.experienceLevel = 0; player.totalExperience = 0; player.experienceProgress = 0;
            ItemStack bottle = transmuted(Items.GLASS_BOTTLE);
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            player.giveExperiencePoints(4);
            player.giveExperiencePoints(-4);
            player.giveExperiencePoints(4);
            require(countItem(inv, Items.EXPERIENCE_BOTTLE) == 0 && !bottle.isEmpty(),
                "Insufficient spendable XP must not produce a bottle");
            player.giveExperiencePoints(4);
            require(countItem(inv, Items.EXPERIENCE_BOTTLE) == 1 && player.experienceLevel == 0
                && player.experienceProgress < 0.001F, "Conversion must spend eight XP and a bottle once");
        } finally {
            for (int i = 0; i < saved.size(); i++) inv.setItem(i, saved.get(i));
            player.experienceLevel = savedLevel; player.totalExperience = savedTotal;
            player.experienceProgress = savedProgress; player.takeXpDelay = savedDelay;
        }
    }

    private static ItemStack transmuted(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:mending", 1));
        return stack;
    }

    private static int countMatching(Inventory inv, ItemStack sample) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample)) count += stack.getCount();
        }
        return count;
    }

    private static int countItem(Inventory inv, Item item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) count += inv.getItem(i).getCount();
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
