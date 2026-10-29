package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CrossDomainMatrixCompletenessTest {

    // Canonical native-equipment mapping in Minecraft 26.1.2 (where vanilla anvil applies natively)
    private static final Map<String, Set<TargetRole>> NATIVE_ROLES = Map.ofEntries(
        Map.entry("minecraft:protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:fire_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:blast_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:projectile_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:feather_falling", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:respiration", Set.of(TargetRole.HEAD_ARMOR)),
        Map.entry("minecraft:aqua_affinity", Set.of(TargetRole.HEAD_ARMOR)),
        Map.entry("minecraft:thorns", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:depth_strider", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:frost_walker", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:soul_speed", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:swift_sneak", Set.of(TargetRole.LEG_ARMOR)),
        Map.entry("minecraft:sharpness", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE)),
        Map.entry("minecraft:smite", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE)),
        Map.entry("minecraft:bane_of_arthropods", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE)),
        Map.entry("minecraft:knockback", Set.of(TargetRole.SWORD, TargetRole.SPEAR)),
        Map.entry("minecraft:fire_aspect", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.MACE)),
        Map.entry("minecraft:looting", Set.of(TargetRole.SWORD, TargetRole.SPEAR)),
        Map.entry("minecraft:sweeping_edge", Set.of(TargetRole.SWORD)),
        Map.entry("minecraft:lunge", Set.of(TargetRole.SPEAR)),
        Map.entry("minecraft:efficiency", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:silk_touch", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:fortune", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:power", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:punch", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:flame", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:infinity", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:luck_of_the_sea", Set.of(TargetRole.FISHING_ROD)),
        Map.entry("minecraft:lure", Set.of(TargetRole.FISHING_ROD)),
        Map.entry("minecraft:loyalty", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:impaling", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:riptide", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:channeling", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:multishot", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:quick_charge", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:piercing", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:density", Set.of(TargetRole.MACE)),
        Map.entry("minecraft:breach", Set.of(TargetRole.MACE)),
        Map.entry("minecraft:wind_burst", Set.of(TargetRole.MACE))
    );

    private static final TargetRole[] EQUIPMENT_ROLES = new TargetRole[]{
        TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE, TargetRole.TRIDENT,
        TargetRole.BOW, TargetRole.CROSSBOW, TargetRole.DIG_TOOL, TargetRole.SHIELD,
        TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR,
        TargetRole.ELYTRA, TargetRole.FISHING_ROD
    };

    @Test
    void everyNonNativeEquipmentCombinationResolvesToRegisteredLocalizedTransmute() throws Exception {
        JsonObject profiles = JsonParser.parseString(
            Files.readString(
                Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json"),
                StandardCharsets.UTF_8
            )
        ).getAsJsonObject().getAsJsonObject("profiles");

        JsonObject enUs = JsonParser.parseString(
            Files.readString(
                Path.of("src/client/resources/assets/enchantment_fusion/lang/en_us.json"),
                StandardCharsets.UTF_8
            )
        ).getAsJsonObject();

        Set<String> registeredIds = CrossDomainTransmutations.allVariants().stream()
            .map(TransmuteVariant::id)
            .collect(Collectors.toSet());
        assertTrue(registeredIds.size() >= 135, "Expected full 0.5.0 cross-domain variant catalog");

        for (String enchId : profiles.keySet()) {
            // Universal durability/curse enchantments intentionally keep vanilla behavior on damageable gear
            if ("minecraft:unbreaking".equals(enchId)
                || "minecraft:mending".equals(enchId)
                || "minecraft:vanishing_curse".equals(enchId)) {
                continue;
            }
            if ("minecraft:binding_curse".equals(enchId)) {
                // Binding curse is native on armor/elytra, cross-domain on handheld weapons/tools/shield/rod
                for (TargetRole role : new TargetRole[]{
                    TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE,
                    TargetRole.TRIDENT, TargetRole.BOW, TargetRole.CROSSBOW, TargetRole.DIG_TOOL,
                    TargetRole.SHIELD, TargetRole.FISHING_ROD
                }) {
                    Optional<TransmuteVariant> res = CrossDomainTransmutations.resolveForRole(enchId, role, true);
                    assertTrue(res.isPresent(), "Missing cross-domain variant for " + enchId + " on " + role);
                }
                continue;
            }

            Set<TargetRole> nativeSet = NATIVE_ROLES.getOrDefault(enchId, Set.of());
            for (TargetRole role : EQUIPMENT_ROLES) {
                if (nativeSet.contains(role)) {
                    continue;
                }
                Optional<TransmuteVariant> res = CrossDomainTransmutations.resolveForRole(enchId, role, true);
                assertTrue(
                    res.isPresent(),
                    "Expected meaningful cross-domain transmutation for " + enchId + " on non-native role " + role
                );
                TransmuteVariant tv = res.get();
                assertTrue(
                    registeredIds.contains(tv.id()),
                    "Variant " + tv.id() + " (" + enchId + " -> " + role + ") missing from allVariants()"
                );
                assertTrue(enUs.has(tv.titleKey()), "Missing title key " + tv.titleKey());
                assertTrue(enUs.has(tv.descKey()), "Missing desc key " + tv.descKey());
            }
        }
    }
}
