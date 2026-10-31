package enchantment_fusion.smoke;

import com.google.gson.JsonPrimitive;
import enchantment_fusion.compat.CetCleanseOperations;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.data.FusionDataLoader;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/** Prepared disposable-world probes; a pass does not establish multiplayer/mod compatibility. */
final class DurabilityEconomyProbe {
    private static final FusedPair GROWTH = FusedPair.clean("minecraft:mending", 1, "minecraft:unbreaking", 1);
    private static final FusedPair RESCUE = GROWTH.flipped();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private DurabilityEconomyProbe() {}

    static void exerciseGrowth(ServerPlayer player) throws Exception {
        ServerLevel level = player.level();
        checkCarriers(level, GROWTH);
        Path path = FabricLoader.getInstance().getConfigDir().resolve("enchantment_fusion/overrides.json");
        byte[] savedConfig = Files.exists(path) ? Files.readAllBytes(path) : null;
        ItemStack[] saved = saveSlots(player);
        int oldLevel = player.experienceLevel, oldTotal = player.totalExperience, oldDelay = player.takeXpDelay;
        float oldProgress = player.experienceProgress;
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        try {
            require(FusionConfigStore.update("signatures", List.of("signatures", DurabilityEconomy.GROWTH, "growth_chance_percent"),
                new JsonPrimitive(100), false, FusionConfigStore.revision()).success(), "Growth chance test override must save");
            clearSlots(player);
            player.setGameMode(GameType.SURVIVAL);
            ItemStack pick = fused(level, new ItemStack(Items.IRON_PICKAXE), GROWTH);
            int originalMaximum = pick.getMaxDamage();
            pick.setDamageValue(20);
            player.setItemSlot(EquipmentSlot.MAINHAND, pick);
            touchOrb(player, 2);
            require(pick.getDamageValue() == 16, "A real orb must use native Mending and spend its own XP");
            require(pick.getMaxDamage() > originalMaximum
                && pick.getOrDefault(FusionDataComponents.DURABILITY_GROWTH_BASE, 0) == originalMaximum,
                "A real repair must grow this particular item's maximum");
            int grownMaximum = pick.getMaxDamage();
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            output.store("item", ItemStack.CODEC, pick);
            ItemStack roundTrip = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult())
                .read("item", ItemStack.CODEC).orElseThrow();
            require(roundTrip.getMaxDamage() == grownMaximum
                && roundTrip.getOrDefault(FusionDataComponents.DURABILITY_GROWTH_BASE, 0) == originalMaximum,
                "The growth and original cap base must survive a codec round trip");
            var extracted = CetCleanseOperations.extractFusedPairToBook(pick, 0);
            require(extracted != null && extracted.updatedTarget().getMaxDamage() == grownMaximum
                && extracted.updatedTarget().getOrDefault(FusionDataComponents.DURABILITY_GROWTH_BASE, 0) == originalMaximum,
                "Extracting the fusion must not remove permanent growth");

            ItemStack nonNative = new ItemStack(Items.APPLE);
            nonNative.set(DataComponents.MAX_STACK_SIZE, 1);
            nonNative.set(DataComponents.MAX_DAMAGE, 100);
            nonNative.set(DataComponents.DAMAGE, 20);
            FusionStationOperations.applyFusedPairToGear(nonNative, GROWTH, level.registryAccess());
            var mending = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("mending")));
            require(!mending.value().canEnchant(nonNative), "Fixture must actually be non-native for Mending");
            player.setItemSlot(EquipmentSlot.MAINHAND, nonNative);
            touchOrb(player, 2);
            require(nonNative.getDamageValue() == 16 && nonNative.getMaxDamage() > 100,
                "A damaged non-native carrier must use remaining real orb XP and can grow");
        } finally {
            restoreSlots(player, saved);
            player.experienceLevel = oldLevel; player.totalExperience = oldTotal;
            player.experienceProgress = oldProgress; player.takeXpDelay = oldDelay;
            player.setGameMode(oldMode);
            if (savedConfig == null) Files.deleteIfExists(path); else Files.write(path, savedConfig);
            new FusionDataLoader().onResourceManagerReload(level.getServer().getResourceManager());
        }
    }

    static void exerciseRescue(ServerPlayer player) {
        ServerLevel level = player.level();
        checkCarriers(level, RESCUE);
        ItemStack[] saved = saveSlots(player);
        int oldLevel = player.experienceLevel, oldTotal = player.totalExperience, oldDelay = player.takeXpDelay;
        float oldProgress = player.experienceProgress, oldHealth = player.getHealth();
        int oldFood = player.getFoodData().getFoodLevel();
        float oldSaturation = player.getFoodData().getSaturationLevel();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        AABB area = player.getBoundingBox().inflate(8.0D);
        Set<UUID> existing = new HashSet<>();
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) existing.add(orb.getUUID());
        try {
            clearSlots(player);
            player.setGameMode(GameType.SURVIVAL);
            ItemStack tool = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            int max = tool.getMaxDamage();
            tool.setDamageValue(max - 3);
            player.setItemSlot(EquipmentSlot.MAINHAND, tool);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            damageUntilChanged(player, tool);
            int paid = 0;
            for (EquipmentSlot slot : ARMOR) {
                ItemStack armor = player.getItemBySlot(slot);
                require(!armor.isEmpty() && armor.getDamageValue() < armor.getMaxDamage(), "Conversion must not break other armor");
                paid += armor.getDamageValue();
            }
            List<ExperienceOrb> generated = level.getEntitiesOfClass(ExperienceOrb.class, area,
                orb -> !existing.contains(orb.getUUID()));
            require(paid == 8 && generated.size() == 1 && generated.getFirst().getValue() == 2,
                "A real 1% crossing must pay 4 armor durability per generated XP");
            for (ExperienceOrb orb : generated) orb.discard();

            // Four almost-broken pieces have only four safe points, below the cost of eight.
            ItemStack insufficient = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            insufficient.setDamageValue(insufficient.getMaxDamage() - 3);
            player.setItemSlot(EquipmentSlot.MAINHAND, insufficient);
            for (EquipmentSlot slot : ARMOR) {
                ItemStack armor = player.getItemBySlot(slot);
                armor.setDamageValue(armor.getMaxDamage() - 2);
            }
            damageUntilChanged(player, insufficient);
            for (EquipmentSlot slot : ARMOR) {
                ItemStack armor = player.getItemBySlot(slot);
                require(armor.getDamageValue() == armor.getMaxDamage() - 2,
                    "An insufficient safe armor pool must pay nothing");
            }
            require(level.getEntitiesOfClass(ExperienceOrb.class, area, orb -> !existing.contains(orb.getUUID())).isEmpty(),
                "An insufficient safe armor pool must not generate XP");

            // No eligible armor: crossing is free of both armor payment and generated XP.
            for (EquipmentSlot slot : ARMOR) player.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack unprotected = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            unprotected.setDamageValue(unprotected.getMaxDamage() - 3);
            player.setItemSlot(EquipmentSlot.MAINHAND, unprotected);
            damageUntilChanged(player, unprotected);
            require(level.getEntitiesOfClass(ExperienceOrb.class, area, orb -> !existing.contains(orb.getUUID())).isEmpty(),
                "No other armor must skip the 1% XP stage");

            ItemStack almostBroken = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            almostBroken.setDamageValue(almostBroken.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.MAINHAND, almostBroken);
            setXp(player, 4);
            player.getFoodData().setSaturation(0.0F); player.getFoodData().setFoodLevel(0);
            damageUntilChanged(player, almostBroken);
            require(!almostBroken.isEmpty() && almostBroken.getDamageValue() == almostBroken.getMaxDamage() - 8
                && player.experienceLevel == 0 && player.experienceProgress < 0.0001F,
                "An impending break should consume four real XP to restore eight durability");
            require(player.getHealth() == oldHealth, "Rescue must never charge health");

            ItemStack hungry = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            hungry.setDamageValue(hungry.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.MAINHAND, hungry);
            setXp(player, 0);
            player.getFoodData().setSaturation(0.5F); player.getFoodData().setFoodLevel(2);
            damageUntilChanged(player, hungry);
            require(!hungry.isEmpty() && hungry.getDamageValue() == hungry.getMaxDamage() - 2
                && player.getFoodData().getSaturationLevel() < 0.001F && player.getFoodData().getFoodLevel() == 0,
                "When XP is absent, saturation and then hunger must fund only actually paid repair");
            require(player.getHealth() == oldHealth, "Food-funded rescue must never charge health");

            ItemStack doomed = fused(level, new ItemStack(Items.IRON_PICKAXE), RESCUE);
            doomed.setDamageValue(doomed.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.MAINHAND, doomed);
            setXp(player, 0);
            player.getFoodData().setSaturation(0.5F); player.getFoodData().setFoodLevel(0);
            damageUntilChanged(player, doomed);
            require(doomed.isEmpty() && Math.abs(player.getFoodData().getSaturationLevel() - 0.5F) < 0.001F,
                "Insufficient resources for one durability must leave the item broken without charging saturation");
        } finally {
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
                if (!existing.contains(orb.getUUID())) orb.discard();
            }
            restoreSlots(player, saved);
            player.experienceLevel = oldLevel; player.totalExperience = oldTotal;
            player.experienceProgress = oldProgress; player.takeXpDelay = oldDelay;
            player.getFoodData().setFoodLevel(oldFood); player.getFoodData().setSaturation(oldSaturation);
            player.setHealth(oldHealth);
            player.setGameMode(oldMode);
        }
    }

    private static void damageUntilChanged(ServerPlayer player, ItemStack item) {
        int initial = item.getDamageValue();
        for (int i = 0; i < 256 && !item.isEmpty() && item.getDamageValue() == initial; i++) {
            item.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        require(item.isEmpty() || item.getDamageValue() != initial,
            "Expected an actual durability change after vanilla Unbreaking checks");
    }
    private static void touchOrb(ServerPlayer player, int value) {
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), value);
        try {
            require(player.level().addFreshEntity(orb), "XP orb fixture must enter the world");
            player.takeXpDelay = 0;
            orb.playerTouch(player);
        } finally { orb.discard(); }
    }
    private static void setXp(ServerPlayer player, int amount) {
        player.experienceLevel = 0; player.totalExperience = amount; player.experienceProgress = amount / 7.0F;
    }
    private static ItemStack[] saveSlots(ServerPlayer player) {
        ItemStack[] result = new ItemStack[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) result[i] = player.getItemBySlot(SLOTS[i]);
        return result;
    }
    private static void clearSlots(ServerPlayer player) { for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY); }
    private static void restoreSlots(ServerPlayer player, ItemStack[] saved) {
        for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], saved[i]);
    }
    private static ItemStack fused(ServerLevel level, ItemStack stack, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
    }
    private static void checkCarriers(ServerLevel level, FusedPair pair) {
        var registries = level.registryAccess();
        var primary = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : List.of(false, true)) {
            SimpleContainer container = new SimpleContainer(4);
            if (direct) {
                container.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                container.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else container.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            for (ItemStack candidate : List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_BOOTS),
                new ItemStack(Items.BOW), new ItemStack(Items.SHIELD))) {
                container.setItem(FusionStationOperations.SLOT_TARGET, candidate);
                require(FusionStationOperations.inspect(container, registries, FusionStationOperations.TAB_SOCKET, a, b).executable(),
                    "Both station paths must accept damageable equipment");
            }
            ItemStack nonDamageableBow = new ItemStack(Items.BOW);
            nonDamageableBow.remove(DataComponents.MAX_DAMAGE);
            nonDamageableBow.remove(DataComponents.DAMAGE);
            container.setItem(FusionStationOperations.SLOT_TARGET, nonDamageableBow);
            require(FusionStationOperations.inspect(container, registries, FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station paths must reject non-damageable items");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
