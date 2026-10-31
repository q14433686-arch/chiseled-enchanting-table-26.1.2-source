package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Carrier and dry-input fixtures only; an actual underwater multi-tick playtest remains required. */
final class UnderwaterMiningProbe {
    private UnderwaterMiningProbe() {}

    static void exercise(ServerPlayer player) {
        var registries = player.level().registryAccess();
        FusedPair spend = FusedPair.clean("minecraft:respiration", 3, "minecraft:aqua_affinity", 1);
        FusedPair bubble = spend.flipped();
        Holder<Enchantment> respiration = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("respiration")));
        Holder<Enchantment> aqua = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("aqua_affinity")));
        for (FusedPair pair : new FusedPair[]{spend, bubble}) {
            for (ItemStack supported : new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_AXE),
                new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.DIAMOND_HOE)}) {
                require(DirectionalHostRules.validateNewOrChanged(supported, pair) == RejectReason.NONE,
                    "Helmet and actual mining tools must be accepted: " + pair + " / " + supported);
            }
            ItemStack shield = new ItemStack(Items.SHIELD);
            require(DirectionalHostRules.validateNewOrChanged(shield, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "A shield must not accept these mining directions");
            var socket = new SimpleContainer(4);
            socket.setItem(FusionStationOperations.SLOT_TARGET, shield);
            socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            var denied = FusionStationOperations.inspect(socket, registries, FusionStationOperations.TAB_SOCKET, -1, -1);
            require(!denied.executable() && denied.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Fused-book socketing must reject the unsupported host");
            socket.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_PICKAXE));
            require(FusionStationOperations.inspect(socket, registries, FusionStationOperations.TAB_SOCKET, -1, -1).executable(),
                "Fused-book socketing must accept a pickaxe even though its enchants are normally helmet-only");

            var direct = new SimpleContainer(4);
            direct.setItem(FusionStationOperations.SLOT_TARGET, shield.copy());
            direct.setItem(FusionStationOperations.SLOT_PRIMARY,
                FusionStationOperations.createSingleEnchantedBook(UnderwaterMiningSkills.isSpendPair(pair) ? respiration : aqua, pair.primaryLevel()));
            direct.setItem(FusionStationOperations.SLOT_SECONDARY,
                FusionStationOperations.createSingleEnchantedBook(UnderwaterMiningSkills.isSpendPair(pair) ? aqua : respiration, pair.secondaryLevel()));
            var directDenied = FusionStationOperations.inspect(direct, registries, FusionStationOperations.TAB_SOCKET, 0, 1);
            require(!directDenied.executable() && directDenied.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Direct fusion must reject the same unsupported host");
            direct.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_PICKAXE));
            require(FusionStationOperations.inspect(direct, registries, FusionStationOperations.TAB_SOCKET, 0, 1).executable(),
                "Direct fusion must accept the supported tool");

            ItemStack legacyShield = new ItemStack(Items.SHIELD);
            legacyShield.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(pair));
            String id = UnderwaterMiningSkills.isSpendPair(pair)
                ? UnderwaterMiningSkills.SPEND_SIGNATURE : UnderwaterMiningSkills.BUBBLE_SIGNATURE;
            require(!UnderwaterMiningSkills.applies(pair, legacyShield, id)
                && legacyShield.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair),
                "An existing unsupported host keeps its data and old runtime path");
        }
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            int before = player.getAirSupply();
            require(!UnderwaterMiningSkills.activate(player) && player.getAirSupply() == before,
                "Dry input must never spend air");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
