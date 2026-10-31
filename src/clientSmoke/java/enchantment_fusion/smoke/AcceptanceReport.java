package enchantment_fusion.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-fixture results for one integrated-client process. A probe pass only covers its stated scope. */
final class AcceptanceReport {
    @FunctionalInterface interface CheckedStep { void run() throws Exception; }
    private record Entry(String scope, String status, String detail) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    AcceptanceReport() {
        plan("compat-aberration", "09/11 aberration dispatch, target and cooldown fixture", "Not reached if game setup fails.");
        plan("common-format", "Risk text formatting", "Not reached if an earlier setup/UI assertion fails.");
        plan("common-config", "Server configuration save/validation", "Not reached if setup fails.");
        plan("common-frost-smelt", "Frost/water and crouching smelt fixtures", "Not reached if setup fails.");
        plan("31.3-glaze", "Burning-glaze prepared server fixture", "Not reached if setup fails.");
        plan("31.3-amber", "Amber seal prepared server fixture", "Not reached if setup fails.");
        plan("31.4-carrier", "Water/fire carrier checks", "Not reached if setup fails.");
        plan("21-underwater", "Underwater carrier and dry-input checks", "Not reached if setup fails.");
        plan("23-frost-fall", "Frost landing / air-step prepared fixture", "Not reached if setup fails.");
        plan("22-frost-wake", "Frost wake / dive carrier checks", "Not reached if setup fails.");
        plan("24.1-frost-fire", "Frost/fire worn-carrier and block fixture", "Not reached if setup fails.");
        plan("11-multishot-piercing", "Multishot ▷ Piercing: prepared server fixture", "Not reached if setup fails.");
        plan("11-piercing-multishot", "Piercing ▷ Multishot: prepared server fixture", "Not reached if setup fails.");
        plan("05-mending-unbreaking", "Mending ▷ Unbreaking: real orb repair and permanent growth fixture", "Not reached if setup fails.");
        plan("05-unbreaking-mending", "Unbreaking ▷ Mending: real wear, XP conversion and break rescue fixture", "Not reached if setup fails.");
        plan("16-blast-protection", "Blast Protection ▷ Protection: armor calculation and counter fixture", "Not reached if setup fails.");
        plan("16-protection-blast", "Protection ▷ Blast Protection: explosion and ordinary damage fixture", "Not reached if setup fails.");
        plan("39.1-density-breach", "Density ▷ Breach: equal armor math, real crater and arrow fixture", "Not reached if setup fails.");
        plan("39.1-breach-density", "Breach ▷ Density: equal armor math, real crater and arrow fixture", "Not reached if setup fails.");
        plan("09-efficiency-silk", "Efficiency ▷ Silk Touch: prepared server fixture", "Not reached if setup fails.");
        plan("09-silk-efficiency", "Silk Touch ▷ Efficiency: prepared server fixture", "Not reached if setup fails.");
        plan("common-station-selection", "Station selection and stale-target checks", "Not reached if setup fails.");
        plan("25.3-loyalty", "Loyalty guard prepared fixture", "Not reached if setup fails.");
        plan("common-third-party", "Third-party enchantment carrier checks", "Not reached if setup fails.");
        plan("transmute-replenish", "XP/consumable replenish prepared fixture", "Not reached if setup fails.");
        plan("transmute-dropped-item", "Dropped-item safety prepared fixture", "Not reached if setup fails.");
        plan("common-harvest-provenance", "Real harvest drop provenance fixture", "Not reached if setup fails.");
        plan("common-extra-mining", "Extra mining and recursion guard fixture", "Not reached if setup fails.");
        plan("common-lifeline", "Lifeline anchor checks", "Not reached if setup fails.");
        plan("15-loyalty-riptide", "Legacy loyalty/riptide fixture", "Not reached if setup fails.");
        plan("01-fire-knockback", "Multi-tick fire/knockback integration sequence", "Not reached if an earlier phase fails.");
        plan("05-runtime-compat", "05 real restart, unusual item formats, external mod cancellation and multiplayer synchronization", "Not covered by the prepared single-player server fixture.");
        plan("16-runtime-compat", "16 natural blast push, multiplayer PvP, totems and protection-mod compatibility", "Not covered by the prepared single-player server fixture.");
        plan("39.1-runtime-compat", "39.1 real draw/throw, firework rockets, multiplayer PvP, totems and protection mods", "Not covered by the prepared fixture; firework rocket support is not yet implemented.");
        plan("09-input-config", "09 real client G/V input, configuration reload and multiplayer permissions", "Not covered by the prepared server fixture.");
        plan("11-shot-pvp", "11 real draw/reload, randomness, two-player PvP and natural save/unload", "Not covered by the prepared server fixture.");
        plan("multiplayer-protection", "Real multiplayer, third-party protection plugins and modded projectile compatibility", "Requires additional clients/mods; not covered by these probes.");
        plan("remaining-directions", "All other redesigned directions and single-enchantment variants", "Many are not yet implemented or individually exercised; theoretical catalog coverage is not a pass.");
    }

    private void plan(String id, String scope, String reason) { entries.put(id, new Entry(scope, "SKIP", reason)); }

    synchronized void run(String id, CheckedStep step) {
        Entry planned = entries.get(id);
        if (planned == null) throw new IllegalArgumentException("Unplanned smoke fixture: " + id);
        entries.put(id, new Entry(planned.scope(), "SKIP", "Started but not completed if the process stopped here."));
        checkpoint();
        try {
            step.run();
            entries.put(id, new Entry(planned.scope(), "PASS", "Prepared fixture only; see implementation document for untested behavior."));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace();
            String detail = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            entries.put(id, new Entry(planned.scope(), "FAIL", detail));
        } finally {
            checkpoint();
        }
    }

    synchronized void passed(String id, String detail) {
        Entry planned = entries.get(id);
        if (planned == null) throw new IllegalArgumentException("Unplanned smoke fixture: " + id);
        entries.put(id, new Entry(planned.scope(), "PASS", detail));
    }

    synchronized void failed(String id, Throwable error) {
        Entry planned = entries.get(id);
        if (planned == null) throw new IllegalArgumentException("Unplanned smoke fixture: " + id);
        entries.put(id, new Entry(planned.scope(), "FAIL",
            error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())));
    }

    synchronized boolean hasFailures() { return entries.values().stream().anyMatch(e -> e.status().equals("FAIL")); }

    /** Preserve completed rows if the game later crashes or is killed before the final phase. */
    synchronized void checkpoint() {
        try {
            Path temporary = Path.of("acceptance-report.md.tmp"), report = Path.of("acceptance-report.md");
            Files.writeString(temporary, markdown("IN PROGRESS / INTERRUPTED"));
            try {
                Files.move(temporary, report, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, report, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            System.err.println("Could not write smoke result checkpoint: " + error);
        }
    }

    synchronized String markdown(String overall) {
        long pass = entries.values().stream().filter(e -> e.status().equals("PASS")).count();
        long fail = entries.values().stream().filter(e -> e.status().equals("FAIL")).count();
        long skip = entries.values().stream().filter(e -> e.status().equals("SKIP")).count();
        StringBuilder out = new StringBuilder("# Integrated client smoke: scoped results\n\n")
            .append("Overall: ").append(clean(overall)).append("\n\n")
            .append("One integrated game process; fixture-level PASS is not full play or balance acceptance. ")
            .append("A stopped fixture does not establish its later assertions. Counts: ")
            .append(pass).append(" pass / ").append(fail).append(" fail / ").append(skip).append(" skip.\n\n")
            .append("| Item | Scope | Status | Reason / boundary |\n| --- | --- | --- | --- |\n");
        entries.forEach((id, entry) -> out.append("| ").append(clean(id)).append(" | ")
            .append(clean(entry.scope())).append(" | ").append(entry.status()).append(" | ")
            .append(clean(entry.detail())).append(" |\n"));
        return out.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ');
    }
}
