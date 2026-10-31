package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.LoyaltyGuard;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

/** Prepared for the eventual integrated game acceptance; this has not been run yet. */
final class LoyaltyGuardProbe {
    private static final FusedPair GUARD = FusedPair.clean("minecraft:loyalty", 3, "minecraft:protection", 4);
    private static final FusedPair OTHER = FusedPair.clean("minecraft:mending", 1, "minecraft:aqua_affinity", 1);
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private LoyaltyGuardProbe() {}

    static void exercise(ServerPlayer player) {
        ServerLevel level = player.level();
        Holder<Enchantment> loyalty = enchant(level, "loyalty");
        Holder<Enchantment> protection = enchant(level, "protection");
        Holder<Enchantment> unbreaking = enchant(level, "unbreaking");
        exerciseData(player, level, loyalty, protection, unbreaking);
        exerciseIndependentSecondaryPreservation(player, unbreaking);

        List<ItemStack> savedSlots = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) savedSlots.add(player.getItemBySlot(slot));
        List<MobEffectInstance> savedEffects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        float oldHealth = player.getHealth();
        float oldAbsorption = player.getAbsorptionAmount();
        int oldInvulnerability = player.invulnerableTime;
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        Zombie hostile = new Zombie(level);
        Zombie denied = new Zombie(level);
        Wolf pet = new Wolf(EntityType.WOLF, level);
        Wolf neutral = new Wolf(EntityType.WOLF, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Cow cow = new Cow(EntityType.COW, level);
        WitherBoss boss = new WitherBoss(EntityType.WITHER, level);
        List<LivingEntity> fixtures = List.of(hostile, denied, pet, neutral, villager, cow, boss);
        Set<UUID> deniedDamage = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deniedDamage.contains(entity.getUUID()));
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.removeAllEffects();
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
            FusionStationOperations.applyFusedPairToGear(boots, GUARD.withPrimaryLevel(2), level.registryAccess());
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            int offset = 0;
            for (LivingEntity fixture : fixtures) {
                fixture.setPos(player.getX() + 2.0D + 0.35D * offset++, player.getY(), player.getZ());
                if (fixture instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                require(level.addFreshEntity(fixture), "Fixture entity must be admitted to the level");
            }
            pet.setTame(true, false);
            require(LoyaltyGuard.eligibleTarget(player, hostile), "Ordinary hostile should be eligible");
            require(LoyaltyGuard.eligibleTarget(player, boss), "A hostile boss must not be excluded by category");
            require(!LoyaltyGuard.eligibleTarget(player, pet) && !LoyaltyGuard.eligibleTarget(player, villager)
                && !LoyaltyGuard.eligibleTarget(player, cow) && !LoyaltyGuard.eligibleTarget(player, neutral),
                "Tame, villager, passive and non-attacking neutral entities must be protected");
            neutral.setTarget(player);
            require(LoyaltyGuard.eligibleTarget(player, neutral), "A neutral mob attacking the player can be eligible");
            neutral.setTarget(null);

            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(0);
            player.invulnerableTime = 0;
            deniedDamage.add(player.getUUID());
            require(!player.hurtServer(level, level.damageSources().generic(), 1000.0F), "Cancelled damage must not land");
            deniedDamage.remove(player.getUUID());
            require(pair(player.getItemBySlot(EquipmentSlot.FEET)).primaryLevel() == 2,
                "Cancelled damage must not charge the fusion");
            require(LoyaltyGuard.allowDeath(level, player, level.damageSources().fellOutOfWorld()),
                "Totem-bypassing damage must be left to the normal death path");
            require(pair(player.getItemBySlot(EquipmentSlot.FEET)).primaryLevel() == 2,
                "A bypassing source must not charge the fusion");

            deniedDamage.add(denied.getUUID());
            player.setHealth(1.0F);
            player.setAbsorptionAmount(0);
            player.invulnerableTime = 0;
            require(player.hurtServer(level, level.damageSources().generic(), 20.0F), "Lethal damage must be processed");
            require(player.isAlive() && player.getHealth() == 1.0F, "The equipped fusion must save its owner");
            require(pair(player.getItemBySlot(EquipmentSlot.FEET)).primaryLevel() == 1,
                "A successful rescue must consume exactly one level");
            require(player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && player.getOffhandItem().getCount() == 1,
                "The real totem must not be consumed");
            require(hostile.isDeadOrDying() && boss.isDeadOrDying(), "Ordinary hostile and vulnerable boss must be executed");
            require(denied.isAlive() && pet.isAlive() && neutral.isAlive() && villager.isAlive() && cow.isAlive(),
                "Damage cancellation and protected entities must be respected");

            player.setHealth(1.0F);
            player.setAbsorptionAmount(0);
            player.invulnerableTime = 0;
            require(player.hurtServer(level, level.damageSources().generic(), 20.0F), "Second lethal hit must be processed");
            ItemStack exhausted = player.getItemBySlot(EquipmentSlot.FEET);
            require(player.isAlive() && exhausted.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty(),
                "The last charge must save once and then remove the exhausted fusion");
            require(exhausted.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(protection) == 4,
                "The unprojected Protection secondary must survive exhaustion at its exact level");
            require(player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "The second save must still not consume the totem");
        } finally {
            deniedDamage.clear();
            for (LivingEntity fixture : fixtures) fixture.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], savedSlots.get(i));
            player.removeAllEffects();
            for (MobEffectInstance effect : savedEffects) player.addEffect(effect);
            player.setGameMode(oldMode);
            player.setHealth(oldHealth);
            player.setAbsorptionAmount(oldAbsorption);
            player.invulnerableTime = oldInvulnerability;
        }
    }

    private static void exerciseData(ServerPlayer player, ServerLevel level, Holder<Enchantment> loyalty,
                                     Holder<Enchantment> protection, Holder<Enchantment> unbreaking) {
        ItemStack trident = new ItemStack(Items.TRIDENT);
        trident.enchant(unbreaking, 3);
        FusionStationOperations.applyFusedPairToGear(trident, GUARD, level.registryAccess());
        var list = trident.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        trident.set(FusionDataComponents.FUSED_PAIRS, list.append(OTHER));
        require(trident.getOrDefault(FusionDataComponents.LOYALTY_GUARD_PROJECTION, 0) == 3,
            "New native Loyalty projection must be marked");
        ItemStack p2 = LoyaltyGuard.afterUse(trident, 0, level.registryAccess());
        require(p2 != null && pair(p2).primaryLevel() == 2 && enchantLevel(p2, loyalty) == 2,
            "A marked native projection must track the charged fusion");
        require(pair(trident).primaryLevel() == 3 && enchantLevel(trident, loyalty) == 3,
            "Preparing payment must not mutate the input");
        ItemStack p1 = LoyaltyGuard.afterUse(p2, 0, level.registryAccess());
        ItemStack exhausted = LoyaltyGuard.afterUse(p1, 0, level.registryAccess());
        require(exhausted != null && enchantLevel(exhausted, loyalty) == 0 && enchantLevel(exhausted, protection) == 4
            && enchantLevel(exhausted, unbreaking) == 3, "Exhaustion must remove only the proven projection and preserve levels");
        require(exhausted.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().equals(List.of(OTHER)),
            "Another fusion must survive unchanged");

        ItemStack legacy = new ItemStack(Items.DIAMOND_SWORD);
        legacy.enchant(loyalty, 3);
        legacy.enchant(protection, 2);
        legacy.enchant(unbreaking, 3);
        legacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(GUARD.withPrimaryLevel(1)));
        ItemStack kept = LoyaltyGuard.afterUse(legacy, 0, level.registryAccess());
        require(kept != null && enchantLevel(kept, loyalty) == 3 && enchantLevel(kept, protection) == 2
            && enchantLevel(kept, unbreaking) == 3, "Unmarked legacy or independent vanilla levels must never be debited");
        List<RetainedEnchant> retained = kept.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.of());
        require(retained.size() == 1 && retained.getFirst().level() == 4,
            "A conflicting secondary must be kept separately at its exact level");
        exerciseRetainedExtraction(player, kept.copy(), protection, unbreaking);
        FusionStationOperations.removeGearVanillaEnchant(kept, "minecraft:protection");
        require(LoyaltyGuard.materializeRetained(kept, level.registryAccess()) && enchantLevel(kept, protection) == 4
            && kept.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.of()).isEmpty(),
            "A preserved secondary should restore once its collision is gone");
    }

    private static void exerciseIndependentSecondaryPreservation(ServerPlayer player, Holder<Enchantment> unbreaking) {
        var registries = player.level().registryAccess();
        SimpleContainer inv = new SimpleContainer(4);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        FusedPair old = FusedPair.clean("minecraft:sharpness", 3, "minecraft:unbreaking", 3);
        sword.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(old));
        sword.enchant(unbreaking, 2); // An independent legacy same-name secondary.
        inv.setItem(FusionStationOperations.SLOT_TARGET, sword);
        inv.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD));
        require(FusionStationOperations.executeUnsocketOrSplitWithReason(inv, player, registries, 0, old.fingerprint()).success(),
            "A valid legacy fusion should be extractable");
        require(enchantLevel(sword, unbreaking) == 2 && inv.getItem(FusionStationOperations.SLOT_PRIMARY).is(enchantment_fusion.registry.FusionItems.FUSED_ENCHANTED_BOOK),
            "Extracting a fusion must not delete an independent native secondary");

        SimpleContainer shared = new SimpleContainer(4);
        ItemStack sword2 = new ItemStack(Items.DIAMOND_SWORD);
        FusedPair first = FusedPair.clean("minecraft:unbreaking", 2, "minecraft:sharpness", 3);
        FusedPair remaining = FusedPair.clean("minecraft:mending", 1, "minecraft:unbreaking", 2);
        sword2.enchant(unbreaking, 2);
        sword2.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(first).append(remaining));
        shared.setItem(FusionStationOperations.SLOT_TARGET, sword2);
        shared.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD));
        require(FusionStationOperations.executeUnsocketOrSplitWithReason(shared, player, registries, 0, first.fingerprint()).success()
            && enchantLevel(sword2, unbreaking) == 2
            && sword2.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().equals(List.of(remaining)),
            "A primary referenced by another legacy fusion must not be erased on extraction");

        SimpleContainer changed = new SimpleContainer(4);
        ItemStack sword3 = new ItemStack(Items.DIAMOND_SWORD);
        sword3.enchant(unbreaking, 3); // Different from the pair's level; not an untouched projection.
        sword3.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(first));
        changed.setItem(FusionStationOperations.SLOT_TARGET, sword3);
        changed.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD));
        require(FusionStationOperations.executeUnsocketOrSplitWithReason(changed, player, registries, 0, first.fingerprint()).success()
            && enchantLevel(sword3, unbreaking) == 3,
            "A native primary changed to another level must not be erased as a projection");
    }

    private static void exerciseRetainedExtraction(ServerPlayer player, ItemStack target,
                                                   Holder<Enchantment> protection, Holder<Enchantment> unbreaking) {
        SimpleContainer inv = new SimpleContainer(4);
        inv.setItem(FusionStationOperations.SLOT_TARGET, target);
        inv.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD, 2));
        RetainedEnchant retained = target.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).getFirst();
        EnchantmentFusionMenu menu = new EnchantmentFusionMenu(0, player.getInventory(), inv,
            ContainerLevelAccess.NULL, BlockPos.ZERO);
        try {
            require(!menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0, "stale")
                && target.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).size() == 1,
                "A stale preserved-entry token must do nothing");
            inv.setItem(FusionStationOperations.SLOT_PRIMARY, new ItemStack(Items.BOOK));
            var occupied = menu.performActionWithResult(player, StationAction.EXTRACT_RETAINED, 0, 0, RetainedEnchant.selectionToken(List.of(retained), 0));
            require(!occupied.success() && occupied.reason() == RejectReason.RETAINED_OUTPUT_OCCUPIED
                && inv.getItem(FusionStationOperations.SLOT_CATALYST).getCount() == 2,
                "An occupied output must reject before taking payment or modifying the item");
            inv.setItem(FusionStationOperations.SLOT_PRIMARY, ItemStack.EMPTY);
            RetainedEnchant missing = new RetainedEnchant("missing:unloaded_enchantment", 4);
            target.set(FusionDataComponents.RETAINED_ENCHANTS, List.of(missing));
            var absent = menu.performActionWithResult(player, StationAction.EXTRACT_RETAINED, 0, 0, RetainedEnchant.selectionToken(List.of(missing), 0));
            require(!absent.success() && absent.reason() == RejectReason.RETAINED_ENCHANTMENT_UNAVAILABLE
                && target.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).equals(List.of(missing))
                && inv.getItem(FusionStationOperations.SLOT_CATALYST).getCount() == 2,
                "A missing mod enchantment must remain preserved without payment");
            target.set(FusionDataComponents.RETAINED_ENCHANTS, List.of(retained));
            require(menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0, RetainedEnchant.selectionToken(List.of(retained), 0)),
                "The preserved secondary should be extractable through the station menu");
            ItemStack book = inv.getItem(FusionStationOperations.SLOT_PRIMARY);
            require(book.is(Items.ENCHANTED_BOOK)
                && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(protection) == 4,
                "The extracted book must retain the exact secondary level");
            require(enchantLevel(target, protection) == 2 && enchantLevel(target, unbreaking) == 3
                && target.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).isEmpty()
                && inv.getItem(FusionStationOperations.SLOT_CATALYST).getCount() == 1,
                "Extraction must preserve independent enchantments and deduct exactly one catalyst");
            require(!menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0, RetainedEnchant.selectionToken(List.of(retained), 0))
                && inv.getItem(FusionStationOperations.SLOT_PRIMARY) == book,
                "Replaying an extraction cannot create another book");

            // A delayed duplicate packet must not accidentally consume a second identical entry.
            inv.setItem(FusionStationOperations.SLOT_PRIMARY, ItemStack.EMPTY);
            target.set(FusionDataComponents.RETAINED_ENCHANTS, List.of(retained, retained));
            inv.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD, 2));
            String twoToken = RetainedEnchant.selectionToken(List.of(retained, retained), 0);
            require(menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0, twoToken),
                "The first of two identical preserved entries must be extractable");
            ItemStack firstBook = inv.getItem(FusionStationOperations.SLOT_PRIMARY);
            inv.setItem(FusionStationOperations.SLOT_PRIMARY, ItemStack.EMPTY);
            require(!menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0, twoToken)
                && target.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).size() == 1
                && inv.getItem(FusionStationOperations.SLOT_CATALYST).getCount() == 1,
                "An old token must not consume the remaining identical entry");
            require(menu.performAction(player, StationAction.EXTRACT_RETAINED, 0, 0,
                    RetainedEnchant.selectionToken(List.of(retained), 0))
                && inv.getItem(FusionStationOperations.SLOT_PRIMARY) != firstBook
                && inv.getItem(FusionStationOperations.SLOT_PRIMARY).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                    .getLevel(protection) == 4,
                "The second entry requires a fresh selection and keeps its exact level");
        } finally {
            menu.removed(player);
        }
    }

    private static FusedPair pair(ItemStack stack) {
        return stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst();
    }
    private static int enchantLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
    }
    private static Holder<Enchantment> enchant(ServerLevel level, String path) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
