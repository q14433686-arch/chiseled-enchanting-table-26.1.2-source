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
        plan("06-fortune-silk", "Fortune ▷ Silk Touch: real ore mining, cancellation, bounded reserve, target redemption and direct fallback", "Not reached if setup fails.");
        plan("06-runtime-compat", "06 actual key input, unusual loot functions and stack splitting, save/restart, multiplayer and protection mods", "Not covered by the prepared single-player fixture.");
        plan("10-sharpness-bane", "Sharpness ▷ Bane: real hit and simulated movement/mark age fixture", "Not reached if setup fails.");
        plan("10-bane-sharpness", "Bane ▷ Sharpness: real hit and simulated circling/protected-victim fixture", "Not reached if setup fails.");
        plan("45.1-power-unbreaking", "power ▷ unbreaking: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.1-unbreaking-power", "unbreaking ▷ power: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.1-punch-unbreaking", "punch ▷ unbreaking: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.1-unbreaking-punch", "unbreaking ▷ punch: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.1-flame-unbreaking", "flame ▷ unbreaking: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.1-unbreaking-flame", "unbreaking ▷ flame: real bow/crossbow shot, observed wear and bounded virtual echo", "Not reached if setup fails; natural input, timing, multiplayer and third-party projectile/durability mods remain untested.");
        plan("45.3-power-respiration", "power ▷ respiration: prepared underwater real-arrow hit and owner bubble", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-respiration-power", "respiration ▷ power: air payment and real-arrow water inertia", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-punch-respiration", "punch ▷ respiration: prepared underwater real-arrow hit and owner bubble", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-respiration-punch", "respiration ▷ punch: air payment and real-arrow water inertia", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-flame-respiration", "flame ▷ respiration: prepared underwater real-arrow hit and owner bubble", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-respiration-flame", "respiration ▷ flame: air payment and real-arrow water inertia", "Not reached if setup fails; fluid and flight are prepared.");
        plan("45.3-runtime-compat", "45.3 natural fluid updates and input, arrow velocity sync, datapacks, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("45.2-power-protection", "power ▷ protection: true arrow hit and nearby friendly screen", "Not reached if setup fails; targets and projectile flight are prepared.");
        plan("45.2-protection-power", "protection ▷ power: attributable real defense and guarded arrow collision", "Not reached if setup fails; shield timing and projectile flight are prepared.");
        plan("45.2-punch-protection", "punch ▷ protection: true arrow hit and nearby friendly screen", "Not reached if setup fails; targets and projectile flight are prepared.");
        plan("45.2-protection-punch", "protection ▷ punch: attributable real defense and guarded arrow collision", "Not reached if setup fails; shield timing and projectile flight are prepared.");
        plan("45.2-flame-protection", "flame ▷ protection: true arrow hit and nearby friendly screen", "Not reached if setup fails; targets and projectile flight are prepared.");
        plan("45.2-protection-flame", "protection ▷ flame: attributable real defense and guarded arrow collision", "Not reached if setup fails; shield timing and projectile flight are prepared.");
        plan("45.2-runtime-compat", "45.2 natural real projectile collision, shields, PvP, latency, multi-gear capture and other mods", "Not covered by the prepared single-player fixture.");
        plan("46.1-power-swift", "power ▷ swift_sneak: real sneaking hit, sustained aim and next bow/crossbow charge", "Not reached if setup fails; pose, aim and projectile flight are prepared.");
        plan("46.1-swift-power", "swift_sneak ▷ power: real sneaking shot and one collision-respecting retreat", "Not reached if setup fails; pose, input, wall and flight are prepared.");
        plan("46.1-punch-swift", "punch ▷ swift_sneak: real sneaking hit, sustained aim and next bow/crossbow charge", "Not reached if setup fails; pose, aim and projectile flight are prepared.");
        plan("46.1-swift-punch", "swift_sneak ▷ punch: real sneaking shot and one collision-respecting retreat", "Not reached if setup fails; pose, input, wall and flight are prepared.");
        plan("46.1-flame-swift", "flame ▷ swift_sneak: real sneaking hit, sustained aim and next bow/crossbow charge", "Not reached if setup fails; pose, aim and projectile flight are prepared.");
        plan("46.1-swift-flame", "swift_sneak ▷ flame: real sneaking shot and one collision-respecting retreat", "Not reached if setup fails; pose, input, wall and flight are prepared.");
        plan("46.1-runtime-compat", "46.1 actual keyboard, target tracking, client item model, latency, terrain, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("46.2-power-depth", "power ▷ depth_strider: real submerged arrow path, protected hits and current ride", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-depth-power", "depth_strider ▷ power: observed water movement and prepared real-arrow inertia", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-punch-depth", "punch ▷ depth_strider: real submerged arrow path, protected hits and current ride", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-depth-punch", "depth_strider ▷ punch: observed water movement and prepared real-arrow inertia", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-flame-depth", "flame ▷ depth_strider: real submerged arrow path, protected hits and current ride", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-depth-flame", "depth_strider ▷ flame: observed water movement and prepared real-arrow inertia", "Not reached if setup fails; water, flight and input are prepared.");
        plan("46.2-runtime-compat", "46.2 natural fluid/input, movement and projectile synchronization, save/reload, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("46.3-power-efficiency", "power ▷ efficiency: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-efficiency-power", "efficiency ▷ power: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-punch-efficiency", "punch ▷ efficiency: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-efficiency-punch", "efficiency ▷ punch: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-flame-efficiency", "flame ▷ efficiency: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-efficiency-flame", "efficiency ▷ flame: real bow/crossbow and legal mining, prepared active input and observed virtual blade", "Not reached if setup fails; input and flight are prepared, not natural gameplay.");
        plan("46.3-runtime-compat", "46.3 natural input, client mining synchronization, trajectory changes, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("45.4-power-silk", "power ▷ silk: real marked target and later real arrow", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-silk-power", "silk ▷ power: original grounded arrow through vanilla pickup", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-punch-silk", "punch ▷ silk: real marked target and later real arrow", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-silk-punch", "silk ▷ punch: original grounded arrow through vanilla pickup", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-flame-silk", "flame ▷ silk: real marked target and later real arrow", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-silk-flame", "silk ▷ flame: original grounded arrow through vanilla pickup", "Not reached if setup fails; targets, flight, pickup and obstacles are prepared.");
        plan("45.4-runtime-compat", "45.4 natural aiming, projectile travel, item pickup, save/reload, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("46.4-power-mending", "power ▷ mending: actual arrow death XP, route and original-orb pickup", "Not reached if setup fails; XP values, aim, flight and pickup are prepared.");
        plan("46.4-mending-power", "mending ▷ power: actual orb repair and prepared damage arrow", "Not reached if setup fails; aim, flight and pickup are prepared.");
        plan("46.4-punch-mending", "punch ▷ mending: actual arrow death XP, route and original-orb pickup", "Not reached if setup fails; XP values, aim, flight and pickup are prepared.");
        plan("46.4-mending-punch", "mending ▷ punch: actual orb repair and prepared knockback arrow", "Not reached if setup fails; aim, flight and pickup are prepared.");
        plan("46.4-flame-mending", "flame ▷ mending: actual arrow death XP, route and original-orb pickup", "Not reached if setup fails; XP values, aim, flight and pickup are prepared.");
        plan("46.4-mending-flame", "mending ▷ flame: actual orb repair and prepared fire arrow", "Not reached if setup fails; aim, flight and pickup are prepared.");
        plan("46.4-runtime-compat", "46.4 natural XP routing/pickup, merge and nested awards, datapack Mending, multiplayer and other mods", "Not covered by the prepared single-player fixture.");
        plan("45.1-runtime-compat", "45.1 natural input, projectile deflection, save/reload, multiplayer and third-party projectile/durability mods", "Not covered by the simulated single-player fixture.");
        plan("42.1-sharp-unbreaking", "Sharpness ▷ Unbreaking: confirmed combo and actual guarded wear", "Not reached if setup fails.");
        plan("42.1-unbreaking-sharp", "Unbreaking ▷ Sharpness: real prevented wear and prepared melee bonus", "Not reached if setup fails.");
        plan("42.1-smite-unbreaking", "Smite ▷ Unbreaking: undead combo and actual guarded wear", "Not reached if setup fails.");
        plan("42.1-unbreaking-smite", "Unbreaking ▷ Smite: real prevented wear and undead bonus", "Not reached if setup fails.");
        plan("42.1-bane-unbreaking", "Bane ▷ Unbreaking: arthropod combo and actual guarded wear", "Not reached if setup fails.");
        plan("42.1-unbreaking-bane", "Unbreaking ▷ Bane: real prevented wear and arthropod bonus", "Not reached if setup fails.");
        plan("42.1-runtime-compat", "42.1 natural inputs and timing, spear, reload, multiplayer and other durability mods", "Not covered by the simulated single-player fixture.");
        plan("42.2-sharp-breath", "Sharpness ▷ Respiration: real underwater hits and simulated water state", "Not reached if setup fails.");
        plan("42.2-breath-sharp", "Respiration ▷ Sharpness: prepared helmet, air payment and real underwater hit", "Not reached if setup fails.");
        plan("42.2-smite-breath", "Smite ▷ Respiration: underwater bubble and undead filtering", "Not reached if setup fails.");
        plan("42.2-breath-smite", "Respiration ▷ Smite: prepared helmet and undead filtering", "Not reached if setup fails.");
        plan("42.2-bane-breath", "Bane ▷ Respiration: underwater bubble and arthropod filtering", "Not reached if setup fails.");
        plan("42.2-breath-bane", "Respiration ▷ Bane: prepared helmet and arthropod filtering", "Not reached if setup fails.");
        plan("42.2-runtime-compat", "42.2 natural fluid updates, client key, multiplayer, datapacks and claims", "Not covered by the artificially refreshed fluid-state fixture.");
        plan("42.4-sharp-protection", "Sharpness ▷ Protection: actual projectile impact and simulated recall", "Not reached if setup fails.");
        plan("42.4-protection-sharp", "Protection ▷ Sharpness: real per-item mitigation and counter hit", "Not reached if setup fails.");
        plan("42.4-smite-protection", "Smite ▷ Protection: undead guard activation and actual projectile impact", "Not reached if setup fails.");
        plan("42.4-protection-smite", "Protection ▷ Smite: per-item mitigation and undead counter hit", "Not reached if setup fails.");
        plan("42.4-bane-protection", "Bane ▷ Protection: arthropod guard activation and actual projectile impact", "Not reached if setup fails.");
        plan("42.4-protection-bane", "Protection ▷ Bane: per-item mitigation and arthropod counter hit", "Not reached if setup fails.");
        plan("42.4-runtime-compat", "42.4 natural swing packets, diverse projectiles, overlapping defense effects, multiplayer and protection mods", "Not covered by the prepared single-player fixture.");
        plan("07-fire-looting", "Fire Aspect ▷ Looting: prepared burn age, real extraction and same-enemy ordinary loot", "Not reached if setup fails; natural burn timing, multiplayer and third-party callbacks remain untested.");
        plan("07-looting-fire", "Looting ▷ Fire Aspect: real direct kills, normal loot and virtual ring with prepared approach", "Not reached if setup fails; natural input, aging, multiplayer and third-party hooks remain untested.");
        plan("07-runtime-compat", "07 natural input, drop attribution, PvP, claims, multiplayer and third-party callbacks", "Not covered by the prepared single-player fixture.");
        plan("08-knockback-looting", "Knockback ▷ Looting: real damage and knockback, prepared movement, same-weapon pursuit and bounded transfer", "Not reached if setup fails; natural movement and multiplayer are not covered.");
        plan("08-looting-knockback", "Looting ▷ Knockback: real equipment disarm, protected targets and Looting-primary projection", "Not reached if setup fails; natural input, external transfer hooks and multiplayer are not covered.");
        plan("08-runtime-compat", "08 natural movement and input, actual non-native weapon use, PvP, multiplayer and protection mods", "Not covered by the prepared single-player fixture.");
        plan("42.5-sharp-knockback", "Sharpness ▷ Knockback: actual knockback with simulated pursuit and follow-up", "Not reached if setup fails.");
        plan("42.5-knockback-sharp", "Knockback ▷ Sharpness: actual knockback and simulated swept contact", "Not reached if setup fails.");
        plan("42.5-smite-knockback", "Smite ▷ Knockback: undead pursuit and attack-primary replay", "Not reached if setup fails.");
        plan("42.5-knockback-smite", "Knockback ▷ Smite: undead contact and protected-target exclusion", "Not reached if setup fails.");
        plan("42.5-bane-knockback", "Bane ▷ Knockback: arthropod pursuit and attack-primary replay", "Not reached if setup fails.");
        plan("42.5-knockback-bane", "Knockback ▷ Bane: arthropod contact and protected-target exclusion", "Not reached if setup fails.");
        plan("42.5-runtime-compat", "42.5 natural movement and collision, client motion packets, spear, multiplayer and protection mods", "Not covered by simulated single-player motion.");
        plan("42.6-sharp-efficiency", "Sharpness ▷ Efficiency: real hits and one-block work boost", "Not reached if setup fails.");
        plan("42.6-efficiency-sharp", "Efficiency ▷ Sharpness: real mining and directed blade", "Not reached if setup fails.");
        plan("42.6-smite-efficiency", "Smite ▷ Efficiency: real hits and one-block work boost", "Not reached if setup fails.");
        plan("42.6-efficiency-smite", "Efficiency ▷ Smite: real mining and directed blade", "Not reached if setup fails.");
        plan("42.6-bane-efficiency", "Bane ▷ Efficiency: real hits and one-block work boost", "Not reached if setup fails.");
        plan("42.6-efficiency-bane", "Efficiency ▷ Bane: real mining and directed blade", "Not reached if setup fails.");
        plan("42.6-runtime-compat", "42.6 natural input, client mining sync, custom datapacks, multiplayer and claims", "Not covered by the simulated fixture.");
        plan("44.4-sharp-aqua", "Sharpness ▷ Aqua: real hits and one-block work boost", "Not reached if setup fails.");
        plan("44.4-aqua-sharp", "Aqua ▷ Sharpness: real mining and directed blade", "Not reached if setup fails.");
        plan("44.4-smite-aqua", "Smite ▷ Aqua: real hits and one-block work boost", "Not reached if setup fails.");
        plan("44.4-aqua-smite", "Aqua ▷ Smite: real mining and directed blade", "Not reached if setup fails.");
        plan("44.4-bane-aqua", "Bane ▷ Aqua: real hits and one-block work boost", "Not reached if setup fails.");
        plan("44.4-aqua-bane", "Aqua ▷ Bane: real mining and directed blade", "Not reached if setup fails.");
        plan("44.4-runtime-compat", "44.4 natural input, client mining sync, custom datapacks, multiplayer and claims", "Not covered by the simulated fixture.");
        plan("33.4-sharp-silk", "Sharpness ▷ Silk Touch: intercept and redirect a real hostile projectile", "Not implemented; no candidate fixture yet.");
        plan("44.1-smite-silk", "Smite ▷ Silk Touch: intercept and redirect a real hostile projectile", "Not implemented; no candidate fixture yet.");
        plan("44.1-bane-silk", "Bane ▷ Silk Touch: intercept and redirect a real hostile projectile", "Not implemented; no candidate fixture yet.");
        plan("33.4-44.1-runtime-compat", "Melee silk: natural input, PvP, armor, totems, save/unload and third-party hooks", "Not covered by the simulated single-player fixture.");
        plan("33.4-silk-sharp", "Silk Touch ▷ Sharpness: prepared hit, real permission, one release and cleanup", "Not reached if setup fails; natural input, shield timing, PvP and mod interactions remain separate.");
        plan("44.1-silk-smite", "Silk Touch ▷ Smite: qualified target, real permission, one release and cleanup", "Not reached if setup fails; natural input, shield timing, PvP and mod interactions remain separate.");
        plan("44.1-silk-bane", "Silk Touch ▷ Bane: qualified target, real permission, one release and cleanup", "Not reached if setup fails; natural input, shield timing, PvP and mod interactions remain separate.");
        plan("44.2-smite-mending", "Smite ▷ Mending: actual marked damage diversion and weapon repair", "Not reached if setup fails.");
        plan("44.2-mending-smite", "Mending ▷ Smite: actual experience-orb repair and prepared blade", "Not reached if setup fails.");
        plan("44.2-bane-mending", "Bane ▷ Mending: actual marked damage diversion and weapon repair", "Not reached if setup fails.");
        plan("44.2-mending-bane", "Mending ▷ Bane: actual experience-orb repair and prepared blade", "Not reached if setup fails.");
        plan("44.2-runtime-compat", "44.2 natural orb pickup, charged input, mixed equipment, datapacks and multiplayer", "Not covered by the simulated single-player fixture.");
        plan("40.1-sharp-lure", "Sharpness ▷ Lure: real hit and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-smite-lure", "Smite ▷ Lure: real undead hit and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-bane-lure", "Bane ▷ Lure: real arthropod hit and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-efficiency-lure", "Efficiency ▷ Lure: real block break and simulated pickup activation", "Not reached if setup fails.");
        plan("40.1-silk-lure", "Silk Touch ▷ Lure: real block loot and simulated pickup activation", "Not reached if setup fails.");
        plan("40.1-fortune-lure", "Fortune ▷ Lure: real block loot and simulated pickup activation", "Not reached if setup fails.");
        plan("40.1-power-lure", "Power ▷ Lure: real full-draw bow hit and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-flame-lure", "Flame ▷ Lure: real burning bow/crossbow hit and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-punch-lure", "Punch ▷ Lure: real arrow push and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-multishot-lure", "Multishot ▷ Lure: real multi-arrow volleys and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-infinity-lure", "Infinity ▷ Lure: real ammunition handling and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-piercing-lure", "Piercing ▷ Lure: real continuous penetration and simulated active target recall", "Not reached if setup fails.");
        plan("40.1-protection-lure", "Protection ▷ Lure: real hurt and shield block, simulated aim and recall", "Not reached if setup fails.");
        plan("40.1-fire-protection-lure", "Fire Protection ▷ Lure: real hurt with simulated fire source and recall", "Not reached if setup fails.");
        plan("40.1-blast-protection-lure", "Blast Protection ▷ Lure: real hurt with simulated explosion source and recall", "Not reached if setup fails.");
        plan("40.1-projectile-protection-lure", "Projectile Protection ▷ Lure: real hurt and shield block with simulated arrow source and recall", "Not reached if setup fails.");
        plan("40.1-feather-lure", "Feather Falling ▷ Lure: real hurt with simulated fall source and recall", "Not reached if setup fails.");
        plan("40.1-quick-lure", "Quick Charge ▷ Lure: actual load and arrow hit, simulated timing and recall", "Not reached if setup fails.");
        plan("40.1-mending-lure", "Mending ▷ Lure: actual orb repair and simulated aimed recall", "Not reached if setup fails.");
        plan("40.1-unbreaking-lure", "Unbreaking ▷ Lure: actual durability request and simulated aimed recall", "Not reached if setup fails.");
        plan("40.1-knockback-lure", "Knockback ▷ Lure: actual hit and resisted impulse, simulated recall", "Not reached if setup fails.");
        plan("40.1-breach-lure", "Breach ▷ Lure: actual armored hit and simulated recall", "Not reached if setup fails.");
        plan("40.1-density-lure", "Density ▷ Lure: actual mace hit with simulated fall and recall", "Not reached if setup fails.");
        plan("40.1-wind-lure", "Wind Burst ▷ Lure: real hit and wind, simulated fall and recall", "Not reached if setup fails.");
        plan("40.1-thorns-lure", "Thorns ▷ Lure: real hurt/retaliation with high test-only level and recall", "Not reached if setup fails.");
        plan("40.1-swift-lure", "Swift Sneak ▷ Lure: real movement with simulated input and equipment refresh", "Not reached if setup fails.");
        plan("40.1-depth-lure", "Depth Strider ▷ Lure: real movement with simulated input, water and equipment refresh", "Not reached if setup fails.");
        plan("40.1-soul-lure", "Soul Speed ▷ Lure: prepared soul ground, input, conditional effects and statistical wear", "Not reached if setup fails; wear samples are probabilistic.");
        plan("40.1-aqua-lure", "Aqua Affinity ▷ Lure: prepared water, real block loot and normal pickup", "Not reached if setup fails; client timing and other mods remain untested.");
        plan("40.1-breath-lure", "Respiration ▷ Lure: prepared water, real breathing rolls and shared oxygen projection", "Not reached if setup fails; positive sample is probabilistic and natural client timing remains untested.");
        plan("40.1-lunge-lure", "Lunge ▷ Lure: actual spear collision and impulse with prepared aim, charge and hunger", "Not reached if setup fails; natural input, multiplayer and third-party effects remain untested.");
        plan("40.1-riptide-lure", "Riptide ▷ Lure: vanilla held use, temporary water, simulated charge and collision-clipped movement", "Not reached if setup fails; natural input and timing, rain, passenger, multiplayer and third-party mods remain untested.");
        plan("40.1-loyalty-lure", "Loyalty ▷ Lure: real charged release and original return/pickup with prepared wall and server ticks", "Not reached if setup fails; natural client timing, disconnect/reload, datapack conditions, claims, multiplayer and third-party pickup remain untested.");
        plan("40.1-frost-lure", "Frost Walker ▷ Lure: prepared source water, explicit location events, thin-ice attribution and shared Soul projection", "Not reached if setup fails; natural movement, swimming, riding, timed decay, datapack changes, claims and multiplayer remain untested.");
        plan("40.1-impaling-lure", "Impaling ▷ Lure: real melee and trident release with a prepared collision and aquatic target", "Not reached if setup fails; natural flight, pickup/reload, multiplayer and datapacks remain untested.");
        plan("40.1-channeling-lure", "Channeling ▷ Lure: real trident release and lightning summons with prepared weather and collisions", "Not reached if setup fails; bolts are removed before ticking. Natural flight, storm, lightning damage, pickup/reload, multiplayer and datapacks remain untested.");
        plan("40.1-looting-lure", "Looting ▷ Lure: real melee death, deterministic test loot, count/chance provenance and normal pickup", "Not reached if setup fails; data tables, delay and activation are prepared. Natural input, projectiles, equipment attribution, multiplayer and third-party loot remain untested.");
        plan("40.1-sweep-lure", "Sweeping Edge ▷ Lure: real sword sweep with prepared equipment and victims", "Not reached if setup fails.");
        plan("40.1-attribute-shared-regression", "Shared work, movement and sweep primary attribute projections", "Not reached if setup fails.");
        plan("43.3-smite-channel", "Smite ▷ Channeling: real marks and virtual chain", "Not reached if setup fails.");
        plan("43.4-sharp-wind", "sharpness ▷ Wind Burst: real hit and simulated player movement", "Not reached if setup fails.");
        plan("43.4-wind-sharp", "Wind Burst ▷ sharpness: same real wind effect and simulated updraft follow-up", "Not reached if setup fails.");
        plan("43.4-smite-wind", "smite ▷ Wind Burst: real hit and simulated player movement", "Not reached if setup fails.");
        plan("43.4-wind-smite", "Wind Burst ▷ smite: same real wind effect and simulated updraft follow-up", "Not reached if setup fails.");
        plan("43.4-bane-wind", "bane_of_arthropods ▷ Wind Burst: real hit and simulated player movement", "Not reached if setup fails.");
        plan("43.4-wind-bane", "Wind Burst ▷ bane_of_arthropods: same real wind effect and simulated updraft follow-up", "Not reached if setup fails.");
        plan("43.3-channel-smite", "Channeling ▷ Smite: real trident and trace settlement", "Not reached if setup fails.");
        plan("43.3-bane-channel", "Bane ▷ Channeling: real marks and virtual chain", "Not reached if setup fails.");
        plan("43.3-channel-bane", "Channeling ▷ Bane: real trident and trace settlement", "Not reached if setup fails.");
        plan("43.2-sharp-frost", "Sharpness ▷ Frost Walker: real hit, mark and frost damage", "Not reached if setup fails.");
        plan("43.2-frost-sharp", "Frost Walker ▷ Sharpness: boots, ice-constrained blade and water guard", "Not reached if setup fails.");
        plan("43.2-smite-frost", "Smite ▷ Frost Walker: real hit, mark and frost damage", "Not reached if setup fails.");
        plan("43.2-frost-smite", "Frost Walker ▷ Smite: boots, ice-constrained blade and water guard", "Not reached if setup fails.");
        plan("43.2-bane-frost", "Bane ▷ Frost Walker: real hit, mark and frost damage", "Not reached if setup fails.");
        plan("43.2-frost-bane", "Frost Walker ▷ Bane: boots, ice-constrained blade and water guard", "Not reached if setup fails.");
        plan("43.1-sharp-fire", "Sharpness ▷ Fire Aspect: real burning hit and simulated extraction", "Not reached if setup fails.");
        plan("43.1-fire-sharp", "Fire Aspect ▷ Sharpness: real ignition and directed blade", "Not reached if setup fails.");
        plan("43.1-smite-fire", "Smite ▷ Fire Aspect: real burning hit and simulated extraction", "Not reached if setup fails.");
        plan("43.1-fire-smite", "Fire Aspect ▷ Smite: real ignition and directed blade", "Not reached if setup fails.");
        plan("43.1-bane-fire", "Bane ▷ Fire Aspect: real burning hit and simulated extraction", "Not reached if setup fails.");
        plan("43.1-fire-bane", "Fire Aspect ▷ Bane: real ignition and directed blade", "Not reached if setup fails.");
        plan("33.2-sharp-fortune", "Sharpness ▷ Fortune: real melee and simulated charge refresh", "Not reached if setup fails.");
        plan("33.2-fortune-sharp", "Fortune ▷ Sharpness: real melee and simulated stance timing", "Not reached if setup fails.");
        plan("44.3-smite-fortune", "Smite ▷ Fortune: real melee and simulated charge refresh", "Not reached if setup fails.");
        plan("44.3-fortune-smite", "Fortune ▷ Smite: real melee and simulated stance timing", "Not reached if setup fails.");
        plan("44.3-bane-fortune", "Bane ▷ Fortune: real melee and simulated charge refresh", "Not reached if setup fails.");
        plan("44.3-fortune-bane", "Fortune ▷ Bane: real melee and simulated stance timing", "Not reached if setup fails.");
        plan("40.1-runtime-compat", "40.1 natural key input and charge, spear, other players, claims, datapacks and remaining source adapters", "Not covered by the prepared single-player fixtures.");
        plan("42.3-sharp-swift", "Sharpness ▷ Swift Sneak: real hits and simulated imprint movement", "Not reached if setup fails.");
        plan("42.3-swift-sharp", "Swift Sneak ▷ Sharpness: worn leggings, simulated crouch and real hit", "Not reached if setup fails.");
        plan("42.3-smite-swift", "Smite ▷ Swift Sneak: real hits, undead filtering and simulated imprint movement", "Not reached if setup fails.");
        plan("42.3-swift-smite", "Swift Sneak ▷ Smite: worn leggings, simulated crouch and undead filtering", "Not reached if setup fails.");
        plan("42.3-bane-swift", "Bane ▷ Swift Sneak: real hits, arthropod filtering and simulated imprint movement", "Not reached if setup fails.");
        plan("42.3-swift-bane", "Swift Sneak ▷ Bane: worn leggings, simulated crouch and arthropod filtering", "Not reached if setup fails.");
        plan("42.3-runtime-compat", "42.3 natural input, sneak pose, lance thrust, multiplayer, datapacks and third-party claims", "Not covered by the simulated single-player fixtures.");
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
        plan("13-channeling-riptide", "Channeling ▷ Riptide: actual hit anchor and directed rush", "Not implemented; no candidate fixture yet.");
        plan("13-riptide-channeling", "Riptide ▷ Channeling: real held releases with prepared water, charge and collision-clipped movement", "Not reached if setup fails; spin ending is simulated and natural client movement, multiplayer and third-party hooks remain untested.");
        plan("13-runtime-compat", "13 natural movement and input, weather, projectile recovery, PvP, claims, multiplayer and third-party effects", "Not covered by the prepared single-player fixture.");
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
