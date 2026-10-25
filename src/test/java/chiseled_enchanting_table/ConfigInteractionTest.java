package chiseled_enchanting_table;

import chiseled_enchanting_table.config.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The state machines used by the actual screen, including transport failures and stale acks. */
class ConfigInteractionTest {
    @Test void searchNativeOwnershipFollowsFocusAndAcceptsUnicode() {
        var nativeCalls = new ArrayList<Boolean>();
        var input = new SearchInputSession(nativeCalls::add);
        input.setFocused(true);
        input.buffer().insertText("洗练 abc");
        assertTrue(input.focused());
        assertEquals("洗练 abc", input.buffer().text());
        input.setFocused(false);
        assertFalse(input.focused());
        assertEquals(List.of(true, false), nativeCalls);
    }
    @Test void editingAndRejectingNeverChangesBaselineOrDiscardsDraft() {
        var editor = new ConfigEditSession(ModRuntimeConfig.DEFAULT);
        ConfigCatalog.byKey("disenchant_enabled").orElseThrow().set(editor.draft(), false);
        assertTrue(editor.dirty());
        var saves = new ConfigSaveSession();
        saves.begin(101, true, 0);
        assertEquals(ConfigSaveSession.Result.REJECTED, saves.acknowledge(101, false, 1));
        assertTrue(editor.baseline().disenchantEnabled());
        assertFalse(editor.draft().disenchantEnabled);
        assertTrue(editor.dirty());
    }
    @Test void acceptedSaveReloadsPreferencesAndPreservesCloseIntent() {
        var editor = new ConfigEditSession(ModRuntimeConfig.DEFAULT);
        editor.draft().disenchantEnabled = false;
        var submitted = ModRuntimeConfig.fromJsonString(editor.draft().build().toJsonString());
        var saves = new ConfigSaveSession();
        saves.begin(1, true, 0);
        assertEquals(ConfigSaveSession.Result.ACCEPTED_CLOSE, saves.acknowledge(1, true, 10));
        editor.acceptSaved(submitted);
        assertFalse(editor.dirty());
        assertFalse(new ConfigEditSession(editor.baseline()).draft().disenchantEnabled);
    }
    @Test void staleDuplicateAndLateAcksCannotAcceptAnotherRequest() {
        var saves = new ConfigSaveSession();
        saves.begin(2, false, 0);
        assertEquals(ConfigSaveSession.Result.IGNORED, saves.acknowledge(1, true, 1));
        assertEquals(2, saves.pendingRequestId());
        assertThrows(IllegalStateException.class, () -> saves.begin(3, true, 1));
        assertEquals(ConfigSaveSession.Result.ACCEPTED, saves.acknowledge(2, true, 2));
        assertEquals(ConfigSaveSession.Result.IGNORED, saves.acknowledge(2, true, 3));
        saves.begin(3, false, 4);
        assertTrue(saves.expire(4 + ConfigSaveSession.TIMEOUT_MS));
        assertTrue(saves.failed());
        assertEquals(0, saves.pendingRequestId());
        assertEquals(ConfigSaveSession.Result.IGNORED, saves.acknowledge(3, true, 20000));
        saves.begin(4, false, 20001); // Retry is possible after timeout.
    }
    @Test void unavailableTransportFailureDoesNotCreateAPendingRequest() {
        var saves = new ConfigSaveSession();
        saves.completed(false, 100);
        assertTrue(saves.failed());
        assertEquals(0, saves.pendingRequestId());
        assertTrue(saves.toastUntil() > 100);
    }
    @Test void allPresetEditsSurviveSerializationBeforeSubmission() {
        var editor = new ConfigEditSession(ModRuntimeConfig.DEFAULT);
        for (var preset : ModRuntimeConfig.Preset.values()) {
            preset.applyTo(editor.draft());
            var submitted = ModRuntimeConfig.fromJsonString(editor.draft().build().toJsonString());
            assertTrue(preset.matches(submitted.toDraft()), preset.toString());
        }
    }
}
