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
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostWakeDiveSkills;
import enchantment_fusion.mechanics.signatures.FrostFireSkills;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.DurabilityReserveSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import enchantment_fusion.mechanics.signatures.ThermalProjectileSkills;
import enchantment_fusion.mechanics.signatures.FireTradeoffSkills;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.FireSweepSkills;
import enchantment_fusion.mechanics.signatures.WindSweepSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.mechanics.signatures.StealthBladeSkills;
import enchantment_fusion.mechanics.signatures.WaterPressureSkills;
import enchantment_fusion.mechanics.signatures.SealedOreSkills;
import enchantment_fusion.mechanics.signatures.FortuneBankSkills;
import enchantment_fusion.mechanics.signatures.RetainedVolleyHosts;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

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

        Integer sealedFortune = stack.get(FusionDataComponents.SEALED_ORE_FORTUNE);
        if (sealedFortune != null) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.sealed_ore", sealedFortune,
                Component.keybind("key.enchantment_fusion.activate_skill"), Component.keybind("key.enchantment_fusion.cycle_skill"))
                .withStyle(ChatFormatting.AQUA));
        }
        boolean shift = isShiftHeld();
        List<RetainedEnchant> retainedEntries = stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of());
        for (RetainedEnchant retained : retainedEntries) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.retained_enchantment",
                formatEnchantmentWithLevel(registries, retained.id(), retained.level())).withStyle(ChatFormatting.GOLD));
        }
        if (!retainedEntries.isEmpty()) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.retained_recovery").withStyle(ChatFormatting.GRAY));
        }
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
            boolean legacyUnderwaterCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ((UnderwaterMiningSkills.isSpendPair(pair) && UnderwaterMiningSkills.SPEND_SIGNATURE.equals(sigOpt.get().id()))
                    || (UnderwaterMiningSkills.isBubblePair(pair) && UnderwaterMiningSkills.BUBBLE_SIGNATURE.equals(sigOpt.get().id())))
                && !UnderwaterMiningSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFrostFallCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ((FrostFallSkills.isWavePair(pair) && FrostFallSkills.WAVE_SIGNATURE.equals(sigOpt.get().id()))
                    || (FrostFallSkills.isStepPair(pair) && FrostFallSkills.STEP_SIGNATURE.equals(sigOpt.get().id())))
                && !FrostFallSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFrostWakeCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ((FrostWakeDiveSkills.isWakePair(pair) && FrostWakeDiveSkills.WAKE.equals(sigOpt.get().id()))
                    || (FrostWakeDiveSkills.isDivePair(pair) && FrostWakeDiveSkills.DIVE.equals(sigOpt.get().id())))
                && !FrostWakeDiveSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFrostFireCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FrostFireSkills.matches(pair, sigOpt.get().id())
                && !FrostFireSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyPhantomCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && PhantomVolley.matches(pair, sigOpt.get().id())
                && !PhantomVolley.applies(pair, stack, sigOpt.get().id());
            boolean legacyPrecisionCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && PrecisionConstruction.matches(pair, sigOpt.get().id())
                && !PrecisionConstruction.applies(pair, stack, sigOpt.get().id());
            boolean legacyDurabilityCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && DurabilityEconomy.matches(pair, sigOpt.get().id())
                && !DurabilityEconomy.applies(pair, stack, sigOpt.get().id());
            boolean legacyBlastCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && BlastDefenseSkills.matches(pair, sigOpt.get().id())
                && !BlastDefenseSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyHeavyCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && HeavyCrushSkill.matches(pair, sigOpt.get().id())
                && !HeavyCrushSkill.applies(pair, stack, sigOpt.get().id());
            boolean legacySoulCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && SoulStepSkills.matches(pair, sigOpt.get().id()) && !SoulStepSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFishingCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FishingTideSkills.matches(pair, sigOpt.get().id()) && !FishingTideSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyCadenceCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && RangedCadenceSkills.matches(pair, sigOpt.get().id()) && !RangedCadenceSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyReserveCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ((EchoPathSkills.matches(pair, sigOpt.get().id()) && !EchoPathSkills.applies(pair, stack, sigOpt.get().id()))
                    || (DurabilityReserveSkills.matches(pair, sigOpt.get().id()) && !DurabilityReserveSkills.applies(pair, stack, sigOpt.get().id())));
            boolean legacyMomentumCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && MomentumSealSkills.matches(pair, sigOpt.get().id()) && !MomentumSealSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyPiercingChainCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && PiercingChainSkills.matches(pair, sigOpt.get().id()) && !PiercingChainSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyThermalCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ThermalProjectileSkills.matches(pair, sigOpt.get().id()) && !ThermalProjectileSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFireTradeoffCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FireTradeoffSkills.matches(pair, sigOpt.get().id()) && !FireTradeoffSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacySoulThreadCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && SoulThreadSkills.matches(pair, sigOpt.get().id()) && !SoulThreadSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFireThornsCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FireThornsSkills.matches(pair, sigOpt.get().id()) && !FireThornsSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFireSweepCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FireSweepSkills.matches(pair, sigOpt.get().id()) && !FireSweepSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyWindSweepCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && WindSweepSkills.matches(pair, sigOpt.get().id()) && !WindSweepSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacySoulBladeCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && SoulBladeSkills.matches(pair, sigOpt.get().id()) && !SoulBladeSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacySegmentThreadCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && SegmentThreadSkills.matches(pair, sigOpt.get().id()) && !SegmentThreadSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacySealedOreCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && SealedOreSkills.matches(pair, sigOpt.get().id()) && !SealedOreSkills.applies(pair, stack, sigOpt.get().id());
            boolean legacyFortuneBankCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && FortuneBankSkills.matches(pair, sigOpt.get().id()) && !FortuneBankSkills.applies(pair, stack, sigOpt.get().id());
            boolean stealthTemplate = StealthBladeSkills.matches(pair);
            boolean legacyStealthCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && stealthTemplate
                && !StealthBladeSkills.applies(pair, stack);
            boolean waterPressureTemplate = WaterPressureSkills.matches(pair);
            boolean legacyWaterPressureCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && waterPressureTemplate
                && !WaterPressureSkills.applies(pair, stack);
            boolean legacyRetainedVolleyCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && RetainedVolleyHosts.matches(pair, sigOpt.get().id()) && !RetainedVolleyHosts.supportsHost(stack, sigOpt.get().id());
            boolean legacyGlazeAmberCarrier = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK) && sigOpt.isPresent()
                && ((GlazedStrikeEffect.isPair(pair) && GlazedStrikeEffect.SIGNATURE.equals(sigOpt.get().id())
                        && !GlazedStrikeEffect.applies(pair, stack, sigOpt.get().id()))
                    || (AmberSealSkill.isPair(pair) && AmberSealSkill.SIGNATURE.equals(sigOpt.get().id())
                        && !AmberSealSkill.applies(pair, stack, sigOpt.get().id())));

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
                    var econ = FusionMatrixRegistry.clientView().economy();
                    lines.add(Component.translatable(
                        "tooltip.enchantment_fusion.aberrant_native_purify",
                        econ.purifyAberrantStabilizers(),
                        econ.purifyAberrantXp()
                    ).withStyle(ChatFormatting.AQUA));
                }
                lines.add(Component.translatable(
                    CetSoftBridge.isCetLoaded()
                        ? "tooltip.enchantment_fusion.aberrant_cet_active"
                        : "tooltip.enchantment_fusion.aberrant_cet_required"
                ).withStyle(ChatFormatting.DARK_RED));
                if (shift && stealthTemplate && !legacyStealthCarrier) {
                    lines.add(Component.translatable(StealthBladeSkills.isAttackFirst(pair)
                        ? "tooltip.enchantment_fusion.stealth_blade_weapon"
                        : "tooltip.enchantment_fusion.stealth_blade_legs").withStyle(ChatFormatting.AQUA));
                }
                if (shift && waterPressureTemplate && !legacyWaterPressureCarrier) {
                    lines.add(Component.translatable(WaterPressureSkills.isAttackFirst(pair)
                        ? "tooltip.enchantment_fusion.water_pressure_weapon"
                        : "tooltip.enchantment_fusion.water_pressure_helmet").withStyle(ChatFormatting.AQUA));
                }
            } else if (sigOpt.isPresent()) {
                Component sigTitle = Component.translatable(sigOpt.get().titleKey());
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_signature",
                    sigTitle,
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (shift && !legacyUnderwaterCarrier && !legacyFrostFallCarrier && !legacyFrostWakeCarrier
                    && !legacyFrostFireCarrier && !legacyPhantomCarrier && !legacyPrecisionCarrier
                    && !legacyDurabilityCarrier && !legacyBlastCarrier && !legacyHeavyCarrier && !legacySoulCarrier
                    && !legacyFishingCarrier && !legacyCadenceCarrier && !legacyReserveCarrier
                    && !legacyMomentumCarrier && !legacyPiercingChainCarrier && !legacyThermalCarrier && !legacyFireTradeoffCarrier
                    && !legacySoulThreadCarrier && !legacyFireThornsCarrier && !legacyFireSweepCarrier && !legacyWindSweepCarrier
                    && !legacySoulBladeCarrier && !legacySegmentThreadCarrier && !legacySealedOreCarrier
                    && !legacyFortuneBankCarrier && !legacyGlazeAmberCarrier) {
                    lines.add(Component.translatable(sigOpt.get().descKey()).withStyle(ChatFormatting.AQUA));
                }
            } else {
                lines.add(Component.translatable(
                    "tooltip.enchantment_fusion.pair_header",
                    primaryLabel,
                    secondaryLabel
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (shift) {
                    if (stealthTemplate && !legacyStealthCarrier) {
                        lines.add(Component.translatable(StealthBladeSkills.isAttackFirst(pair)
                            ? "tooltip.enchantment_fusion.stealth_blade_weapon"
                            : "tooltip.enchantment_fusion.stealth_blade_legs").withStyle(ChatFormatting.AQUA));
                    } else if (waterPressureTemplate && !legacyWaterPressureCarrier) {
                        lines.add(Component.translatable(WaterPressureSkills.isAttackFirst(pair)
                            ? "tooltip.enchantment_fusion.water_pressure_weapon"
                            : "tooltip.enchantment_fusion.water_pressure_helmet").withStyle(ChatFormatting.AQUA));
                    } else {
                        lines.add(buildMatrixSentence(pair, hostForm).copy().withStyle(ChatFormatting.AQUA));
                    }
                }
            }
            if (legacyStealthCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_stealth_blade_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyWaterPressureCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_water_pressure_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (shift && waterPressureTemplate && WaterPressureSkills.isBreathFirst(pair) && !legacyWaterPressureCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.active_skill_keys",
                    Component.keybind("key.enchantment_fusion.activate_skill"),
                    Component.keybind("key.enchantment_fusion.cycle_skill")).withStyle(ChatFormatting.GRAY));
            }
            if (legacyUnderwaterCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_underwater_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFrostFallCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_frost_fall_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFrostWakeCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_frost_wake_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFrostFireCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_frost_fire_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyPhantomCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_phantom_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyPrecisionCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_precision_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyDurabilityCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_durability_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyBlastCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_blast_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyHeavyCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_heavy_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacySoulCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_soul_step_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFishingCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_fishing_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyCadenceCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_cadence_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyReserveCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_reserve_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyMomentumCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_momentum_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyPiercingChainCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_piercing_chain_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyThermalCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_thermal_projectile_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFireTradeoffCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_fire_tradeoff_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacySoulThreadCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_soul_thread_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFireThornsCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_fire_thorns_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFireSweepCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_fire_sweep_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyWindSweepCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_wind_sweep_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacySoulBladeCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_soul_blade_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacySegmentThreadCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_segment_thread_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacySealedOreCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_sealed_ore_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyFortuneBankCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_fortune_bank_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyRetainedVolleyCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_retained_volley_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (legacyGlazeAmberCarrier) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.legacy_glaze_amber_carrier")
                    .withStyle(ChatFormatting.GOLD));
            }
            if (shift && !legacyFrostFallCarrier && !legacyUnderwaterCarrier && sigOpt.isPresent()
                && (FrostFallSkills.applies(pair, stack, sigOpt.get().id())
                    || AmberSealSkill.applies(pair, stack, sigOpt.get().id())
                    || (SoulStepSkills.REVERSE.equals(sigOpt.get().id()) && SoulStepSkills.applies(pair, stack, sigOpt.get().id()))
                    || (RangedCadenceSkills.CROSSBOW.equals(sigOpt.get().id()) && RangedCadenceSkills.applies(pair, stack, sigOpt.get().id()))
                    || DurabilityReserveSkills.applies(pair, stack, sigOpt.get().id())
                    || EchoPathSkills.applies(pair, stack, sigOpt.get().id())
                    || MomentumSealSkills.applies(pair, stack, sigOpt.get().id())
                    || (UnderwaterMiningSkills.isSpendPair(pair) && UnderwaterMiningSkills.applies(pair, stack, sigOpt.get().id())))) {
                lines.add(Component.translatable("tooltip.enchantment_fusion.active_skill_keys",
                    Component.keybind("key.enchantment_fusion.activate_skill"),
                    Component.keybind("key.enchantment_fusion.cycle_skill")).withStyle(ChatFormatting.GRAY));
            }
        }

        if ((!list.isEmpty() || hasCrossTransmute) && !shift) {
            lines.add(Component.translatable("tooltip.enchantment_fusion.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Renders cross-domain standalone enchantments exclusively from {@link FusionDataComponents#TRANSMUTED_ENCHANTS}
     * (API-01 / fusion_table_only_transmuted_name: Vanilla Anvil enchantments never become cross-domain transmutations).
     */
    private static boolean renderCrossDomainEnchantmentLines(
        ItemStack stack,
        HolderLookup.Provider registries,
        FusedEnchantmentList fusedList,
        List<Component> lines,
        boolean shift
    ) {
        boolean any = false;
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
        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.clientView();
        var trig = snap.resolve(pair.primaryId()).trigger();
        var pay = snap.resolve(pair.secondaryId()).payload();
        return Component.translatable(
            "tooltip.enchantment_fusion.matrix_sentence",
            Component.translatable(trig.translationKey()),
            Component.translatable(pay.effectKey(hostForm))
        );
    }

    public static Component buildClueSentence(FusedPair pair) {
        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.clientView();
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
