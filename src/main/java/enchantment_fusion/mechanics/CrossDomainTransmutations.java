package enchantment_fusion.mechanics;

import enchantment_fusion.model.HostForm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 3 ("异位单附魔化形"): When a single enchantment is inscribed onto a non-native item
 * at the Enchantment Fusion Table (consuming 0 Vessel Spirit slots), it transmutes into a
 * named variant with physically grounded cross-domain mechanics.
 */
public final class CrossDomainTransmutations {
    public enum TargetRole {
        SWORD,
        SPEAR,
        AXE,
        MACE,
        TRIDENT,
        BOW,
        CROSSBOW,
        DIG_TOOL,
        SHIELD,
        HEAD_ARMOR,
        CHEST_ARMOR,
        LEG_ARMOR,
        FOOT_ARMOR,
        ELYTRA,
        FISHING_ROD,
        TOTEM,
        GLASS_BOTTLE,
        CONSUMABLE,
        OTHER;

        public boolean isMeleeWeapon() {
            return this == SWORD || this == SPEAR || this == AXE || this == MACE || this == TRIDENT;
        }

        public boolean isRangedWeapon() {
            return this == BOW || this == CROSSBOW;
        }

        public boolean isAttackWeapon() {
            return isMeleeWeapon() || isRangedWeapon();
        }

        public boolean isTool() {
            return this == DIG_TOOL || this == AXE;
        }

        public boolean isArmor() {
            return this == HEAD_ARMOR || this == CHEST_ARMOR || this == LEG_ARMOR || this == FOOT_ARMOR;
        }

        public boolean isArmorOrShield() {
            return isArmor() || this == SHIELD || this == ELYTRA;
        }
    }

    public record TransmuteVariant(String id, String sourceEnchantId) {
        public String titleKey() {
            return "transmute.enchantment_fusion." + id + ".title";
        }

        public String descKey() {
            return "transmute.enchantment_fusion." + id + ".desc";
        }
    }

    private static final Map<String, TransmuteVariant> ALL_VARIANTS = new LinkedHashMap<>();
    private static final ThreadLocal<Boolean> IN_MULTI_BREAK = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> IN_CUSTOM_COMBAT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Runtime state maps
    private static final Map<UUID, UUID> SOUL_CHASE_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SOUL_CHASE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SWING_TICK = new ConcurrentHashMap<>();
    private static final Set<UUID> PICKPOCKETED_MOBS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> LAUNCH_PROCESSED_PROJ = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Integer> LAST_PLAYER_XP = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BOTTLE_XP_BUFFER = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REPLENISH_XP_BUFFER = new ConcurrentHashMap<>();
    private static final Map<String, Long> ACTIVE_SKILL_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SPEAR_CHARGE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> SPEAR_CHARGE_WEAPON = new ConcurrentHashMap<>();

    static {
        // Pre-register every canonical TransmuteVariant ID for Compendium & Locale parity verification
        String[][] defs = {
            // 0a: Fire Aspect / Flame -> Tools & Axe
            {"auto_smelt_pick", "minecraft:fire_aspect"},
            {"auto_smelt_axe", "minecraft:fire_aspect"},
            // 0b: Aqua Affinity -> Attack Weapons
            {"yishui", "minecraft:aqua_affinity"},
            // 1: Frost Walker
            {"frost_edge", "minecraft:frost_walker"},
            {"frost_seal", "minecraft:frost_walker"},
            // 2: Depth Strider
            {"wave_parting", "minecraft:depth_strider"},
            {"current_anchor", "minecraft:depth_strider"},
            // 3: Respiration
            {"steady_breath", "minecraft:respiration"},
            {"qi_breath", "minecraft:respiration"},
            // 4: Feather Falling
            {"light_feather", "minecraft:feather_falling"},
            {"feather_flight", "minecraft:feather_falling"},
            // 5: Soul Speed
            {"soul_chase", "minecraft:soul_speed"},
            {"soul_escape", "minecraft:soul_speed"},
            {"soul_seeker_arrow", "minecraft:soul_speed"},
            // 6: Swift Sneak
            {"silent_blade", "minecraft:swift_sneak"},
            {"silent_arrow", "minecraft:swift_sneak"},
            {"silent_pick", "minecraft:swift_sneak"},
            {"shroud_cloak", "minecraft:swift_sneak"},
            // 7: Thorns
            {"serrated_hook", "minecraft:thorns"},
            {"barbed_arrow", "minecraft:thorns"},
            {"spiked_shield", "minecraft:thorns"},
            // 8: Protection
            {"blade_parry", "minecraft:protection"},
            {"bracer_guard", "minecraft:protection"},
            {"miner_guard", "minecraft:protection"},
            {"bastion_shield", "minecraft:protection"},
            // 9: Fire Protection
            {"heat_shield_pick", "minecraft:fire_protection"},
            {"flame_cut", "minecraft:fire_protection"},
            {"fireproof_shield", "minecraft:fire_protection"},
            // 10: Blast Protection
            {"creeper_defuse", "minecraft:blast_protection"},
            {"blast_breech", "minecraft:blast_protection"},
            {"blast_guard", "minecraft:blast_protection"},
            // 11: Projectile Protection
            {"arrow_deflect", "minecraft:projectile_protection"},
            {"arrow_catch_shield", "minecraft:projectile_protection"},
            // 12: Efficiency
            {"swift_swing", "minecraft:efficiency"},
            {"rapid_draw", "minecraft:efficiency"},
            {"nimble_gear", "minecraft:efficiency"},
            // 13: Silk Touch
            {"intact_blade", "minecraft:silk_touch"},
            {"featherstep_lining", "minecraft:silk_touch"},
            // 14: Fortune ($3 Bill!)
            {"three_dollar_luck", "minecraft:fortune"},
            // 15: Sharpness
            {"sharpened_tool", "minecraft:sharpness"},
            {"broadhead_arrow", "minecraft:sharpness"},
            {"bladed_rim", "minecraft:sharpness"},
            // 16: Smite
            {"holy_bolt", "minecraft:smite"},
            {"hallowed_pick", "minecraft:smite"},
            {"undead_ward", "minecraft:smite"},
            // 17: Bane of Arthropods
            {"arthropod_bolt", "minecraft:bane_of_arthropods"},
            {"nest_purger", "minecraft:bane_of_arthropods"},
            {"venom_ward", "minecraft:bane_of_arthropods"},
            // 18: Knockback / Punch
            {"heavy_impact", "minecraft:knockback"},
            {"recoil_jump", "minecraft:knockback"},
            {"repulse_guard", "minecraft:knockback"},
            {"warding_shove", "minecraft:knockback"},
            // 19: Sweeping Edge
            {"heavy_cleave", "minecraft:sweeping_edge"},
            {"wide_reap", "minecraft:sweeping_edge"},
            {"wind_cutter_arrow", "minecraft:sweeping_edge"},
            {"shield_sweep", "minecraft:sweeping_edge"},
            // 20: Looting
            {"ranged_heavy_plunder", "minecraft:looting"},
            {"forager_luck", "minecraft:looting"},
            {"pickpocket_guard", "minecraft:looting"},
            // 21: Power
            {"mighty_bolt", "minecraft:power"},
            {"full_draw_cleave", "minecraft:power"},
            {"hardrock_breaker", "minecraft:power"},
            {"titan_brace", "minecraft:power"},
            // 22: Infinity
            {"endless_crossbow", "minecraft:infinity"},
            {"phantom_trident", "minecraft:infinity"},
            {"unending_edge", "minecraft:infinity"},
            // 23: Multishot
            {"fan_volley", "minecraft:multishot"},
            {"twin_shadow_slash", "minecraft:multishot"},
            {"trifork_mine", "minecraft:multishot"},
            {"splinter_shield", "minecraft:multishot"},
            // 24: Piercing
            {"sun_piercer", "minecraft:piercing"},
            {"skewer_thrust", "minecraft:piercing"},
            {"deep_bore", "minecraft:piercing"},
            // 25: Quick Charge
            {"swift_draw", "minecraft:quick_charge"},
            {"rapid_recovery", "minecraft:quick_charge"},
            {"reflex_shield", "minecraft:quick_charge"},
            {"chain_tempo", "minecraft:quick_charge"},
            // 26: Loyalty
            {"returning_blade_throw", "minecraft:loyalty"},
            {"boomerang_arrow", "minecraft:loyalty"},
            {"auto_loot_return", "minecraft:loyalty"},
            {"void_homing", "minecraft:loyalty"},
            // 27: Riptide
            {"aqua_spiral_dash", "minecraft:riptide"},
            {"hydro_propulsion", "minecraft:riptide"},
            {"torrent_mine", "minecraft:riptide"},
            {"surge_ram", "minecraft:riptide"},
            // 28: Channeling
            {"storm_rod_strike", "minecraft:channeling"},
            {"lightning_conductor_armor", "minecraft:channeling"},
            // 29: Impaling
            {"drenched_bane", "minecraft:impaling"},
            {"prismarine_chisel", "minecraft:impaling"},
            {"guardian_ward", "minecraft:impaling"},
            // 30: Density
            {"heavy_plummet", "minecraft:density"},
            {"heavy_slug_arrow", "minecraft:density"},
            {"anvil_stomp", "minecraft:density"},
            // 31: Breach
            {"hard_breach", "minecraft:breach"},
            {"shell_cracker", "minecraft:breach"},
            // 32: Wind Burst
            {"pogo_gale_slash", "minecraft:wind_burst"},
            {"wind_charge_arrow", "minecraft:wind_burst"},
            {"gale_shield", "minecraft:wind_burst"},
            // 33: Luck of the Sea
            {"ocean_bounty", "minecraft:luck_of_the_sea"},
            {"sand_panning", "minecraft:luck_of_the_sea"},
            {"sea_luck_aura", "minecraft:luck_of_the_sea"},
            // 34: Lure
            {"lure_hook_arrow", "minecraft:lure"},
            {"taunt_blade", "minecraft:lure"},
            {"shepherd_lure", "minecraft:lure"},
            {"monster_beacon", "minecraft:lure"},
            // 35: Curse of Binding
            {"sticky_root_curse", "minecraft:binding_curse"},
            // 37a: Unbreaking on Special Items
            {"unbreaking_totem", "minecraft:unbreaking"},
            {"unbreaking_ration", "minecraft:unbreaking"},
            // 37b: Mending on Special Items
            {"mending_xp_bottle", "minecraft:mending"},
            {"mending_replenish", "minecraft:mending"},
            {"mending_vitality", "minecraft:mending"},
            // 38: 26.1.2 Lunge (突进) Cross-Domain Transmutations
            {"lunge_dash_strike", "minecraft:lunge"},
            {"lunge_vault_shot", "minecraft:lunge"},
            {"lunge_wind_stride", "minecraft:lunge"},
            {"lunge_tunnel_rush", "minecraft:lunge"},
            // 39: 0.5.0 Complete Cross-Domain Equipment & Elytra / Fishing Rod Matrix
            {"searing_bolt", "minecraft:fire_aspect"},
            {"solar_brand", "minecraft:flame"},
            {"ember_mantle", "minecraft:fire_aspect"},
            {"boiling_cast", "minecraft:fire_aspect"},
            {"tidal_carapace", "minecraft:aqua_affinity"},
            {"abyssal_dredge", "minecraft:aqua_affinity"},
            {"rime_aegis", "minecraft:frost_walker"},
            {"ice_hole_angler", "minecraft:frost_walker"},
            {"pelagic_stream", "minecraft:depth_strider"},
            {"gill_membrane", "minecraft:respiration"},
            {"updraft_plume", "minecraft:feather_falling"},
            {"soul_excavator", "minecraft:soul_speed"},
            {"briar_mattock", "minecraft:thorns"},
            {"counter_volley", "minecraft:projectile_protection"},
            {"slipstream_wings", "minecraft:efficiency"},
            {"gentle_reel", "minecraft:silk_touch"},
            {"crushing_edge", "minecraft:sharpness"},
            {"whirlwind_hem", "minecraft:sweeping_edge"},
            {"refracted_carapace", "minecraft:multishot"},
            {"armor_needle", "minecraft:piercing"},
            {"adrenaline_surge", "minecraft:quick_charge"},
            {"sunder_plate", "minecraft:breach"},
            {"jetstream_boost", "minecraft:wind_burst"},
            {"storm_glider", "minecraft:riptide"},
            {"auto_reel", "minecraft:loyalty"}
        };
        for (String[] d : defs) {
            ALL_VARIANTS.put(d[0], new TransmuteVariant(d[0], d[1]));
        }
    }

    private CrossDomainTransmutations() {}

    public static List<TransmuteVariant> allVariants() {
        return List.copyOf(ALL_VARIANTS.values());
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) return;
        SOUL_CHASE_TARGET.remove(playerId);
        SOUL_CHASE_UNTIL.remove(playerId);
        LAST_SWING_TICK.remove(playerId);
        LAST_PLAYER_XP.remove(playerId);
        BOTTLE_XP_BUFFER.remove(playerId);
        REPLENISH_XP_BUFFER.remove(playerId);
        SPEAR_CHARGE_TICKS.remove(playerId);
        SPEAR_CHARGE_WEAPON.remove(playerId);
        String prefix = playerId + "|";
        ACTIVE_SKILL_COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clearRuntimeState() {
        SOUL_CHASE_TARGET.clear();
        SOUL_CHASE_UNTIL.clear();
        LAST_SWING_TICK.clear();
        PICKPOCKETED_MOBS.clear();
        LAUNCH_PROCESSED_PROJ.clear();
        LAST_PLAYER_XP.clear();
        BOTTLE_XP_BUFFER.clear();
        REPLENISH_XP_BUFFER.clear();
        ACTIVE_SKILL_COOLDOWNS.clear();
        SPEAR_CHARGE_TICKS.clear();
        SPEAR_CHARGE_WEAPON.clear();
    }

    public static boolean isEligibleCrossHost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (VesselCapacityRules.isPotentialEquipmentHost(stack)) {
            return true;
        }
        TargetRole role = classifyTarget(stack);
        return role == TargetRole.TOTEM || role == TargetRole.GLASS_BOTTLE || role == TargetRole.CONSUMABLE;
    }

    public static boolean isEligibleCrossDomainHost(ItemStack stack) {
        return isEligibleCrossHost(stack);
    }

    public static boolean isSpecialNonEquipmentHost(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VesselCapacityRules.isPotentialEquipmentHost(stack)) {
            return false;
        }
        TargetRole role = classifyTarget(stack);
        return role == TargetRole.TOTEM || role == TargetRole.GLASS_BOTTLE || role == TargetRole.CONSUMABLE;
    }

    public static TargetRole classifyTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return TargetRole.OTHER;
        }
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return TargetRole.TOTEM;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            return TargetRole.GLASS_BOTTLE;
        }
        if (stack.is(Items.ELYTRA) || stack.has(DataComponents.GLIDER)) {
            return TargetRole.ELYTRA;
        }
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

    /**
     * Resolves the Cross-Domain Single Transmutation when {@code enchId} is applied onto {@code stack}.
     * Returns {@link Optional#empty()} if the enchantment is native on {@code stack} or unsupported on {@code stack}.
     */
    public static Optional<TransmuteVariant> resolve(String enchId, ItemStack stack) {
        if (enchId == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return resolveForRole(enchId, classifyTarget(stack), stack.isDamageableItem());
    }

    public static Optional<TransmuteVariant> resolve(ItemStack stack, String enchId) {
        return resolve(enchId, stack);
    }

    public static Optional<TransmuteVariant> resolveForRole(String enchId, TargetRole role, boolean damageable) {
        if (enchId == null || role == TargetRole.OTHER) {
            return Optional.empty();
        }
        String vid = switch (enchId) {
            case "minecraft:vanishing_curse" -> null; // Universal — keeps vanilla

            case "minecraft:unbreaking" -> {
                if (damageable) yield null;
                if (role == TargetRole.TOTEM) yield "unbreaking_totem";
                if (role == TargetRole.CONSUMABLE) yield "unbreaking_ration";
                yield null;
            }
            case "minecraft:mending" -> {
                if (damageable) yield null;
                if (role == TargetRole.GLASS_BOTTLE) yield "mending_xp_bottle";
                if (role == TargetRole.TOTEM) yield "mending_vitality";
                if (role == TargetRole.CONSUMABLE) yield "mending_replenish";
                yield null;
            }

            // 0a: Fire Aspect / Flame -> Tools, Ranged/Melee, Fishing Rod & Armor
            case "minecraft:fire_aspect" -> {
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.MACE) yield null;
                if (role == TargetRole.AXE) yield "auto_smelt_axe";
                if (role == TargetRole.DIG_TOOL) yield "auto_smelt_pick";
                if (role.isRangedWeapon() || role == TargetRole.TRIDENT) yield "searing_bolt";
                if (role == TargetRole.FISHING_ROD) yield "boiling_cast";
                if (role.isArmorOrShield()) yield "ember_mantle";
                yield null;
            }
            case "minecraft:flame" -> {
                if (role == TargetRole.BOW) yield null;
                if (role == TargetRole.AXE) yield "auto_smelt_axe";
                if (role == TargetRole.DIG_TOOL) yield "auto_smelt_pick";
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.MACE) yield "solar_brand";
                if (role == TargetRole.CROSSBOW || role == TargetRole.TRIDENT) yield "searing_bolt";
                if (role == TargetRole.FISHING_ROD) yield "boiling_cast";
                if (role.isArmorOrShield()) yield "ember_mantle";
                yield null;
            }

            // 0b: Aqua Affinity -> Attack Weapons "沂水", Dredge Tools & Tidal Carapace
            case "minecraft:aqua_affinity" -> {
                if (role == TargetRole.HEAD_ARMOR) yield null;
                if (role.isAttackWeapon()) yield "yishui";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "abyssal_dredge";
                if (role.isArmorOrShield()) yield "tidal_carapace";
                yield null;
            }

            // 1: Frost Walker
            case "minecraft:frost_walker" -> {
                if (role == TargetRole.FOOT_ARMOR) yield null;
                if (role == TargetRole.DIG_TOOL) yield "frost_seal";
                if (role.isAttackWeapon()) yield "frost_edge";
                if (role == TargetRole.FISHING_ROD) yield "ice_hole_angler";
                if (role.isArmorOrShield()) yield "rime_aegis";
                yield null;
            }

            // 2: Depth Strider
            case "minecraft:depth_strider" -> {
                if (role == TargetRole.FOOT_ARMOR) yield null;
                if (role == TargetRole.DIG_TOOL) yield "current_anchor";
                if (role.isAttackWeapon()) yield "wave_parting";
                if (role.isArmorOrShield() || role == TargetRole.FISHING_ROD) yield "pelagic_stream";
                yield null;
            }

            // 3: Respiration
            case "minecraft:respiration" -> {
                if (role == TargetRole.HEAD_ARMOR) yield null;
                if (role.isRangedWeapon()) yield "steady_breath";
                if (role.isMeleeWeapon()) yield "qi_breath";
                if (role.isArmorOrShield() || role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "gill_membrane";
                yield null;
            }

            // 4: Feather Falling
            case "minecraft:feather_falling" -> {
                if (role == TargetRole.FOOT_ARMOR) yield null;
                if (role.isRangedWeapon()) yield "feather_flight";
                if (role.isMeleeWeapon() || role == TargetRole.DIG_TOOL) yield "light_feather";
                if (role.isArmorOrShield() || role == TargetRole.FISHING_ROD) yield "updraft_plume";
                yield null;
            }

            // 5: Soul Speed
            case "minecraft:soul_speed" -> {
                if (role == TargetRole.FOOT_ARMOR) yield null;
                if (role.isRangedWeapon()) yield "soul_seeker_arrow";
                if (role.isMeleeWeapon()) yield "soul_chase";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "soul_excavator";
                if (role.isArmorOrShield()) yield "soul_escape";
                yield null;
            }

            // 6: Swift Sneak
            case "minecraft:swift_sneak" -> {
                if (role == TargetRole.LEG_ARMOR) yield null;
                if (role.isMeleeWeapon()) yield "silent_blade";
                if (role.isRangedWeapon()) yield "silent_arrow";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "silent_pick";
                if (role.isArmorOrShield()) yield "shroud_cloak";
                yield null;
            }

            // 7: Thorns
            case "minecraft:thorns" -> {
                if (role.isArmor()) yield null;
                if (role.isMeleeWeapon()) yield "serrated_hook";
                if (role.isRangedWeapon()) yield "barbed_arrow";
                if (role == TargetRole.SHIELD) yield "spiked_shield";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD || role == TargetRole.ELYTRA) yield "briar_mattock";
                yield null;
            }

            // 8: Protection
            case "minecraft:protection" -> {
                if (role.isArmor()) yield null;
                if (role.isMeleeWeapon()) yield "blade_parry";
                if (role.isRangedWeapon()) yield "bracer_guard";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "miner_guard";
                if (role == TargetRole.SHIELD || role == TargetRole.ELYTRA) yield "bastion_shield";
                yield null;
            }

            // 9: Fire Protection
            case "minecraft:fire_protection" -> {
                if (role.isArmor()) yield null;
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "heat_shield_pick";
                if (role.isMeleeWeapon() || role.isRangedWeapon()) yield "flame_cut";
                if (role == TargetRole.SHIELD || role == TargetRole.ELYTRA) yield "fireproof_shield";
                yield null;
            }

            // 10: Blast Protection
            case "minecraft:blast_protection" -> {
                if (role.isArmor()) yield null;
                if (role.isMeleeWeapon()) yield "creeper_defuse";
                if (role.isRangedWeapon()) yield "blast_breech";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.SHIELD || role == TargetRole.ELYTRA || role == TargetRole.FISHING_ROD) {
                    yield "blast_guard";
                }
                yield null;
            }

            // 11: Projectile Protection
            case "minecraft:projectile_protection" -> {
                if (role.isArmor()) yield null;
                if (role.isMeleeWeapon()) yield "arrow_deflect";
                if (role == TargetRole.SHIELD) yield "arrow_catch_shield";
                if (role.isRangedWeapon() || role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD || role == TargetRole.ELYTRA) {
                    yield "counter_volley";
                }
                yield null;
            }

            // 12: Efficiency
            case "minecraft:efficiency" -> {
                if (role == TargetRole.DIG_TOOL || role == TargetRole.AXE) yield null;
                if (role.isMeleeWeapon()) yield "swift_swing";
                if (role.isRangedWeapon()) yield "rapid_draw";
                if (role == TargetRole.FISHING_ROD || role == TargetRole.ELYTRA) yield "slipstream_wings";
                if (role.isArmor() || role == TargetRole.SHIELD) yield "nimble_gear";
                yield null;
            }

            // 13: Silk Touch
            case "minecraft:silk_touch" -> {
                if (role == TargetRole.DIG_TOOL || role == TargetRole.AXE) yield null;
                if (role.isAttackWeapon()) yield "intact_blade";
                if (role == TargetRole.FISHING_ROD) yield "gentle_reel";
                if (role.isArmorOrShield()) yield "featherstep_lining";
                yield null;
            }

            // 14: Fortune ($3 Bill!)
            case "minecraft:fortune" -> {
                if (role == TargetRole.DIG_TOOL || role == TargetRole.AXE) yield null;
                if (role.isAttackWeapon() || role.isArmorOrShield() || role == TargetRole.FISHING_ROD) yield "three_dollar_luck";
                yield null;
            }

            // 15: Sharpness
            case "minecraft:sharpness" -> {
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.AXE) yield null;
                if (role == TargetRole.MACE || role == TargetRole.FISHING_ROD || role == TargetRole.ELYTRA) yield "crushing_edge";
                if (role == TargetRole.DIG_TOOL) yield "sharpened_tool";
                if (role.isRangedWeapon() || role == TargetRole.TRIDENT) yield "broadhead_arrow";
                if (role.isArmor() || role == TargetRole.SHIELD) yield "bladed_rim";
                yield null;
            }

            // 16: Smite
            case "minecraft:smite" -> {
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.AXE || role == TargetRole.MACE) yield null;
                if (role.isRangedWeapon() || role == TargetRole.TRIDENT || role == TargetRole.FISHING_ROD) yield "holy_bolt";
                if (role == TargetRole.DIG_TOOL) yield "hallowed_pick";
                if (role.isArmorOrShield()) yield "undead_ward";
                yield null;
            }

            // 17: Bane of Arthropods
            case "minecraft:bane_of_arthropods" -> {
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.AXE || role == TargetRole.MACE) yield null;
                if (role.isRangedWeapon() || role == TargetRole.TRIDENT || role == TargetRole.FISHING_ROD) yield "arthropod_bolt";
                if (role == TargetRole.DIG_TOOL) yield "nest_purger";
                if (role.isArmorOrShield()) yield "venom_ward";
                yield null;
            }

            // 18: Knockback / Punch
            case "minecraft:knockback", "minecraft:punch" -> {
                if ("minecraft:knockback".equals(enchId) && (role == TargetRole.SWORD || role == TargetRole.SPEAR)) yield null;
                if ("minecraft:punch".equals(enchId) && role == TargetRole.BOW) {
                    yield null;
                }
                if (role == TargetRole.CROSSBOW || role == TargetRole.TRIDENT) yield "heavy_impact";
                if (role == TargetRole.BOW || role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.AXE || role == TargetRole.MACE) {
                    yield role.isRangedWeapon() ? "recoil_jump" : "warding_shove";
                }
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "warding_shove";
                if (role.isArmorOrShield()) yield "repulse_guard";
                yield null;
            }

            // 19: Sweeping Edge
            case "minecraft:sweeping_edge" -> {
                if (role == TargetRole.SWORD) yield null;
                if (role == TargetRole.SPEAR || role == TargetRole.AXE || role == TargetRole.TRIDENT || role == TargetRole.MACE) yield "heavy_cleave";
                if (role == TargetRole.DIG_TOOL) yield "wide_reap";
                if (role.isRangedWeapon()) yield "wind_cutter_arrow";
                if (role == TargetRole.SHIELD) yield "shield_sweep";
                if (role.isArmor() || role == TargetRole.ELYTRA || role == TargetRole.FISHING_ROD) yield "whirlwind_hem";
                yield null;
            }

            // 20: Looting
            case "minecraft:looting" -> {
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR) yield null;
                if (role.isRangedWeapon() || role == TargetRole.AXE || role == TargetRole.TRIDENT
                    || role == TargetRole.MACE || role == TargetRole.FISHING_ROD) {
                    yield "ranged_heavy_plunder";
                }
                if (role == TargetRole.DIG_TOOL) yield "forager_luck";
                if (role.isArmorOrShield()) yield "pickpocket_guard";
                yield null;
            }

            // 21: Power
            case "minecraft:power" -> {
                if (role == TargetRole.BOW) yield null;
                if (role == TargetRole.CROSSBOW || role == TargetRole.TRIDENT || role == TargetRole.FISHING_ROD) yield "mighty_bolt";
                if (role.isMeleeWeapon()) yield "full_draw_cleave";
                if (role == TargetRole.DIG_TOOL) yield "hardrock_breaker";
                if (role.isArmorOrShield()) yield "titan_brace";
                yield null;
            }

            // 22: Infinity
            case "minecraft:infinity" -> {
                if (role == TargetRole.BOW) yield null;
                if (role == TargetRole.CROSSBOW) yield "endless_crossbow";
                if (role == TargetRole.TRIDENT) yield "phantom_trident";
                if (damageable) yield "unending_edge";
                yield null;
            }

            // 23: Multishot
            case "minecraft:multishot" -> {
                if (role == TargetRole.CROSSBOW) yield null;
                if (role == TargetRole.BOW || role == TargetRole.TRIDENT) yield "fan_volley";
                if (role.isMeleeWeapon()) yield "twin_shadow_slash";
                if (role == TargetRole.DIG_TOOL) yield "trifork_mine";
                if (role == TargetRole.SHIELD) yield "splinter_shield";
                if (role.isArmor() || role == TargetRole.ELYTRA || role == TargetRole.FISHING_ROD) yield "refracted_carapace";
                yield null;
            }

            // 24: Piercing
            case "minecraft:piercing" -> {
                if (role == TargetRole.CROSSBOW) yield null;
                if (role == TargetRole.BOW || role == TargetRole.TRIDENT) yield "sun_piercer";
                if (role.isMeleeWeapon()) yield "skewer_thrust";
                if (role == TargetRole.DIG_TOOL) yield "deep_bore";
                if (role.isArmorOrShield() || role == TargetRole.FISHING_ROD) yield "armor_needle";
                yield null;
            }

            // 25: Quick Charge
            case "minecraft:quick_charge" -> {
                if (role == TargetRole.CROSSBOW) yield null;
                if (role == TargetRole.BOW || role == TargetRole.TRIDENT) yield "swift_draw";
                if (role.isMeleeWeapon()) yield "rapid_recovery";
                if (role == TargetRole.SHIELD) yield "reflex_shield";
                if (role == TargetRole.DIG_TOOL) yield "chain_tempo";
                if (role.isArmor() || role == TargetRole.ELYTRA || role == TargetRole.FISHING_ROD) yield "adrenaline_surge";
                yield null;
            }

            // 26: Loyalty
            case "minecraft:loyalty" -> {
                if (role == TargetRole.TRIDENT) yield null;
                if (role == TargetRole.SWORD || role == TargetRole.SPEAR || role == TargetRole.AXE || role == TargetRole.MACE) yield "returning_blade_throw";
                if (role.isRangedWeapon()) yield "boomerang_arrow";
                if (role == TargetRole.DIG_TOOL) yield "auto_loot_return";
                if (role == TargetRole.FISHING_ROD || role == TargetRole.ELYTRA) yield "auto_reel";
                if (role.isArmor() || role == TargetRole.SHIELD) yield "void_homing";
                yield null;
            }

            // 27: Riptide
            case "minecraft:riptide" -> {
                if (role == TargetRole.TRIDENT) yield null;
                if (role.isMeleeWeapon()) yield "aqua_spiral_dash";
                if (role.isRangedWeapon()) yield "hydro_propulsion";
                if (role == TargetRole.DIG_TOOL) yield "torrent_mine";
                if (role == TargetRole.ELYTRA || role == TargetRole.FISHING_ROD) yield "storm_glider";
                if (role.isArmor() || role == TargetRole.SHIELD) yield "surge_ram";
                yield null;
            }

            // 28: Channeling
            case "minecraft:channeling" -> {
                if (role == TargetRole.TRIDENT) yield null;
                if (role.isAttackWeapon() || role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "storm_rod_strike";
                if (role.isArmorOrShield()) yield "lightning_conductor_armor";
                yield null;
            }

            // 29: Impaling
            case "minecraft:impaling" -> {
                if (role == TargetRole.TRIDENT) yield null;
                if (role.isAttackWeapon() || role == TargetRole.FISHING_ROD) yield "drenched_bane";
                if (role == TargetRole.DIG_TOOL) yield "prismarine_chisel";
                if (role.isArmorOrShield()) yield "guardian_ward";
                yield null;
            }

            // 30: Density
            case "minecraft:density" -> {
                if (role == TargetRole.MACE) yield null;
                if (role.isMeleeWeapon() || role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "heavy_plummet";
                if (role.isRangedWeapon()) yield "heavy_slug_arrow";
                if (role.isArmorOrShield()) yield "anvil_stomp";
                yield null;
            }

            // 31: Breach
            case "minecraft:breach" -> {
                if (role == TargetRole.MACE) yield null;
                if (role.isAttackWeapon()) yield "hard_breach";
                if (role == TargetRole.DIG_TOOL) yield "shell_cracker";
                if (role.isArmorOrShield() || role == TargetRole.FISHING_ROD) yield "sunder_plate";
                yield null;
            }

            // 32: Wind Burst
            case "minecraft:wind_burst" -> {
                if (role == TargetRole.MACE) yield null;
                if (role.isMeleeWeapon()) yield "pogo_gale_slash";
                if (role.isRangedWeapon()) yield "wind_charge_arrow";
                if (role == TargetRole.ELYTRA || role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "jetstream_boost";
                if (role.isArmor() || role == TargetRole.SHIELD) yield "gale_shield";
                yield null;
            }

            // 33: Luck of the Sea
            case "minecraft:luck_of_the_sea" -> {
                if (role == TargetRole.FISHING_ROD) yield null;
                if (role.isAttackWeapon()) yield "ocean_bounty";
                if (role == TargetRole.DIG_TOOL) yield "sand_panning";
                if (role.isArmorOrShield()) yield "sea_luck_aura";
                yield null;
            }

            // 34: Lure
            case "minecraft:lure" -> {
                if (role == TargetRole.FISHING_ROD) yield null;
                if (role.isRangedWeapon()) yield "lure_hook_arrow";
                if (role.isMeleeWeapon()) yield "taunt_blade";
                if (role == TargetRole.DIG_TOOL) yield "shepherd_lure";
                if (role.isArmorOrShield()) yield "monster_beacon";
                yield null;
            }

            // 35: Curse of Binding
            case "minecraft:binding_curse" -> {
                if (role.isArmor() || role == TargetRole.ELYTRA) yield null;
                if (role.isAttackWeapon() || role == TargetRole.DIG_TOOL || role == TargetRole.SHIELD || role == TargetRole.FISHING_ROD) {
                    yield "sticky_root_curse";
                }
                yield null;
            }

            // 38: 26.1.2 Lunge (突进 — native on SPEAR, transmutes on other weapons/tools/armor!)
            case "minecraft:lunge" -> {
                if (role == TargetRole.SPEAR) yield null;
                if (role.isMeleeWeapon()) yield "lunge_dash_strike";
                if (role.isRangedWeapon()) yield "lunge_vault_shot";
                if (role == TargetRole.DIG_TOOL || role == TargetRole.FISHING_ROD) yield "lunge_tunnel_rush";
                if (role.isArmorOrShield()) yield "lunge_wind_stride";
                yield null;
            }

            default -> null;
        };

        if (vid == null) {
            return Optional.empty();
        }
        TransmuteVariant found = ALL_VARIANTS.get(vid);
        return Optional.ofNullable(found != null ? found : new TransmuteVariant(vid, enchId));
    }

    public static boolean hasAnyTransmutedEnchants(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return !stack.getOrDefault(enchantment_fusion.registry.FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()).isEmpty();
    }

    public static int getTransmutedLevel(ItemStack stack, String variantId) {
        if (stack == null || stack.isEmpty() || variantId == null) {
            return 0;
        }
        // Strictly authoritative: ONLY FusionDataComponents.TRANSMUTED_ENCHANTS (written exclusively by 附魔融合台)
        // activates cross-domain transmutations — Vanilla Anvil ENCHANTMENTS never trigger 异位化形!
        Map<String, Integer> transMap = stack.getOrDefault(
            enchantment_fusion.registry.FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()
        );
        if (!transMap.isEmpty()) {
            for (Map.Entry<String, Integer> e : transMap.entrySet()) {
                Optional<TransmuteVariant> v = resolve(e.getKey(), stack);
                if (v.isPresent() && variantId.equals(v.get().id())) {
                    return Math.max(0, e.getValue());
                }
            }
        }
        return 0;
    }

    public static int getStandaloneEnchantLevel(ItemStack stack, String enchId) {
        if (stack == null || stack.isEmpty() || enchId == null) return 0;
        int transLv = stack.getOrDefault(
            enchantment_fusion.registry.FusionDataComponents.TRANSMUTED_ENCHANTS, Map.<String, Integer>of()
        ).getOrDefault(enchId, 0);
        if (transLv > 0) {
            return transLv;
        }
        ItemEnchantments enchs = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchs.keySet()) {
            String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (enchId.equals(id)) {
                return enchs.getLevel(holder);
            }
        }
        return 0;
    }

    // =========================================================================
    // 1. Reach, Attack Speed & Mining Speed (Client + Server Synced Mixins)
    // =========================================================================

    public static double modifyEntityReach(Player player, double baseReach) {
        ItemStack held = player.getMainHandItem();
        int yishuiLv = getTransmutedLevel(held, "yishui");
        if (yishuiLv > 0 && player.isInWaterOrRain()) {
            return baseReach + 1.5D;
        }
        return baseReach;
    }

    public static float modifyAttackDelay(Player player, float baseDelay) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return baseDelay;

        float mult = 1.0F;
        if (getTransmutedLevel(held, "light_feather") > 0) {
            mult *= (1.0F - 0.06F * getTransmutedLevel(held, "light_feather"));
        }
        int swiftLv = getTransmutedLevel(held, "swift_swing");
        if (swiftLv > 0) {
            mult *= Math.max(0.55F, 1.0F - 0.07F * swiftLv);
        }
        int rapidLv = getTransmutedLevel(held, "rapid_recovery");
        if (rapidLv > 0) {
            mult *= Math.max(0.55F, 1.0F - 0.11F * rapidLv);
        }
        int waveLv = getTransmutedLevel(held, "wave_parting");
        if (waveLv > 0 && player.isInWaterOrRain()) {
            mult *= Math.max(0.60F, 1.0F - 0.12F * waveLv);
        }
        // Grounded physical slowdowns at low enchantment levels / high density:
        int frostLv = getTransmutedLevel(held, "frost_edge");
        if (frostLv == 1) mult *= 1.14F;
        else if (frostLv == 2) mult *= 1.05F;

        int serratedLv = getTransmutedLevel(held, "serrated_hook");
        if (serratedLv == 1) mult *= 1.15F;
        else if (serratedLv == 2) mult *= 1.08F;
        else if (serratedLv >= 3) mult *= 1.03F;

        if (getTransmutedLevel(held, "heavy_plummet") > 0) {
            mult *= 1.12F;
        }
        return baseDelay * mult;
    }

    public static float modifyDestroySpeed(Player player, ItemStack tool, BlockState state, float speed) {
        if (tool.isEmpty() || speed <= 1.0F) {
            return speed;
        }
        float mult = 1.0F;

        // USER RULE #0a: Fire Aspect on Pickaxe/Axe slows mining speed more at lower levels!
        int faLv = getStandaloneEnchantLevel(tool, "minecraft:fire_aspect");
        if (faLv > 0 && (getTransmutedLevel(tool, "auto_smelt_pick") > 0 || getTransmutedLevel(tool, "auto_smelt_axe") > 0)) {
            mult *= (faLv == 1 ? 0.58F : 0.82F);
        }

        int featherLv = getTransmutedLevel(tool, "light_feather");
        if (featherLv > 0) {
            mult *= (1.0F + 0.08F * featherLv);
        }
        if (getTransmutedLevel(tool, "heat_shield_pick") > 0 && (player.isOnFire() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.NETHERRACK))) {
            mult *= 1.45F;
        }
        int sharpLv = getTransmutedLevel(tool, "sharpened_tool");
        if (sharpLv > 0 && (state.is(BlockTags.LEAVES) || state.is(Blocks.COBWEB) || state.is(Blocks.BAMBOO) || state.is(BlockTags.WOOL))) {
            mult *= (1.6F + 0.2F * sharpLv);
        }
        int smiteLv = getTransmutedLevel(tool, "hallowed_pick");
        if (smiteLv > 0 && (state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.BONE_BLOCK) || state.is(Blocks.SCULK))) {
            mult *= (1.5F + 0.2F * smiteLv);
        }
        if (getTransmutedLevel(tool, "nest_purger") > 0 && state.is(Blocks.COBWEB)) {
            mult *= 5.0F;
        }
        int silentPickLv = getTransmutedLevel(tool, "silent_pick");
        if (silentPickLv > 0) {
            mult *= player.isShiftKeyDown() ? (1.15F + 0.25F * silentPickLv) : (1.08F + 0.12F * silentPickLv);
        }
        int dredgeLv = getTransmutedLevel(tool, "abyssal_dredge");
        if (dredgeLv > 0 && player.isInWaterOrRain()) {
            mult *= (1.35F + 0.20F * dredgeLv);
        }
        int powerLv = getTransmutedLevel(tool, "hardrock_breaker");
        if (powerLv > 0 && state.getBlock().defaultDestroyTime() >= 3.0F) {
            mult *= (1.0F + 0.16F * powerLv);
        }
        int riptideLv = getTransmutedLevel(tool, "torrent_mine");
        if (riptideLv > 0 && player.isInWaterOrRain()) {
            mult *= (1.0F + 0.45F * riptideLv);
        }
        int impaleLv = getTransmutedLevel(tool, "prismarine_chisel");
        if (impaleLv > 0 && (state.is(Blocks.PRISMARINE) || state.is(Blocks.DARK_PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.SEA_LANTERN))) {
            mult *= (1.5F + 0.25F * impaleLv);
        }
        int breachLv = getTransmutedLevel(tool, "shell_cracker");
        if (breachLv > 0 && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.ANCIENT_DEBRIS) || state.getBlock().defaultDestroyTime() >= 4.5F)) {
            mult *= (1.0F + 0.28F * breachLv);
        }
        if (getTransmutedLevel(tool, "heavy_plummet") > 0 && player.getXRot() > 45.0F) {
            mult *= 1.25F;
        }
        return speed * mult;
    }

    // =========================================================================
    // 2. Block Break Interception (Auto-Smelt Pick & Axe + Silverfish Purger)
    // =========================================================================

    /**
     * API-03 & BAL-09: Only intercepts infested silverfish stone AFTER checking Adventure/build restrictions;
     * Auto-Smelt Pick & Charcoal Axe run post-break in {@link #onAfterBlockBreak} so vanilla protection checks,
     * Fortune, stats, and exhaustion are NEVER bypassed, and Sneak cleanly pauses smelting!
     */
    public static boolean onBeforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (player.getAbilities().instabuild || player.isSpectator()) {
            return true;
        }
        if (!player.mayBuild() || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return true;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return true;
        }

        // 17. Bane of Arthropods "清巢": crush silverfish inside infested blocks before InfestedBlock#spawnAfterBreak fires
        if (getTransmutedLevel(tool, "nest_purger") > 0) {
            Item cleanStone = mapInfestedBlockToCleanItem(state.getBlock());
            if (cleanStone != null) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(cleanStone, 1));
                level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.05D);
                damageItemSafely(tool, 1, player);
                onAfterBlockBreak(level, player, pos, state);
                return false;
            }
        }

        return true;
    }

    /**
     * Runs strictly AFTER {@code block.playerDestroy(...)} at the tail of {@code ServerPlayerGameMode#destroyBlock},
     * when Fortune-multiplied {@link ItemEntity} drops already exist in {@code level} at {@code age == 0}.
     */
    public static void onPostDestroyBlockDrops(
        ServerLevel level,
        ServerPlayer player,
        BlockPos pos,
        BlockState state,
        ItemStack toolSnapshot
    ) {
        ItemStack tool = (toolSnapshot == null || toolSnapshot.isEmpty()) ? player.getMainHandItem() : toolSnapshot;
        if (tool.isEmpty()) {
            return;
        }

        // 1. Run binary-fusion harvest drop transformations first (smelt_fortune, molten_core_pick, raw_ore_geode, etc.)
        SignaturePairs.onPostDestroyHarvestDrops(level, player, tool, pos, state);

        // 2. Axe Charcoal Kiln ("炭化伐木"): Logs -> Charcoal (always active); Planks -> thermal char (Sneak pauses plank burn)
        int axeLv = getTransmutedLevel(tool, "auto_smelt_axe");
        if (axeLv > 0) {
            RandomSource rng = level.getRandom();
            AABB dropBox = new AABB(pos).inflate(1.9D);
            if (state.is(BlockTags.LOGS)) {
                float charcoalChance = axeLv >= 2 ? 1.0F : 0.65F;
                boolean charred = false;
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2)) {
                    if (ie.getItem().is(ItemTags.LOGS) && rng.nextFloat() < charcoalChance) {
                        int outCount = ie.getItem().getCount() + (axeLv >= 2 && rng.nextFloat() < 0.25F ? 1 : 0);
                        ie.setItem(new ItemStack(Items.CHARCOAL, outCount));
                        charred = true;
                    }
                }
                if (charred) {
                    ExperienceOrb.award(level, Vec3.atCenterOf(pos), 1);
                    level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 7, 0.25D, 0.25D, 0.25D, 0.02D);
                }
            } else if (!player.isShiftKeyDown()
                && (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_FENCES))) {
                float ashChance = axeLv >= 2 ? 0.20F : 0.40F;
                float charcoalThreshold = axeLv >= 2 ? 0.85F : 0.78F;
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2)) {
                    float roll = rng.nextFloat();
                    if (roll < ashChance) {
                        ie.discard();
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.03D);
                        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.3F);
                    } else if (roll < charcoalThreshold) {
                        ie.setItem(new ItemStack(Items.CHARCOAL, 1));
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.2D, 0.2D, 0.2D, 0.02D);
                    }
                }
            }
        }

        // 3. Pickaxe / Tool Auto-Smelt ("自动熔炼"): Queries real RecipeType.SMELTING on actual post-Fortune ItemEntity drops!
        if (getTransmutedLevel(tool, "auto_smelt_pick") > 0) {
            smeltFreshBlockDropsInPlace(level, pos, state, 1.0F);
        }

        // 4. Post-smelt Drop Magnets ("归囊 / 静默镐 / 定流 / 浚川 / 磁吸灵镐") teleport smelted ingots & XP directly to player!
        boolean magnetActive = (getTransmutedLevel(tool, "current_anchor") > 0 && player.isInWater())
            || (getTransmutedLevel(tool, "abyssal_dredge") > 0 && player.isInWaterOrRain())
            || getTransmutedLevel(tool, "silent_pick") > 0
            || getTransmutedLevel(tool, "auto_loot_return") > 0
            || SignaturePairs.stackHasSignature(tool, "magnetic_pick")
            || SignaturePairs.playerHasEquippedSignature(player, "magnetic_pick");
        if (magnetActive) {
            AABB pullBox = new AABB(pos).inflate(2.4D);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, pullBox, e -> e.isAlive() && e.getAge() <= 3)) {
                drop.setNoPickUpDelay();
                drop.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, pullBox, Entity::isAlive)) {
                orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
        }
    }

    /**
     * Smelts freshly dropped {@link ItemEntity} stacks around {@code pos} using live {@link RecipeType#SMELTING}
     * (covering all vanilla + modded ores, sand, clay, stone, kelp, sponge, ancient debris, etc.) while preserving
     * Fortune-multiplied item counts!
     */
    public static boolean smeltFreshBlockDropsInPlace(
        ServerLevel level,
        BlockPos pos,
        BlockState brokenState,
        float xpMultiplier
    ) {
        AABB dropBox = new AABB(pos).inflate(1.9D);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2);
        if (drops.isEmpty()) {
            return false;
        }
        boolean smeltedAny = false;
        float totalXp = 0.0F;
        for (ItemEntity ie : drops) {
            ItemStack rawStack = ie.getItem();
            if (rawStack.isEmpty()) {
                continue;
            }
            SmeltOutcome outcome = resolveSmeltedStack(level, rawStack, brokenState.getBlock());
            if (outcome != null && !outcome.result().isEmpty()) {
                ie.setItem(outcome.result());
                smeltedAny = true;
                totalXp += outcome.xp();
            }
        }
        if (smeltedAny) {
            int xpAward = Math.max(1, Math.round(totalXp * xpMultiplier));
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), xpAward);
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 7, 0.25D, 0.25D, 0.25D, 0.02D);
        }
        return smeltedAny;
    }

    public record SmeltOutcome(ItemStack result, float xp) {}

    @Nullable
    public static SmeltOutcome resolveSmeltedStack(@Nullable ServerLevel level, ItemStack rawStack, @Nullable Block brokenBlock) {
        if (rawStack == null || rawStack.isEmpty()) {
            return null;
        }
        int inputCount = Math.max(1, rawStack.getCount());
        if (level != null) {
            SingleRecipeInput singleInput = new SingleRecipeInput(rawStack.copyWithCount(1));
            Optional<RecipeHolder<SmeltingRecipe>> recipeOpt =
                level.recipeAccess().getRecipeFor(RecipeType.SMELTING, singleInput, level);
            if (recipeOpt.isPresent()) {
                SmeltingRecipe recipe = recipeOpt.get().value();
                ItemStack assembled = recipe.assemble(singleInput);
                if (!assembled.isEmpty() && !ItemStack.isSameItem(assembled, rawStack)) {
                    int outCount = Math.min(99, inputCount * Math.max(1, assembled.getCount()));
                    return new SmeltOutcome(assembled.copyWithCount(outCount), Math.max(0.2F, recipe.experience()) * inputCount);
                }
            }
        }
        Item fallback = mapItemAutoSmeltFallback(rawStack.getItem(), brokenBlock);
        if (fallback != null && !rawStack.is(fallback)) {
            return new SmeltOutcome(new ItemStack(fallback, inputCount), 0.7F * inputCount);
        }
        return null;
    }

    public static void onAfterBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // 1. Frost Walker "封冻": seal adjacent water/lava into frosted ice / cobblestone!
        if (getTransmutedLevel(tool, "frost_seal") > 0) {
            for (Direction dir : Direction.values()) {
                BlockPos adj = pos.relative(dir);
                BlockState adjState = level.getBlockState(adj);
                if (adjState.is(Blocks.WATER)) {
                    level.setBlockAndUpdate(adj, Blocks.FROSTED_ICE.defaultBlockState());
                } else if (adjState.is(Blocks.LAVA)) {
                    level.setBlockAndUpdate(adj, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }

        // 9. Fire Protection "隔热": extinguish self while mining
        if (getTransmutedLevel(tool, "heat_shield_pick") > 0 && player.isOnFire()) {
            player.clearFire();
        }

        // 3: Respiration "鳃息护膜" — recover oxygen bubbles on submerged block harvest
        int gillLv = getTransmutedLevel(tool, "gill_membrane");
        if (gillLv > 0 && player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 28 * gillLv));
        }

        // 5: Soul Speed "摄魂疾行" — surging Soul Stride speed on harvest
        int soulExcLv = getTransmutedLevel(tool, "soul_excavator");
        if (soulExcLv > 0) {
            grantEffect(player, "speed", 45 + 15 * soulExcLv, Math.min(1, soulExcLv - 1));
            level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.02D);
        }

        // 7: Thorns "荆棘缠钩" — entangle & scratch nearby lunging hostiles while working
        int briarLv = getTransmutedLevel(tool, "briar_mattock");
        if (briarLv > 0) {
            for (LivingEntity mob : nearbyHostiles(level, player, Vec3.atCenterOf(pos), 3.2D, null)) {
                mob.hurtServer(level, level.damageSources().thorns(player), 1.2F + 0.7F * briarLv);
                grantEffect(mob, "slowness", 35, 1);
            }
        }

        // 10: Blast Protection "防爆震凿" — small concussive crack on adjacent stone
        int blastGuardLv = getTransmutedLevel(tool, "blast_guard");
        if (blastGuardLv > 0 && !Boolean.TRUE.equals(IN_MULTI_BREAK.get()) && level.getRandom().nextFloat() < 0.14F * blastGuardLv) {
            IN_MULTI_BREAK.set(Boolean.TRUE);
            try {
                BlockPos adj = pos.relative(Direction.getRandom(level.getRandom()));
                BlockState adjSt = level.getBlockState(adj);
                if (adjSt.is(BlockTags.BASE_STONE_OVERWORLD) || adjSt.is(Blocks.COBBLESTONE) || adjSt.is(Blocks.NETHERRACK)) {
                    level.destroyBlock(adj, true, player);
                }
            } finally {
                IN_MULTI_BREAK.set(Boolean.FALSE);
            }
        }

        // 38: Lunge "掘进冲刺" — burst mining stride
        int tunnelLv = getTransmutedLevel(tool, "lunge_tunnel_rush");
        if (tunnelLv > 0) {
            grantEffect(player, "speed", 50, Math.min(1, tunnelLv - 1));
        }

        // 25. Quick Charge "连掘": brief Haste
        int chainLv = getTransmutedLevel(tool, "chain_tempo");
        if (chainLv > 0) {
            grantEffect(player, "haste", 45, Math.min(1, chainLv - 1));
        }

        // 20 & 33: Looting "寻宝" & Luck of the Sea "淘沙"
        RandomSource rng = level.getRandom();
        int forageLv = getTransmutedLevel(tool, "forager_luck");
        if (forageLv > 0 && rng.nextFloat() < 0.08F * forageLv) {
            if (state.is(Blocks.GRAVEL)) {
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(rng.nextBoolean() ? Items.FLINT : Items.IRON_NUGGET, 1));
            } else if (state.is(BlockTags.LEAVES)) {
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(Items.APPLE, 1));
            } else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) {
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(rng.nextFloat() < 0.2F ? Items.EMERALD : Items.BONE_MEAL, 1));
            }
        }
        int panLv = getTransmutedLevel(tool, "sand_panning");
        if (panLv > 0 && (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD))
            && rng.nextFloat() < 0.10F * panLv) {
            Item prize = switch (rng.nextInt(4)) {
                case 0 -> Items.PRISMARINE_CRYSTALS;
                case 1 -> Items.GOLD_NUGGET;
                case 2 -> Items.LAPIS_LAZULI;
                default -> Items.EMERALD;
            };
            spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(prize, 1));
        }

        // Multi-block mining (Sweeping Edge "横刈", Multishot "三岔掘", Piercing "纵深钻探") — sneak pauses!
        if (!player.isShiftKeyDown() && !Boolean.TRUE.equals(IN_MULTI_BREAK.get())) {
            IN_MULTI_BREAK.set(Boolean.TRUE);
            try {
                if (getTransmutedLevel(tool, "wide_reap") > 0
                    && (state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SNOW))) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            BlockPos adj = pos.offset(dx, 0, dz);
                            if (level.getBlockState(adj).is(state.getBlock())) {
                                level.destroyBlock(adj, true, player);
                            }
                        }
                    }
                } else if (getTransmutedLevel(tool, "trifork_mine") > 0 || getTransmutedLevel(tool, "wide_reap") > 0) {
                    Direction right = player.getDirection().getClockWise();
                    for (int s : new int[]{-1, 1}) {
                        BlockPos adj = pos.relative(right, s);
                        BlockState adjSt = level.getBlockState(adj);
                        if (adjSt.is(state.getBlock()) && adjSt.getDestroySpeed(level, adj) >= 0.0F) {
                            level.destroyBlock(adj, true, player);
                        }
                    }
                }
                int boreLv = getTransmutedLevel(tool, "deep_bore");
                if (boreLv > 0) {
                    Direction forward = Direction.orderedByNearest(player)[0];
                    int depth = Math.min(2, boreLv);
                    for (int d = 1; d <= depth; d++) {
                        BlockPos deeper = pos.relative(forward, d);
                        BlockState deepSt = level.getBlockState(deeper);
                        if (deepSt.is(state.getBlock()) && deepSt.getDestroySpeed(level, deeper) >= 0.0F) {
                            level.destroyBlock(deeper, true, player);
                        } else {
                            break;
                        }
                    }
                }
            } finally {
                IN_MULTI_BREAK.set(Boolean.FALSE);
            }
        }
    }

    // =========================================================================
    // 3. Melee Combat & Friendly-Pet Protection
    // =========================================================================

    public static boolean allowAttackTarget(Player player, Entity target) {
        ItemStack weapon = player.getMainHandItem();
        if (getTransmutedLevel(weapon, "intact_blade") > 0 && target instanceof LivingEntity liv && !(liv instanceof Player)) {
            if (liv.hasCustomName()) {
                return false;
            }
            if (liv instanceof Mob mob && mob.isLeashed()) {
                return false;
            }
        }
        return true;
    }

    public static void onMeleeAttack(ServerLevel level, ServerPlayer player, LivingEntity target, boolean fullCharge) {
        if (Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        LAST_SWING_TICK.put(player.getUUID(), level.getGameTime());
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            Vec3 pos = target.position();
            RandomSource rng = level.getRandom();

            // 0b: Aqua Affinity -> "沂水" (+damage in water/rain, reach handled in modifyEntityReach)
            if (getTransmutedLevel(weapon, "yishui") > 0 && player.isInWaterOrRain()) {
                target.hurtServer(level, level.damageSources().magic(), 3.2F);
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.8D, pos.z, 10, 0.3D, 0.25D, 0.3D, 0.08D);
            }

            // 1: Frost Walker -> "凝霜" (with Frost Walker frosted_ice snare!)
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 70 * frostLv));
                grantEffect(target, "slowness", 55, frostLv - 1);
                if (target.getTicksFrozen() >= 140 || rng.nextFloat() < 0.25F) {
                    AberrantRegistry.placeSingleFrostedIce(level, target.blockPosition());
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.8D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.04D);
            }

            // 21: Power on Armor/Shield -> "泰坦护腕" melee force bonus!
            if (fullCharge && hasArmorOrShieldTransmute(player, "titan_brace")) {
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.6F);
            }

            // 38: Lunge -> "寸步突刺" forward micro-dash & pierce strike!
            int lungeStrikeLv = getTransmutedLevel(weapon, "lunge_dash_strike");
            if (lungeStrikeLv > 0 && fullCharge) {
                Vec3 dash = player.getLookAngle().normalize().scale(0.42D + 0.14D * lungeStrikeLv);
                player.push(dash.x, 0.12D, dash.z);
                player.hurtMarked = true;
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F * lungeStrikeLv);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4D, player.getZ(), 8, 0.25D, 0.15D, 0.25D, 0.05D);
            }

            // 3: Respiration -> "气劲吐纳"
            int qiLv = getTransmutedLevel(weapon, "qi_breath");
            if (qiLv > 0 && fullCharge && player.getAirSupply() >= player.getMaxAirSupply() - 15) {
                player.setAirSupply(Math.max(0, player.getAirSupply() - 35));
                target.hurtServer(level, level.damageSources().magic(), 1.8F + 0.8F * qiLv);
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.9D, pos.z, 6, 0.2D, 0.2D, 0.2D, 0.05D);
            }

            // 4: Feather Falling -> "轻羽" (dampen knockback; if Mace, reduce heavy fall bonus!)
            if (getTransmutedLevel(weapon, "light_feather") > 0) {
                target.setDeltaMovement(target.getDeltaMovement().scale(0.35D));
                if (weapon.getItem() instanceof MaceItem && player.fallDistance > 1.5D) {
                    player.fallDistance *= 0.45D;
                }
            }

            // 5: Soul Speed -> "追魂步"
            int soulLv = getTransmutedLevel(weapon, "soul_chase");
            if (soulLv > 0) {
                SOUL_CHASE_TARGET.put(player.getUUID(), target.getUUID());
                SOUL_CHASE_UNTIL.put(player.getUUID(), level.getGameTime() + 90L);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.6D, pos.z, 6, 0.2D, 0.2D, 0.2D, 0.03D);
            }

            // 6: Swift Sneak -> "暗刃"
            int silentLv = getTransmutedLevel(weapon, "silent_blade");
            if (silentLv > 0 && (player.isShiftKeyDown() || player.getLookAngle().dot(target.getLookAngle()) > 0.35D)) {
                target.hurtServer(level, level.damageSources().magic(), 2.0F + 1.0F * silentLv);
            }

            // 7: Thorns -> "锯刃" (bleed + INDISCRIMINATE HOOK PULL toward player — even Creepers!)
            int hookLv = getTransmutedLevel(weapon, "serrated_hook");
            if (hookLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.0F + 0.7F * hookLv);
                Vec3 yank = player.position().subtract(pos);
                if (yank.lengthSqr() > 0.25D) {
                    Vec3 pull = yank.normalize().scale(0.55D + 0.15D * hookLv);
                    target.push(pull.x, 0.22D, pull.z);
                }
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.8D, pos.z, 5, 0.2D, 0.2D, 0.2D, 0.04D);
            }

            // 9: Fire Protection -> "斩焰"
            if (getTransmutedLevel(weapon, "flame_cut") > 0) {
                player.clearFire();
                BlockPos tp = target.blockPosition();
                if (level.getBlockState(tp).is(Blocks.FIRE)) {
                    level.setBlockAndUpdate(tp, Blocks.AIR.defaultBlockState());
                }
            }

            // 10: Blast Protection -> "镇爆" (interrupt swelling Creeper fuse!)
            int defuseLv = getTransmutedLevel(weapon, "creeper_defuse");
            if (defuseLv > 0 && target.getType() == EntityType.CREEPER && rng.nextFloat() < (0.35F + 0.15F * defuseLv)) {
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                }
                Vec3 pushAway = pos.subtract(player.position()).normalize().scale(1.15D);
                target.push(pushAway.x, 0.25D, pushAway.z);
                grantEffect(target, "slowness", 40, 2);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.02D);
                level.playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
            }

            // 11: Projectile Protection -> "拨矢" (deflect nearby airborne projectiles!)
            int deflectLv = getTransmutedLevel(weapon, "arrow_deflect");
            if (deflectLv > 0) {
                deflectNearbyProjectiles(level, player, 2.8D + 0.5D * deflectLv);
            }

            // 14: Fortune -> "$3 Bill" Random Roulette!
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.28F + 0.14F * fortuneLv)) {
                triggerThreeDollarRoulette(level, player, target, fortuneLv);
            }

            // 15, 16, 17: Tool Weaponization (Sharpness "开刃", Smite "净土", Bane "清巢")
            int sharpTool = getTransmutedLevel(weapon, "sharpened_tool");
            if (sharpTool > 0) {
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.5F + 0.8F * sharpTool);
            }
            int smiteTool = getTransmutedLevel(weapon, "hallowed_pick");
            if (smiteTool > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)) {
                target.hurtServer(level, level.damageSources().magic(), 2.2F * smiteTool);
            }

            // 18: Knockback/Punch -> "护身震退"
            int shoveLv = getTransmutedLevel(weapon, "warding_shove");
            if (shoveLv > 0) {
                Vec3 dir = pos.subtract(player.position()).normalize().scale(0.55D * shoveLv);
                target.push(dir.x, 0.24D, dir.z);
            }

            // 19: Sweeping Edge -> "顺劈" on Axe / Trident / Mace
            int cleaveLv = getTransmutedLevel(weapon, "heavy_cleave");
            if (cleaveLv > 0 && fullCharge) {
                for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.2D, target)) {
                    extra.hurtServer(level, level.damageSources().playerAttack(player), 1.8F + 1.1F * cleaveLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 21: Power -> "满蓄重劈" (+damage at full charge, -damage heal-back on spam click!)
            int powerLv = getTransmutedLevel(weapon, "full_draw_cleave");
            if (powerLv > 0) {
                if (fullCharge) {
                    target.hurtServer(level, level.damageSources().playerAttack(player), 1.2F + 0.7F * powerLv);
                } else {
                    target.heal(Math.min(2.0F, target.getMaxHealth() - target.getHealth()));
                }
            }

            // 23: Multishot -> "分影斩"
            if (getTransmutedLevel(weapon, "twin_shadow_slash") > 0 && fullCharge) {
                int hit = 0;
                for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.6D, target)) {
                    extra.hurtServer(level, level.damageSources().magic(), 3.0F);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, extra.getX(), extra.getY() + 0.8D, extra.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    if (++hit >= 2) break;
                }
            }

            // 24: Piercing -> "透甲串刺" (break shield guard + line skewer behind target!)
            int skewerLv = getTransmutedLevel(weapon, "skewer_thrust");
            if (skewerLv > 0) {
                if (target.isBlocking()) {
                    target.stopUsingItem();
                }
                target.hurtServer(level, level.damageSources().magic(), 0.9F * skewerLv);
                Vec3 lineCenter = pos.add(player.getLookAngle().normalize().scale(2.0D));
                for (LivingEntity behind : nearbyHostiles(level, player, lineCenter, 2.2D, target)) {
                    behind.hurtServer(level, level.damageSources().magic(), 2.2F + 0.7F * skewerLv);
                }
            }

            // 28: Channeling -> Strict Thunderstorm Lightning (close range can shock self too!)
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(pos.x, pos.y, pos.z);
                bolt.setCause(player);
                level.addFreshEntity(bolt);
                if (player.distanceToSqr(target) < 6.25D && !hasArmorTransmute(player, "lightning_conductor_armor")) {
                    grantEffect(player, "slowness", 20, 1);
                }
            }

            // 29: Impaling -> "破潮" (aquatic + wet/rain targets!)
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 2.0F * impaleLv);
            }

            // 30: Density -> "重钧" (falling height smash!)
            int densityLv = getTransmutedLevel(weapon, "heavy_plummet");
            if (densityLv > 0 && player.fallDistance > 1.8D) {
                float smash = (float) Math.min(18.0D, player.fallDistance * 0.85D * densityLv);
                target.hurtServer(level, level.damageSources().playerAttack(player), smash);
                player.resetFallDistance();
                level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45F, 1.25F);
            }

            // 31: Breach -> "硬碰硬裂甲" (bonus vs armored, slight over-penetration loss vs 0-armor!)
            int breachLv = getTransmutedLevel(weapon, "hard_breach");
            if (breachLv > 0) {
                if (target.getArmorValue() > 0 || target.isBlocking()) {
                    if (target.isBlocking()) target.stopUsingItem();
                    target.hurtServer(level, level.damageSources().magic(), 1.2F * breachLv);
                } else {
                    target.heal(Math.min(1.0F, target.getMaxHealth() - target.getHealth()));
                }
            }

            // 32: Wind Burst -> "踩头连斩" (strictly disabled underwater!)
            int windLv = getTransmutedLevel(weapon, "pogo_gale_slash");
            if (windLv > 0 && !player.onGround() && !player.isUnderWater()) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, 0.68D + 0.14D * windLv, v.z);
                player.hurtMarked = true;
                player.resetFallDistance();
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 12, 0.35D, 0.15D, 0.35D, 0.08D);
            }

            // 34: Lure -> "挑衅"
            if (getTransmutedLevel(weapon, "taunt_blade") > 0 && target instanceof Mob mob) {
                mob.setTarget(player);
            }

            // 35: Binding Curse -> "定身粘地"
            if (getTransmutedLevel(weapon, "sticky_root_curse") > 0 && rng.nextFloat() < 0.28F) {
                grantEffect(target, "slowness", 35, 4);
                target.setDeltaMovement(0.0D, Math.min(0.0D, target.getDeltaMovement().y), 0.0D);
            }

            // 0a-Melee: Flame -> "阳炎烙刃" (solar fire brand + full-charge forward searing arc!)
            int solarLv = getTransmutedLevel(weapon, "solar_brand");
            if (solarLv > 0) {
                target.igniteForSeconds(3.5F + 2.0F * solarLv);
                if (fullCharge) {
                    Vec3 ahead = pos.add(player.getLookAngle().normalize().scale(1.6D));
                    for (LivingEntity extra : nearbyHostiles(level, player, ahead, 2.4D, target)) {
                        extra.igniteForSeconds(3.0F + solarLv);
                        extra.hurtServer(level, level.damageSources().magic(), 1.8F + 0.7F * solarLv);
                    }
                    level.sendParticles(ParticleTypes.FLAME, ahead.x, ahead.y + 0.7D, ahead.z, 12, 0.35D, 0.2D, 0.35D, 0.04D);
                }
            }

            // 15-Mace: Sharpness -> "重锋锐击" (flat baseline edge crush even without high fall!)
            int crushEdgeLv = getTransmutedLevel(weapon, "crushing_edge");
            if (crushEdgeLv > 0) {
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.4F + 0.85F * crushEdgeLv);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 0.8D, pos.z, 6, 0.2D, 0.2D, 0.2D, 0.06D);
            }

            // Spear Channel 3 (Sprint / Couched Thrust): Riptide "水龙突刺" triggers on sprint-thrust without stealing Hold-Right-Click!
            if (classifyTarget(weapon) == TargetRole.SPEAR && fullCharge && player.isSprinting()) {
                int spearDashLv = getTransmutedLevel(weapon, "aqua_spiral_dash");
                if (spearDashLv > 0 && checkSkillCooldown(player, "spear_sprint_dash", level.getGameTime(), 24)) {
                    boolean wet = player.isInWaterOrRain();
                    Vec3 surge = player.getLookAngle().normalize().scale(wet ? (0.95D + 0.24D * spearDashLv) : (0.58D + 0.14D * spearDashLv));
                    player.push(surge.x, 0.14D, surge.z);
                    player.hurtMarked = true;
                    target.hurtServer(level, level.damageSources().playerAttack(player), (wet ? 3.8F : 2.2F) + 1.1F * spearDashLv);
                    level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.8D, pos.z, 14, 0.35D, 0.25D, 0.35D, 0.08D);
                }
            }
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    /**
     * Applies weapon-resident cross-domain strike effects along the 3D flight path of {@code 飞剑掷斧}.
     */
    public static void onFlyingBladeHit(ServerLevel level, ServerPlayer player, ItemStack weapon, LivingEntity target) {
        if (weapon.isEmpty() || Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) {
            return;
        }
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            Vec3 pos = target.position();
            RandomSource rng = level.getRandom();
            if (getTransmutedLevel(weapon, "yishui") > 0 && (player.isInWaterOrRain() || target.isInWaterOrRain())) {
                target.hurtServer(level, level.damageSources().magic(), 2.8F);
            }
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 65 * frostLv));
                grantEffect(target, "slowness", 50, frostLv - 1);
            }
            int solarLv = getTransmutedLevel(weapon, "solar_brand");
            if (solarLv > 0) {
                target.igniteForSeconds(3.5F + 1.5F * solarLv);
            }
            int hookLv = getTransmutedLevel(weapon, "serrated_hook");
            if (hookLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.0F + 0.65F * hookLv);
            }
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.25F + 0.12F * fortuneLv)) {
                triggerThreeDollarRoulette(level, player, target, fortuneLv);
            }
            int crushLv = getTransmutedLevel(weapon, "crushing_edge");
            if (crushLv > 0) {
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.2F + 0.75F * crushLv);
            }
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                AberrantRegistry.spawnLightning(level, pos);
            }
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 1.8F * impaleLv);
            }
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    // =========================================================================
    // 4. Right-Click Active Skills, Fishing Reel & Consumable Unbreaking
    // =========================================================================

    /**
     * Client + Server check so {@code UseItemCallback} returns {@code InteractionResult.SUCCESS}
     * (giving immediate arm swing and preventing offhand Shield/Food from swallowing weapon skills).
     */
    /**
     * Spear Right-Click Protection: Plain Right-Click &amp; Hold-Right-Click on {@link TargetRole#SPEAR}
     * NEVER intercepts {@code UseItemCallback}, 100% preserving vanilla Spear Hold-Right-Click Charging!
     * Active Spear javelin throws instead trigger on {@code Sneak + Right-Click}.
     */
    public static boolean canTriggerDirectRightClick(boolean playerSneaking, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (classifyTarget(stack) == TargetRole.SPEAR) {
            return playerSneaking;
        }
        return true;
    }

    public static boolean hasReadyRightClickAbility(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        if (!canTriggerDirectRightClick(player.isShiftKeyDown(), stack)) {
            return false;
        }
        boolean isSpearSneak = classifyTarget(stack) == TargetRole.SPEAR;
        if (getTransmutedLevel(stack, "returning_blade_throw") > 0) return true;
        if (getTransmutedLevel(stack, "phantom_trident") > 0) return true;
        // On Spears, arrow_deflect activates automatically while Hold-Right-Click charging!
        if (!isSpearSneak && getTransmutedLevel(stack, "arrow_deflect") > 0) return true;
        if (getTransmutedLevel(stack, "aqua_spiral_dash") > 0) return true;
        if (getTransmutedLevel(stack, "pogo_gale_slash") > 0 && !player.isUnderWater()) return true;
        if (getTransmutedLevel(stack, "jetstream_boost") > 0 && !(stack.getItem() instanceof FishingRodItem) && !player.isUnderWater()) {
            return true;
        }
        return false;
    }

    public static boolean onUseItem(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty()) return false;
        long now = level.getGameTime();
        boolean activatedSkill = false;
        boolean isSpear = classifyTarget(stack) == TargetRole.SPEAR;
        boolean allowWeaponActive = canTriggerDirectRightClick(player.isShiftKeyDown(), stack);
        boolean itemReady = allowWeaponActive && !player.getCooldowns().isOnCooldown(stack);
        int maxVisualCooldown = 0;

        // 26: Loyalty "飞剑掷斧 / 潜行回旋飞矛" — launches a real 3D weapon boomerang tracked in ProjectilePathTracker!
        int throwLv = getTransmutedLevel(stack, "returning_blade_throw");
        if (itemReady && throwLv > 0) {
            int cd = Math.max(14, 34 - 5 * throwLv);
            if (checkSkillCooldown(player, "returning_blade_throw", now, cd)
                && FlyingBladeManager.launchReturningBlade(level, player, hand, stack, throwLv)) {
                CompendiumDiscoveryTracker.recordTransmuteProgress(player, "returning_blade_throw", CompendiumDiscoveryTracker.STATE_MASTERED);
                maxVisualCooldown = Math.max(maxVisualCooldown, cd);
                activatedSkill = true;
            }
        }

        // 22: Infinity "幻影连投" on Trident — launches a 3D glowing Phantom Trident without losing Trident!
        int phantomLv = getTransmutedLevel(stack, "phantom_trident");
        if (itemReady && phantomLv > 0 && checkSkillCooldown(player, "phantom_trident", now, 18)) {
            if (FlyingBladeManager.launchLinearPhantomWeapon(level, player, hand, stack, phantomLv, FlyingBladeManager.FlightStyle.PHANTOM_LANCE)) {
                CompendiumDiscoveryTracker.recordTransmuteProgress(player, "phantom_trident", CompendiumDiscoveryTracker.STATE_MASTERED);
                maxVisualCooldown = Math.max(maxVisualCooldown, 18);
                activatedSkill = true;
            }
        }

        // 11: Projectile Protection "拨矢" — 360° sword-parry gale (on Spears this triggers during Hold-Right-Click charge; can co-stack with飞剑!)
        int deflectLv = getTransmutedLevel(stack, "arrow_deflect");
        if (itemReady && !isSpear && deflectLv > 0 && checkSkillCooldown(player, "arrow_deflect", now, 18)) {
            deflectNearbyProjectiles(level, player, 3.6D + 0.65D * deflectLv);
            Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(1.1D));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y - 0.2D, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.75F, 1.35F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 18);
            activatedSkill = true;
        }

        // 27: Riptide "水龙突刺 / 驭浪盾冲" — spiral surge (on Spears requires Sneak+Right-Click or Sprint-Jab!)
        int dashLv = Math.max(getTransmutedLevel(stack, "aqua_spiral_dash"), getTransmutedLevel(stack, "surge_ram"));
        if (itemReady && dashLv > 0 && !(stack.getItem() instanceof ShieldItem && !player.isInWaterOrRain())
            && checkSkillCooldown(player, "aqua_spiral_dash", now, 26)) {
            boolean wet = player.isInWaterOrRain();
            double scale = wet ? (1.20D + 0.34D * dashLv) : (0.76D + 0.16D * dashLv);
            Vec3 launch = player.getLookAngle().normalize().scale(scale);
            player.setDeltaMovement(launch.x, Math.max(0.24D, launch.y * 0.85D), launch.z);
            player.hurtMarked = true;
            player.resetFallDistance();
            for (LivingEntity hit : nearbyHostiles(level, player, player.position().add(launch), 2.8D, null)) {
                hit.hurtServer(level, level.damageSources().playerAttack(player), (wet ? 4.8F : 3.2F) + 1.4F * dashLv);
            }
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.8D, player.getZ(), 18, 0.45D, 0.3D, 0.45D, 0.1D);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.85F, wet ? 1.15F : 1.40F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 26);
            activatedSkill = true;
        }

        // 32: Wind Burst "踩头连斩 / 罡风助翼" — updraft vault / Elytra jetstream boost!
        int pogoLv = getTransmutedLevel(stack, "pogo_gale_slash");
        int jetLv = getTransmutedLevel(stack, "jetstream_boost");
        int activeWindLv = Math.max(pogoLv, (stack.getItem() instanceof FishingRodItem) ? 0 : jetLv);
        if (itemReady && activeWindLv > 0 && !player.isUnderWater()
            && checkSkillCooldown(player, "pogo_gale_boost", now, 28)) {
            Vec3 look = player.getLookAngle().normalize();
            if (player.isFallFlying()) {
                Vec3 cur = player.getDeltaMovement();
                player.setDeltaMovement(cur.scale(0.45D).add(look.scale(1.15D + 0.25D * activeWindLv)));
            } else {
                player.setDeltaMovement(look.x * (0.55D + 0.12D * activeWindLv), Math.max(0.56D + 0.10D * activeWindLv, look.y * 0.75D), look.z * (0.55D + 0.12D * activeWindLv));
            }
            player.hurtMarked = true;
            player.resetFallDistance();
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(), 16, 0.4D, 0.2D, 0.4D, 0.08D);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.80F, 0.85F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 28);
            activatedSkill = true;
        }

        if (maxVisualCooldown > 0) {
            player.getCooldowns().addCooldown(stack, maxVisualCooldown);
        }

        // Elytra Jetpack while worn on CHEST (independent cooldown from weapon skills!)
        if (player.isFallFlying() && !player.isUnderWater()) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int wornJet = getTransmutedLevel(chest, "jetstream_boost");
            int wornStorm = player.isInWaterOrRain() ? getTransmutedLevel(chest, "storm_glider") : 0;
            int boostLv = Math.max(wornJet, wornStorm);
            if (boostLv > 0 && !player.getCooldowns().isOnCooldown(chest)
                && checkSkillCooldown(player, "worn_elytra_jet", now, 34)) {
                Vec3 look = player.getLookAngle().normalize();
                player.setDeltaMovement(player.getDeltaMovement().scale(0.5D).add(look.scale(1.10D + 0.24D * boostLv)));
                player.hurtMarked = true;
                player.getCooldowns().addCooldown(chest, 34);
                level.sendParticles(
                    wornStorm > 0 ? ParticleTypes.SPLASH : ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.5D, player.getZ(), 18, 0.4D, 0.3D, 0.4D, 0.08D
                );
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.35F);
                activatedSkill = true;
            }
        }

        // 37a: Unbreaking on Instant Throwable Consumables ("经久省用" — EF-002):
        if (!stack.has(DataComponents.CONSUMABLE) && getTransmutedLevel(stack, "unbreaking_ration") > 0 && !player.getAbilities().instabuild) {
            Item expectedItem = stack.getItem();
            int countBefore = stack.getCount();
            ItemStack snapshot = stack.copyWithCount(1);
            level.getServer().execute(() -> {
                if (!player.isAlive()) return;
                ItemStack after = player.getItemInHand(hand);
                boolean consumed = after.isEmpty() || (after.is(expectedItem) && after.getCount() < countBefore);
                if (consumed) {
                    tryRefundRationItemImmediate(level, player, snapshot);
                }
            });
        }
        return activatedSkill;
    }

    /**
     * Executes Fishing Rod standalone cross-domain transmutations upon reeling in a bobber!
     */
    public static void onFishingReel(
        ServerLevel level,
        ServerPlayer player,
        ItemStack rod,
        @Nullable LivingEntity hookedMob,
        Vec3 bobberPos
    ) {
        if (rod == null || rod.isEmpty()) return;
        RandomSource rng = level.getRandom();

        int boilLv = getTransmutedLevel(rod, "boiling_cast");
        if (boilLv > 0) {
            if (hookedMob != null) {
                hookedMob.igniteForSeconds(3.0F + 2.0F * boilLv);
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.8F * boilLv);
            }
            level.getServer().execute(() -> {
                AABB nearPlayer = player.getBoundingBox().inflate(3.5D);
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, nearPlayer, e -> e.isAlive() && e.getAge() <= 4)) {
                    Item cooked = mapRawFishToCooked(ie.getItem().getItem());
                    if (cooked != null) {
                        ie.setItem(new ItemStack(cooked, ie.getItem().getCount()));
                    }
                }
            });
            level.sendParticles(ParticleTypes.FLAME, bobberPos.x, bobberPos.y + 0.3D, bobberPos.z, 8, 0.25D, 0.2D, 0.25D, 0.03D);
        }

        int iceHoleLv = getTransmutedLevel(rod, "ice_hole_angler");
        if (iceHoleLv > 0) {
            if (hookedMob != null) {
                hookedMob.setTicksFrozen(Math.min(350, hookedMob.getTicksFrozen() + 90 * iceHoleLv));
                grantEffect(hookedMob, "slowness", 60, iceHoleLv - 1);
            }
            if (player.mayBuild()) {
                AberrantRegistry.placeSingleFrostedIce(level, BlockPos.containing(bobberPos));
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, bobberPos.x, bobberPos.y + 0.4D, bobberPos.z, 10, 0.35D, 0.2D, 0.35D, 0.03D);
        }

        if (getTransmutedLevel(rod, "gentle_reel") > 0 && hookedMob != null && !(hookedMob instanceof Player)) {
            hookedMob.resetFallDistance();
            Vec3 gentle = player.position().add(player.getLookAngle().scale(1.4D));
            hookedMob.teleportTo(gentle.x, gentle.y + 0.2D, gentle.z);
        }

        if (getTransmutedLevel(rod, "auto_reel") > 0 || getTransmutedLevel(rod, "abyssal_dredge") > 0) {
            level.getServer().execute(() -> {
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(bobberPos, 6.0D, 4.0D, 6.0D), Entity::isAlive)) {
                    ie.setNoPickUpDelay();
                    ie.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
            });
        }

        int netLv = getTransmutedLevel(rod, "whirlwind_hem");
        if (netLv > 0) {
            for (LivingEntity nearby : nearbyHostiles(level, player, bobberPos, 2.8D + 0.5D * netLv, null)) {
                Vec3 yank = player.position().subtract(nearby.position()).normalize().scale(0.65D);
                nearby.push(yank.x, 0.28D, yank.z);
                nearby.hurtServer(level, level.damageSources().playerAttack(player), 1.6F + 0.6F * netLv);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, bobberPos.x, bobberPos.y + 0.5D, bobberPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        if (hookedMob != null) {
            int crushLv = getTransmutedLevel(rod, "crushing_edge");
            int mightyLv = getTransmutedLevel(rod, "mighty_bolt");
            int needleLv = getTransmutedLevel(rod, "armor_needle");
            float hookDmg = 1.5F * crushLv + 1.2F * mightyLv + 1.3F * needleLv;
            if (hookDmg > 0.0F) {
                hookedMob.hurtServer(level, level.damageSources().playerAttack(player), hookDmg);
            }
            int holyLv = getTransmutedLevel(rod, "holy_bolt");
            if (holyLv > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(hookedMob.getType()).is(EntityTypeTags.UNDEAD)) {
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.5F * holyLv);
            }
            int arthLv = getTransmutedLevel(rod, "arthropod_bolt");
            if (arthLv > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(hookedMob.getType()).is(EntityTypeTags.ARTHROPOD)) {
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.5F * arthLv);
                grantEffect(hookedMob, "slowness", 60, 2);
            }
            int sunderLv = getTransmutedLevel(rod, "sunder_plate");
            if (sunderLv > 0) {
                if (hookedMob.isBlocking()) hookedMob.stopUsingItem();
                grantEffect(hookedMob, "weakness", 60, Math.min(1, sunderLv - 1));
            }
            int fortuneLv = getTransmutedLevel(rod, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < 0.35F + 0.12F * fortuneLv) {
                triggerThreeDollarRoulette(level, player, hookedMob, fortuneLv);
            }
            if (getTransmutedLevel(rod, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(hookedMob.blockPosition())) {
                AberrantRegistry.spawnLightning(level, hookedMob.position());
            }
        }

        int gillLv = getTransmutedLevel(rod, "gill_membrane");
        if (gillLv > 0) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 45 * gillLv));
        }
        int soulLv = getTransmutedLevel(rod, "soul_excavator");
        if (soulLv > 0) {
            grantEffect(player, "speed", 60, Math.min(1, soulLv - 1));
        }
        int adrenLv = getTransmutedLevel(rod, "adrenaline_surge");
        if (adrenLv > 0) {
            grantEffect(player, "haste", 70, Math.min(1, adrenLv - 1));
        }
        int stormGlide = getTransmutedLevel(rod, "storm_glider");
        if (stormGlide > 0 && player.isInWaterOrRain()) {
            Vec3 pull = bobberPos.subtract(player.position());
            if (pull.lengthSqr() > 1.0D) {
                Vec3 dash = pull.normalize().scale(0.85D + 0.20D * stormGlide);
                player.push(dash.x, Math.max(0.24D, dash.y * 0.5D), dash.z);
                player.hurtMarked = true;
            }
        }
        int jetRodLv = getTransmutedLevel(rod, "jetstream_boost");
        if (jetRodLv > 0 && !player.isUnderWater()) {
            player.push(0.0D, 0.42D + 0.10D * jetRodLv, 0.0D);
            player.hurtMarked = true;
            player.resetFallDistance();
        }
    }

    public static void onConsumableFinished(ServerLevel level, ServerPlayer player, ItemStack snapshotBeforeConsume) {
        if (snapshotBeforeConsume == null || snapshotBeforeConsume.isEmpty()) {
            return;
        }
        ItemStack singleCopy = snapshotBeforeConsume.copyWithCount(1);
        level.getServer().execute(() -> {
            if (player.isAlive()) {
                tryRefundRationItemImmediate(level, player, singleCopy);
            }
        });
    }

    private static void tryRefundRationItemImmediate(ServerLevel level, ServerPlayer player, ItemStack singleSnapshot) {
        int rationLv = getTransmutedLevel(singleSnapshot, "unbreaking_ration");
        if (rationLv > 0 && !player.getAbilities().instabuild) {
            CompendiumDiscoveryTracker.recordTransmuteProgress(player, "unbreaking_ration", CompendiumDiscoveryTracker.STATE_MASTERED);
            float saveChance = switch (rationLv) {
                case 1 -> 0.22F;
                case 2 -> 0.35F;
                default -> 0.48F;
            };
            if (level.getRandom().nextFloat() < saveChance) {
                player.getInventory().placeItemBackInInventory(singleSnapshot.copyWithCount(1));
            }
        }
    }

    // =========================================================================
    // 5. Projectile Flight & Impact Physics
    // =========================================================================

    public static void onProjectileTick(ServerLevel level, Projectile proj, ServerPlayer shooter, ItemStack weapon) {
        if (weapon.isEmpty() || !proj.isAlive()) return;

        // First-tick launch modifiers
        if (LAUNCH_PROCESSED_PROJ.size() > 4096) {
            LAUNCH_PROCESSED_PROJ.clear();
        }
        if (LAUNCH_PROCESSED_PROJ.add(proj.getUUID())) {
            double speedBoost = 1.0D;
            if (getTransmutedLevel(weapon, "yishui") > 0 && shooter.isInWaterOrRain()) {
                speedBoost *= 1.30D;
            }
            int rapidDraw = Math.max(
                Math.max(getTransmutedLevel(weapon, "rapid_draw"), getTransmutedLevel(weapon, "swift_draw")),
                getTransmutedLevel(weapon, "slipstream_wings")
            );
            if (rapidDraw > 0) {
                speedBoost *= (1.0D + 0.12D * rapidDraw);
            }
            int mightyLv = getTransmutedLevel(weapon, "mighty_bolt");
            if (mightyLv > 0) {
                speedBoost *= (1.0D + 0.08D * mightyLv);
            }
            int steadyLv = getTransmutedLevel(weapon, "steady_breath");
            if (steadyLv > 0 && shooter.getAirSupply() > 30) {
                shooter.setAirSupply(Math.max(0, shooter.getAirSupply() - 25));
                speedBoost *= (1.0D + 0.10D * steadyLv);
            }
            int hydroLv = getTransmutedLevel(weapon, "hydro_propulsion");
            if (hydroLv > 0 && shooter.isInWaterOrRain()) {
                speedBoost *= (1.0D + 0.15D * hydroLv);
                Vec3 forward = shooter.getLookAngle().normalize().scale(0.55D + 0.18D * hydroLv);
                shooter.push(forward.x, 0.18D, forward.z);
                shooter.hurtMarked = true;
            }
            if (speedBoost != 1.0D) {
                proj.setDeltaMovement(proj.getDeltaMovement().scale(speedBoost));
            }

            // 11: Projectile Protection "偏导迎矢" — firing intercepts hostile projectiles approaching shooter!
            int counterLv = getTransmutedLevel(weapon, "counter_volley");
            if (counterLv > 0) {
                deflectNearbyProjectiles(level, shooter, 3.2D + 0.6D * counterLv);
            }

            // 38: Lunge on Bow/Crossbow -> "跃射步" agile vault step on shot!
            int vaultLv = getTransmutedLevel(weapon, "lunge_vault_shot");
            if (vaultLv > 0) {
                Vec3 horiz = new Vec3(shooter.getLookAngle().x, 0.0D, shooter.getLookAngle().z);
                if (horiz.lengthSqr() > 0.001D) {
                    Vec3 step = horiz.normalize().scale(0.38D + 0.12D * vaultLv);
                    shooter.push(step.x, 0.16D, step.z);
                    shooter.hurtMarked = true;
                    level.sendParticles(ParticleTypes.CLOUD, shooter.getX(), shooter.getY() + 0.2D, shooter.getZ(), 6, 0.2D, 0.1D, 0.2D, 0.04D);
                }
            }

            // 18: Knockback/Punch "反冲跳" (Aim downward > 55 deg to rocket-jump directly!)
            int recoilLv = Math.max(getTransmutedLevel(weapon, "recoil_jump"), getTransmutedLevel(weapon, "heavy_impact"));
            if (recoilLv > 0 && shooter.getXRot() > 55.0F) {
                Vec3 recoil = shooter.getLookAngle().normalize().scale(-0.75D - 0.22D * recoilLv);
                shooter.setDeltaMovement(shooter.getDeltaMovement().add(recoil.x, Math.max(0.55D, recoil.y), recoil.z));
                shooter.hurtMarked = true;
                shooter.resetFallDistance();
            }

            // 22: Infinity on Crossbow "无尽箭"
            if (getTransmutedLevel(weapon, "endless_crossbow") > 0 && proj.getType() == EntityType.ARROW && !shooter.getAbilities().instabuild) {
                shooter.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
            }
        }

        // Ongoing flight physics
        if (proj.onGround()) return;

        // 2: Depth Strider "分波" — negate water drag so arrows torpedo straight through water!
        if (getTransmutedLevel(weapon, "wave_parting") > 0 && proj.isInWater()) {
            Vec3 v = proj.getDeltaMovement();
            if (v.lengthSqr() < 4.5D) {
                proj.setDeltaMovement(v.scale(1.45D));
            }
        }

        // 4: Feather Falling "羽翎" — low-gravity flat glide!
        if (getTransmutedLevel(weapon, "feather_flight") > 0) {
            Vec3 v = proj.getDeltaMovement();
            proj.setDeltaMovement(v.x, v.y + 0.036D, v.z);
        }

        // 30: Density "重磅矢" — steeper heavy slug drop!
        if (getTransmutedLevel(weapon, "heavy_slug_arrow") > 0) {
            Vec3 v = proj.getDeltaMovement();
            proj.setDeltaMovement(v.x, v.y - 0.03D, v.z);
        }

        // 5: Soul Speed "引魂矢" — blue soul-fire trail & gentle homing nudge toward wounded foes!
        int soulArrowLv = getTransmutedLevel(weapon, "soul_seeker_arrow");
        if (soulArrowLv > 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, proj.getX(), proj.getY(), proj.getZ(), 1, 0.02D, 0.02D, 0.02D, 0.0D);
            for (LivingEntity mob : nearbyHostiles(level, shooter, proj.position(), 5.0D, null)) {
                if (mob.getHealth() < mob.getMaxHealth() * 0.75F) {
                    Vec3 cur = proj.getDeltaMovement();
                    double spd = cur.length();
                    if (spd > 0.2D) {
                        Vec3 toMob = mob.getEyePosition().subtract(proj.position()).normalize();
                        proj.setDeltaMovement(cur.normalize().scale(0.84D).add(toMob.scale(0.16D)).normalize().scale(spd));
                    }
                    break;
                }
            }
        }
    }

    public static void onProjectileImpact(
        ServerLevel level,
        @Nullable Projectile proj,
        ServerPlayer shooter,
        ItemStack weapon,
        @Nullable LivingEntity target,
        Vec3 hitPos
    ) {
        if (weapon.isEmpty()) return;
        RandomSource rng = level.getRandom();
        Vec3 flightDir = (proj != null && proj.getDeltaMovement().lengthSqr() > 0.001D)
            ? proj.getDeltaMovement().normalize()
            : shooter.getLookAngle().normalize();

        if (target != null) {
            int searLv = getTransmutedLevel(weapon, "searing_bolt");
            if (searLv > 0) {
                boolean wasBurning = target.isOnFire();
                target.igniteForSeconds(3.5F + 2.0F * searLv);
                if (wasBurning) {
                    target.hurtServer(level, level.damageSources().magic(), 2.2F + 0.9F * searLv);
                    level.sendParticles(ParticleTypes.LAVA, hitPos.x, hitPos.y + 0.6D, hitPos.z, 6, 0.25D, 0.25D, 0.25D, 0.04D);
                }
                level.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y + 0.6D, hitPos.z, 10, 0.25D, 0.25D, 0.25D, 0.03D);
            }
            if (getTransmutedLevel(weapon, "yishui") > 0 && shooter.isInWaterOrRain()) {
                target.hurtServer(level, level.damageSources().magic(), 2.8F);
            }
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 70 * frostLv));
                grantEffect(target, "slowness", 55, frostLv - 1);
                if (target.getTicksFrozen() >= 140 || rng.nextFloat() < 0.28F) {
                    AberrantRegistry.placeSingleFrostedIce(level, target.blockPosition());
                }
            }
            int breechLv = getTransmutedLevel(weapon, "blast_breech");
            if (breechLv > 0) {
                for (LivingEntity mob : nearbyHostiles(level, shooter, hitPos, 2.4D, null)) {
                    mob.hurtServer(level, level.damageSources().explosion(shooter, proj != null ? proj : shooter), 1.4F + 0.5F * breechLv);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y + 0.4D, hitPos.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            int barbLv = getTransmutedLevel(weapon, "barbed_arrow");
            if (barbLv > 0) {
                grantEffect(target, "slowness", 60, barbLv - 1);
                target.hurtServer(level, level.damageSources().magic(), 1.2F * barbLv);
            }
            int sharpBow = getTransmutedLevel(weapon, "broadhead_arrow");
            if (sharpBow > 0) {
                target.hurtServer(level, level.damageSources().magic(), 0.9F * sharpBow);
            }
            int holyBow = getTransmutedLevel(weapon, "holy_bolt");
            if (holyBow > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)) {
                target.hurtServer(level, level.damageSources().magic(), 2.5F * holyBow);
            }
            int bugBow = getTransmutedLevel(weapon, "arthropod_bolt");
            if (bugBow > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.ARTHROPOD)) {
                target.hurtServer(level, level.damageSources().magic(), 2.5F * bugBow);
                grantEffect(target, "slowness", 70, 3);
            }
            int heavyImp = getTransmutedLevel(weapon, "heavy_impact");
            if (heavyImp > 0) {
                Vec3 dir = target.position().subtract(shooter.position()).normalize().scale(0.65D * heavyImp);
                target.push(dir.x, 0.25D, dir.z);
            }
            int mightyLv = getTransmutedLevel(weapon, "mighty_bolt");
            if (mightyLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.1F * mightyLv);
            }
            int slugLv = getTransmutedLevel(weapon, "heavy_slug_arrow");
            if (slugLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.5F * slugLv);
            }
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 2.0F * impaleLv);
            }
            int breachLv = getTransmutedLevel(weapon, "hard_breach");
            if (breachLv > 0 && target.getArmorValue() > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.2F * breachLv);
            }
            int windCut = getTransmutedLevel(weapon, "wind_cutter_arrow");
            if (windCut > 0) {
                for (LivingEntity adj : nearbyHostiles(level, shooter, hitPos, 2.8D, target)) {
                    adj.hurtServer(level, level.damageSources().magic(), 1.5F + windCut);
                }
            }
            if (getTransmutedLevel(weapon, "fan_volley") > 0) {
                int extraHits = 0;
                for (LivingEntity adj : nearbyHostiles(level, shooter, hitPos, 4.0D, target)) {
                    adj.hurtServer(level, level.damageSources().magic(), 3.2F);
                    if (++extraHits >= 2) break;
                }
            }
            int pierceBow = getTransmutedLevel(weapon, "sun_piercer");
            if (pierceBow > 0) {
                if (target.isBlocking()) target.stopUsingItem();
                Vec3 behind = hitPos.add(flightDir.scale(2.2D));
                for (LivingEntity back : nearbyHostiles(level, shooter, behind, 2.2D, target)) {
                    back.hurtServer(level, level.damageSources().magic(), 2.5F + 0.6F * pierceBow);
                }
            }
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.28F + 0.14F * fortuneLv)) {
                triggerThreeDollarRoulette(level, shooter, target, fortuneLv);
            }
            if (getTransmutedLevel(weapon, "sticky_root_curse") > 0 && rng.nextFloat() < 0.30F) {
                grantEffect(target, "slowness", 40, 4);
            }
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(hitPos.x, hitPos.y, hitPos.z);
                bolt.setCause(shooter);
                level.addFreshEntity(bolt);
            }
        }

        // 26: Loyalty "回旋收箭"
        int boomLv = getTransmutedLevel(weapon, "boomerang_arrow");
        if (boomLv > 0 && proj != null && proj.getType() == EntityType.ARROW && !shooter.getAbilities().instabuild && rng.nextFloat() < (0.30F + 0.18F * boomLv)) {
            shooter.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
            proj.discard();
        }

        // 32: Wind Burst "风弹跳矢" (non-destructive Wind Charge jump — disabled underwater!)
        int windArrowLv = getTransmutedLevel(weapon, "wind_charge_arrow");
        if (windArrowLv > 0 && (proj == null || !proj.isInWater())) {
            for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, 5.2D, 5.2D, 5.2D), Entity::isAlive)) {
                Vec3 away = ent.position().add(0.0D, 0.5D, 0.0D).subtract(hitPos);
                double d = Math.max(0.6D, away.length());
                Vec3 boost = away.normalize().scale((0.95D + 0.2D * windArrowLv) / (d * 0.55D));
                ent.setDeltaMovement(ent.getDeltaMovement().add(boost.x, Math.max(0.55D, boost.y), boost.z));
                ent.hurtMarked = true;
                if (ent == shooter) {
                    shooter.resetFallDistance();
                }
            }
            level.sendParticles(ParticleTypes.CLOUD, hitPos.x, hitPos.y + 0.3D, hitPos.z, 16, 0.5D, 0.25D, 0.5D, 0.1D);
        }

        // 34: Lure "诱敌钩矢" (Entity hit = yank toward shooter; Block hit = lure nearby creatures to hitPos!)
        int lureBowLv = getTransmutedLevel(weapon, "lure_hook_arrow");
        if (lureBowLv > 0) {
            if (target != null) {
                Vec3 yank = shooter.position().subtract(target.position()).normalize().scale(0.75D + 0.2D * lureBowLv);
                target.push(yank.x, 0.3D, yank.z);
            } else {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.ofSize(hitPos, 18.0D, 8.0D, 18.0D), Entity::isAlive)) {
                    mob.getNavigation().moveTo(hitPos.x, hitPos.y, hitPos.z, 1.2D);
                }
                level.sendParticles(ParticleTypes.HEART, hitPos.x, hitPos.y + 0.6D, hitPos.z, 4, 0.3D, 0.2D, 0.3D, 0.02D);
            }
        }
    }

    // =========================================================================
    // 6. Incoming Damage, Shield Blocking & Totem Unbreaking Save
    // =========================================================================

    public static boolean allowIncomingDamage(ServerLevel level, ServerPlayer player, DamageSource source, float amount) {
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // 28: Channeling on Armor/Shield -> 100% Lightning immunity + charge buff!
        if (source != null && source.getDirectEntity() instanceof LightningBolt && hasArmorTransmute(player, "lightning_conductor_armor")) {
            grantEffect(player, "strength", 140, 0);
            grantEffect(player, "speed", 140, 1);
            for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 5.0D, null)) {
                mob.hurtServer(level, level.damageSources().magic(), 5.0F);
            }
            return false;
        }
        return true;
    }

    public static void onAfterDamage(ServerLevel level, ServerPlayer player, DamageSource source, float damageTaken, boolean blocked) {
        if (Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) {
            return;
        }
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            onAfterDamageInternal(level, player, source, damageTaken, blocked);
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    private static void onAfterDamageInternal(ServerLevel level, ServerPlayer player, DamageSource source, float damageTaken, boolean blocked) {
        LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
        RandomSource rng = level.getRandom();
        if (PICKPOCKETED_MOBS.size() > 4096) {
            PICKPOCKETED_MOBS.clear();
        }

        // Shield-specific reactions when blocking
        ItemStack useItem = player.isBlocking() ? player.getUseItem() : ItemStack.EMPTY;
        if (blocked && !useItem.isEmpty()) {
            int spikeLv = getTransmutedLevel(useItem, "spiked_shield");
            if (spikeLv > 0 && attacker != null) {
                attacker.hurtServer(level, level.damageSources().thorns(player), 1.5F + 1.2F * spikeLv);
            }
            int bastionLv = getTransmutedLevel(useItem, "bastion_shield");
            if (bastionLv > 0 && useItem.isDamaged() && rng.nextFloat() < 0.20F * bastionLv) {
                useItem.setDamageValue(Math.max(0, useItem.getDamageValue() - 1));
            }
            if (getTransmutedLevel(useItem, "fireproof_shield") > 0) {
                player.clearFire();
            }
            int catchLv = getTransmutedLevel(useItem, "arrow_catch_shield");
            if (catchLv > 0 && source.getDirectEntity() instanceof Projectile && rng.nextFloat() < 0.25F * catchLv) {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
            }
            if (getTransmutedLevel(useItem, "shield_sweep") > 0) {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.65D);
                    mob.push(away.x, 0.2D, away.z);
                }
            }
            if (getTransmutedLevel(useItem, "splinter_shield") > 0 && source.getDirectEntity() instanceof Projectile) {
                int hit = 0;
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 6.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F);
                    if (++hit >= 3) break;
                }
            }
        }

        // Handheld parry & defensive mitigation heal-back
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        int parryLv = getTransmutedLevel(main, "blade_parry");
        if (parryLv > 0 && damageTaken > 0.5F) {
            long lastSwing = LAST_SWING_TICK.getOrDefault(player.getUUID(), -100L);
            boolean activeParry = (level.getGameTime() - lastSwing) <= 12L;
            float refundRatio = activeParry ? 0.28F : (0.04F * parryLv);
            player.heal(damageTaken * refundRatio);
            if (activeParry) {
                damageItemSafely(main, 1, player);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.8F);
            }
        }
        int bracerLv = Math.max(getTransmutedLevel(main, "bracer_guard"), getTransmutedLevel(off, "bracer_guard"));
        if (bracerLv > 0 && damageTaken > 0.5F) {
            player.heal(damageTaken * (0.10F + 0.04F * bracerLv));
        }
        int minerGuardLv = Math.max(getTransmutedLevel(main, "miner_guard"), getTransmutedLevel(off, "miner_guard"));
        if (minerGuardLv > 0 && damageTaken > 0.5F) {
            player.heal(damageTaken * (0.10F + 0.04F * minerGuardLv));
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) && damageTaken > 0.5F) {
            int blastMit = Math.max(
                Math.max(getTransmutedLevel(main, "blast_breech"), getTransmutedLevel(off, "blast_breech")),
                Math.max(getTransmutedLevel(main, "blast_guard"), getTransmutedLevel(off, "blast_guard"))
            );
            if (blastMit > 0) {
                player.heal(damageTaken * Math.min(0.65F, 0.25F + 0.10F * blastMit));
            }
        }

        // Armor & Shield cross-domain passives
        EquipmentSlot[] slots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        for (EquipmentSlot slot : slots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty()) continue;

            if (getTransmutedLevel(gear, "soul_escape") > 0 && damageTaken > 0.5F) {
                grantEffect(player, "speed", 60, 1);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.5D, player.getZ(), 8, 0.3D, 0.2D, 0.3D, 0.04D);
            }
            int rimLv = getTransmutedLevel(gear, "bladed_rim");
            if (rimLv > 0 && attacker != null && player.distanceToSqr(attacker) < 9.0D) {
                attacker.hurtServer(level, level.damageSources().thorns(player), 0.8F + 0.5F * rimLv);
            }
            int undeadWard = getTransmutedLevel(gear, "undead_ward");
            if (undeadWard > 0 && attacker != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(attacker.getType()).is(EntityTypeTags.UNDEAD)) {
                player.heal(Math.min(damageTaken * 0.25F, 2.5F));
                attacker.igniteForSeconds(2.0F);
            }
            int repulseLv = getTransmutedLevel(gear, "repulse_guard");
            if (repulseLv > 0 && attacker != null && player.distanceToSqr(attacker) < 10.0D) {
                Vec3 push = attacker.position().subtract(player.position()).normalize().scale(0.55D * repulseLv);
                attacker.push(push.x, 0.25D, push.z);
            }
            int guardianLv = getTransmutedLevel(gear, "guardian_ward");
            if (guardianLv > 0 && (player.isInWaterOrRain()
                || (attacker != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(attacker.getType()).is(EntityTypeTags.AQUATIC)))) {
                player.heal(Math.min(2.5F, damageTaken * 0.20F));
                if (attacker != null) {
                    attacker.hurtServer(level, level.damageSources().thorns(player), 1.4F * guardianLv);
                }
            }
            if (getTransmutedLevel(gear, "titan_brace") > 0) {
                player.setDeltaMovement(player.getDeltaMovement().scale(0.55D));
                player.hurtMarked = true;
            }
            if (getTransmutedLevel(gear, "gale_shield") > 0 && !player.isUnderWater() && damageTaken >= 2.0F) {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                    mob.push(away.x, 0.35D, away.z);
                }
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6D, player.getZ(), 12, 0.4D, 0.2D, 0.4D, 0.08D);
            }
            // 20: Looting "顺手牵羊" (pickpocket a living mob once!)
            int pickLv = getTransmutedLevel(gear, "pickpocket_guard");
            if (pickLv > 0 && attacker != null && !(attacker instanceof Player)
                && !PICKPOCKETED_MOBS.contains(attacker.getUUID())
                && rng.nextFloat() < (0.14F * pickLv)) {
                PICKPOCKETED_MOBS.add(attacker.getUUID());
                Item stolen = pickCommonMobDrop(attacker.getType(), rng);
                spawnItem(level, player.position(), new ItemStack(stolen, 1));
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
            }

            // 0.5.0 Complete Armor / Shield / Elytra Cross-Domain Passives
            int emberLv = getTransmutedLevel(gear, "ember_mantle");
            if (emberLv > 0) {
                if (attacker != null && player.distanceToSqr(attacker) < 12.0D) {
                    attacker.igniteForSeconds(2.5F + 1.5F * emberLv);
                }
                if (source.is(DamageTypeTags.IS_FIRE)) {
                    grantEffect(player, "fire_resistance", 55, 0);
                }
            }
            int rimeLv = getTransmutedLevel(gear, "rime_aegis");
            if (rimeLv > 0) {
                if (player.isOnFire()) player.clearFire();
                if (attacker != null && player.distanceToSqr(attacker) < 12.0D) {
                    attacker.setTicksFrozen(Math.min(320, attacker.getTicksFrozen() + 70 * rimeLv));
                    grantEffect(attacker, "slowness", 50, rimeLv - 1);
                }
            }
            int counterLv = getTransmutedLevel(gear, "counter_volley");
            if (counterLv > 0 && source.is(DamageTypeTags.IS_PROJECTILE) && damageTaken > 0.5F) {
                player.heal(damageTaken * Math.min(0.45F, 0.14F + 0.06F * counterLv));
            }
            int plumeLv = getTransmutedLevel(gear, "updraft_plume");
            if (plumeLv > 0 && (source.is(DamageTypeTags.IS_FALL) || player.isFallFlying()) && damageTaken > 1.0F) {
                player.heal(damageTaken * Math.min(0.55F, 0.18F + 0.08F * plumeLv));
                grantEffect(player, "slow_falling", 50, 0);
            }
            int hemLv = getTransmutedLevel(gear, "whirlwind_hem");
            if (hemLv > 0 && attacker != null && player.distanceToSqr(attacker) < 12.0D) {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.0D, null)) {
                    mob.hurtServer(level, level.damageSources().thorns(player), 1.0F + 0.7F * hemLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            int prismLv = getTransmutedLevel(gear, "refracted_carapace");
            if (prismLv > 0 && damageTaken >= 1.5F) {
                int hit = 0;
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 5.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 1.8F + 0.7F * prismLv);
                    level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 0.9D, mob.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.05D);
                    if (++hit >= 3) break;
                }
            }
            int needleLv = getTransmutedLevel(gear, "armor_needle");
            if (needleLv > 0 && attacker != null) {
                if (attacker.isBlocking()) attacker.stopUsingItem();
                attacker.hurtServer(level, level.damageSources().magic(), 1.2F + 0.55F * needleLv);
            }
            int adrenLv = getTransmutedLevel(gear, "adrenaline_surge");
            if (adrenLv > 0 && damageTaken >= 1.5F) {
                String prefix = player.getUUID().toString() + ":";
                ACTIVE_SKILL_COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
                grantEffect(player, "haste", 60, Math.min(1, adrenLv - 1));
                grantEffect(player, "speed", 50, 0);
            }
            int sunderLv = getTransmutedLevel(gear, "sunder_plate");
            if (sunderLv > 0 && attacker != null) {
                grantEffect(attacker, "weakness", 65, Math.min(1, sunderLv - 1));
            }
        }
    }

    public static boolean allowPlayerDeath(ServerLevel level, ServerPlayer player, DamageSource source) {
        // API-04: Never block /kill or void BYPASSES_INVULNERABILITY sources with Totem Unbreaking!
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // 37a: Unbreaking on Totem of Undying -> 10% / 18% / 25% chance to trigger Totem save WITHOUT shattering the Totem!
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            int unbTotem = getTransmutedLevel(held, "unbreaking_totem");
            if (unbTotem > 0) {
                float keepChance = switch (unbTotem) {
                    case 1 -> 0.10F;
                    case 2 -> 0.18F;
                    default -> 0.25F;
                };
                if (level.getRandom().nextFloat() < keepChance) {
                    player.setHealth(1.0F);
                    player.removeAllEffects();
                    grantEffect(player, "regeneration", 900, 1);
                    grantEffect(player, "absorption", 100, 1);
                    grantEffect(player, "fire_resistance", 800, 0);
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 32, 0.5D, 0.5D, 0.5D, 0.2D);
                    level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.15F);
                    return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // 7. Kill Drops, Player Passive Tick & World Item Physics
    // =========================================================================

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty()) return;
        RandomSource rng = level.getRandom();

        // 13: Silk Touch "完壁"
        if (getTransmutedLevel(weapon, "intact_blade") > 0 && rng.nextFloat() < 0.10F) {
            EntityType<?> vt = victim.getType();
            if (vt == EntityType.ZOMBIE) spawnItem(level, victim.position(), new ItemStack(Items.ZOMBIE_HEAD, 1));
            else if (vt == EntityType.SKELETON) spawnItem(level, victim.position(), new ItemStack(Items.SKELETON_SKULL, 1));
            else if (vt == EntityType.CREEPER) spawnItem(level, victim.position(), new ItemStack(Items.CREEPER_HEAD, 1));
            else if (vt == EntityType.SHEEP) spawnItem(level, victim.position(), new ItemStack(Items.WHITE_WOOL, 2));
        }

        // 20: Looting on Bow/Crossbow/Heavy Weapons "猎宝"
        int plunderLv = getTransmutedLevel(weapon, "ranged_heavy_plunder");
        if (plunderLv > 0 && rng.nextFloat() < 0.30F * plunderLv) {
            Item bonus = pickCommonMobDrop(victim.getType(), rng);
            spawnItem(level, victim.position(), new ItemStack(bonus, 1 + rng.nextInt(plunderLv)));
            ExperienceOrb.award(level, victim.position(), 2 * plunderLv);
        }

        // 33: Luck of the Sea "海猎"
        int seaLv = getTransmutedLevel(weapon, "ocean_bounty");
        if (seaLv > 0 && (victim.isInWaterOrRain() || killer.isInWaterOrRain()
            || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(victim.getType()).is(EntityTypeTags.AQUATIC))
            && rng.nextFloat() < 0.16F * seaLv) {
            Item treasure = switch (rng.nextInt(5)) {
                case 0 -> Items.PRISMARINE_SHARD;
                case 1 -> Items.PRISMARINE_CRYSTALS;
                case 2 -> Items.LAPIS_LAZULI;
                case 3 -> Items.EMERALD;
                default -> Items.NAUTILUS_SHELL;
            };
            spawnItem(level, victim.position(), new ItemStack(treasure, 1));
        }
    }

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        UUID pid = player.getUUID();

        // Spear Channel 2 (Hold-Right-Click Charge Sustain & Charged Release — preserves 100% of vanilla Spear charging!)
        tickSpearHoldChargeAndRelease(level, player);

        // 13: Silk Touch on Armor "护田轻衬" — prevent jumping from trampling farmland / turtle eggs!
        if (hasArmorTransmute(player, "featherstep_lining") && player.fallDistance > 0.4F && player.fallDistance < 3.0F) {
            BlockPos below = player.blockPosition().below();
            BlockState bs = level.getBlockState(below);
            BlockState at = level.getBlockState(player.blockPosition());
            if (bs.is(Blocks.FARMLAND) || at.is(Blocks.FARMLAND) || bs.is(Blocks.TURTLE_EGG) || at.is(Blocks.TURTLE_EGG)) {
                player.resetFallDistance();
            }
        }

        // 30: Density on Armor/Boots "千斤坠" — stomp crush mobs on landing & sink faster in water!
        if (hasArmorTransmute(player, "anvil_stomp")) {
            if (player.isInWater() && !player.onGround()) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, Math.max(-0.45D, v.y - 0.035D), v.z);
                player.hurtMarked = true;
            }
            if (!player.onGround() && player.fallDistance >= 3.2D) {
                for (LivingEntity under : nearbyHostiles(level, player, player.position().subtract(0.0D, 0.8D, 0.0D), 1.8D, null)) {
                    float crush = (float) Math.min(16.0D, player.fallDistance * 1.4D);
                    under.hurtServer(level, level.damageSources().playerAttack(player), crush);
                    player.fallDistance *= 0.45D;
                    level.playSound(null, under.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.1F);
                }
            }
        }

        // 37b: Special Mending XP absorption tracking (Glass Bottle -> XP Bottle, Totem -> Heal, Consumable -> Replenish)
        int curXp = player.totalExperience;
        Integer prevXp = LAST_PLAYER_XP.put(pid, curXp);
        if (prevXp != null && curXp > prevXp) {
            int gained = curXp - prevXp;
            handleSpecialMendingXpGain(player, gained);
        }

        if (level.getGameTime() % 10L != 0L) {
            return;
        }

        // 5: Soul Speed "追魂步" sprint toward marked soul
        Long chaseUntil = SOUL_CHASE_UNTIL.get(pid);
        if (chaseUntil != null && level.getGameTime() < chaseUntil) {
            grantEffect(player, "speed", 25, 1);
        }

        // 6: Swift Sneak "暗矢" (bow sneak speed) & "暗影披风" (shroud cloak sneak stealth!)
        if (player.isShiftKeyDown() && getTransmutedLevel(player.getMainHandItem(), "silent_arrow") > 0) {
            grantEffect(player, "speed", 25, 0);
        }
        if (player.isShiftKeyDown() && hasArmorOrShieldTransmute(player, "shroud_cloak")) {
            grantEffect(player, "invisibility", 30, 0);
        }

        // 38: Lunge "疾行突步" (periodic forward wind-stride while sprinting!)
        if (player.isSprinting() && level.getGameTime() % 50L == 0L && hasArmorOrShieldTransmute(player, "lunge_wind_stride")) {
            Vec3 horiz = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
            if (horiz.lengthSqr() > 0.001D) {
                Vec3 boost = horiz.normalize().scale(0.42D);
                player.push(boost.x, 0.08D, boost.z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.15D, player.getZ(), 5, 0.2D, 0.1D, 0.2D, 0.03D);
            }
        }

        // 26: Loyalty "虚空归返 / 归鸿引线" emergency player void rescue!
        if (player.getY() < level.getMinY() - 6
            && (hasArmorOrShieldTransmute(player, "void_homing") || hasArmorOrShieldTransmute(player, "auto_reel"))
            && checkSkillCooldown(player, "void_homing_rescue", level.getGameTime(), 1200)) {
            int terrainY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                player.getBlockX(),
                player.getBlockZ()
            );
            player.resetFallDistance();
            player.teleportTo(player.getX(), Math.max(level.getMinY() + 68, terrainY + 4), player.getZ());
            grantEffect(player, "slow_falling", 180, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.1F);
        }

        // 0.5.0 Aquatic & Elytra Flight Passives
        if (player.isInWaterOrRain()) {
            if (hasArmorOrShieldTransmute(player, "tidal_carapace") || hasArmorOrShieldTransmute(player, "gill_membrane")) {
                player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 25));
            }
            if (player.isInWater() && hasArmorOrShieldTransmute(player, "pelagic_stream")) {
                grantEffect(player, "dolphins_grace", 35, 0);
            }
        }
        if (player.isFallFlying()) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int slipLv = getTransmutedLevel(chest, "slipstream_wings");
            if (slipLv > 0 && player.getXRot() < 8.0F && player.getXRot() > -30.0F) {
                Vec3 vel = player.getDeltaMovement();
                if (vel.horizontalDistanceSqr() < 2.2D) {
                    Vec3 nudge = player.getLookAngle().normalize().scale(0.06D + 0.025D * slipLv);
                    player.setDeltaMovement(vel.add(nudge.x, 0.015D, nudge.z));
                    player.hurtMarked = true;
                }
            }
            int stormLv = getTransmutedLevel(chest, "storm_glider");
            if (stormLv > 0 && player.isInWaterOrRain()) {
                Vec3 boost = player.getLookAngle().normalize().scale(0.14D + 0.05D * stormLv);
                player.setDeltaMovement(player.getDeltaMovement().add(boost));
                player.hurtMarked = true;
            }
            int diveEdgeLv = getTransmutedLevel(chest, "crushing_edge");
            if (diveEdgeLv > 0 && player.getDeltaMovement().lengthSqr() > 0.65D) {
                for (LivingEntity hit : nearbyHostiles(level, player, player.position(), 2.0D, null)) {
                    hit.hurtServer(level, level.damageSources().playerAttack(player), 2.5F + 1.2F * diveEdgeLv);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.getX(), hit.getY() + 0.8D, hit.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }

        // 12: Efficiency "利落" (speed compensation while shielding/eating)
        if (player.isUsingItem() && hasArmorOrShieldTransmute(player, "nimble_gear")) {
            grantEffect(player, "speed", 25, 0);
        }

        // 17: Bane of Arthropods "免网避毒"
        if (hasArmorOrShieldTransmute(player, "venom_ward")) {
            BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace("poison"))
                .ifPresent(player::removeEffect);
        }

        // 25: Quick Charge "快盾" — agile instant shield guard reinforcement!
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = player.getItemInHand(h);
            if (getTransmutedLevel(st, "reflex_shield") > 0 && player.isUsingItem()) {
                grantEffect(player, "resistance", 25, 0);
            }
        }

        // 33: Luck of the Sea "海运"
        if (hasArmorOrShieldTransmute(player, "sea_luck_aura") && (player.isInWaterOrRain() || player.isPassenger())) {
            grantEffect(player, "luck", 40, 0);
        }

        // 34: Lure "牧歌" (farm animals follow tool!) & "诱怪香囊" (hostiles drawn to armor!)
        if (getTransmutedLevel(player.getMainHandItem(), "shepherd_lure") > 0
            || getTransmutedLevel(player.getOffhandItem(), "shepherd_lure") > 0) {
            for (Mob animal : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(9.0D), m -> isFarmAnimal(m.getType()))) {
                animal.getNavigation().moveTo(player, 1.1D);
            }
        }
        if (hasArmorOrShieldTransmute(player, "monster_beacon")) {
            for (Mob monster : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(18.0D), m -> m.getType().getCategory() == MobCategory.MONSTER)) {
                if (monster.getTarget() == null) {
                    monster.setTarget(player);
                }
                monster.getNavigation().moveTo(player, 1.15D);
            }
        }

        // 22: Infinity "不竭" — lock equipment at >= 1 remaining durability before shattering!
        EquipmentSlot[] allSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        for (EquipmentSlot slot : allSlots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (!gear.isEmpty() && getTransmutedLevel(gear, "unending_edge") > 0
                && gear.isDamageableItem() && gear.getDamageValue() >= gear.getMaxDamage() - 1) {
                gear.setDamageValue(Math.max(0, gear.getMaxDamage() - 2));
            }
        }
    }

    public static void tickWorldItems(ServerLevel level) {
        // CPL-03: Throttled to 10-tick cadence with visited-entity deduplication across nearby players
        if (level.getGameTime() % 10L != 0L || level.players().isEmpty()) {
            return;
        }
        Set<Integer> visitedIds = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB columnBox = new AABB(
                player.getX() - 96.0D, level.getMinY() - 96.0D, player.getZ() - 96.0D,
                player.getX() + 96.0D, player.getY() + 64.0D, player.getZ() + 96.0D
            );
            List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, columnBox, Entity::isAlive);
            for (ItemEntity ie : nearby) {
                if (!visitedIds.add(ie.getId())) {
                    continue;
                }
                ItemStack st = ie.getItem();
                if (st.isEmpty() || !hasAnyTransmutedEnchants(st)) continue;

                // 9 & 10: Fire Protection / Blast Protection dropped item indestructibility!
                if (getStandaloneEnchantLevel(st, "minecraft:fire_protection") > 0 && resolve("minecraft:fire_protection", st).isPresent()) {
                    ie.setInvulnerable(true);
                    ie.clearFire();
                    if (ie.isInLava()) {
                        ie.setDeltaMovement(ie.getDeltaMovement().x * 0.8D, 0.12D, ie.getDeltaMovement().z * 0.8D);
                    }
                }
                if (getStandaloneEnchantLevel(st, "minecraft:blast_protection") > 0 && resolve("minecraft:blast_protection", st).isPresent()) {
                    ie.setInvulnerable(true);
                }

                // 26: Loyalty void/lava homing back to owner!
                if (getStandaloneEnchantLevel(st, "minecraft:loyalty") > 0 && resolve("minecraft:loyalty", st).isPresent()) {
                    ie.setInvulnerable(true);
                    if (ie.getY() <= level.getMinY() + 6 || ie.isInLava()) {
                        ServerPlayer targetOwner = (ie.getOwner() instanceof ServerPlayer sp && sp.isAlive()) ? sp : player;
                        ie.setNoPickUpDelay();
                        ie.setNoGravity(true);
                        if (targetOwner.getInventory().add(st.copy())) {
                            ie.discard();
                            level.playSound(null, targetOwner.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.7F, 1.2F);
                            continue;
                        } else {
                            ie.setPos(targetOwner.getX(), targetOwner.getY() + 0.5D, targetOwner.getZ());
                            ie.setDeltaMovement(Vec3.ZERO);
                        }
                    }
                }

                // 35: Curse of Binding on handheld items ("粘包防丢": snaps right back if living owner tossed it!)
                if (getTransmutedLevel(st, "sticky_root_curse") > 0 && ie.getOwner() instanceof ServerPlayer owner && owner.isAlive()) {
                    if (owner.getInventory().add(st.copy())) {
                        ie.discard();
                        level.playSound(null, owner.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 0.7F);
                    }
                }
            }
        }
    }

    // =========================================================================
    // Internal Helpers ($3 Bill Roulette, Smelt Tables, Safe Durability, Effects)
    // =========================================================================

    private static void handleSpecialMendingXpGain(ServerPlayer player, int gained) {
        UUID pid = player.getUUID();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = player.getItemInHand(hand);
            if (st.isEmpty()) continue;

            if (getTransmutedLevel(st, "mending_xp_bottle") > 0) {
                int buf = BOTTLE_XP_BUFFER.merge(pid, gained, Integer::sum);
                int converted = 0;
                while (buf >= 8 && !st.isEmpty() && converted < 8) {
                    buf -= 8;
                    converted++;
                    if (player.totalExperience >= 8) {
                        player.giveExperiencePoints(-8);
                    }
                    // Prefer converting plain glass bottles in inventory first so the enchanted condenser bottle can keep working!
                    int plainSlot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
                    if (plainSlot >= 0 && player.getInventory().getItem(plainSlot) != st) {
                        player.getInventory().getItem(plainSlot).shrink(1);
                    } else {
                        st.shrink(1);
                    }
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.EXPERIENCE_BOTTLE, 1));
                }
                BOTTLE_XP_BUFFER.put(pid, Math.max(0, buf));
                LAST_PLAYER_XP.put(pid, player.totalExperience);
                return;
            }
            if (getTransmutedLevel(st, "mending_vitality") > 0) {
                player.heal(Math.min(4.0F, 0.35F * gained));
                return;
            }
            if (getTransmutedLevel(st, "mending_replenish") > 0 && st.getCount() < st.getMaxStackSize()) {
                int buf = REPLENISH_XP_BUFFER.merge(pid, gained, Integer::sum);
                if (buf >= 22) {
                    REPLENISH_XP_BUFFER.put(pid, buf - 22);
                    st.grow(1);
                }
                return;
            }
        }
    }

    private static void triggerThreeDollarRoulette(ServerLevel level, ServerPlayer player, LivingEntity target, int fortuneLv) {
        RandomSource rng = level.getRandom();
        Vec3 pos = target.position();
        switch (rng.nextInt(8)) {
            case 0 -> { // Golden Crit + Gold Nugget!
                target.hurtServer(level, level.damageSources().magic(), 3.0F + fortuneLv);
                spawnItem(level, pos, new ItemStack(Items.GOLD_NUGGET, 1 + rng.nextInt(fortuneLv)));
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.1D);
            }
            case 1 -> { // Ice Freeze Shot!
                target.setTicksFrozen(260);
                grantEffect(target, "slowness", 70, 2);
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
            }
            case 2 -> { // Brimstone Ignite!
                target.igniteForSeconds(4.0F + fortuneLv);
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.6D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case 3 -> { // Spider Bite Poison + Weakness!
                grantEffect(target, "poison", 80, 1);
                grantEffect(target, "weakness", 80, 0);
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.7D, pos.z, 10, 0.3D, 0.3D, 0.3D, 0.04D);
            }
            case 4 -> { // Anti-Gravity Balloon Levitation!
                grantEffect(target, "levitation", 32, 1);
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.8D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case 5 -> { // Jacob's Ladder Chain Zap!
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 14, 0.35D, 0.35D, 0.35D, 0.08D);
            }
            case 6 -> { // Mystery Concussion Blind + Glowing!
                grantEffect(target, "blindness", 60, 0);
                grantEffect(target, "glowing", 100, 0);
                level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 1.0D, pos.z, 14, 0.3D, 0.3D, 0.3D, 0.08D);
            }
            default -> { // Knockout Punch Launch!
                Vec3 dir = pos.subtract(player.position()).normalize().scale(1.2D);
                target.push(dir.x, 0.42D, dir.z);
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5D, pos.z, 10, 0.3D, 0.2D, 0.3D, 0.05D);
            }
        }
    }

    private static void deflectNearbyProjectiles(ServerLevel level, ServerPlayer player, double radius) {
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius), e -> e.isAlive() && e.getOwner() != player)) {
            p.setDeltaMovement(player.getLookAngle().normalize().scale(1.25D));
            p.setOwner(player);
            level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1D, 0.1D, 0.1D, 0.05D);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.9F);
        }
    }

    private static boolean hasArmorTransmute(ServerPlayer player, String variantId) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (getTransmutedLevel(player.getItemBySlot(s), variantId) > 0) return true;
        }
        return false;
    }

    private static boolean hasArmorOrShieldTransmute(ServerPlayer player, String variantId) {
        if (hasArmorTransmute(player, variantId)) return true;
        return getTransmutedLevel(player.getMainHandItem(), variantId) > 0
            || getTransmutedLevel(player.getOffhandItem(), variantId) > 0;
    }

    /**
     * Spear Hold-Right-Click Charge Sustain &amp; Charged Release Engine:
     * <ul>
     *   <li>While holding vanilla Right-Click Spear charge: activates {@code 拨矢护幕 / 剑格} projectile deflection
     *       and {@code 破浪架矛} aquatic couched momentum without cancelling vanilla charging.</li>
     *   <li>Upon releasing a charged Spear ({@code >= 10} ticks): projects a 3D cached-path {@code 贯虹枪芒}
     *       when bearing spear-projection transmutations or signatures!</li>
     * </ul>
     */
    private static void tickSpearHoldChargeAndRelease(ServerLevel level, ServerPlayer player) {
        UUID pid = player.getUUID();
        ItemStack activeUse = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (!activeUse.isEmpty() && classifyTarget(activeUse) == TargetRole.SPEAR) {
            int ticks = Math.max(1, player.getTicksUsingItem());
            SPEAR_CHARGE_TICKS.put(pid, ticks);
            SPEAR_CHARGE_WEAPON.put(pid, activeUse.copyWithCount(1));

            // Sustain A: Spinning spearhead projectile deflection while holding charge
            int deflectLv = Math.max(getTransmutedLevel(activeUse, "arrow_deflect"), getTransmutedLevel(activeUse, "blade_parry"));
            if ((deflectLv > 0 || SignaturePairs.stackHasSignature(activeUse, "mirror_moon_sweep")) && ticks >= 3 && ticks % 4 == 0) {
                deflectNearbyProjectiles(level, player, 3.0D + 0.55D * Math.max(1, deflectLv));
                Vec3 tip = player.getEyePosition().add(player.getLookAngle().scale(1.25D));
                level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y - 0.15D, tip.z, 2, 0.12D, 0.12D, 0.12D, 0.02D);
            }

            // Sustain B: Couched hydro-propulsion while holding Spear charge in water or rain
            int wetRush = Math.max(getTransmutedLevel(activeUse, "aqua_spiral_dash"), getTransmutedLevel(activeUse, "wave_parting"));
            if ((wetRush > 0 || SignaturePairs.stackHasSignature(activeUse, "tidal_harpoon_rush"))
                && player.isInWaterOrRain() && ticks >= 5 && ticks % 8 == 0) {
                Vec3 push = player.getLookAngle().normalize().scale(0.28D + 0.08D * Math.max(1, wetRush));
                player.push(push.x, Math.max(0.02D, push.y * 0.3D), push.z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6D, player.getZ(), 5, 0.25D, 0.15D, 0.25D, 0.04D);
            }

            // Visual charge stage chime at 10 & 20 ticks when Spear carries release-projection traits
            if ((ticks == 10 || ticks == 20) && hasSpearChargeReleaseProjection(activeUse, player)) {
                Vec3 tip = player.getEyePosition().add(player.getLookAngle().scale(1.2D));
                level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y - 0.15D, tip.z, 5, 0.08D, 0.08D, 0.08D, 0.03D);
            }
            return;
        }

        Integer prevTicks = SPEAR_CHARGE_TICKS.remove(pid);
        ItemStack prevSpear = SPEAR_CHARGE_WEAPON.remove(pid);
        if (prevTicks != null && prevTicks >= 10 && prevSpear != null && !prevSpear.isEmpty()) {
            if (hasSpearChargeReleaseProjection(prevSpear, player)
                && checkSkillCooldown(player, "spear_charge_release_lance", level.getGameTime(), 18)) {
                int tier = prevTicks >= 22 ? 4 : (prevTicks >= 15 ? 3 : 2);
                FlyingBladeManager.launchLinearPhantomWeapon(
                    level, player, InteractionHand.MAIN_HAND, prevSpear, tier, FlyingBladeManager.FlightStyle.SPEAR_CHARGE_LANCE
                );
            }
        }
    }

    private static boolean hasSpearChargeReleaseProjection(ItemStack spear, ServerPlayer player) {
        if (getTransmutedLevel(spear, "unending_edge") > 0
            || getTransmutedLevel(spear, "sun_piercer") > 0
            || getTransmutedLevel(spear, "twin_shadow_slash") > 0
            || getTransmutedLevel(spear, "storm_rod_strike") > 0
            || getTransmutedLevel(spear, "full_draw_cleave") > 0) {
            return true;
        }
        return SignaturePairs.stackHasSignature(spear, "flying_sword_qi")
            || SignaturePairs.stackHasSignature(spear, "hydro_flying_sword")
            || SignaturePairs.stackHasSignature(spear, "boomerang_lance")
            || SignaturePairs.stackHasSignature(spear, "hundred_step_skewer")
            || SignaturePairs.stackHasSignature(spear, "thunderbolt_cavalry")
            || SignaturePairs.stackHasSignature(spear, "abyssal_full_breath")
            || SignaturePairs.playerHasEquippedSignature(player, "hundred_step_skewer");
    }

    public static boolean checkSkillCooldown(ServerPlayer player, String skillKey, long now, int ticks) {
        String key = player.getUUID() + "|" + skillKey;
        Long until = ACTIVE_SKILL_COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        ACTIVE_SKILL_COOLDOWNS.put(key, now + Math.max(1, ticks));
        return true;
    }

    private static void damageItemSafely(ItemStack stack, int amount, ServerPlayer player) {
        if (player.getAbilities().instabuild || !stack.isDamageableItem()) return;
        if (getTransmutedLevel(stack, "unending_edge") > 0) {
            if (player.getRandom().nextBoolean()) return;
            int maxSafe = Math.max(0, stack.getMaxDamage() - 2);
            stack.setDamageValue(Math.min(maxSafe, stack.getDamageValue() + amount));
            return;
        }
        int next = stack.getDamageValue() + amount;
        if (next < stack.getMaxDamage()) {
            stack.setDamageValue(next);
        }
    }

    @Nullable
    private static Item mapInfestedBlockToCleanItem(Block b) {
        if (b == Blocks.INFESTED_STONE) return Items.COBBLESTONE;
        if (b == Blocks.INFESTED_COBBLESTONE) return Items.COBBLESTONE;
        if (b == Blocks.INFESTED_DEEPSLATE) return Items.COBBLED_DEEPSLATE;
        if (b == Blocks.INFESTED_STONE_BRICKS || b == Blocks.INFESTED_MOSSY_STONE_BRICKS
            || b == Blocks.INFESTED_CRACKED_STONE_BRICKS || b == Blocks.INFESTED_CHISELED_STONE_BRICKS) {
            return Items.STONE_BRICKS;
        }
        return null;
    }

    @Nullable
    private static Item mapRawFishToCooked(Item raw) {
        if (raw == Items.COD) return Items.COOKED_COD;
        if (raw == Items.SALMON) return Items.COOKED_SALMON;
        if (raw == Items.KELP) return Items.DRIED_KELP;
        return null;
    }

    @Nullable
    public static Item mapItemAutoSmeltFallback(Item rawItem, @Nullable Block brokenBlock) {
        if (rawItem == Items.RAW_IRON || rawItem == Items.IRON_ORE || rawItem == Items.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
        if (rawItem == Items.RAW_GOLD || rawItem == Items.GOLD_ORE || rawItem == Items.DEEPSLATE_GOLD_ORE) return Items.GOLD_INGOT;
        if (rawItem == Items.RAW_COPPER || rawItem == Items.COPPER_ORE || rawItem == Items.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
        if (rawItem == Items.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        if (rawItem == Items.COBBLESTONE) return Items.STONE;
        if (rawItem == Items.STONE) return Items.SMOOTH_STONE;
        if (rawItem == Items.COBBLED_DEEPSLATE) return Items.DEEPSLATE;
        if (rawItem == Items.SAND || rawItem == Items.RED_SAND) return Items.GLASS;
        if (rawItem == Items.CLAY_BALL) return Items.BRICK;
        if (rawItem == Items.CLAY) return Items.TERRACOTTA;
        if (rawItem == Items.NETHERRACK) return Items.NETHER_BRICK;
        if (rawItem == Items.WET_SPONGE) return Items.SPONGE;
        if (rawItem == Items.CACTUS) return Items.GREEN_DYE;
        if (rawItem == Items.KELP) return Items.DRIED_KELP;
        if (brokenBlock != null) {
            if (brokenBlock == Blocks.IRON_ORE || brokenBlock == Blocks.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
            if (brokenBlock == Blocks.GOLD_ORE || brokenBlock == Blocks.DEEPSLATE_GOLD_ORE) return Items.GOLD_INGOT;
            if (brokenBlock == Blocks.COPPER_ORE || brokenBlock == Blocks.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
            if (brokenBlock == Blocks.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        }
        return null;
    }

    private static boolean isFarmAnimal(EntityType<?> type) {
        return type == EntityType.COW || type == EntityType.SHEEP || type == EntityType.PIG
            || type == EntityType.CHICKEN || type == EntityType.GOAT || type == EntityType.RABBIT
            || type == EntityType.MOOSHROOM;
    }

    private static Item pickCommonMobDrop(EntityType<?> type, RandomSource rng) {
        if (type == EntityType.SKELETON || type == EntityType.STRAY) return rng.nextBoolean() ? Items.BONE : Items.ARROW;
        if (type == EntityType.CREEPER) return Items.GUNPOWDER;
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) return Items.STRING;
        if (type == EntityType.ENDERMAN) return Items.ENDER_PEARL;
        if (type == EntityType.BLAZE) return Items.BLAZE_ROD;
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return rng.nextFloat() < 0.25F ? Items.IRON_INGOT : Items.ROTTEN_FLESH;
        }
        return Items.GOLD_NUGGET;
    }

    private static List<LivingEntity> nearbyHostiles(
        ServerLevel level,
        ServerPlayer player,
        Vec3 center,
        double radius,
        @Nullable LivingEntity exclude
    ) {
        AABB box = AABB.ofSize(center, radius * 2.0D, 3.5D, radius * 2.0D);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            e -> e != player && e != exclude && e.isAlive() && !e.isAlliedTo(player)
        );
    }

    private static void grantEffect(LivingEntity entity, String effectPath, int durationTicks, int amplifier) {
        BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(effectPath))
            .ifPresent(holder -> entity.addEffect(new MobEffectInstance(holder, Math.max(10, durationTicks), amplifier, false, true)));
    }

    private static void spawnItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ent = new ItemEntity(level, pos.x, pos.y + 0.35D, pos.z, stack);
        ent.setDefaultPickUpDelay();
        level.addFreshEntity(ent);
    }
}
