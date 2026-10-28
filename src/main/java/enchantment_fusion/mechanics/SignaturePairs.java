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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
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
 * Handcrafted directional Signature Pair registry and runtime mechanics across all 16 user-designed groups.
 * Every entry defines {@code [Primary A ▷ Secondary B]} with bespoke combat, mining, defense, or world effects.
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

    private static final Map<String, SignatureEntry> DIRECT_MAP = new LinkedHashMap<>();
    private static final List<SignatureEntry> UNIQUE_SIGNATURES = new ArrayList<>();

    // Per-entity neurotoxin stack counter & player snapback / shield / vengeance states
    private static final Map<UUID, Integer> NEUROTOXIN_STACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> RIPTIDE_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIPTIDE_ANCHOR_EXPIRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_SAFE_GROUND = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> RECENT_DAMAGE_TAKEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECENT_DAMAGE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GLASS_SHIELD_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FORTUNE_GRACE_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SOUL_FIRE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SKYFIRE_BOLT_TICKS = new ConcurrentHashMap<>();

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
        regAlias("concussive_plunder", "minecraft:looting", "minecraft:knockback");
        reg("legion_plunder", "minecraft:looting", "minecraft:sweeping_edge");
        regAlias("legion_plunder", "minecraft:sweeping_edge", "minecraft:looting");

        // === Batch 3: Tool Core (Silk Touch x Fortune x Efficiency x Mending x Unbreaking) ===
        reg("raw_ore_geode", "minecraft:silk_touch", "minecraft:fortune");
        reg("smelt_fortune", "minecraft:fortune", "minecraft:silk_touch");
        reg("pulse_prospect", "minecraft:efficiency", "minecraft:fortune");
        reg("gold_rush_haste", "minecraft:fortune", "minecraft:efficiency");
        reg("vein_peel", "minecraft:efficiency", "minecraft:silk_touch");
        regAlias("vein_peel", "minecraft:silk_touch", "minecraft:efficiency");
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
        regAlias("swift_draw_burst", "minecraft:power", "minecraft:quick_charge");

        // === Batch 5: Trident & Mace (Loyalty x Riptide x Channeling, Wind Burst x Density x Breach) ===
        reg("hydro_flying_sword", "minecraft:loyalty", "minecraft:riptide");
        reg("mirage_snapback", "minecraft:riptide", "minecraft:loyalty");
        reg("clear_sky_thunder", "minecraft:channeling", "minecraft:riptide");
        reg("thunder_avatar", "minecraft:riptide", "minecraft:channeling");
        reg("meteor_crush", "minecraft:wind_burst", "minecraft:density");
        regAlias("meteor_crush", "minecraft:density", "minecraft:wind_burst");
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
        regAlias("pyro_thorns", "minecraft:fire_protection", "minecraft:thorns");
        reg("blast_thorns", "minecraft:thorns", "minecraft:blast_protection");
        regAlias("blast_thorns", "minecraft:blast_protection", "minecraft:thorns");
        reg("sniper_thorns", "minecraft:thorns", "minecraft:projectile_protection");
        regAlias("sniper_thorns", "minecraft:projectile_protection", "minecraft:thorns");

        // === Batch 7: Boots, Leggings & Helmet Mobility (incl. Frost x Fire Protection magma basalt!) ===
        reg("tide_breaker_dive", "minecraft:depth_strider", "minecraft:frost_walker");
        reg("permafrost_wake", "minecraft:frost_walker", "minecraft:depth_strider");
        reg("magma_basalt_step", "minecraft:frost_walker", "minecraft:fire_protection");
        regAlias("magma_basalt_step", "minecraft:fire_protection", "minecraft:frost_walker");
        reg("frost_meteor_fall", "minecraft:feather_falling", "minecraft:frost_walker");
        regAlias("frost_meteor_fall", "minecraft:frost_walker", "minecraft:feather_falling");
        reg("soul_stride_landing", "minecraft:feather_falling", "minecraft:soul_speed");
        regAlias("soul_stride_landing", "minecraft:soul_speed", "minecraft:feather_falling");
        reg("phantom_assassin_step", "minecraft:soul_speed", "minecraft:swift_sneak");
        regAlias("phantom_assassin_step", "minecraft:swift_sneak", "minecraft:soul_speed");
        reg("abyssal_sight", "minecraft:respiration", "minecraft:aqua_affinity");
        reg("ocean_breath_mine", "minecraft:aqua_affinity", "minecraft:respiration");

        // === Batch 8: Fishing Rod & Curses Turned Into Power ===
        reg("tidal_double_catch", "minecraft:luck_of_the_sea", "minecraft:lure");
        reg("raging_hook", "minecraft:lure", "minecraft:luck_of_the_sea");

        // === Batch 9 & 10: Signature Cross-Domain Weapons / Tools / Bows / Armor ===
        reg("thunder_iaido", "minecraft:sharpness", "minecraft:channeling");
        regAlias("thunder_iaido", "minecraft:sweeping_edge", "minecraft:channeling");
        reg("phantom_triple_slash", "minecraft:sharpness", "minecraft:multishot");
        reg("flying_sword_qi", "minecraft:sharpness", "minecraft:loyalty");
        reg("midas_cleave", "minecraft:sharpness", "minecraft:fortune");
        regAlias("midas_cleave", "minecraft:looting", "minecraft:fortune");
        reg("rising_dragon_leap", "minecraft:sharpness", "minecraft:feather_falling");
        reg("blood_vengeance", "minecraft:sharpness", "minecraft:thorns");
        reg("molten_core_pick", "minecraft:efficiency", "minecraft:fire_aspect");
        regAlias("molten_core_pick", "minecraft:efficiency", "minecraft:flame");
        reg("magnetic_pick", "minecraft:efficiency", "minecraft:loyalty");
        reg("rock_splitter_pick", "minecraft:efficiency", "minecraft:sweeping_edge");
        regAlias("rock_splitter_pick", "minecraft:efficiency", "minecraft:blast_protection");
        reg("thunder_bow", "minecraft:power", "minecraft:channeling");
        reg("winter_frost_arrow", "minecraft:power", "minecraft:frost_walker");
        reg("gale_guardian_armor", "minecraft:protection", "minecraft:wind_burst");
        regAlias("gale_guardian_armor", "minecraft:feather_falling", "minecraft:wind_burst");

        // === Batch 11: Elemental Reactions (User-Specified Steam True Damage & 3x3x3 Frostfire Cage!) ===
        reg("steam_scald", "minecraft:fire_aspect", "minecraft:frost_walker");
        regAlias("steam_scald", "minecraft:flame", "minecraft:frost_walker");
        reg("frostfire_cage", "minecraft:frost_walker", "minecraft:fire_aspect");
        regAlias("frostfire_cage", "minecraft:frost_walker", "minecraft:flame");
        reg("superconduct_shatter", "minecraft:channeling", "minecraft:frost_walker");
        reg("aurora_thunder_prison", "minecraft:frost_walker", "minecraft:channeling");
        reg("plasma_overload", "minecraft:channeling", "minecraft:fire_aspect");
        regAlias("plasma_overload", "minecraft:channeling", "minecraft:flame");
        reg("skyfire_lightning", "minecraft:fire_aspect", "minecraft:channeling");
        regAlias("skyfire_lightning", "minecraft:flame", "minecraft:channeling");
        reg("boiling_blood", "minecraft:respiration", "minecraft:fire_aspect");
        regAlias("boiling_blood", "minecraft:depth_strider", "minecraft:fire_aspect");
        reg("choking_smoke", "minecraft:fire_aspect", "minecraft:respiration");
        reg("flame_tornado", "minecraft:wind_burst", "minecraft:fire_aspect");
        reg("blizzard_squall", "minecraft:wind_burst", "minecraft:frost_walker");

        // === Batch 12: Concept Cross-Domain Weapons ===
        reg("oxygen_drain_slash", "minecraft:sharpness", "minecraft:respiration");
        regAlias("oxygen_drain_slash", "minecraft:power", "minecraft:respiration");
        reg("abyssal_full_breath", "minecraft:respiration", "minecraft:sharpness");
        reg("shadow_backstab", "minecraft:swift_sneak", "minecraft:sharpness");
        regAlias("shadow_backstab", "minecraft:swift_sneak", "minecraft:power");
        reg("vanish_on_kill", "minecraft:sharpness", "minecraft:swift_sneak");
        reg("nether_soul_fire", "minecraft:soul_speed", "minecraft:fire_aspect");
        regAlias("nether_soul_fire", "minecraft:soul_speed", "minecraft:sharpness");
        reg("soul_steal_haste", "minecraft:sharpness", "minecraft:soul_speed");
        reg("pristine_trophy", "minecraft:silk_touch", "minecraft:sharpness");
        regAlias("pristine_trophy", "minecraft:silk_touch", "minecraft:looting");
        reg("crystal_snare", "minecraft:silk_touch", "minecraft:knockback");

        // === Batch 13: Defense & Utility Concept Crosses ===
        reg("frost_spike_armor", "minecraft:thorns", "minecraft:frost_walker");
        reg("ice_thorn_trail", "minecraft:frost_walker", "minecraft:thorns");
        reg("loyal_lifeline", "minecraft:protection", "minecraft:loyalty");
        regAlias("loyal_lifeline", "minecraft:feather_falling", "minecraft:loyalty");
        reg("boundless_mining", "minecraft:efficiency", "minecraft:aqua_affinity");
        reg("liquid_cutter", "minecraft:aqua_affinity", "minecraft:efficiency");
        reg("soul_siphon_mend", "minecraft:sharpness", "minecraft:mending");
        regAlias("soul_siphon_mend", "minecraft:power", "minecraft:mending");
        reg("overflow_edge", "minecraft:mending", "minecraft:sharpness");
        regAlias("overflow_edge", "minecraft:mending", "minecraft:power");

        // === Batch 14 & 15: User-Specified Lucky Golden Body (immune + 2x durability loss!) & Wild Crosses ===
        reg("fortune_grace", "minecraft:protection", "minecraft:fortune");
        reg("lucky_golden_body", "minecraft:fortune", "minecraft:protection");
        reg("triple_excavate", "minecraft:multishot", "minecraft:efficiency");
        reg("rapid_bowstring", "minecraft:efficiency", "minecraft:multishot");
        reg("land_water_dragon", "minecraft:riptide", "minecraft:sharpness");
        regAlias("land_water_dragon", "minecraft:riptide", "minecraft:wind_burst");
        reg("drenched_impale", "minecraft:impaling", "minecraft:sharpness");
        regAlias("drenched_impale", "minecraft:impaling", "minecraft:power");
        reg("gale_blade_flurry", "minecraft:quick_charge", "minecraft:sharpness");
        regAlias("gale_blade_flurry", "minecraft:quick_charge", "minecraft:sweeping_edge");
        reg("shield_piercer", "minecraft:piercing", "minecraft:sharpness");
        reg("perpetual_craft", "minecraft:infinity", "minecraft:efficiency");
        regAlias("perpetual_craft", "minecraft:infinity", "minecraft:unbreaking");
        reg("undying_hellfire", "minecraft:infinity", "minecraft:fire_aspect");
        regAlias("undying_hellfire", "minecraft:infinity", "minecraft:flame");
        reg("shakedown_thorns", "minecraft:looting", "minecraft:thorns");
        regAlias("shakedown_thorns", "minecraft:thorns", "minecraft:looting");
        reg("glass_holy_shield", "minecraft:silk_touch", "minecraft:protection");
        regAlias("glass_holy_shield", "minecraft:protection", "minecraft:silk_touch");

        // === Wildcard Curse / Fishing / Grappling Secondary Rules ===
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

    private static void regAlias(String id, String p, String s) {
        DIRECT_MAP.put(p + ">" + s, new SignatureEntry(id, p, s));
    }

    public static List<SignatureEntry> allUniqueSignatures() {
        return Collections.unmodifiableList(UNIQUE_SIGNATURES);
    }

    public static Optional<SignatureEntry> resolve(String primaryId, String secondaryId) {
        if (primaryId == null || secondaryId == null) {
            return Optional.empty();
        }
        SignatureEntry direct = DIRECT_MAP.get(primaryId + ">" + secondaryId);
        if (direct != null) {
            return Optional.of(direct);
        }
        if ("minecraft:binding_curse".equals(secondaryId) || "minecraft:binding_curse".equals(primaryId)) {
            return Optional.of(new SignatureEntry("blood_pact_curse", primaryId, secondaryId));
        }
        if ("minecraft:vanishing_curse".equals(secondaryId) || "minecraft:vanishing_curse".equals(primaryId)) {
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

    public static void clearRuntimeState() {
        NEUROTOXIN_STACKS.clear();
        RIPTIDE_ANCHORS.clear();
        RIPTIDE_ANCHOR_EXPIRY.clear();
        LAST_SAFE_GROUND.clear();
        RECENT_DAMAGE_TAKEN.clear();
        RECENT_DAMAGE_UNTIL.clear();
        GLASS_SHIELD_COOLDOWN.clear();
        FORTUNE_GRACE_COOLDOWN.clear();
        SOUL_FIRE_TICKS.clear();
        SKYFIRE_BOLT_TICKS.clear();
    }

    // =========================================================================
    // 1. Pre-Damage Interception (Lucky Golden Body & Glass Holy Shield)
    // =========================================================================

    public static boolean allowIncomingDamage(ServerPlayer player, float amount) {
        if (!(player.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return true;
        }
        long now = level.getGameTime();
        EquipmentSlot[] slots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };

        for (EquipmentSlot slot : slots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty()) continue;
            FusedEnchantmentList list = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair pair : list.pairs()) {
                Optional<SignatureEntry> sigOpt = resolve(pair);
                if (sigOpt.isEmpty()) continue;
                String sigId = sigOpt.get().id();

                // [时运 ▷ 保护] 招财金身：小概率免疫本次伤害，但加倍耐久降低！
                if ("lucky_golden_body".equals(sigId)) {
                    float procChance = 0.10F + 0.05F * pair.primaryLevel();
                    if (level.getRandom().nextFloat() < procChance) {
                        if (gear.isDamageableItem()) {
                            int extraDurabilityLoss = Math.max(2, Math.round(amount * 2.0F));
                            int nextDmg = Math.min(gear.getMaxDamage() - 1, gear.getDamageValue() + extraDurabilityLoss);
                            gear.setDamageValue(nextDmg);
                        }
                        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 14, 0.35D, 0.4D, 0.35D, 0.08D);
                        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.4F);
                        return false;
                    }
                }

                // [精准采集 ▷ 保护] 琉璃圣盾：满血时完美抵挡一次伤害（15秒脱战充能）
                if ("glass_holy_shield".equals(sigId)) {
                    long readyAt = GLASS_SHIELD_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                    if (player.getHealth() >= player.getMaxHealth() - 0.1F && now >= readyAt) {
                        GLASS_SHIELD_COOLDOWN.put(player.getUUID(), now + 300L);
                        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.4D, 0.5D, 0.4D, 0.06D);
                        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.95F, 1.55F);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // =========================================================================
    // 2. Lethal Save Interception (Fortune Grace & Loyal Lifeline Void Rescue)
    // =========================================================================

    public static boolean allowPlayerDeath(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        long now = level.getGameTime();
        EquipmentSlot[] slots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };

        for (EquipmentSlot slot : slots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty()) continue;
            FusedEnchantmentList list = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair pair : list.pairs()) {
                Optional<SignatureEntry> sigOpt = resolve(pair);
                if (sigOpt.isEmpty()) continue;
                String sigId = sigOpt.get().id();

                // [保护 ▷ 时运] 吉星高照：概率锁 1 滴血幸运免死（60秒冷却）
                if ("fortune_grace".equals(sigId)) {
                    long readyAt = FORTUNE_GRACE_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                    float saveChance = 0.18F + 0.08F * pair.secondaryLevel();
                    if (now >= readyAt && level.getRandom().nextFloat() < saveChance) {
                        FORTUNE_GRACE_COOLDOWN.put(player.getUUID(), now + 1200L);
                        player.setHealth(1.0F);
                        grantEffect(player, "absorption", 100, 1);
                        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.12D);
                        return false;
                    }
                }

                // [保护/摔落缓冲 ▷ 忠诚] 归元护主：虚空坠落拉回最后安全立足点！
                if ("loyal_lifeline".equals(sigId) && player.getY() < level.getMinY() + 2) {
                    Vec3 safe = LAST_SAFE_GROUND.get(player.getUUID());
                    if (safe != null) {
                        player.setHealth(Math.max(2.0F, player.getHealth()));
                        player.fallDistance = 0.0F;
                        player.teleportTo(safe.x, safe.y + 0.5D, safe.z);
                        if (gear.isDamageableItem()) {
                            gear.setDamageValue(Math.min(gear.getMaxDamage() - 1, gear.getDamageValue() + 20));
                        }
                        level.sendParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1.0D, safe.z, 30, 0.4D, 0.6D, 0.4D, 0.15D);
                        return false;
                    }
                }
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
        RandomSource rng = level.getRandom();
        int pLv = Math.max(1, pair.primaryLevel());
        int sLv = Math.max(1, pair.secondaryLevel());

        // Curses turned into power: +25% bonus strike on Soulburn Elegy!
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
            // --- User's Custom #1: [火焰附加 ▷ 冰霜行者] 高压蒸汽（无视护甲真伤！）---
            case "steam_scald" -> {
                if (target != null) {
                    float trueSteamDmg = 2.5F + 1.15F * pLv + 1.0F * sLv;
                    target.hurtServer(level, level.damageSources().magic(), trueSteamDmg);
                    target.clearFire();
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.7D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.06D);
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.7D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.04D);
                return true;
            }
            // --- User's Custom #2: [冰霜行者 ▷ 火焰附加] 冰火两重天（缓慢 + 燃烧 + 低概率 3x3x3 冰块！）---
            case "frostfire_cage" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 60 + 20 * pLv, 1);
                    target.igniteForSeconds(3.0F + 2.0F * sLv);
                    if (rng.nextFloat() < 0.20F) {
                        AberrantRegistry.placeIceCage3x3x3(level, target.blockPosition(), Blocks.ICE.defaultBlockState(), false);
                        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 1.3F);
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
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
                    for (int step = 1; step <= 3; step++) {
                        BlockPos firePos = BlockPos.containing(pos.add(dir.scale(step)));
                        if (level.getBlockState(firePos).isAir() && !level.getBlockState(firePos.below()).isAir()) {
                            level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
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
                Vec3 center = pos;
                for (LivingEntity mob : nearbyHostiles(level, player, center, 5.5D, target)) {
                    Vec3 pull = center.subtract(mob.position()).scale(0.42D);
                    mob.push(pull.x, 0.15D, pull.z);
                }
                level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.5D, center.z, 12, 0.5D, 0.2D, 0.5D, 0.03D);
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
            case "spectral_condense" -> {
                if (!hostStack.isDamaged() && rng.nextFloat() < 0.22F) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.SPECTRAL_ARROW, 1));
                }
                return true;
            }
            case "converging_volley" -> {
                if (target != null) {
                    target.invulnerableTime = 0;
                    target.hurtServer(level, level.damageSources().magic(), 2.8F + 0.5F * sLv);
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
            case "explosive_flare_arrow", "plasma_overload" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.igniteForSeconds(4.0F);
                    mob.hurtServer(level, level.damageSources().magic(), 3.2F);
                    Vec3 blast = mob.position().subtract(pos).normalize().scale(0.75D);
                    mob.push(blast.x, 0.38D, blast.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5D, pos.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
                return true;
            }
            case "meteor_streak" -> {
                if (target != null) {
                    target.igniteForSeconds(6.0F);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 2.8D, target)) {
                        mob.igniteForSeconds(4.0F);
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
            case "thunder_avatar", "aurora_thunder_prison" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F + 0.5F * pLv);
                    grantEffect(mob, "slowness", 60, 2);
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
            case "ground_gust", "gale_guardian_armor" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                    mob.push(away.x, 0.42D, away.z);
                }
                if ("gale_guardian_armor".equals(sigId)) {
                    player.push(0.0D, 0.45D, 0.0D);
                    player.hurtMarked = true;
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
            case "blast_thorns", "kinetic_payback" -> {
                grantEffect(player, "strength", 80, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.hurtServer(level, level.damageSources().thorns(player), 2.5F);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.75D);
                    mob.push(away.x, 0.3D, away.z);
                }
                return true;
            }
            case "deflection_field", "sniper_thorns" -> {
                if (target != null && target != player) {
                    target.hurtServer(level, level.damageSources().thorns(player), 3.0F + 0.5F * pLv);
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
                    target.invulnerableTime = 0;
                    target.hurtServer(level, level.damageSources().magic(), 1.8F + 0.35F * pLv);
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
            case "oxygen_drain_slash" -> {
                if (target != null) {
                    int nextAir = target.getAirSupply() - 110;
                    target.setAirSupply(Math.max(-20, nextAir));
                    if (nextAir <= 0) {
                        target.hurtServer(level, level.damageSources().drown(), 3.8F + 0.5F * pLv);
                    }
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
            case "shadow_backstab" -> {
                if (target != null) {
                    double dot = player.getLookAngle().normalize().dot(target.getLookAngle().normalize());
                    if (player.isShiftKeyDown() || dot > 0.35D) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 5.0F + 1.1F * sLv);
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
            case "crystal_snare" -> {
                if (target != null && rng.nextFloat() < 0.22F) {
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
            case "drenched_impale" -> {
                if (target != null && (target.isInWaterOrRain() || player.isInWaterOrRain())) {
                    target.hurtServer(level, level.damageSources().magic(), 3.0F + 1.1F * pLv);
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
            case "perpetual_craft" -> {
                if (hostStack.isDamaged() && rng.nextFloat() < 0.75F) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                return true;
            }
            case "undying_hellfire" -> {
                if (target != null) {
                    target.igniteForSeconds(12.0F + 4.0F * sLv);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // =========================================================================
    // 4. Block Harvest Execution (Silk x Fortune, Molten Pick, 3x1 Excavate, etc.)
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
                case "molten_core_pick" -> {
                    Item smelted = smeltedResultForBlock(minedState.getBlock());
                    if (smelted != null) {
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(smelted, 1));
                        ExperienceOrb.award(level, Vec3.atCenterOf(pos), 1);
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.2D, 0.2D, 0.2D, 0.02D);
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
                case "perpetual_craft", "overflow_plating" -> {
                    if (tool.isDamaged() && rng.nextFloat() < 0.75F) {
                        tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                    }
                }
                case "ocean_breath_mine" -> {
                    player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 45));
                }
                case "rock_splitter_pick", "triple_excavate", "vein_peel" -> {
                    if (!player.isShiftKeyDown()) {
                        Direction facing = player.getDirection();
                        Direction side = facing.getClockWise();
                        for (int offset : new int[]{-1, 1}) {
                            BlockPos adj = pos.relative(side, offset);
                            BlockState adjState = level.getBlockState(adj);
                            if (adjState.is(minedState.getBlock()) && adjState.getDestroySpeed(level, adj) >= 0.0F) {
                                level.destroyBlock(adj, true, player);
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. Entity Kill Drops (Pristine Trophy Heads, Vanish-on-Kill, Abyssal Heal)
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
    // 6. Periodic Passive & World Tick (Magma Basalt, Ice Thorns, Soul Fire, Loyal Save)
    // =========================================================================

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        if (player.onGround() && player.getY() > level.getMinY() + 2) {
            LAST_SAFE_GROUND.put(player.getUUID(), player.position());
        }

        if (level.getGameTime() % 10L != 0L) {
            return;
        }

        EquipmentSlot[] slots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };

        for (EquipmentSlot slot : slots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty()) continue;
            FusedEnchantmentList list = gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair pair : list.pairs()) {
                Optional<SignatureEntry> sigOpt = resolve(pair);
                if (sigOpt.isEmpty()) continue;
                String sigId = sigOpt.get().id();

                switch (sigId) {
                    case "loyal_lifeline" -> {
                        // Lock durability at 1 remaining point before shattering and pop safely into inventory!
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
    }

    public static void tickWorldDots(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        SOUL_FIRE_TICKS.entrySet().removeIf(entry -> {
            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof LivingEntity liv) || !liv.isAlive()) {
                return true;
            }
            liv.hurtServer(level, level.damageSources().magic(), 2.0F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, liv.getX(), liv.getY() + 0.6D, liv.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.02D);
            int next = entry.getValue() - 20;
            if (next <= 0) {
                return true;
            }
            entry.setValue(next);
            return false;
        });

        SKYFIRE_BOLT_TICKS.entrySet().removeIf(entry -> {
            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof LivingEntity liv) || !liv.isAlive()) {
                return true;
            }
            if (entry.getValue() % 40 == 0) {
                AberrantRegistry.spawnLightning(level, liv.position());
            }
            int next = entry.getValue() - 20;
            if (next <= 0) {
                return true;
            }
            entry.setValue(next);
            return false;
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
