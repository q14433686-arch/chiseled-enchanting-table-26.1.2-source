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
    private Path outputDirectory;

    AcceptanceReport() {
        plan("compat-aberration", "09/11 aberration dispatch, target and cooldown fixture", "Not reached if game setup fails.");
        plan("02-fire-sweep", "Fire Aspect ▷ Sweeping Edge: real attack, crossing and primary-effect fixture", "Not reached if setup fails.");
        plan("02-sweep-fire", "Sweeping Edge ▷ Fire Aspect: real sweep, fuse and delayed-chain scheduling fixture", "Not reached if setup fails.");
        plan("03-knockback-sweep", "Knockback ▷ Sweeping Edge: attack, resisted knockback and moving eye fixture", "Not reached if setup fails.");
        plan("03-sweep-knockback", "Sweeping Edge ▷ Knockback: real sweep, pull and simulated aimed swing release fixture", "Not reached if setup fails.");
        plan("04-sharpness-smite", "Sharpness ▷ Smite: weakness, opposite-side damage and control fixture", "Not reached if setup fails.");
        plan("04-smite-sharpness", "Smite ▷ Sharpness: undead kill, stored charge and piercing blade fixture", "Not reached if setup fails.");
        plan("06-silk-fortune", "Silk Touch ▷ Fortune: real ore mining, cancellation, provenance and prepared single-sample exchange", "Not reached if setup fails.");
        plan("10-sharpness-bane", "Sharpness ▷ Bane: real hit and simulated movement/mark age fixture", "Not reached if setup fails.");
        plan("10-bane-sharpness", "Bane ▷ Sharpness: real hit and simulated circling/protected-victim fixture", "Not reached if setup fails.");
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
        plan("12-quick-piercing", "Quick Charge ▷ Piercing: real repeated arrow and old collision batch fixture", "Not reached if setup fails.");
        plan("12-piercing-quick", "Piercing ▷ Quick Charge: actual penetrating arrow and reload credit fixture", "Not reached if setup fails.");
        plan("14-flame-punch", "Flame ▷ Punch: real heat arrow, moving hostile projectile and wall fixture", "Not reached if setup fails.");
        plan("14-punch-flame", "Punch ▷ Flame: actual push, simulated landing and protected vortex fixture", "Not reached if setup fails.");
        plan("17-protection-fire", "Protection ▷ Fire Protection: real fire damage and safe wear fixture", "Not reached if setup fails.");
        plan("17-fire-protection", "Fire Protection ▷ Protection: real mitigation and directed wave fixture", "Not reached if setup fails.");
        plan("18-smite-bane", "Smite ▷ Bane: real direct kill, virtual web and primary-effect fixture", "Not reached if setup fails.");
        plan("18-bane-smite", "Bane ▷ Smite: confirmed arthropod hits, pressure release and primary-effect fixture", "Not reached if setup fails.");
        plan("19-thorns-fire", "Thorns ▷ Fire Protection: real cancelled/accepted melee and delayed retaliation fixture", "Not reached if setup fails.");
        plan("19-fire-thorns", "Fire Protection ▷ Thorns: shield protection and fire curtain fixture", "Not reached if setup fails.");
        plan("20-efficiency-multishot", "Efficiency ▷ Multishot: retained real-arrow scatter and station fixture", "Not reached if setup fails.");
        plan("20-multishot-efficiency", "Multishot ▷ Efficiency: retained mining, silk, crouch and cancellation fixture", "Not reached if setup fails.");
        plan("05-mending-unbreaking", "Mending ▷ Unbreaking: real orb repair and permanent growth fixture", "Not reached if setup fails.");
        plan("05-unbreaking-mending", "Unbreaking ▷ Mending: real wear, XP conversion and break rescue fixture", "Not reached if setup fails.");
        plan("16-blast-protection", "Blast Protection ▷ Protection: armor calculation and counter fixture", "Not reached if setup fails.");
        plan("16-protection-blast", "Protection ▷ Blast Protection: explosion and ordinary damage fixture", "Not reached if setup fails.");
        plan("39.1-density-breach", "Density ▷ Breach: equal armor math, crater, arrow and direct rocket fixture", "Not reached if setup fails.");
        plan("39.1-breach-density", "Breach ▷ Density: equal armor math, crater, arrow and direct rocket fixture", "Not reached if setup fails.");
        plan("39.2-infinity-unbreaking", "Infinity ▷ Unbreaking: real bow/crossbow hit and virtual path echo fixture", "Not reached if setup fails.");
        plan("39.2-unbreaking-infinity", "Unbreaking ▷ Infinity: native/non-native real durability rolls and reserve fixture", "Not reached if setup fails.");
        plan("39.3-knockback-silk", "Knockback ▷ Silk Touch: real hits, capture and original-direction release fixture", "Not reached if setup fails.");
        plan("39.3-silk-knockback", "Silk Touch ▷ Knockback: real hits, held movement and chosen-direction release fixture", "Not reached if setup fails.");
        plan("39.4-luck-lure", "Luck of the Sea ▷ Lure: bite, simulated wait and native catch fixture", "Not reached if setup fails.");
        plan("39.4-lure-luck", "Lure ▷ Luck of the Sea: native catch, virtual school and next cast fixture", "Not reached if setup fails.");
        plan("39.5-power-quick", "Power ▷ Quick Charge: manually timed real bow release and collision fixture", "Not reached if setup fails.");
        plan("39.5-quick-power", "Quick Charge ▷ Power: manually timed real crossbow loading and firing fixture", "Not reached if setup fails.");
        plan("39.6-soul-swift", "Soul Speed ▷ Swift Sneak: simulated soul-step charge and movement vibrations", "Not reached if setup fails.");
        plan("39.6-swift-soul", "Swift Sneak ▷ Soul Speed: simulated soul form, legal crawl and wall collision", "Not reached if setup fails.");
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
        plan("02-runtime-compat", "02 natural sweep timing, delayed chain damage, PvP, walls, datapacks and protection mods", "Not covered by the prepared single-player attack fixture.");
        plan("03-runtime-compat", "03 real client swing packets, natural knockback/movement, PvP, walls and protection mods", "Not covered by the simulated input and timing fixture.");
        plan("04-runtime-compat", "04 natural positioning, sprint/charge timing, chained kills, walls, datapacks and PvP", "Not covered by the simulated position and charge fixture.");
        plan("05-runtime-compat", "05 real restart, unusual item formats, external mod cancellation and multiplayer synchronization", "Not covered by the prepared single-player server fixture.");
        plan("16-runtime-compat", "16 natural blast push, multiplayer PvP, totems and protection-mod compatibility", "Not covered by the prepared single-player server fixture.");
        plan("39.1-runtime-compat", "39.1 real draw/throw, client rocket sync, multiplayer PvP, totems and protection mods", "Not covered by the prepared fixture.");
        plan("39.4-runtime-compat", "39.4 natural bite timing, loot distributions, multiplayer and fishing mods", "Not covered by the manually advanced fixture.");
        plan("39.5-runtime-compat", "39.5 physical draw and input, latency, multiplayer, and modded projectiles", "Not covered by the manually timed server fixtures.");
        plan("39.6-runtime-compat", "39.6 real keyboard movement, client pose sync, sensors and multiplayer", "Not covered by the prepared server fixtures.");
        plan("09-input-config", "09 real client G/V input, configuration reload and multiplayer permissions", "Not covered by the prepared server fixture.");
        plan("11-shot-pvp", "11 real draw/reload, randomness, two-player PvP and natural save/unload", "Not covered by the prepared server fixture.");
        plan("12-runtime-compat", "12 physical input, new walls, rapid switching, two-player PvP and plugin protections", "Not covered by the manually advanced server fixtures.");
        plan("14-runtime-compat", "14 natural projectile timing and landing, PvP, client synchronization and modded projectiles", "Not covered by the manually advanced server fixtures.");
        plan("17-runtime-compat", "17 actual key input, armor damage mods, shields, multiple pieces, PvP and protection plugins", "Not covered by the prepared single-player server fixtures.");
        plan("18-runtime-compat", "18 natural attack timing, spear thrust, blocked/lethal hits, PvP and third-party protection", "Not covered by the prepared single-player server fixtures.");
        plan("19-runtime-compat", "19 vanilla thorns odds, projectiles, lethal/totem ordering, PvP and third-party damage/claims", "Not covered by the prepared single-player server fixtures.");
        plan("20-runtime-compat", "20 held Haste timing, all ranged launchers, legacy scatter targets, multiplayer and claims", "Not covered by the prepared single-player server fixtures.");
        plan("multiplayer-protection", "Real multiplayer, third-party protection plugins and modded projectile compatibility", "Requires additional clients/mods; not covered by these probes.");
        plan("dedicated-server-compat", "Dedicated-server-only loading and external server integration", "Not covered by the single integrated-client game process.");
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

    synchronized void enableOutput(Path validatedDirectory) { this.outputDirectory = validatedDirectory; }

    /** Preserve completed rows if the game later crashes or is killed before the final phase. */
    synchronized void checkpoint() { writeReport("IN PROGRESS / INTERRUPTED"); }

    synchronized void writeReport(String overall) {
        if (outputDirectory == null) return; // Validation precedes every checkpoint.
        try {
            Path temporary = outputDirectory.resolve("acceptance-report.md.tmp");
            Path report = outputDirectory.resolve("acceptance-report.md");
            Files.writeString(temporary, markdown(overall), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
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
