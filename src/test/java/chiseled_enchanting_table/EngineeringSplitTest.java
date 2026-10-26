package chiseled_enchanting_table;

import chiseled_enchanting_table.config.*;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioral contracts across the extracted schema, presets, edit actions and persisted queue. */
class EngineeringSplitTest {
    @Test void schemaAndEveryPresetRoundTripWithoutPublishingRuntimeState() {
        var published = ChiseledEnchantingTableData.runtimeFrame();
        for (var preset : ModRuntimeConfig.Preset.values()) {
            var draft = ModRuntimeConfig.DEFAULT.toDraft();
            draft.overrideDatapacks = true;
            preset.applyTo(draft);
            var expected = draft.build();
            assertEquals(expected, ModRuntimeConfig.fromJson(expected.toJson()), preset.id());
            assertTrue(draft.overrideDatapacks, "presets must preserve the draft's datapack preference");
        }
        assertSame(published, ChiseledEnchantingTableData.runtimeFrame());
    }

    @Test void presetApplicationCannotAliasAnotherDraft() {
        var first = ModRuntimeConfig.DEFAULT.toDraft();
        var second = ModRuntimeConfig.DEFAULT.toDraft();
        var before = second.build();
        ModRuntimeConfig.Preset.CLASSIC.applyTo(first);
        assertEquals(before, second.build());
        ConfigCatalog.byKey("disenchant_enabled").orElseThrow().set(first, false);
        assertEquals(before, second.build());
    }

    @Test void allDirectTraceAtomsToggleInBothDirectionsWithoutChangingDefaults() {
        var defaults = ModRuntimeConfig.DEFAULT.toJson();
        int tested = 0;
        for (var trace : ConfigCatalog.RULE_TRACE_ENTRIES) {
            if (trace.derived() || trace.optionKey() == null) continue;
            var draft = ModRuntimeConfig.DEFAULT.toDraft();
            trace.applyModern110(draft);
            ConfigTraceActions.toggle(draft, trace);
            assertTrue(trace.isUsingClassic103(draft), trace.traceId());
            ConfigTraceActions.toggle(draft, trace);
            assertTrue(trace.isUsingModern110(draft), trace.traceId());
            tested++;
        }
        assertEquals(ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT, tested);
        assertEquals(defaults, ModRuntimeConfig.DEFAULT.toJson());
    }

    @Test void coupledTomeTraceAndDerivedTracePreserveTheirSemantics() {
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        var tome = ConfigCatalog.RULE_TRACE_ENTRIES.stream()
            .filter(t -> "tome_consumption_mode".equals(t.optionKey())).findFirst().orElseThrow();
        tome.applyModern110(draft);
        ConfigTraceActions.toggle(draft, tome);
        assertFalse(draft.tomeRegenEnabled);
        ConfigTraceActions.toggle(draft, tome);
        assertTrue(draft.tomeRegenEnabled);
        var before = draft.build();
        for (var trace : ConfigCatalog.RULE_TRACE_ENTRIES) {
            if (trace.derived()) ConfigTraceActions.toggle(draft, trace);
        }
        assertEquals(before, draft.build());
    }

    @Test void sliderOwnsDragClampingAndReleaseWithoutOwningTheScreen() {
        var entry = ConfigCatalog.ALL_OPTIONS.stream()
            .filter(e -> e.kind() == ConfigCatalog.ControlKind.INT_SLIDER).findFirst().orElseThrow();
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        var drag = new ConfigSliderDragSession();
        assertFalse(drag.drag(draft, 30));
        drag.begin(entry, new ConfigLayoutSpec.Box(10, 0, 100, 12), draft, -100);
        assertTrue(drag.active());
        assertEquals(entry.min(), ((Number) entry.get(draft)).doubleValue());
        assertTrue(drag.drag(draft, 500));
        assertEquals(entry.max(), ((Number) entry.get(draft)).doubleValue());
        drag.release();
        var before = draft.build();
        assertFalse(drag.drag(draft, 10));
        assertEquals(before, draft.build());
        // A zero-width track still has a finite denominator, just as the original mouse path.
        drag.begin(entry, new ConfigLayoutSpec.Box(10, 0, 0, 12), draft, 10);
        assertEquals(entry.min(), ((Number) entry.get(draft)).doubleValue());
    }

    @Test void legacySevenAndTenFieldQueueFixturesMigrateToExactElevenFieldEncoding() {
        var seven = QueuedInscription.decode("minecraft:sharpness|5|3|1|0|12|0");
        assertNotNull(seven);
        assertEquals("minecraft:sharpness|5|3|1|0|12|0|0.0000|0||-1", seven.encode());
        var ten = QueuedInscription.decode("minecraft:sharpness|5|3|1|0|12|0|5.5|94|minecraft:lapis_lazuli*6");
        assertNotNull(ten);
        assertEquals("minecraft:sharpness|5|3|1|0|12|0|5.5000|94|minecraft:lapis_lazuli*6|-1", ten.encode());
        assertEquals(List.of(seven, ten), InscriptionRitualMechanics.deserializeQueue(
            InscriptionRitualMechanics.serializeQueue(List.of(seven, ten))));
    }

    @Test void queueEncodingIsLocaleIndependentAndPreservesInheritedGeneration() {
        var originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            String golden = "minecraft:efficiency|5|2|1|1|18|0|5.5000|94|minecraft:lapis_lazuli*6|3";
            var step = QueuedInscription.decode(golden);
            assertNotNull(step);
            assertEquals(golden, step.encode());
            assertEquals(3, step.inheritedGeneration());
        } finally { Locale.setDefault(originalLocale); }
    }

    @Test void queueRecoveryRejectsAnInvalidTailAndAggregateRefundOverflow() {
        String valid = "minecraft:sharpness|1|0|0|0|1|0";
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue(valid + ";bad"));
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue(valid + ";"));
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue(
            "minecraft:sharpness|1|0|0|0|2147483647|0;" + valid));
        assertTrue(InscriptionRitualMechanics.deserializeQueue("").isEmpty());
    }

    @Test void structuredRecoveryReconcilesLegacyMirrorsAndReturnsOwnedMutableQueue() {
        var modern = QueuedInscription.decode("minecraft:sharpness|2|1|1|0|0|0");
        var legacy = QueuedInscription.decode("minecraft:sharpness|1|0|1|0|0|0");
        var recovered = InscriptionRitualMechanics.recoverQueue(java.util.Optional.of(List.of(modern)), "");
        assertEquals(List.of(modern), recovered);
        recovered.clear();
        assertEquals(List.of(legacy), InscriptionRitualMechanics.recoverQueue(
            java.util.Optional.of(List.of(modern)), legacy.encode()));
        var huge = QueuedInscription.decode("minecraft:sharpness|1|0|0|0|2147483647|0");
        var one = QueuedInscription.decode("minecraft:sharpness|1|0|0|0|1|0");
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.recoverQueue(
            java.util.Optional.of(List.of(huge, one)), ""));
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.recoverQueue(
            java.util.Optional.of(java.util.Collections.nCopies(257, one)), ""));
    }

    @Test void policySchemaMigratesLegacyAtomKeysWithoutPublishingPolicy() {
        var policy = ModRuntimeConfig.PackPolicy.fromJsonString("{\"locked_keys\":[\"classic_1_0_3_mode\"],\"enforce_datapack_priority\":true}");
        assertEquals(ModRuntimeConfig.GRANULAR_RULE_ATOM_KEYS, policy.lockedKeys());
        assertTrue(policy.enforceDatapackPriority());
        assertEquals(policy, ModRuntimeConfig.PackPolicy.fromJsonString(policy.toJsonString()));
        assertThrows(IllegalArgumentException.class, () -> ModRuntimeConfig.PackPolicy.fromJsonString(
            "{\"enforce_datapack_priority\":\"true\"}"));
    }
}
