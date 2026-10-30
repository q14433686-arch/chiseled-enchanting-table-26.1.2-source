package enchantment_fusion.mechanics.transmute;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Data-driven catalog for Cross-Domain Transmutations loaded from
 * {@code data/enchantment_fusion/enchantment_fusion/transmutations.json} (and reloadable via datapacks).
 */
public final class TransmuteCatalog {
    public static final String CLASSPATH_RESOURCE =
        "/data/enchantment_fusion/enchantment_fusion/transmutations.json";

    public record RoleBinding(
        TransmuteVariant variant,
        boolean requireDamageable
    ) {}

    public record Snapshot(
        Map<String, Map<TargetRole, RoleBinding>> matrix,
        List<TransmuteVariant> allVariants,
        Map<String, String> domainById
    ) {}

    private static volatile Snapshot activeSnapshot = loadClasspathDefault();

    private TransmuteCatalog() {}

    private static Snapshot loadClasspathDefault() {
        try (InputStream in = TransmuteCatalog.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in != null) {
                try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    return parseFromJson(obj);
                }
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Failed to bootstrap classpath transmutations.json", ex);
        }
        return new Snapshot(Map.of(), List.of(), Map.of());
    }

    public static Snapshot parseFromJson(@Nullable JsonObject root) {
        if (root == null || !root.has("variants") || !root.get("variants").isJsonObject()) {
            return activeSnapshot != null
                ? activeSnapshot
                : new Snapshot(Map.of(), List.of(), Map.of());
        }
        Map<String, Map<TargetRole, RoleBinding>> matrix = new LinkedHashMap<>();
        List<TransmuteVariant> variants = new ArrayList<>();
        Map<String, String> domains = new LinkedHashMap<>();

        JsonObject varObj = root.getAsJsonObject("variants");
        for (Map.Entry<String, JsonElement> entry : varObj.entrySet()) {
            String id = entry.getKey();
            if (id != null && id.length() > 128) throw new IllegalArgumentException("Catalog ID too long: " + id);
            if (id == null || id.isBlank() || !entry.getValue().isJsonObject()) continue;
            JsonObject def = entry.getValue().getAsJsonObject();
            String source = def.has("source") ? def.get("source").getAsString() : "";
            String domain = def.has("domain") ? def.get("domain").getAsString() : "melee_spear";
            boolean reqDmg = def.has("require_damageable") && def.get("require_damageable").getAsBoolean();
            Set<TargetRole> roles = parseRoleArray(def.getAsJsonArray("roles"));
            if (source.isBlank() || roles.isEmpty()) continue;

            source = FusedPair.canonicalizeEnchantId(source);
            TransmuteVariant primaryVariant = new TransmuteVariant(id, source);
            variants.add(primaryVariant);
            domains.put(id, domain);
            RoleBinding binding = new RoleBinding(primaryVariant, reqDmg);
            for (TargetRole role : roles) {
                addBinding(matrix, source, role, binding);
            }

            if (def.has("extra_sources") && def.get("extra_sources").isJsonArray()) {
                JsonObject overrideRolesObj = def.has("extra_source_roles") && def.get("extra_source_roles").isJsonObject()
                    ? def.getAsJsonObject("extra_source_roles")
                    : null;
                for (JsonElement extraEl : def.getAsJsonArray("extra_sources")) {
                    String rawSource = extraEl.getAsString();
                    Set<TargetRole> extraRoles = (overrideRolesObj != null && overrideRolesObj.has(rawSource))
                        ? parseRoleArray(overrideRolesObj.getAsJsonArray(rawSource))
                        : roles;
                    if (extraRoles.isEmpty()) continue;
                    String extraSource = FusedPair.canonicalizeEnchantId(rawSource);
                    RoleBinding aliasBinding = new RoleBinding(new TransmuteVariant(id, extraSource), reqDmg);
                    for (TargetRole r : extraRoles) {
                        addBinding(matrix, extraSource, r, aliasBinding);
                    }
                }
            }
        }
        Map<String, Map<TargetRole, RoleBinding>> frozen = new LinkedHashMap<>();
        matrix.forEach((source, byRole) -> frozen.put(source, Collections.unmodifiableMap(new EnumMap<>(byRole))));
        return new Snapshot(
            Collections.unmodifiableMap(frozen),
            Collections.unmodifiableList(variants),
            Collections.unmodifiableMap(domains)
        );
    }

    private static void addBinding(Map<String, Map<TargetRole, RoleBinding>> matrix, String source,
                                   TargetRole role, RoleBinding binding) {
        Map<TargetRole, RoleBinding> roles = matrix.computeIfAbsent(source, k -> new EnumMap<>(TargetRole.class));
        RoleBinding previous = roles.putIfAbsent(role, binding);
        if (previous != null && !previous.variant().id().equals(binding.variant().id())) {
            throw new IllegalArgumentException("Conflicting transmutations for " + source + " / " + role
                + ": " + previous.variant().id() + " and " + binding.variant().id());
        }
    }

    private static Set<TargetRole> parseRoleArray(@Nullable JsonArray arr) {
        EnumSet<TargetRole> out = EnumSet.noneOf(TargetRole.class);
        if (arr == null) return out;
        for (JsonElement el : arr) {
            try {
                out.add(TargetRole.valueOf(el.getAsString()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    /** Publish a previously parsed snapshot, so configuration changes can be staged first. */
    public static void publish(Snapshot snapshot) {
        if (snapshot != null) activeSnapshot = snapshot;
    }

    public static void reloadFromJson(@Nullable JsonObject mergedRoot) {
        if (mergedRoot == null) {
            activeSnapshot = loadClasspathDefault();
            return;
        }
        Snapshot parsed = parseFromJson(mergedRoot);
        if (!parsed.allVariants().isEmpty()) {
            activeSnapshot = parsed;
        }
    }

    public static List<TransmuteVariant> allVariants() {
        return activeSnapshot.allVariants();
    }

    public static boolean isEligibleCrossHost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (VesselCapacityRules.isPotentialEquipmentHost(stack)) return true;
        TargetRole role = classifyTarget(stack);
        return role == TargetRole.TOTEM || role == TargetRole.GLASS_BOTTLE || role == TargetRole.CONSUMABLE;
    }

    public static boolean isSpecialNonEquipmentHost(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VesselCapacityRules.isPotentialEquipmentHost(stack)) {
            return false;
        }
        TargetRole role = classifyTarget(stack);
        return role == TargetRole.TOTEM || role == TargetRole.GLASS_BOTTLE || role == TargetRole.CONSUMABLE;
    }

    public static TargetRole classifyTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return TargetRole.OTHER;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return TargetRole.TOTEM;
        if (stack.is(Items.GLASS_BOTTLE)) return TargetRole.GLASS_BOTTLE;
        if (stack.is(Items.ELYTRA) || stack.has(DataComponents.GLIDER)) return TargetRole.ELYTRA;

        Item item = stack.getItem();
        if (item instanceof BowItem) return TargetRole.BOW;
        if (item instanceof CrossbowItem) return TargetRole.CROSSBOW;
        if (item instanceof TridentItem) return TargetRole.TRIDENT;
        if (item instanceof MaceItem) return TargetRole.MACE;
        if (item instanceof ShieldItem) return TargetRole.SHIELD;
        if (item instanceof FishingRodItem) return TargetRole.FISHING_ROD;

        if (stack.is(ItemTags.SWORDS)) return TargetRole.SWORD;
        if (HostForm.isSpear(stack)) return TargetRole.SPEAR;
        if (stack.is(ItemTags.AXES)) return TargetRole.AXE;
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
            || stack.is(Items.SHEARS) || stack.is(Items.BRUSH) || stack.is(Items.FLINT_AND_STEEL)
            || stack.is(Items.CARROT_ON_A_STICK) || stack.is(Items.WARPED_FUNGUS_ON_A_STICK)) {
            return TargetRole.DIG_TOOL;
        }
        if (stack.is(ItemTags.HEAD_ARMOR)) return TargetRole.HEAD_ARMOR;
        if (stack.is(ItemTags.CHEST_ARMOR)) return TargetRole.CHEST_ARMOR;
        if (stack.is(ItemTags.LEG_ARMOR)) return TargetRole.LEG_ARMOR;
        if (stack.is(ItemTags.FOOT_ARMOR)) return TargetRole.FOOT_ARMOR;

        if (!stack.isDamageableItem() && (
            stack.is(Items.ENDER_PEARL)
                || stack.is(Items.WIND_CHARGE)
                || stack.is(Items.FIREWORK_ROCKET)
                || stack.is(Items.SNOWBALL)
                || stack.is(Items.EGG)
                || stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)
                || stack.has(DataComponents.CONSUMABLE)
                || stack.has(DataComponents.FOOD)
        )) {
            return TargetRole.CONSUMABLE;
        }
        return TargetRole.OTHER;
    }

    public static Optional<TransmuteVariant> resolve(String enchantId, ItemStack target) {
        if (enchantId == null || target == null || target.isEmpty()) return Optional.empty();
        return resolveForRole(enchantId, classifyTarget(target), target.isDamageableItem());
    }

    public static Optional<TransmuteVariant> resolveForRole(String enchantId, TargetRole role, boolean damageable) {
        if (enchantId == null || role == null || role == TargetRole.OTHER) return Optional.empty();
        if ("minecraft:vanishing_curse".equals(enchantId)) return Optional.empty();
        if (("minecraft:unbreaking".equals(enchantId) || "minecraft:mending".equals(enchantId)) && damageable) {
            return Optional.empty();
        }
        Map<TargetRole, RoleBinding> byRole = activeSnapshot.matrix().get(enchantId);
        if (byRole == null) return Optional.empty();
        RoleBinding b = byRole.get(role);
        if (b == null || (b.requireDamageable() && !damageable)) return Optional.empty();
        return Optional.of(b.variant());
    }

    public static boolean hasAnyTransmutedEnchants(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return !stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()).isEmpty();
    }

    public static int getTransmutedLevel(ItemStack stack, String variantId) {
        if (stack == null || stack.isEmpty() || variantId == null) return 0;
        Map<String, Integer> transMap = stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        for (Map.Entry<String, Integer> e : transMap.entrySet()) {
            Optional<TransmuteVariant> v = resolve(e.getKey(), stack);
            if (v.isPresent() && variantId.equals(v.get().id())) {
                return Math.max(0, e.getValue());
            }
        }
        return 0;
    }

    public static int getStandaloneEnchantLevel(ItemStack stack, String enchId) {
        if (stack == null || stack.isEmpty() || enchId == null) return 0;
        int transLv = stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.<String, Integer>of())
            .getOrDefault(enchId, 0);
        if (transLv > 0) return transLv;
        ItemEnchantments enchs = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchs.keySet()) {
            String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (enchId.equals(id)) return enchs.getLevel(holder);
        }
        return 0;
    }

    public static int getEffectiveUseSpeedBoost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof CrossbowItem) return 0;
        int best = Math.max(getTransmutedLevel(stack, "swift_draw"), getTransmutedLevel(stack, "reflex_shield"));
        FusedEnchantmentList pairs = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair p : pairs.pairs()) {
            if ("minecraft:quick_charge".equals(p.primaryId())) {
                best = Math.max(best, p.primaryLevel());
            } else if ("minecraft:quick_charge".equals(p.secondaryId())) {
                best = Math.max(best, Math.max(1, p.secondaryLevel() - 1));
            }
        }
        return best;
    }
}
