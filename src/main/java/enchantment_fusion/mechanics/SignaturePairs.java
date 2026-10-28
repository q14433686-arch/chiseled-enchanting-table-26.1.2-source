package enchantment_fusion.mechanics;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Handcrafted directional Signature Resonance registry ({@code 150} unique ordered entries, {@code 0} symmetric aliases).
 * Every ordered pair {@code [Primary A ▷ Secondary B]} has a distinct identity from {@code [Secondary B ▷ Primary A]} (BAL-06).
 * Same-ID signatures equipped across multiple slots deduplicate to the strongest copy (BAL-02).
 */
public final class SignaturePairs {
    public record SignatureEntry(
        String id,
        String primaryId,
        String secondaryId
    ) {
        public String titleKey() {
            return "signature.enchantment_fusion." + id + ".title";
        }

        public String descKey() {
            return "signature.enchantment_fusion." + id + ".desc";
        }
    }

    private record EquippedSignature(
        String sigId,
        FusedPair pair,
        EquipmentSlot slot,
        ItemStack stack
    ) {
        int totalLevel() {
            return pair.primaryLevel() + pair.secondaryLevel();
        }
    }

    private static final EquipmentSlot[] ALL_EQUIP_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private static final Map<String, SignatureEntry> DIRECT_MAP = new LinkedHashMap<>();
    private static final List<SignatureEntry> UNIQUE_SIGNATURES = new ArrayList<>();

    private static final Map<UUID, Integer> NEUROTOXIN_STACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> RIPTIDE_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIPTIDE_ANCHOR_EXPIRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_SAFE_GROUND = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> RECENT_DAMAGE_TAKEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECENT_DAMAGE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GLASS_SHIELD_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FORTUNE_GRACE_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LUCKY_GOLDEN_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PORCELAIN_BASTION_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOYAL_LIFELINE_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SOUL_FIRE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SKYFIRE_BOLT_TICKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IN_SIGNATURE_MINING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    static {
        // === Batch 1: Damage Trio (Sharpness x Smite x Bane of Arthropods) ===
        reg("sanctified_execute", "minecraft:sharpness", "minecraft:smite");
        reg("radiant_shockwave", "minecraft:smite", "minecraft:sharpness");
        reg("hamstring_rend", "minecraft:sharpness", "minecraft:bane_of_arthropods");
        reg("neurotoxin_bleed", "minecraft:bane_of_arthropods", "minecraft:sharpness");
        reg("blight_halo", "minecraft:smite", "minecraft:bane_of_arthropods");
        reg("soul_cocoon", "minecraft:bane_of_arthropods", "minecraft:smite");

        // === Batch 2: Melee Tactics (Fire Aspect x Knockback x Sweeping Edge x Looting) ===
        reg("blast_flare", "minecraft:fire_aspect", "minecraft:knockback");
        reg("blazing_furrow", "minecraft:knockback", "minecraft:fire_aspect");
        reg("wildfire_arc", "minecraft:fire_aspect", "minecraft:sweeping_edge");
        reg("soulburn_detonate", "minecraft:sweeping_edge", "minecraft:fire_aspect");
        reg("cyclone_repulse", "minecraft:knockback", "minecraft:sweeping_edge");
        reg("gale_vortex", "minecraft:sweeping_edge", "minecraft:knockback");
        reg("searing_feast", "minecraft:looting", "minecraft:fire_aspect");
        reg("alchemic_pyre", "minecraft:fire_aspect", "minecraft:looting");
        reg("concussive_plunder", "minecraft:knockback", "minecraft:looting");
        reg("trophy_shockwave", "minecraft:looting", "minecraft:knockback");
        reg("legion_plunder", "minecraft:looting", "minecraft:sweeping_edge");
        reg("reaping_gale", "minecraft:sweeping_edge", "minecraft:looting");

        // === Batch 3: Tool Core (Silk Touch x Fortune x Efficiency x Mending x Unbreaking) ===
        reg("raw_ore_geode", "minecraft:silk_touch", "minecraft:fortune");
        reg("smelt_fortune", "minecraft:fortune", "minecraft:silk_touch");
        reg("pulse_prospect", "minecraft:efficiency", "minecraft:fortune");
        reg("gold_rush_haste", "minecraft:fortune", "minecraft:efficiency");
        reg("vein_peel", "minecraft:efficiency", "minecraft:silk_touch");
        reg("velvet_shear", "minecraft:silk_touch", "minecraft:efficiency");
        reg("overflow_plating", "minecraft:mending", "minecraft:unbreaking");
        reg("self_healing_lattice", "minecraft:unbreaking", "minecraft:mending");

        // === Batch 4: Bow & Crossbow (Infinity x Mending, Multishot x Piercing, Flame x Punch, Quick Charge) ===
        reg("starlight_quiver", "minecraft:infinity", "minecraft:mending");
        reg("spectral_condense", "minecraft:mending", "minecraft:infinity");
        reg("converging_volley", "minecraft:multishot", "minecraft:piercing");
        reg("fission_warhead", "minecraft:piercing", "minecraft:multishot");
        reg("explosive_flare_arrow", "minecraft:flame", "minecraft:punch");
        reg("meteor_streak", "minecraft:punch", "minecraft:flame");
        reg("swift_draw_burst", "minecraft:quick_charge", "minecraft:power");
        reg("overdraw_snipe", "minecraft:power", "minecraft:quick_charge");

        // === Batch 5: Trident & Mace (Loyalty x Riptide x Channeling, Wind Burst x Density x Breach) ===
        reg("hydro_flying_sword", "minecraft:loyalty", "minecraft:riptide");
        reg("mirage_snapback", "minecraft:riptide", "minecraft:loyalty");
        reg("clear_sky_thunder", "minecraft:channeling", "minecraft:riptide");
        reg("thunder_avatar", "minecraft:riptide", "minecraft:channeling");
        reg("meteor_crush", "minecraft:wind_burst", "minecraft:density");
        reg("crushing_updraft", "minecraft:density", "minecraft:wind_burst");
        reg("armor_shatter", "minecraft:density", "minecraft:breach");
        reg("ground_gust", "minecraft:breach", "minecraft:wind_burst");

        // === Batch 6: Four Protections & Elemental Thorns ===
        reg("furnace_aegis", "minecraft:protection", "minecraft:fire_protection");
        reg("searing_counter", "minecraft:fire_protection", "minecraft:protection");
        reg("reactive_plate", "minecraft:protection", "minecraft:blast_protection");
        reg("kinetic_payback", "minecraft:blast_protection", "minecraft:protection");
        reg("deflection_field", "minecraft:protection", "minecraft:projectile_protection");
        reg("arrow_stride", "minecraft:projectile_protection", "minecraft:protection");
        reg("pyro_thorns", "minecraft:thorns", "minecraft:fire_protection");
        reg("cinder_backlash", "minecraft:fire_protection", "minecraft:thorns");
        reg("blast_thorns", "minecraft:thorns", "minecraft:blast_protection");
        reg("concussion_shrapnel", "minecraft:blast_protection", "minecraft:thorns");
        reg("sniper_thorns", "minecraft:thorns", "minecraft:projectile_protection");
        reg("ricochet_ward", "minecraft:projectile_protection", "minecraft:thorns");

        // === Batch 7: Boots, Leggings & Helmet Mobility (incl. Frost x Fire Protection magma basalt!) ===
        reg("tide_breaker_dive", "minecraft:depth_strider", "minecraft:frost_walker");
        reg("permafrost_wake", "minecraft:frost_walker", "minecraft:depth_strider");
        reg("magma_basalt_step", "minecraft:frost_walker", "minecraft:fire_protection");
        reg("glacial_hearth", "minecraft:fire_protection", "minecraft:frost_walker");
        reg("frost_meteor_fall", "minecraft:feather_falling", "minecraft:frost_walker");
        reg("feather_rime_glide", "minecraft:frost_walker", "minecraft:feather_falling");
        reg("soul_stride_landing", "minecraft:feather_falling", "minecraft:soul_speed");
        reg("wraith_plume", "minecraft:soul_speed", "minecraft:feather_falling");
        reg("phantom_assassin_step", "minecraft:soul_speed", "minecraft:swift_sneak");
        reg("grave_stalk_rush", "minecraft:swift_sneak", "minecraft:soul_speed");
        reg("abyssal_sight", "minecraft:respiration", "minecraft:aqua_affinity");
        reg("ocean_breath_mine", "minecraft:aqua_affinity", "minecraft:respiration");

        // === Batch 8: Fishing Rod ===
        reg("tidal_double_catch", "minecraft:luck_of_the_sea", "minecraft:lure");
        reg("raging_hook", "minecraft:lure", "minecraft:luck_of_the_sea");

        // === Batch 9 & 10: Signature Cross-Domain Weapons / Tools / Bows / Armor ===
        reg("thunder_iaido", "minecraft:sharpness", "minecraft:channeling");
        reg("arc_lightning_cleave", "minecraft:sweeping_edge", "minecraft:channeling");
        reg("phantom_triple_slash", "minecraft:sharpness", "minecraft:multishot");
        reg("flying_sword_qi", "minecraft:sharpness", "minecraft:loyalty");
        reg("midas_cleave", "minecraft:sharpness", "minecraft:fortune");
        reg("bounty_jackpot", "minecraft:looting", "minecraft:fortune");
        reg("rising_dragon_leap", "minecraft:sharpness", "minecraft:feather_falling");
        reg("blood_vengeance", "minecraft:sharpness", "minecraft:thorns");
        reg("molten_core_pick", "minecraft:efficiency", "minecraft:fire_aspect");
        reg("kiln_overdrive", "minecraft:efficiency", "minecraft:flame");
        reg("magnetic_pick", "minecraft:efficiency", "minecraft:loyalty");
        reg("rock_splitter_pick", "minecraft:efficiency", "minecraft:sweeping_edge");
        reg("controlled_demolition", "minecraft:efficiency", "minecraft:blast_protection");
        reg("thunder_bow", "minecraft:power", "minecraft:channeling");
        reg("winter_frost_arrow", "minecraft:power", "minecraft:frost_walker");
        reg("gale_guardian_armor", "minecraft:protection", "minecraft:wind_burst");
        reg("zephyr_cushion", "minecraft:feather_falling", "minecraft:wind_burst");

        // === Batch 11: Elemental Reactions (User-Specified Steam True Damage & 3x3x3 Frostfire Cage!) ===
        reg("steam_scald", "minecraft:fire_aspect", "minecraft:frost_walker");
        reg("scald_plume_arrow", "minecraft:flame", "minecraft:frost_walker");
        reg("frostfire_cage", "minecraft:frost_walker", "minecraft:fire_aspect");
        reg("permafrost_brand_arrow", "minecraft:frost_walker", "minecraft:flame");
        reg("superconduct_shatter", "minecraft:channeling", "minecraft:frost_walker");
        reg("aurora_thunder_prison", "minecraft:frost_walker", "minecraft:channeling");
        reg("plasma_overload", "minecraft:channeling", "minecraft:fire_aspect");
        reg("thunder_pyre_lance", "minecraft:channeling", "minecraft:flame");
        reg("skyfire_lightning", "minecraft:fire_aspect", "minecraft:channeling");
        reg("solar_arc_arrow", "minecraft:flame", "minecraft:channeling");
        reg("boiling_blood", "minecraft:respiration", "minecraft:fire_aspect");
        reg("hydrothermal_wake", "minecraft:depth_strider", "minecraft:fire_aspect");
        reg("choking_smoke", "minecraft:fire_aspect", "minecraft:respiration");
        reg("flame_tornado", "minecraft:wind_burst", "minecraft:fire_aspect");
        reg("blizzard_squall", "minecraft:wind_burst", "minecraft:frost_walker");

        // === Batch 12: Concept Cross-Domain Weapons ===
        reg("oxygen_drain_slash", "minecraft:sharpness", "minecraft:respiration");
        reg("pressure_pierce_shot", "minecraft:power", "minecraft:respiration");
        reg("abyssal_full_breath", "minecraft:respiration", "minecraft:sharpness");
        reg("shadow_backstab", "minecraft:swift_sneak", "minecraft:sharpness");
        reg("ambush_longshot", "minecraft:swift_sneak", "minecraft:power");
        reg("vanish_on_kill", "minecraft:sharpness", "minecraft:swift_sneak");
        reg("nether_soul_fire", "minecraft:soul_speed", "minecraft:fire_aspect");
        reg("wraith_rend_step", "minecraft:soul_speed", "minecraft:sharpness");
        reg("soul_steal_haste", "minecraft:sharpness", "minecraft:soul_speed");
        reg("pristine_trophy", "minecraft:silk_touch", "minecraft:sharpness");
        reg("curated_harvest", "minecraft:silk_touch", "minecraft:looting");
        reg("crystal_snare", "minecraft:silk_touch", "minecraft:knockback");

        // === Batch 13: Defense & Utility Concept Crosses ===
        reg("frost_spike_armor", "minecraft:thorns", "minecraft:frost_walker");
        reg("ice_thorn_trail", "minecraft:frost_walker", "minecraft:thorns");
        reg("loyal_lifeline", "minecraft:protection", "minecraft:loyalty");
        reg("homing_plume", "minecraft:feather_falling", "minecraft:loyalty");
        reg("boundless_mining", "minecraft:efficiency", "minecraft:aqua_affinity");
        reg("liquid_cutter", "minecraft:aqua_affinity", "minecraft:efficiency");
        reg("soul_siphon_mend", "minecraft:sharpness", "minecraft:mending");
        reg("vital_siphon_shot", "minecraft:power", "minecraft:mending");
        reg("overflow_edge", "minecraft:mending", "minecraft:sharpness");
        reg("pristine_overcharge", "minecraft:mending", "minecraft:power");

        // === Batch 14 & 15: User-Specified Lucky Golden Body (immune + 2x durability loss!) & Wild Crosses ===
        reg("fortune_grace", "minecraft:protection", "minecraft:fortune");
        reg("lucky_golden_body", "minecraft:fortune", "minecraft:protection");
        reg("triple_excavate", "minecraft:multishot", "minecraft:efficiency");
        reg("rapid_bowstring", "minecraft:efficiency", "minecraft:multishot");
        reg("land_water_dragon", "minecraft:riptide", "minecraft:sharpness");
        reg("maelstrom_vault", "minecraft:riptide", "minecraft:wind_burst");
        reg("drenched_impale", "minecraft:impaling", "minecraft:sharpness");
        reg("harpoon_ballista", "minecraft:impaling", "minecraft:power");
        reg("gale_blade_flurry", "minecraft:quick_charge", "minecraft:sharpness");
        reg("snap_crescent_draw", "minecraft:quick_charge", "minecraft:sweeping_edge");
        reg("shield_piercer", "minecraft:piercing", "minecraft:sharpness");
        reg("perpetual_craft", "minecraft:infinity", "minecraft:efficiency");
        reg("eternal_bowstring", "minecraft:infinity", "minecraft:unbreaking");
        reg("undying_hellfire", "minecraft:infinity", "minecraft:fire_aspect");
        reg("sunfire_barrage", "minecraft:infinity", "minecraft:flame");
        reg("shakedown_thorns", "minecraft:looting", "minecraft:thorns");
        reg("extortion_carapace", "minecraft:thorns", "minecraft:looting");
        reg("glass_holy_shield", "minecraft:silk_touch", "minecraft:protection");
        reg("porcelain_bastion", "minecraft:protection", "minecraft:silk_touch");

        // === Batch 16: 26.1.2 Spear & Lunge (突进) Directional Signatures ===
        reg("gale_vault_thrust", "minecraft:lunge", "minecraft:wind_burst");
        reg("sky_spear_dive", "minecraft:wind_burst", "minecraft:lunge");
        reg("blazing_dragoon", "minecraft:lunge", "minecraft:fire_aspect");
        reg("searing_charge", "minecraft:fire_aspect", "minecraft:lunge");
        reg("tidal_harpoon_rush", "minecraft:lunge", "minecraft:riptide");
        reg("surge_skewer", "minecraft:riptide", "minecraft:lunge");

        // === Wildcard Secondary Rules ([X ▷ lure], [X ▷ binding_curse], [X ▷ vanishing_curse]) ===
        reg("spirit_grapnel", "*", "minecraft:lure");
        reg("blood_pact_curse", "*", "minecraft:binding_curse");
        reg("soulburn_elegy_curse", "*", "minecraft:vanishing_curse");
    }

    private SignaturePairs() {}

    private static void reg(String id, String p, String s) {
        SignatureEntry entry = new SignatureEntry(id, p, s);
        if (!"*".equals(p)) {
            DIRECT_MAP.put(p + ">" + s, entry);
        }
        UNIQUE_SIGNATURES.add(entry);
    }

    public static List<SignatureEntry> allUniqueSignatures() {
        return Collections.unmodifiableList(UNIQUE_SIGNATURES);
    }

    public static List<SignatureEntry> allOrderedEntries() {
        return Collections.unmodifiableList(UNIQUE_SIGNATURES);
    }

    public static Optional<SignatureEntry> resolve(String primaryId, String secondaryId) {
        if (primaryId == null || secondaryId == null || primaryId.equals(secondaryId)) {
            return Optional.empty();
        }
        SignatureEntry direct = DIRECT_MAP.get(primaryId + ">" + secondaryId);
        if (direct != null) {
            return Optional.of(direct);
        }
        // Strictly secondary-only wildcard match so [A ▷ curse] != [curse ▷ A] (BAL-06)!
        if ("minecraft:binding_curse".equals(secondaryId)) {
            return Optional.of(new SignatureEntry("blood_pact_curse", primaryId, secondaryId));
        }
        if ("minecraft:vanishing_curse".equals(secondaryId)) {
            return Optional.of(new SignatureEntry("soulburn_elegy_curse", primaryId, secondaryId));
        }
        if ("minecraft:lure".equals(secondaryId)) {
            return Optional.of(new SignatureEntry("spirit_grapnel", primaryId, secondaryId));
        }
        return Optional.empty();
    }

    public static Optional<SignatureEntry> resolve(FusedPair pair) {
        if (pair == null) {
            return Optional.empty();
        }
        return resolve(pair.primaryId(), pair.secondaryId());
    }

    public static boolean stackHasSignature(ItemStack stack, String signatureId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        FusedEnchantmentList list = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sig = resolve(pair);
            if (sig.isPresent() && sig.get().id().equals(signatureId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasEquippedSignature(ServerPlayer player, String signatureId) {
        for (EquipmentSlot slot : ALL_EQUIP_SLOTS) {
            if (stackHasSignature(player.getItemBySlot(slot), signatureId)) {
                return true;
            }
        }
        return false;
    }

    /** Deduplicates equipped signatures across all 6 equipment slots so duplicate copies never stack (BAL-02). */
    private static Map<String, EquippedSignature> collectUniqueEquippedSignatures(ServerPlayer player) {
        Map<String, EquippedSignature> best = new LinkedHashMap<>();
        for (EquipmentSlot slot : ALL_EQUIP_SLOTS) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty()) continue;
            FusedEnchantmentList list = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair pair : list.pairs()) {
                Optional<SignatureEntry> sigOpt = resolve(pair);
                if (sigOpt.isEmpty()) continue;
                String sigId = sigOpt.get().id();
                EquippedSignature cand = new EquippedSignature(sigId, pair, slot, gear);
                EquippedSignature prev = best.get(sigId);
                if (prev == null || cand.totalLevel() > prev.totalLevel()) {
                    best.put(sigId, cand);
                }
            }
        }
        return best;
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) return;
        RIPTIDE_ANCHORS.remove(playerId);
        RIPTIDE_ANCHOR_EXPIRY.remove(playerId);
        LAST_SAFE_GROUND.remove(playerId);
        RECENT_DAMAGE_TAKEN.remove(playerId);
        RECENT_DAMAGE_UNTIL.remove(playerId);
        GLASS_SHIELD_COOLDOWN.remove(playerId);
        FORTUNE_GRACE_COOLDOWN.remove(playerId);
        LUCKY_GOLDEN_COOLDOWN.remove(playerId);
        PORCELAIN_BASTION_COOLDOWN.remove(playerId);
        LOYAL_LIFELINE_COOLDOWN.remove(playerId);
    }

    public static void clearRuntimeState() {
        NEUROTOXIN_STACKS.clear();
        RIPTIDE_ANCHORS.clear();
        RIPTIDE_ANCHOR_EXPIRY.clear();
        LAST_SAFE_GROUND.clear();
        RECENT_DAMAGE_TAKEN.clear();
        RECENT_DAMAGE_UNTIL.clear();
        GLASS_SHIELD_COOLDOWN.clear();
        FORTUNE_GRACE_COOLDOWN.clear();
        LUCKY_GOLDEN_COOLDOWN.clear();
        PORCELAIN_BASTION_COOLDOWN.clear();
        LOYAL_LIFELINE_COOLDOWN.clear();
        SOUL_FIRE_TICKS.clear();
        SKYFIRE_BOLT_TICKS.clear();
    }

    public static void onFishingReel(
        ServerLevel level,
        ServerPlayer player,
        ItemStack rod,
        @Nullable LivingEntity hookedMob,
        boolean caughtFishOrLoot
    ) {
        FusedEnchantmentList list = rod.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return;
        }
        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = resolve(pair);
            if (sigOpt.isEmpty()) continue;
            String sid = sigOpt.get().id();
            if ("raging_hook".equals(sid) && hookedMob != null) {
                executeSignatureCombat(level, player, rod, pair, sid, hookedMob, hookedMob.position(), true);
            } else if ("tidal_double_catch".equals(sid) && caughtFishOrLoot) {
                executeSignatureCombat(level, player, rod, pair, sid, null, player.position(), false);
            }
        }
    }

    // =========================================================================
    // 1. Pre-Damage Interception (Lucky Golden Body & Glass Holy Shield)
    // =========================================================================

    public static boolean allowIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        if (!(player.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return true;
        }
        // API-04: Never block /kill, void, or creative BYPASSES_INVULNERABILITY damage!
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        long now = level.getGameTime();
        Map<String, EquippedSignature> unique = collectUniqueEquippedSignatures(player);

        // [时运 ▷ 保护] 招财金身：不跨部位叠加，内置 5 秒冷却，调用真实 hurtAndBreak 可损毁（BAL-01/02, CPL-06）
        EquippedSignature lucky = unique.get("lucky_golden_body");
        if (lucky != null && now >= LUCKY_GOLDEN_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
            float procChance = 0.10F + 0.05F * lucky.pair().primaryLevel();
            if (level.getRandom().nextFloat() < procChance) {
                LUCKY_GOLDEN_COOLDOWN.put(player.getUUID(), now + 100L);
                if (lucky.stack().isDamageableItem()) {
                    int extraDurabilityLoss = Math.max(2, Math.round(amount * 2.0F));
                    lucky.stack().hurtAndBreak(extraDurabilityLoss, player, lucky.slot());
                }
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 14, 0.35D, 0.4D, 0.35D, 0.08D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.4F);
                return false;
            }
        }

        // [精准采集 ▷ 保护] 琉璃圣盾：满血时完美抵挡一次伤害（15秒充能）
        EquippedSignature glass = unique.get("glass_holy_shield");
        if (glass != null) {
            long readyAt = GLASS_SHIELD_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            if (player.getHealth() >= player.getMaxHealth() - 0.1F && now >= readyAt) {
                GLASS_SHIELD_COOLDOWN.put(player.getUUID(), now + 300L);
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.4D, 0.5D, 0.4D, 0.06D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.95F, 1.55F);
                return false;
            }
        }

        return true;
    }

    // =========================================================================
    // 2. Lethal Save Interception (Fortune Grace & Loyal Lifeline Void Rescue)
    // =========================================================================

    public static boolean allowPlayerDeath(ServerPlayer player, DamageSource source) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        // Never block /kill or non-void BYPASSES_INVULNERABILITY sources (API-04)
        boolean isVoidFall = source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD);
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isVoidFall) {
            return true;
        }
        long now = level.getGameTime();
        Map<String, EquippedSignature> unique = collectUniqueEquippedSignatures(player);

        // [保护 ▷ 时运] 吉星高照：概率锁 1 滴血幸运免死（60秒冷却，不挡虚空/kill）
        if (!isVoidFall) {
            EquippedSignature grace = unique.get("fortune_grace");
            if (grace != null) {
                long readyAt = FORTUNE_GRACE_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                float saveChance = 0.18F + 0.08F * grace.pair().secondaryLevel();
                if (now >= readyAt && level.getRandom().nextFloat() < saveChance) {
                    FORTUNE_GRACE_COOLDOWN.put(player.getUUID(), now + 1200L);
                    player.setHealth(1.0F);
                    grantEffect(player, "absorption", 100, 1);
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.12D);
                    return false;
                }
            }
        }

        // [保护 ▷ 忠诚] 归元护主：虚空坠落拉回最后安全立足点（30秒冷却）
        EquippedSignature loyal = unique.get("loyal_lifeline");
        if (loyal != null && (isVoidFall || player.getY() < level.getMinY() + 2)) {
            long readyAt = LOYAL_LIFELINE_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            Vec3 safe = LAST_SAFE_GROUND.get(player.getUUID());
            if (safe != null && now >= readyAt) {
                LOYAL_LIFELINE_COOLDOWN.put(player.getUUID(), now + 600L);
                player.setHealth(Math.max(2.0F, player.getHealth()));
                player.fallDistance = 0.0F;
                player.teleportTo(safe.x, safe.y + 0.5D, safe.z);
                if (loyal.stack().isDamageableItem()) {
                    loyal.stack().hurtAndBreak(20, player, loyal.slot());
                }
                level.sendParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1.0D, safe.z, 30, 0.4D, 0.6D, 0.4D, 0.15D);
                return false;
            }
        }

        return true;
    }

    public static void recordIncomingDamage(ServerPlayer player, float damageTaken, long gameTime) {
        if (damageTaken > 0.25F) {
            RECENT_DAMAGE_TAKEN.put(player.getUUID(), damageTaken);
            RECENT_DAMAGE_UNTIL.put(player.getUUID(), gameTime + 65L);
        }
    }

    // =========================================================================
    // 3. Combat / Projectile Execution (Melee + Ranged + Armor Counter)
    // =========================================================================

    public static boolean executeSignatureCombat(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        FusedPair pair,
        String sigId,
        @Nullable LivingEntity target,
        Vec3 pos,
        boolean fullChargeMelee
    ) {
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
        RandomSource rng = level.getRandom();
        int pLv = Math.max(1, pair.primaryLevel());
        int sLv = Math.max(1, pair.secondaryLevel());

        if ("soulburn_elegy_curse".equals(sigId)) {
            if (target != null) {
                target.hurtServer(level, level.damageSources().magic(), 2.5F + 0.6F * pLv);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.8D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.04D);
            }
            return true;
        }
        if ("blood_pact_curse".equals(sigId)) {
            grantEffect(player, "absorption", 120, 0);
            grantEffect(player, "resistance", 60, 0);
            return true;
        }
        if ("spirit_grapnel".equals(sigId)) {
            if (target != null) {
                Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.85D + 0.15D * sLv);
                target.push(pull.x, 0.25D, pull.z);
                level.sendParticles(ParticleTypes.FISHING, pos.x, pos.y + 0.6D, pos.z, 10, 0.2D, 0.2D, 0.2D, 0.05D);
            }
            return true;
        }

        switch (sigId) {
            case "steam_scald", "scald_plume_arrow" -> {
                if (target != null) {
                    float trueSteamDmg = 2.5F + 1.15F * pLv + 1.0F * sLv;
                    target.hurtServer(level, level.damageSources().magic(), trueSteamDmg);
                    target.clearFire();
                    if ("scald_plume_arrow".equals(sigId)) {
                        grantEffect(target, "blindness", 45, 0);
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.7D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.06D);
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.7D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.04D);
                return true;
            }
            case "frostfire_cage" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 60 + 20 * pLv, 1);
                    target.igniteForSeconds(3.0F + 2.0F * sLv);
                    if (rng.nextFloat() < 0.25F) {
                        AberrantRegistry.placeFrostedIceCage3x3x3(level, target.blockPosition(), false);
                        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 1.3F);
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
                return true;
            }
            case "permafrost_brand_arrow" -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 130));
                    target.igniteForSeconds(2.5F + 1.5F * sLv);
                    grantEffect(target, "slowness", 70, 2);
                    if (rng.nextFloat() < 0.40F && player.mayBuild()) {
                        AberrantRegistry.placeFrostedIceSnare(level, target.blockPosition());
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.04D);
                return true;
            }

            // --- Batch 1: Damage Trio ---
            case "sanctified_execute" -> {
                if (target != null) {
                    float armorBypass = Math.min(6.0F, 1.5F + target.getArmorValue() * 0.22F);
                    target.hurtServer(level, level.damageSources().magic(), armorBypass);
                    if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)
                        && target.getHealth() <= target.getMaxHealth() * 0.22F) {
                        target.hurtServer(level, level.damageSources().magic(), target.getHealth() + 10.0F);
                        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.9D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.08D);
                    }
                }
                return true;
            }
            case "radiant_shockwave" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, target)) {
                    if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).is(EntityTypeTags.UNDEAD)) {
                        mob.hurtServer(level, level.damageSources().magic(), 3.5F + 0.7F * pLv);
                    } else {
                        mob.hurtServer(level, level.damageSources().magic(), 1.8F);
                        grantEffect(mob, "weakness", 70, 0);
                        grantEffect(mob, "blindness", 45, 0);
                    }
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.7D, pos.z, 16, 0.5D, 0.25D, 0.5D, 0.08D);
                return true;
            }
            case "hamstring_rend" -> {
                if (target != null) {
                    boolean alreadySlowed = target.getDeltaMovement().horizontalDistanceSqr() < 0.02D;
                    grantEffect(target, "slowness", 80, 2);
                    if (alreadySlowed) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 2.8F + 0.5F * pLv);
                    }
                }
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5D, pos.z, 10, 0.3D, 0.2D, 0.3D, 0.08D);
                return true;
            }
            case "neurotoxin_bleed" -> {
                if (target != null) {
                    grantEffect(target, "poison", 60, 0);
                    int stacks = NEUROTOXIN_STACKS.merge(target.getUUID(), 1, Integer::sum);
                    if (stacks >= 3) {
                        NEUROTOXIN_STACKS.remove(target.getUUID());
                        target.hurtServer(level, level.damageSources().magic(), 5.5F + 0.8F * sLv);
                        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.08D);
                    }
                }
                return true;
            }
            case "blight_halo" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    grantEffect(mob, "poison", 70, 0);
                    grantEffect(mob, "slowness", 70, 1);
                }
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.5D, pos.z, 14, 0.5D, 0.2D, 0.5D, 0.04D);
                return true;
            }
            case "soul_cocoon" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 55, 3);
                    player.heal(1.5F + 0.3F * sLv);
                }
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2D, player.getZ(), 3, 0.25D, 0.2D, 0.25D, 0.02D);
                return true;
            }

            // --- Batch 2: Melee Tactics ---
            case "blast_flare" -> {
                if (target != null) {
                    Vec3 dir = target.position().subtract(player.position()).normalize().scale(0.85D + 0.25D * sLv);
                    target.push(dir.x, 0.35D, dir.z);
                    target.igniteForSeconds(4.0F * pLv);
                    for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.2D, target)) {
                        extra.igniteForSeconds(3.0F);
                    }
                }
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.4D, pos.z, 8, 0.3D, 0.2D, 0.3D, 0.05D);
                return true;
            }
            case "blazing_furrow" -> {
                if (target != null) {
                    Vec3 dir = target.position().subtract(player.position()).normalize();
                    target.push(dir.x * (0.7D + 0.25D * pLv), 0.28D, dir.z * (0.7D + 0.25D * pLv));
                    target.igniteForSeconds(4.0F);
                    if (player.mayBuild() && !player.isShiftKeyDown()) {
                        for (int step = 1; step <= 3; step++) {
                            BlockPos firePos = BlockPos.containing(pos.add(dir.scale(step)));
                            if (level.getBlockState(firePos).isAir() && !level.getBlockState(firePos.below()).isAir()) {
                                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }
                return true;
            }
            case "wildfire_arc" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.5D, null)) {
                    mob.igniteForSeconds(5.0F + 2.0F * pLv);
                    mob.hurtServer(level, level.damageSources().onFire(), 1.5F + 0.5F * sLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 2, 0.3D, 0.1D, 0.3D, 0.0D);
                return true;
            }
            case "soulburn_detonate" -> {
                if (target != null && target.isOnFire()) {
                    int remTicks = target.getRemainingFireTicks();
                    target.clearFire();
                    float burst = Math.min(10.0F, 3.0F + remTicks * 0.04F);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.5D, null)) {
                        mob.hurtServer(level, level.damageSources().magic(), burst);
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.6D, pos.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
                }
                return true;
            }
            case "cyclone_repulse" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.5D, null)) {
                    Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.85D + 0.2D * pLv);
                    mob.push(push.x, 0.32D, push.z);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D, player.getZ(), 3, 0.6D, 0.1D, 0.6D, 0.0D);
                return true;
            }
            case "gale_vortex" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 5.5D, target)) {
                    Vec3 pull = pos.subtract(mob.position()).scale(0.42D);
                    mob.push(pull.x, 0.15D, pull.z);
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5D, pos.z, 12, 0.5D, 0.2D, 0.5D, 0.03D);
                return true;
            }
            case "trophy_shockwave" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.75D + 0.15D * sLv);
                    mob.push(push.x, 0.28D, push.z);
                }
                if (rng.nextFloat() < 0.20F + 0.05F * pLv) {
                    spawnItem(level, pos, new ItemStack(Items.GOLD_NUGGET, 1 + rng.nextInt(2)));
                }
                return true;
            }
            case "reaping_gale" -> {
                if (fullChargeMelee) {
                    int hits = 0;
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, target)) {
                        mob.hurtServer(level, level.damageSources().playerAttack(player), 1.8F + 0.5F * pLv);
                        if (++hits >= 3) break;
                    }
                    grantEffect(player, "luck", 100, 0);
                    if (rng.nextFloat() < 0.28F) {
                        ExperienceOrb.award(level, pos, 1 + sLv);
                    }
                }
                return true;
            }
            case "bounty_jackpot" -> {
                if (rng.nextFloat() < 0.18F + 0.05F * pLv) {
                    Item jackpot = switch (rng.nextInt(3)) {
                        case 0 -> Items.EMERALD;
                        case 1 -> Items.LAPIS_LAZULI;
                        default -> Items.GOLD_NUGGET;
                    };
                    spawnItem(level, pos, new ItemStack(jackpot, 1));
                    ExperienceOrb.award(level, pos, 2);
                }
                return true;
            }
            case "searing_feast", "alchemic_pyre", "concussive_plunder", "legion_plunder", "midas_cleave" -> {
                if (target != null && ("searing_feast".equals(sigId) || "alchemic_pyre".equals(sigId))) {
                    target.igniteForSeconds(5.0F);
                }
                if (rng.nextFloat() < 0.24F) {
                    Item bonus = switch (sigId) {
                        case "alchemic_pyre" -> rng.nextBoolean() ? Items.GOLD_NUGGET : Items.BLAZE_POWDER;
                        case "midas_cleave" -> rng.nextBoolean() ? Items.EMERALD : Items.AMETHYST_SHARD;
                        default -> Items.GOLD_NUGGET;
                    };
                    spawnItem(level, pos, new ItemStack(bonus, 1));
                }
                return true;
            }

            // --- Batch 4 & 5: Ranged / Trident / Mace ---
            case "starlight_quiver", "soul_siphon_mend" -> {
                if (target != null && hostStack.isDamaged() && fullChargeMelee) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 2));
                    level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.05D);
                }
                return true;
            }
            case "vital_siphon_shot" -> {
                if (target != null) {
                    if (hostStack.isDamaged()) {
                        hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 2));
                    } else {
                        player.heal(1.0F + 0.3F * pLv);
                    }
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.1D, player.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                return true;
            }
            case "pristine_overcharge" -> {
                if (target != null) {
                    boolean pristine = !hostStack.isDamageableItem()
                        || hostStack.getDamageValue() <= hostStack.getMaxDamage() / 10;
                    if (pristine) {
                        target.hurtServer(level, level.damageSources().magic(), 2.8F + 0.65F * sLv);
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 0.8D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.08D);
                    }
                }
                return true;
            }
            case "overdraw_snipe" -> {
                if (target != null) {
                    float bonus = 1.6F + 0.45F * pLv + target.getArmorValue() * 0.12F;
                    target.hurtServer(level, level.damageSources().magic(), bonus);
                    grantEffect(player, "speed", 50 + 15 * sLv, 0);
                }
                return true;
            }
            case "spectral_condense" -> {
                if (!hostStack.isDamaged() && rng.nextFloat() < 0.22F) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.SPECTRAL_ARROW, 1));
                }
                return true;
            }
            case "converging_volley" -> {
                if (target != null) {
                    hurtBypassingIframe(level, target, level.damageSources().magic(), 2.8F + 0.5F * sLv);
                }
                return true;
            }
            case "fission_warhead" -> {
                if (target != null) {
                    Vec3 forward = player.getLookAngle().normalize();
                    Vec3 behind = pos.add(forward.scale(2.5D));
                    int count = 0;
                    for (LivingEntity mob : nearbyHostiles(level, player, behind, 4.5D, target)) {
                        mob.hurtServer(level, level.damageSources().magic(), 3.2F + 0.6F * pLv);
                        level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 0.8D, mob.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.1D);
                        if (++count >= 3) break;
                    }
                    level.sendParticles(ParticleTypes.CRIT, behind.x, behind.y + 0.7D, behind.z, 12, 0.45D, 0.2D, 0.45D, 0.15D);
                }
                return true;
            }
            case "explosive_flare_arrow", "plasma_overload", "thunder_pyre_lance" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.igniteForSeconds(4.0F);
                    mob.hurtServer(level, level.damageSources().magic(), "thunder_pyre_lance".equals(sigId) ? 3.6F : 3.2F);
                    Vec3 blast = mob.position().subtract(pos).normalize().scale(0.75D);
                    mob.push(blast.x, 0.38D, blast.z);
                }
                level.sendParticles(
                    "thunder_pyre_lance".equals(sigId) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.5D, pos.z, 10, 0.35D, 0.25D, 0.35D, 0.05D
                );
                return true;
            }
            case "meteor_streak", "sunfire_barrage" -> {
                if (target != null) {
                    target.igniteForSeconds("sunfire_barrage".equals(sigId) ? 8.0F : 6.0F);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 2.8D, target)) {
                        mob.igniteForSeconds(4.0F);
                    }
                }
                return true;
            }
            case "solar_arc_arrow" -> {
                if (target != null) {
                    target.igniteForSeconds(5.0F);
                    target.hurtServer(level, level.damageSources().magic(), 2.2F + 0.6F * pLv);
                    if (level.canSeeSky(target.blockPosition()) && rng.nextFloat() < 0.45F) {
                        AberrantRegistry.spawnLightning(level, target.position());
                    } else {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 16, 0.3D, 0.4D, 0.3D, 0.1D);
                    }
                }
                return true;
            }
            case "hydro_flying_sword", "flying_sword_qi" -> {
                if (fullChargeMelee) {
                    Vec3 look = player.getLookAngle().normalize();
                    for (int step = 2; step <= 6; step++) {
                        Vec3 p = player.getEyePosition().add(look.scale(step));
                        level.sendParticles(
                            "hydro_flying_sword".equals(sigId) ? ParticleTypes.SPLASH : ParticleTypes.SWEEP_ATTACK,
                            p.x, p.y, p.z, 3, 0.15D, 0.15D, 0.15D, 0.02D
                        );
                        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.8D, 1.8D, 1.8D), e -> e != player && e.isAlive())) {
                            mob.hurtServer(level, level.damageSources().playerAttack(player), 3.0F + 0.6F * pLv);
                        }
                    }
                }
                return true;
            }
            case "mirage_snapback" -> {
                long now = level.getGameTime();
                Long exp = RIPTIDE_ANCHOR_EXPIRY.get(player.getUUID());
                Vec3 anchor = RIPTIDE_ANCHORS.get(player.getUUID());
                if (anchor != null && exp != null && now <= exp && player.isShiftKeyDown()) {
                    RIPTIDE_ANCHORS.remove(player.getUUID());
                    RIPTIDE_ANCHOR_EXPIRY.remove(player.getUUID());
                    player.teleportTo(anchor.x, anchor.y, anchor.z);
                    level.sendParticles(ParticleTypes.SPLASH, anchor.x, anchor.y + 0.8D, anchor.z, 20, 0.3D, 0.4D, 0.3D, 0.08D);
                } else {
                    RIPTIDE_ANCHORS.put(player.getUUID(), player.position());
                    RIPTIDE_ANCHOR_EXPIRY.put(player.getUUID(), now + 60L);
                }
                return true;
            }
            case "clear_sky_thunder", "thunder_iaido", "thunder_bow" -> {
                if (fullChargeMelee && target != null && level.canSeeSky(target.blockPosition())) {
                    AberrantRegistry.spawnLightning(level, target.position());
                } else if (fullChargeMelee && target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 3.5F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 16, 0.3D, 0.4D, 0.3D, 0.1D);
                }
                return true;
            }
            case "arc_lightning_cleave" -> {
                if (fullChargeMelee) {
                    float mult = level.isRaining() ? 1.35F : 1.0F;
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                        mob.hurtServer(level, level.damageSources().magic(), (2.4F + 0.55F * pLv) * mult);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + 0.8D, mob.getZ(), 10, 0.25D, 0.3D, 0.25D, 0.08D);
                    }
                }
                return true;
            }
            case "thunder_avatar", "aurora_thunder_prison" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F + 0.5F * pLv);
                    grantEffect(mob, "slowness", 60, 2);
                    if ("aurora_thunder_prison".equals(sigId) && rng.nextFloat() < 0.35F && player.mayBuild()) {
                        AberrantRegistry.placeFrostedIceSnare(level, mob.blockPosition());
                    }
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 22, 0.6D, 0.3D, 0.6D, 0.1D);
                return true;
            }
            case "superconduct_shatter", "armor_shatter" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    float bonus = 3.0F + mob.getArmorValue() * 0.3F;
                    mob.hurtServer(level, level.damageSources().magic(), bonus);
                    grantEffect(mob, "weakness", 80, 1);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.1D);
                return true;
            }
            case "meteor_crush" -> {
                float slam = Math.min(14.0F, 3.0F + (float) player.fallDistance * 1.1F);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.5D, null)) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), slam);
                }
                return true;
            }
            case "crushing_updraft" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 65, 2);
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.4F + 0.55F * pLv);
                }
                player.fallDistance = 0.0F;
                player.push(0.0D, 0.38D + 0.08D * sLv, 0.0D);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 14, 0.4D, 0.2D, 0.4D, 0.06D);
                return true;
            }
            case "ground_gust", "gale_guardian_armor", "zephyr_cushion" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                    mob.push(away.x, 0.42D, away.z);
                }
                if ("gale_guardian_armor".equals(sigId) || "zephyr_cushion".equals(sigId)) {
                    player.fallDistance = 0.0F;
                    player.push(0.0D, "zephyr_cushion".equals(sigId) ? 0.35D : 0.45D, 0.0D);
                    player.hurtMarked = true;
                    if ("zephyr_cushion".equals(sigId)) {
                        grantEffect(player, "slow_falling", 55, 0);
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5D, pos.z, 12, 0.4D, 0.2D, 0.4D, 0.06D);
                return true;
            }

            // --- Batch 6: Armor Counters ---
            case "searing_counter", "pyro_thorns" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.igniteForSeconds(4.0F);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.55D);
                    mob.push(away.x, 0.2D, away.z);
                }
                return true;
            }
            case "cinder_backlash" -> {
                player.clearFire();
                grantEffect(player, "fire_resistance", 80, 0);
                if (target != null && target != player) {
                    target.igniteForSeconds(3.0F + pLv);
                    target.hurtServer(level, level.damageSources().thorns(player), 2.0F + 0.4F * sLv);
                }
                return true;
            }
            case "blast_thorns", "kinetic_payback" -> {
                grantEffect(player, "strength", 80, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.hurtServer(level, level.damageSources().thorns(player), 2.5F);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.75D);
                    mob.push(away.x, 0.3D, away.z);
                }
                return true;
            }
            case "concussion_shrapnel" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.45F * pLv);
                    grantEffect(mob, "weakness", 70, 0);
                }
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.9D, player.getZ(), 12, 0.4D, 0.3D, 0.4D, 0.1D);
                return true;
            }
            case "deflection_field", "sniper_thorns" -> {
                if (target != null && target != player) {
                    target.hurtServer(level, level.damageSources().thorns(player), 3.0F + 0.5F * pLv);
                }
                return true;
            }
            case "ricochet_ward" -> {
                grantEffect(player, "resistance", 60, 0);
                if (target != null && target != player) {
                    grantEffect(target, "slowness", 60, 1);
                    target.hurtServer(level, level.damageSources().thorns(player), 2.0F + 0.4F * sLv);
                }
                return true;
            }
            case "porcelain_bastion" -> {
                long now = level.getGameTime();
                if (player.getHealth() >= player.getMaxHealth() * 0.5F
                    && now >= PORCELAIN_BASTION_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
                    PORCELAIN_BASTION_COOLDOWN.put(player.getUUID(), now + 240L);
                    grantEffect(player, "absorption", 100, 0);
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.04D);
                }
                return true;
            }
            case "arrow_stride" -> {
                grantEffect(player, "speed", 70, 1);
                return true;
            }
            case "shakedown_thorns" -> {
                if (target != null && rng.nextFloat() < 0.22F) {
                    spawnItem(level, player.position(), new ItemStack(rng.nextBoolean() ? Items.GOLD_NUGGET : Items.EMERALD, 1));
                }
                return true;
            }
            case "extortion_carapace" -> {
                if (target != null && target != player) {
                    target.hurtServer(level, level.damageSources().thorns(player), 1.8F + 0.5F * pLv);
                    grantEffect(target, "weakness", 60, 0);
                    if (rng.nextFloat() < 0.25F) {
                        ExperienceOrb.award(level, player.position(), 1 + sLv);
                    }
                }
                return true;
            }
            case "frost_spike_armor" -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 140));
                    grantEffect(target, "slowness", 70, 2);
                }
                return true;
            }

            // --- Batch 9, 11, 12, 13, 14, 15 Cross-Domain Combat ---
            case "phantom_triple_slash" -> {
                if (fullChargeMelee && target != null) {
                    hurtBypassingIframe(level, target, level.damageSources().magic(), 1.8F + 0.35F * pLv);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.9D, pos.z, 2, 0.25D, 0.2D, 0.25D, 0.0D);
                }
                return true;
            }
            case "rising_dragon_leap" -> {
                if (!player.onGround() && target != null) {
                    player.fallDistance = 0.0F;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.6F + 0.4F * sLv);
                    target.push(0.0D, 0.42D, 0.0D);
                }
                return true;
            }
            case "blood_vengeance" -> {
                Long until = RECENT_DAMAGE_UNTIL.get(player.getUUID());
                Float recent = RECENT_DAMAGE_TAKEN.get(player.getUUID());
                if (target != null && until != null && recent != null && level.getGameTime() <= until) {
                    RECENT_DAMAGE_UNTIL.remove(player.getUUID());
                    target.hurtServer(level, level.damageSources().magic(), Math.min(12.0F, recent * 0.85F + 2.0F));
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x, pos.y + 1.0D, pos.z, 5, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                return true;
            }
            case "winter_frost_arrow" -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 140));
                    grantEffect(target, "slowness", 80, 2);
                    if (rng.nextFloat() < 0.35F && player.mayBuild()) {
                        AberrantRegistry.placeFrostedIceSnare(level, target.blockPosition());
                    }
                }
                return true;
            }
            case "skyfire_lightning" -> {
                if (target != null) {
                    target.igniteForSeconds(6.0F);
                    SKYFIRE_BOLT_TICKS.put(target.getUUID(), 80);
                }
                return true;
            }
            case "boiling_blood" -> {
                if (target != null) {
                    target.igniteForSeconds(4.0F);
                    target.hurtServer(level, level.damageSources().drown(), 2.8F + 0.5F * pLv);
                }
                level.sendParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y + 0.7D, pos.z, 14, 0.3D, 0.3D, 0.3D, 0.05D);
                return true;
            }
            case "hydrothermal_wake" -> {
                if (target != null) {
                    target.igniteForSeconds(3.0F + sLv);
                    float bonus = (player.isInWaterOrRain() || target.isInWaterOrRain()) ? (3.2F + 0.6F * pLv) : 1.8F;
                    target.hurtServer(level, level.damageSources().magic(), bonus);
                }
                if (player.isInWaterOrRain()) {
                    grantEffect(player, "dolphins_grace", 60, 0);
                }
                level.sendParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y + 0.6D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
                return true;
            }
            case "choking_smoke" -> {
                if (target != null) {
                    target.igniteForSeconds(4.0F);
                    target.setAirSupply(Math.max(-20, target.getAirSupply() - 90));
                    grantEffect(target, "blindness", 60, 0);
                    target.hurtServer(level, level.damageSources().drown(), 2.2F);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.02D);
                return true;
            }
            case "flame_tornado" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    mob.igniteForSeconds(5.0F);
                    mob.push(0.0D, 0.58D, 0.0D);
                }
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 18, 0.5D, 0.5D, 0.5D, 0.06D);
                return true;
            }
            case "blizzard_squall" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.5D, null)) {
                    mob.setTicksFrozen(Math.min(300, mob.getTicksFrozen() + 160));
                    grantEffect(mob, "slowness", 80, 2);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 22, 0.7D, 0.4D, 0.7D, 0.05D);
                return true;
            }
            case "oxygen_drain_slash", "pressure_pierce_shot" -> {
                if (target != null) {
                    int drain = "pressure_pierce_shot".equals(sigId) ? 130 : 110;
                    int nextAir = target.getAirSupply() - drain;
                    target.setAirSupply(Math.max(-20, nextAir));
                    float bonus = nextAir <= 0 ? (3.8F + 0.5F * pLv) : (1.6F + 0.35F * sLv);
                    target.hurtServer(level, level.damageSources().drown(), bonus);
                }
                level.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y + 0.8D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.05D);
                return true;
            }
            case "abyssal_full_breath" -> {
                if (target != null && player.getAirSupply() >= player.getMaxAirSupply() - 10) {
                    target.hurtServer(level, level.damageSources().magic(), 2.6F + 0.4F * sLv);
                }
                player.setAirSupply(player.getMaxAirSupply());
                return true;
            }
            case "shadow_backstab", "ambush_longshot" -> {
                if (target != null) {
                    double dot = player.getLookAngle().normalize().dot(target.getLookAngle().normalize());
                    if (player.isShiftKeyDown() || dot > 0.35D) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 4.6F + 1.0F * sLv);
                        if ("ambush_longshot".equals(sigId)) {
                            grantEffect(target, "slowness", 60, 1);
                        }
                        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.8D, pos.z, 16, 0.25D, 0.25D, 0.25D, 0.1D);
                    }
                }
                return true;
            }
            case "vanish_on_kill" -> {
                if (target != null && (!target.isAlive() || target.getHealth() <= 4.0F)) {
                    grantEffect(player, "invisibility", 65, 0);
                    grantEffect(player, "speed", 65, 1);
                }
                return true;
            }
            case "nether_soul_fire" -> {
                if (target != null) {
                    SOUL_FIRE_TICKS.put(target.getUUID(), 100);
                    target.hurtServer(level, level.damageSources().magic(), 2.8F + 0.4F * pLv);
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.7D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.03D);
                return true;
            }
            case "soul_steal_haste" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 60, 1);
                    grantEffect(player, "speed", 70, 1);
                }
                return true;
            }
            case "wraith_rend_step" -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 2.4F + 0.5F * sLv);
                }
                grantEffect(player, "speed", 60, 0);
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.5D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.03D);
                return true;
            }
            case "crystal_snare" -> {
                if (target != null && rng.nextFloat() < 0.22F && player.mayBuild()) {
                    AberrantRegistry.placeIceCage3x3x3(level, target.blockPosition(), Blocks.GLASS.defaultBlockState(), false);
                }
                return true;
            }
            case "overflow_edge" -> {
                if (!hostStack.isDamaged() && target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 2.5F + 0.5F * sLv);
                }
                return true;
            }
            case "land_water_dragon" -> {
                if (player.isSprinting() || fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.95D);
                    player.push(dash.x, 0.22D, dash.z);
                    player.hurtMarked = true;
                    level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6D, player.getZ(), 18, 0.4D, 0.3D, 0.4D, 0.08D);
                }
                return true;
            }
            case "maelstrom_vault" -> {
                player.fallDistance = 0.0F;
                player.push(0.0D, 0.52D + 0.08D * pLv, 0.0D);
                player.hurtMarked = true;
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, target)) {
                    Vec3 pull = pos.subtract(mob.position()).scale(0.38D);
                    mob.push(pull.x, 0.20D, pull.z);
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.5D, pos.z, 18, 0.45D, 0.3D, 0.45D, 0.08D);
                return true;
            }
            case "drenched_impale", "harpoon_ballista" -> {
                if (target != null) {
                    boolean wet = target.isInWaterOrRain() || player.isInWaterOrRain();
                    float base = "harpoon_ballista".equals(sigId) ? (2.2F + 0.55F * sLv) : (1.5F + 0.4F * pLv);
                    target.hurtServer(level, level.damageSources().magic(), wet ? base * 1.85F : base);
                }
                return true;
            }
            case "shield_piercer" -> {
                if (target != null) {
                    Vec3 look = player.getLookAngle().normalize();
                    for (int step = 1; step <= 4; step++) {
                        Vec3 p = player.getEyePosition().add(look.scale(step));
                        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.5D, 1.5D, 1.5D), e -> e != player && e.isAlive())) {
                            mob.hurtServer(level, level.damageSources().magic(), 2.4F + 0.4F * pLv);
                        }
                    }
                }
                return true;
            }
            case "snap_crescent_draw" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.4D, target)) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 2.0F + 0.45F * sLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 2, 0.35D, 0.1D, 0.35D, 0.0D);
                return true;
            }
            case "perpetual_craft", "eternal_bowstring" -> {
                if (hostStack.isDamaged() && rng.nextFloat() < 0.75F) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                if ("eternal_bowstring".equals(sigId)) {
                    grantEffect(player, "haste", 50, 0);
                }
                return true;
            }
            case "undying_hellfire" -> {
                if (target != null) {
                    target.igniteForSeconds(12.0F + 4.0F * sLv);
                }
                return true;
            }
            case "kiln_overdrive" -> {
                if (target != null) {
                    target.igniteForSeconds(3.0F + sLv);
                }
                grantEffect(player, "haste", 70, 0);
                return true;
            }
            case "controlled_demolition" -> {
                grantEffect(player, "resistance", 60, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.65D);
                    mob.push(away.x, 0.25D, away.z);
                }
                return true;
            }
            case "reactive_plate" -> {
                grantEffect(player, "absorption", 140, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.8D + 0.12D * pLv);
                    mob.push(away.x, 0.30D, away.z);
                    mob.hurtServer(level, level.damageSources().explosion(player, player), 2.0F + 0.5F * sLv);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.8D, player.getZ(), 2, 0.25D, 0.2D, 0.25D, 0.02D);
                return true;
            }
            case "frost_meteor_fall" -> {
                if (player.mayBuild()) {
                    AberrantRegistry.placeFrostedIceSnare(level, player.blockPosition());
                }
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.setTicksFrozen(Math.min(320, mob.getTicksFrozen() + 130));
                    grantEffect(mob, "slowness", 80, 2);
                    mob.hurtServer(level, level.damageSources().freeze(), 3.0F + 0.7F * pLv);
                    if (player.mayBuild()) {
                        AberrantRegistry.placeSingleFrostedIce(level, mob.blockPosition());
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.3D, pos.z, 24, 1.2D, 0.2D, 1.2D, 0.06D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 1.25F);
                return true;
            }
            case "feather_rime_glide" -> {
                player.fallDistance = 0.0F;
                grantEffect(player, "slow_falling", 60, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                    grantEffect(mob, "slowness", 50, 1);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.2D, pos.z, 12, 0.6D, 0.15D, 0.6D, 0.03D);
                return true;
            }
            case "soul_stride_landing" -> {
                grantEffect(player, "speed", 110, 1);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F + 0.6F * sLv);
                    SOUL_FIRE_TICKS.put(mob.getUUID(), 60);
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.25D, pos.z, 22, 1.1D, 0.2D, 1.1D, 0.05D);
                return true;
            }
            case "wraith_plume" -> {
                player.fallDistance = 0.0F;
                grantEffect(player, "jump_boost", 80, 1);
                grantEffect(player, "speed", 80, 0);
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.2D, pos.z, 10, 0.5D, 0.15D, 0.5D, 0.03D);
                return true;
            }
            case "homing_plume" -> {
                player.fallDistance = 0.0F;
                grantEffect(player, "slow_falling", 70, 0);
                AABB box = AABB.ofSize(player.position(), 8.0D, 5.0D, 8.0D);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
                    item.setNoPickUpDelay();
                    item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
                return true;
            }
            case "raging_hook" -> {
                if (target != null) {
                    Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.95D + 0.15D * pLv);
                    target.push(pull.x, 0.32D, pull.z);
                    target.hurtMarked = true;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 3.2F + 0.8F * pLv);
                    grantEffect(target, "slowness", 60, 1);
                    if (rng.nextFloat() < 0.22F + 0.06F * sLv) {
                        Item catchDrop = rng.nextFloat() < 0.20F ? Items.NAUTILUS_SHELL : (rng.nextBoolean() ? Items.COD : Items.SALMON);
                        spawnItem(level, player.position(), new ItemStack(catchDrop, 1));
                    }
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6D, pos.z, 16, 0.35D, 0.35D, 0.35D, 0.08D);
                return true;
            }
            case "tidal_double_catch" -> {
                ExperienceOrb.award(level, player.position(), 2 + sLv);
                if (rng.nextFloat() < 0.35F + 0.10F * pLv) {
                    Item bonusCatch = switch (rng.nextInt(5)) {
                        case 0 -> Items.PRISMARINE_CRYSTALS;
                        case 1 -> Items.LAPIS_LAZULI;
                        case 2 -> Items.SALMON;
                        case 3 -> Items.TROPICAL_FISH;
                        default -> Items.NAUTILUS_SHELL;
                    };
                    spawnItem(level, player.position(), new ItemStack(bonusCatch, 1));
                    level.sendParticles(ParticleTypes.FISHING, player.getX(), player.getY() + 0.9D, player.getZ(), 12, 0.3D, 0.2D, 0.3D, 0.05D);
                }
                return true;
            }
            // --- Batch 16: 26.1.2 Spear & Lunge (突进) Signatures ---
            case "gale_vault_thrust" -> {
                if (fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.65D + 0.16D * pLv);
                    player.push(dash.x, 0.24D, dash.z);
                    player.hurtMarked = true;
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                        mob.hurtServer(level, level.damageSources().playerAttack(player), 2.5F + 0.7F * pLv);
                        Vec3 gust = mob.position().subtract(player.position()).normalize().scale(0.65D);
                        mob.push(gust.x, 0.32D, gust.z);
                    }
                    level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.6D, pos.z, 16, 0.4D, 0.3D, 0.4D, 0.08D);
                }
                return true;
            }
            case "sky_spear_dive" -> {
                if (fullChargeMelee) {
                    player.push(0.0D, 0.62D + 0.10D * pLv, 0.0D);
                    player.hurtMarked = true;
                    grantEffect(player, "slow_falling", 50, 0);
                    if (target != null) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 3.0F + 0.8F * sLv);
                    }
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(), 18, 0.45D, 0.2D, 0.45D, 0.09D);
                }
                return true;
            }
            case "blazing_dragoon" -> {
                if (fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.55D + 0.14D * pLv);
                    player.push(dash.x, 0.15D, dash.z);
                    player.hurtMarked = true;
                    if (target != null) {
                        target.igniteForSeconds(5.0F + 2.0F * sLv);
                        target.hurtServer(level, level.damageSources().onFire(), 2.6F + 0.7F * pLv);
                    }
                    level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.05D);
                }
                return true;
            }
            case "searing_charge" -> {
                if (target != null) {
                    target.igniteForSeconds(4.0F + 2.0F * pLv);
                    grantEffect(player, "speed", 70, 1);
                    Vec3 lunge = player.getLookAngle().normalize().scale(0.45D + 0.12D * sLv);
                    player.push(lunge.x, 0.1D, lunge.z);
                    player.hurtMarked = true;
                }
                return true;
            }
            case "tidal_harpoon_rush" -> {
                double scale = player.isInWaterOrRain() ? (0.95D + 0.20D * pLv) : (0.55D + 0.12D * pLv);
                Vec3 surge = player.getLookAngle().normalize().scale(scale);
                player.push(surge.x, 0.18D, surge.z);
                player.hurtMarked = true;
                if (target != null) {
                    float bonus = player.isInWaterOrRain() ? (4.0F + 0.9F * sLv) : (2.4F + 0.6F * pLv);
                    target.hurtServer(level, level.damageSources().trident(player, player), bonus);
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.08D);
                return true;
            }
            case "surge_skewer" -> {
                Vec3 look = player.getLookAngle().normalize();
                for (int step = 1; step <= 4 + sLv; step++) {
                    Vec3 p = player.getEyePosition().add(look.scale(step));
                    level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 3, 0.15D, 0.15D, 0.15D, 0.02D);
                    for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.6D, 1.6D, 1.6D), e -> e != player && e.isAlive())) {
                        mob.hurtServer(level, level.damageSources().playerAttack(player), 2.6F + 0.7F * pLv);
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // =========================================================================
    // 4. Block Harvest Execution
    // =========================================================================

    public static void onBlockHarvested(
        ServerLevel level,
        ServerPlayer player,
        ItemStack tool,
        BlockPos pos,
        BlockState minedState
    ) {
        if (tool == null || tool.isEmpty()) {
            return;
        }
        FusedEnchantmentList list = tool.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return;
        }
        RandomSource rng = level.getRandom();

        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = resolve(pair);
            if (sigOpt.isEmpty()) continue;
            String sigId = sigOpt.get().id();

            switch (sigId) {
                case "raw_ore_geode" -> {
                    Item bonusOre = bonusRawForOre(minedState.getBlock());
                    if (bonusOre != null && rng.nextFloat() < 0.55F) {
                        int count = 1 + rng.nextInt(Math.max(1, pair.secondaryLevel()));
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(bonusOre, count));
                    }
                }
                case "smelt_fortune" -> {
                    Item ingot = smeltedResultForBlock(minedState.getBlock());
                    if (ingot != null) {
                        int bonus = 1 + rng.nextInt(1 + Math.max(1, pair.primaryLevel()));
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(ingot, bonus));
                        ExperienceOrb.award(level, Vec3.atCenterOf(pos), 2);
                    }
                }
                case "molten_core_pick", "kiln_overdrive" -> {
                    Item smelted = smeltedResultForBlock(minedState.getBlock());
                    if (smelted != null) {
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(smelted, 1));
                        ExperienceOrb.award(level, Vec3.atCenterOf(pos), 1);
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.2D, 0.2D, 0.2D, 0.02D);
                    }
                    if ("kiln_overdrive".equals(sigId)) {
                        grantEffect(player, "haste", 70, 0);
                    }
                }
                case "controlled_demolition" -> {
                    grantEffect(player, "resistance", 60, 0);
                    level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.25D, 0.25D, 0.25D, 0.03D);
                }
                case "velvet_shear" -> {
                    grantEffect(player, "haste", 80, 1);
                    AABB box = AABB.ofSize(Vec3.atCenterOf(pos), 5.0D, 4.0D, 5.0D);
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
                        item.setNoPickUpDelay();
                        item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                    }
                }
                case "magnetic_pick" -> {
                    AABB box = AABB.ofSize(Vec3.atCenterOf(pos), 7.0D, 5.0D, 7.0D);
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
                        item.setNoPickUpDelay();
                        item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                    }
                }
                case "gold_rush_haste", "pulse_prospect" -> {
                    grantEffect(player, "haste", 90, 1);
                }
                case "self_healing_lattice" -> {
                    if (tool.isDamaged() && rng.nextFloat() < 0.35F) {
                        tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                    }
                }
                case "perpetual_craft", "eternal_bowstring", "overflow_plating" -> {
                    if (tool.isDamaged() && rng.nextFloat() < 0.75F) {
                        tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                    }
                }
                case "ocean_breath_mine" -> {
                    player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 45));
                }
                case "rock_splitter_pick", "triple_excavate", "vein_peel" -> {
                    if (player.mayBuild() && !player.isShiftKeyDown() && !Boolean.TRUE.equals(IN_SIGNATURE_MINING.get())) {
                        IN_SIGNATURE_MINING.set(Boolean.TRUE);
                        try {
                            Direction facing = player.getDirection();
                            Direction side = facing.getClockWise();
                            for (int offset : new int[]{-1, 1}) {
                                BlockPos adj = pos.relative(side, offset);
                                BlockState adjState = level.getBlockState(adj);
                                if (adjState.is(minedState.getBlock())
                                    && level.getBlockEntity(adj) == null
                                    && adjState.getDestroySpeed(level, adj) >= 0.0F) {
                                    level.destroyBlock(adj, true, player);
                                }
                            }
                        } finally {
                            IN_SIGNATURE_MINING.set(Boolean.FALSE);
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. Entity Kill Drops
    // =========================================================================

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty()) return;
        FusedEnchantmentList list = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = resolve(pair);
            if (sigOpt.isEmpty()) continue;
            switch (sigOpt.get().id()) {
                case "pristine_trophy" -> {
                    if (level.getRandom().nextFloat() < 0.12F) {
                        EntityType<?> vType = victim.getType();
                        Item skull = null;
                        if (vType == EntityType.ZOMBIE) skull = Items.ZOMBIE_HEAD;
                        else if (vType == EntityType.SKELETON) skull = Items.SKELETON_SKULL;
                        else if (vType == EntityType.WITHER_SKELETON) skull = Items.WITHER_SKELETON_SKULL;
                        else if (vType == EntityType.CREEPER) skull = Items.CREEPER_HEAD;
                        else if (vType == EntityType.PIGLIN) skull = Items.PIGLIN_HEAD;
                        if (skull != null) {
                            spawnItem(level, victim.position(), new ItemStack(skull, 1));
                        }
                    }
                }
                case "curated_harvest" -> {
                    if (level.getRandom().nextFloat() < 0.28F) {
                        Item curio = switch (level.getRandom().nextInt(3)) {
                            case 0 -> Items.LEATHER;
                            case 1 -> Items.BONE;
                            default -> Items.FEATHER;
                        };
                        spawnItem(level, victim.position(), new ItemStack(curio, 1 + level.getRandom().nextInt(2)));
                        ExperienceOrb.award(level, victim.position(), 2);
                    }
                }
                case "vanish_on_kill" -> {
                    grantEffect(killer, "invisibility", 70, 0);
                    grantEffect(killer, "speed", 70, 1);
                }
                case "abyssal_full_breath" -> {
                    killer.setAirSupply(killer.getMaxAirSupply());
                    killer.heal(2.0F);
                }
            }
        }
    }

    // =========================================================================
    // 6. Periodic Passive & World Tick
    // =========================================================================

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        if (player.onGround() && player.getY() > level.getMinY() + 2) {
            LAST_SAFE_GROUND.put(player.getUUID(), player.position());
        }

        if (level.getGameTime() % 10L != 0L) {
            return;
        }

        for (EquippedSignature eq : collectUniqueEquippedSignatures(player).values()) {
            String sigId = eq.sigId();
            ItemStack gear = eq.stack();
            EquipmentSlot slot = eq.slot();

            switch (sigId) {
                case "loyal_lifeline" -> {
                    if (gear.isDamageableItem() && gear.getDamageValue() >= gear.getMaxDamage() - 1 && slot != EquipmentSlot.MAINHAND) {
                        ItemStack rescued = gear.copy();
                        rescued.setDamageValue(Math.max(0, gear.getMaxDamage() - 2));
                        player.setItemSlot(slot, ItemStack.EMPTY);
                        player.getInventory().placeItemBackInInventory(rescued);
                    }
                }
                case "furnace_aegis" -> {
                    if (player.isOnFire() || player.isInLava()) {
                        grantEffect(player, "fire_resistance", 40, 0);
                        grantEffect(player, "regeneration", 40, 0);
                    }
                }
                case "glacial_hearth" -> {
                    player.clearFire();
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.0D, null)) {
                        grantEffect(mob, "slowness", 35, 0);
                        mob.setTicksFrozen(Math.min(200, mob.getTicksFrozen() + 25));
                    }
                }
                case "tide_breaker_dive" -> {
                    if (player.isInWater() && player.isShiftKeyDown()) {
                        grantEffect(player, "dolphins_grace", 40, 0);
                    }
                }
                case "permafrost_wake" -> {
                    player.clearFire();
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 2.8D, null)) {
                        mob.setTicksFrozen(Math.min(300, mob.getTicksFrozen() + 50));
                    }
                }
                case "magma_basalt_step" -> {
                    if (player.mayBuild() && !player.isShiftKeyDown()) {
                        BlockPos feet = player.blockPosition();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos lavaPos = feet.offset(dx, -1, dz);
                                BlockState st = level.getBlockState(lavaPos);
                                if (st.is(Blocks.LAVA) && st.getFluidState().isSource() && level.getBlockState(lavaPos.above()).isAir()) {
                                    level.setBlockAndUpdate(lavaPos, Blocks.BASALT.defaultBlockState());
                                }
                            }
                        }
                    }
                }
                case "ice_thorn_trail" -> {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.5D, null)) {
                        BlockState under = level.getBlockState(mob.blockPosition().below());
                        if (under.is(BlockTags.ICE)) {
                            mob.hurtServer(level, level.damageSources().cactus(), 1.5F);
                        }
                    }
                }
                case "phantom_assassin_step" -> {
                    if (player.isShiftKeyDown()) {
                        grantEffect(player, "speed", 30, 1);
                        grantEffect(player, "invisibility", 30, 0);
                    }
                }
                case "grave_stalk_rush" -> {
                    if (player.isShiftKeyDown() || player.isSprinting()) {
                        grantEffect(player, "speed", 30, 1);
                    }
                }
                case "abyssal_sight" -> {
                    if (player.isInWater()) {
                        grantEffect(player, "night_vision", 240, 0);
                        grantEffect(player, "water_breathing", 30, 0);
                    }
                }
                case "gale_blade_flurry", "rapid_bowstring", "swift_draw_burst" -> {
                    if (slot == EquipmentSlot.MAINHAND) {
                        grantEffect(player, "haste", 30, 1);
                    }
                }
                case "blood_pact_curse" -> {
                    grantEffect(player, "health_boost", 60, 0);
                }
            }
        }
    }

    public static void tickWorldDots(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        if (!SOUL_FIRE_TICKS.isEmpty()) {
            for (UUID entityId : new ArrayList<>(SOUL_FIRE_TICKS.keySet())) {
                Integer remaining = SOUL_FIRE_TICKS.get(entityId);
                if (remaining == null || remaining <= 0) {
                    SOUL_FIRE_TICKS.remove(entityId);
                    continue;
                }
                LivingEntity liv = findLivingAcrossLevels(server, entityId);
                if (liv == null || !liv.isAlive() || !(liv.level() instanceof ServerLevel entLevel)) {
                    SOUL_FIRE_TICKS.remove(entityId);
                    continue;
                }
                liv.hurtServer(entLevel, entLevel.damageSources().magic(), 2.0F);
                entLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    liv.getX(), liv.getY() + 0.6D, liv.getZ(),
                    6, 0.25D, 0.25D, 0.25D, 0.02D
                );
                int next = remaining - 20;
                if (next <= 0) {
                    SOUL_FIRE_TICKS.remove(entityId);
                } else {
                    SOUL_FIRE_TICKS.put(entityId, next);
                }
            }
        }

        if (!SKYFIRE_BOLT_TICKS.isEmpty()) {
            for (UUID entityId : new ArrayList<>(SKYFIRE_BOLT_TICKS.keySet())) {
                Integer remaining = SKYFIRE_BOLT_TICKS.get(entityId);
                if (remaining == null || remaining <= 0) {
                    SKYFIRE_BOLT_TICKS.remove(entityId);
                    continue;
                }
                LivingEntity liv = findLivingAcrossLevels(server, entityId);
                if (liv == null || !liv.isAlive() || !(liv.level() instanceof ServerLevel entLevel)) {
                    SKYFIRE_BOLT_TICKS.remove(entityId);
                    continue;
                }
                if (remaining % 40 == 0) {
                    AberrantRegistry.spawnLightning(entLevel, liv.position());
                }
                int next = remaining - 20;
                if (next <= 0) {
                    SKYFIRE_BOLT_TICKS.remove(entityId);
                } else {
                    SKYFIRE_BOLT_TICKS.put(entityId, next);
                }
            }
        }
    }

    private static @Nullable LivingEntity findLivingAcrossLevels(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e instanceof LivingEntity liv) {
                return liv;
            }
        }
        return null;
    }

    private static void hurtBypassingIframe(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        int prevInvuln = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurtServer(level, source, amount);
        } finally {
            target.invulnerableTime = prevInvuln;
        }
    }

    private static List<LivingEntity> nearbyHostiles(
        ServerLevel level,
        ServerPlayer player,
        Vec3 center,
        double radius,
        @Nullable LivingEntity exclude
    ) {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius, radius * 2.0D);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            e -> e != player && e != exclude && e.isAlive() && !e.isAlliedTo(player)
        );
    }

    private static void spawnItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, pos.x, pos.y + 0.35D, pos.z, stack);
        level.addFreshEntity(drop);
    }

    private static void grantEffect(LivingEntity entity, String path, int ticks, int amp) {
        BuiltInRegistries.MOB_EFFECT
            .get(Identifier.withDefaultNamespace(path))
            .ifPresent(h -> entity.addEffect(new MobEffectInstance(h, ticks, amp, true, true)));
    }

    private static @Nullable Item bonusRawForOre(Block block) {
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.RAW_IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return Items.RAW_GOLD;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.RAW_COPPER;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Items.LAPIS_LAZULI;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Items.COAL;
        return null;
    }

    private static @Nullable Item smeltedResultForBlock(Block block) {
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return Items.GOLD_INGOT;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
        if (block == Blocks.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        if (block == Blocks.SAND || block == Blocks.RED_SAND) return Items.GLASS;
        return null;
    }
}
