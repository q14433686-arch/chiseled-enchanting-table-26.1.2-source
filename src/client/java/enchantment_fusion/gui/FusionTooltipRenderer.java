package enchantment_fusion.gui;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Renders the composite Enchantment Fusion tooltip lines on Fused Enchanted Books and
 * equipment bearing fused pairs or expanded Vessel Spirit slots.
 */
public final class FusionTooltipRenderer {
    private FusionTooltipRenderer() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            appendFusionTooltips(stack, tooltipContext.registries(), lines);
        });
    }

    public static boolean isShiftHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);
    }

    public static void appendFusionTooltips(
        ItemStack stack,
        HolderLookup.Provider registries,
        List<Component> lines
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        FusedEnchantmentList list = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        int bonus = VesselCapacityRules.vesselBonus(stack);
        if (list.isEmpty() && bonus <= 0 && !stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return;
        }

        if (VesselCapacityRules.isPotentialEquipmentHost(stack) && (!list.isEmpty() || bonus > 0)) {
            int used = list.size();
            int max = VesselCapacityRules.maxVesselSlots(stack);
            StringBuilder glyphs = new StringBuilder();
            for (int i = 0; i < max; i++) {
                if (i < used) {
                    glyphs.append(list.pairs().get(i).isAberrant() ? "\u25C9" : "\u25C8");
                } else {
                    glyphs.append("\u25C7");
                }
            }
            lines.add(Component.translatable(
                "tooltip.enchantment_fusion.vessel_status",
                glyphs.toString(),
                used,
                max
            ).withStyle(ChatFormatting.GOLD));
        }

        if (list.isEmpty() && stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.empty_fused_book")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        HostForm hostForm = HostForm.resolve(stack);
        boolean shift = isShiftHeld();

        for (FusedPair pair : list.pairs()) {
            Component primaryLabel = formatEnchantmentWithLevel(registries, pair.primaryId(), pair.primaryLevel());
            Component secondaryLabel = formatEnchantmentWithLevel(registries, pair.secondaryId(), pair.secondaryLevel());

            if (pair.isAberrant()) {
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_aberrant",
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.RED));
                lines.add(Component.translatable(
                    CetSoftBridge.isChiseledEnchantingTableLoaded()
                        ? "tooltip.enchantment_fusion.aberrant_cet_active"
                        : "tooltip.enchantment_fusion.aberrant_cet_required"
                ).withStyle(ChatFormatting.DARK_RED));
            } else {
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_header",
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            if (shift) {
                lines.add(buildMatrixSentence(pair, hostForm).copy().withStyle(ChatFormatting.AQUA));
            }
        }

        if (!list.isEmpty() && !shift) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static Component buildMatrixSentence(FusedPair pair, HostForm hostForm) {
        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.current();
        var trig = snap.resolve(pair.primaryId()).trigger();
        var pay = snap.resolve(pair.secondaryId()).payload();
        return Component.translatable(
            "tooltip.enchantment_fusion.matrix_sentence",
            Component.translatable(trig.translationKey()),
            Component.translatable(pay.effectKey(hostForm))
        );
    }

    public static Component buildClueSentence(FusedPair pair) {
        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.current();
        var trig = snap.resolve(pair.primaryId()).trigger();
        var pay = snap.resolve(pair.secondaryId()).payload();
        return Component.translatable(
            "tooltip.enchantment_fusion.clue_sentence",
            Component.translatable(trig.translationKey()),
            Component.translatable(pay.translationKey())
        );
    }

    public static boolean shouldShowUnlockedPreview(FusedPair pair) {
        return CompendiumDiscoveryTracker.isClientDiscovered(pair.primaryId(), pair.secondaryId());
    }

    public static Component formatEnchantmentWithLevel(
        HolderLookup.Provider registries,
        String enchantId,
        int level
    ) {
        Identifier parsed = Identifier.tryParse(enchantId);
        if (registries != null && parsed != null) {
            var optLookup = registries.lookup(Registries.ENCHANTMENT);
            if (optLookup.isPresent()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsed);
                var optHolder = optLookup.get().get(key);
                if (optHolder.isPresent()) {
                    Holder<Enchantment> holder = optHolder.get();
                    return Enchantment.getFullname(holder, level);
                }
            }
        }
        String fallbackName = parsed != null ? parsed.getPath().replace('_', ' ') : enchantId;
        return Component.literal(fallbackName + " " + toRoman(level));
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
