package enchantment_fusion.gui;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Renders Enchantment Fusion tooltips:
 * <ul>
 *   <li>Renders Cross-Domain Transmutations stored in {@link FusionDataComponents#TRANSMUTED_ENCHANTS}
 *       (API-01: never polluting vanilla slot-based {@code DataComponents.ENCHANTMENTS}) as well as
 *       legacy cross-domain items, expanding origin + mechanics on {@code [Shift]}.</li>
 *   <li>Renders Vessel Slot badges ({@code ◇/◈/◉}), Signature Resonance titles, and Aberrant Fusion warnings.</li>
 * </ul>
 */
public final class FusionTooltipRenderer {
    private FusionTooltipRenderer() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            appendFusionTooltips(stack, tooltipContext.registries(), lines);
        });
    }

    public static boolean isShiftHeld() {
        return FusionGuiSkin.isShiftHeld();
    }

    public static void appendFusionTooltips(
        ItemStack stack,
        HolderLookup.Provider registries,
        List<Component> lines
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (stack.is(FusionItems.RESONANCE_STABILIZER)) {
            lines.add(Component.translatable("item.enchantment_fusion.resonance_stabilizer.lore")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(FusionItems.FUSION_GRIMOIRE)) {
            lines.add(Component.translatable("item.enchantment_fusion.fusion_grimoire.lore")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(FusionItems.FUSION_TATTERED_PAGE)) {
            lines.add(Component.translatable("item.enchantment_fusion.fusion_tattered_page.lore")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        boolean shift = isShiftHeld();
        FusedEnchantmentList list = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean hasCrossTransmute = renderCrossDomainEnchantmentLines(stack, registries, list, lines, shift);

        int bonus = VesselCapacityRules.vesselBonus(stack);
        if (list.isEmpty() && bonus <= 0 && !stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            if (hasCrossTransmute && !shift) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }

        if (VesselCapacityRules.isPotentialEquipmentHost(stack) && (!list.isEmpty() || bonus > 0)) {
            int used = list.size();
            int max = VesselCapacityRules.getMaxVesselSlots(stack);
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

        for (FusedPair pair : list.pairs()) {
            Component primaryLabel = formatEnchantmentWithLevel(registries, pair.primaryId(), pair.primaryLevel());
            Component secondaryLabel = formatEnchantmentWithLevel(registries, pair.secondaryId(), pair.secondaryLevel());
            Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(pair);

            if (pair.isAberrant()) {
                Component abTitle = Component.translatable(AberrantRegistry.titleKey(pair.aberrationId()));
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_aberrant_named",
                    abTitle,
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.RED));
                if (shift) {
                    lines.add(Component.translatable(AberrantRegistry.descKey(pair.aberrationId()))
                        .withStyle(ChatFormatting.GOLD));
                }
                lines.add(Component.translatable(
                    CetSoftBridge.isCetLoaded()
                        ? "tooltip.enchantment_fusion.aberrant_cet_active"
                        : "tooltip.enchantment_fusion.aberrant_cet_required"
                ).withStyle(ChatFormatting.DARK_RED));
            } else if (sigOpt.isPresent()) {
                Component sigTitle = Component.translatable(sigOpt.get().titleKey());
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_signature",
                    sigTitle,
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (shift) {
                    lines.add(Component.translatable(sigOpt.get().descKey()).withStyle(ChatFormatting.AQUA));
                }
            } else {
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_header",
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (shift) {
                    lines.add(buildMatrixSentence(pair, hostForm).copy().withStyle(ChatFormatting.AQUA));
                }
            }
        }

        if ((!list.isEmpty() || hasCrossTransmute) && !shift) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Renders cross-domain standalone enchantments from {@link FusionDataComponents#TRANSMUTED_ENCHANTS}
     * (API-01) and replaces any legacy cross-domain lines in {@link DataComponents#ENCHANTMENTS}.
     */
    private static boolean renderCrossDomainEnchantmentLines(
        ItemStack stack,
        HolderLookup.Provider registries,
        FusedEnchantmentList fusedList,
        List<Component> lines,
        boolean shift
    ) {
        boolean any = false;
        Set<String> renderedIds = new HashSet<>();

        // 1. Primary clean storage: TRANSMUTED_ENCHANTS (API-01)
        Map<String, Integer> transmuted = stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        int insertCursor = Math.min(1, lines.size());
        for (Map.Entry<String, Integer> entry : transmuted.entrySet()) {
            String enchId = entry.getKey();
            int level = Math.max(1, entry.getValue());
            if (enchId == null || enchId.isEmpty() || fusedList.containsEnchantmentId(enchId)) {
                continue;
            }
            Optional<TransmuteVariant> varOpt = CrossDomainTransmutations.resolve(stack, enchId);
            if (varOpt.isEmpty()) {
                continue;
            }
            renderedIds.add(enchId);
            any = true;
            TransmuteVariant variant = varOpt.get();
            int maxLv = resolveMaxLevel(registries, enchId);
            Component vanillaFull = formatEnchantmentWithLevel(registries, enchId, level);
            Component transmutedLine = buildTransmutedDisplayName(variant, level, maxLv, enchId);
            lines.add(insertCursor++, transmutedLine);
            if (shift) {
                lines.add(insertCursor++, Component.translatable(
                    "tooltip.enchantment_fusion.transmute_origin_desc",
                    vanillaFull,
                    Component.translatable(variant.descKey())
                ).withStyle(ChatFormatting.DARK_AQUA));
            }
        }

        // 2. Legacy backward-compat fallback for pre-0.4.1 items that stored cross-domain enchants in ENCHANTMENTS
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchants.isEmpty()) {
            return any;
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            String enchId = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (enchId.isEmpty() || renderedIds.contains(enchId) || fusedList.containsEnchantmentId(enchId)) {
                continue;
            }
            Optional<TransmuteVariant> varOpt = CrossDomainTransmutations.resolve(stack, enchId);
            if (varOpt.isEmpty()) {
                continue;
            }
            any = true;
            TransmuteVariant variant = varOpt.get();
            Component vanillaFull = Enchantment.getFullname(holder, level);
            Component transmutedLine = buildTransmutedDisplayName(variant, level, holder.value().getMaxLevel(), enchId);

            int foundIdx = -1;
            String targetText = vanillaFull.getString();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).getString().equals(targetText)) {
                    foundIdx = i;
                    break;
                }
            }

            if (foundIdx >= 0) {
                lines.set(foundIdx, transmutedLine);
                if (shift) {
                    lines.add(foundIdx + 1, Component.translatable(
                        "tooltip.enchantment_fusion.transmute_origin_desc",
                        vanillaFull,
                        Component.translatable(variant.descKey())
                    ).withStyle(ChatFormatting.DARK_AQUA));
                }
            } else {
                int insertAt = Math.min(1, lines.size());
                lines.add(insertAt, transmutedLine);
                if (shift) {
                    lines.add(insertAt + 1, Component.translatable(
                        "tooltip.enchantment_fusion.transmute_origin_desc",
                        vanillaFull,
                        Component.translatable(variant.descKey())
                    ).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
        }
        return any;
    }

    private static int resolveMaxLevel(HolderLookup.Provider registries, String enchantId) {
        Identifier parsed = Identifier.tryParse(enchantId);
        if (registries != null && parsed != null) {
            var optLookup = registries.lookup(Registries.ENCHANTMENT);
            if (optLookup.isPresent()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsed);
                var optHolder = optLookup.get().get(key);
                if (optHolder.isPresent()) {
                    return optHolder.get().value().getMaxLevel();
                }
            }
        }
        return 1;
    }

    public static Component buildTransmutedDisplayName(
        TransmuteVariant variant,
        int level,
        int maxLevel,
        String enchId
    ) {
        ChatFormatting color = "minecraft:binding_curse".equals(enchId) ? ChatFormatting.RED : ChatFormatting.AQUA;
        Component title = Component.translatable(variant.titleKey());
        if (maxLevel <= 1 && level <= 1) {
            return Component.translatable("tooltip.enchantment_fusion.transmute_single", title).withStyle(color);
        }
        return Component.translatable(
            "tooltip.enchantment_fusion.transmute_leveled",
            title,
            toRoman(level)
        ).withStyle(color);
    }

    public static Component buildEffectSummary(FusedPair pair, HostForm hostForm) {
        if (pair.isAberrant()) {
            return Component.translatable(AberrantRegistry.descKey(pair.aberrationId()));
        }
        Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(pair);
        if (sigOpt.isPresent()) {
            return Component.translatable(sigOpt.get().descKey());
        }
        return buildMatrixSentence(pair, hostForm);
    }

    public static Component buildPairDisplayName(HolderLookup.Provider registries, FusedPair pair) {
        Component p = formatEnchantmentWithLevel(registries, pair.primaryId(), pair.primaryLevel());
        Component s = formatEnchantmentWithLevel(registries, pair.secondaryId(), pair.secondaryLevel());
        Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(pair);
        if (sigOpt.isPresent()) {
            return Component.translatable(
                "tooltip.enchantment_fusion.pair_signature",
                Component.translatable(sigOpt.get().titleKey()),
                p, s
            );
        }
        return Component.translatable("tooltip.enchantment_fusion.pair_header", p, s);
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

    public static Component formatEnchantmentBaseName(HolderLookup.Provider registries, String enchantId) {
        Identifier parsed = Identifier.tryParse(enchantId);
        if (registries != null && parsed != null) {
            var optLookup = registries.lookup(Registries.ENCHANTMENT);
            if (optLookup.isPresent()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsed);
                var optHolder = optLookup.get().get(key);
                if (optHolder.isPresent()) {
                    return optHolder.get().value().description();
                }
            }
        }
        return Component.literal(parsed != null ? parsed.getPath().replace('_', ' ') : enchantId);
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
