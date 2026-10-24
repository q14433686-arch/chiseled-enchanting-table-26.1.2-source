package chiseled_enchanting_table;

/**
 * GENERATED bundled-datapack defaults — do not edit by hand.
 *
 * <p>Rendered by {@code scripts/gen_bundled_defaults.py} from
 * {@code library_resonance.json} and {@code shelf_gates.json}, which are the
 * single source of truth for every default below (audit P1: the Java
 * {@code DEFAULT} records used to retype the same numbers and could drift
 * silently). {@code ci_static_checks.py} re-renders this file and fails on
 * any byte difference. To change a default, edit the JSON and re-run the
 * generator ({@code ./gradlew genBundledDefaults} or the script directly).</p>
 */
public final class BundledDefaults {
    private BundledDefaults() {}

    // ---- library_resonance.json / wooden_bookshelves ----
    public static final int MIN_DISCOUNT_BOOKSHELVES = 12;
    public static final int MAX_DISCOUNT_BOOKSHELVES = 38;
    public static final int MAX_WOODEN_DISCOUNT_PERCENT = 15;
    public static final double SHELF_UPGRADE_MATERIAL_MULTIPLIER = 0.9;

    // ---- library_resonance.json / chiseled_mastery ----
    public static final double MASTERY_UNIT_EXPONENT = 1.25;
    public static final double GLOBAL_MASTERY_MULTIPLIER = 1.25;
    public static final int MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT = 20;
    public static final int MAX_BASE_XP_DISCOUNT_PERCENT = 35;
    public static final int MAX_SPECIFIC_ENCHANT_BONUS_PERCENT = 15;
    public static final double SPECIFIC_ENCHANT_EXPONENT = 1.0;
    public static final int MAX_TOTAL_XP_DISCOUNT_PERCENT = 40;
    public static final double MASTER_TOME_UPGRADE_MATERIAL_MULTIPLIER = 0.8;
    public static final double INCREMENTAL_STEP_CREDIT_FACTOR = 0.85;
    public static final String TOME_CONSUMPTION_MODE = "wear";
    public static final int MAX_TOME_USES = 4;
    public static final int BOOK_COPY_WEAR_COST = 2;
    public static final double UNBREAKING_WAIVER_PER_LEVEL = 0.1;
    public static final double UNBREAKING_WAIVER_MAX_CHANCE = 0.3;
    public static final String TOME_DEPLETE_BEHAVIOR = "step_down";
    public static final boolean TOME_REGEN_ENABLED = true;
    public static final int TOME_REGEN_BASE_SECONDS = 7200;
    public static final int TOME_REGEN_TIER1_SHELVES = 38;
    public static final int TOME_REGEN_TIER1_SECONDS = 3600;
    public static final int TOME_REGEN_TIER2_SHELVES = 42;
    public static final int TOME_REGEN_TIER2_SECONDS = 1200;
    public static final String SCAPEGOAT_PRICING = "essence";

    // ---- library_resonance.json / wild_book_wear ----
    public static final boolean WILD_BOOK_WEAR_ENABLED = true;
    public static final java.util.List<Integer> WILD_REMAINING_WEIGHTS = java.util.List.of(45, 35, 15, 5);

    // ---- library_resonance.json / epiphany_affinity ----
    public static final double EPIPHANY_PROBABILITY_GLOBAL_WEIGHT = 0.45;
    public static final double EPIPHANY_PROBABILITY_AFFINITY_WEIGHT = 0.85;
    public static final double BLANK_BOOK_AFFINITY_WEIGHT = 0.5;
    public static final double EPIPHANY_SHELF_SCORE_FACTOR = 1.0;
    public static final double EPIPHANY_GLOBAL_MASTERY_SCORE_FACTOR = 2.2;
    public static final double EPIPHANY_AFFINITY_MASTERY_SCORE_FACTOR = 3.5;
    public static final double EPIPHANY_TIER2_MAX_PROB = 0.65;
    public static final double EPIPHANY_TIER2_SCORE_DIVISOR = 80.0;
    public static final double EPIPHANY_TIER2_MASTERY_BONUS = 0.2;
    public static final double EPIPHANY_TIER_PLUS_MAX_PROB = 0.5;
    public static final double EPIPHANY_TIER_PLUS_SCORE_DIVISOR = 110.0;
    public static final double EPIPHANY_TIER_PLUS_MASTERY_BONUS = 0.25;

    // ---- library_resonance.json / ritual ----
    public static final int MAX_SINGLE_WAIT_SECONDS = 300;
    public static final int MIN_TOME_ENCHANTS_FOR_WAIT = 10;
    public static final int MAX_SACRIFICE_XP_LEVELS = 15;
    public static final double MAX_RUSH_DEGRADE_NORMAL = 0.5;
    public static final double MAX_RUSH_DEGRADE_INFINITY = 0.7;
    public static final double MAX_TRANSCRIPTION_SLIP_CHANCE = 0.18;
    public static final double SHELF_LOAD_REFERENCE = 42.0;
    public static final double TOME_LOAD_REFERENCE = 22.0;

    // ---- library_resonance.json / ritual.wait_curve ----
    public static final double WAIT_CURVE_SHELF_WEIGHT = 0.42;
    public static final double WAIT_CURVE_TOME_WEIGHT = 0.58;
    public static final double WAIT_CURVE_LEVEL_FLOOR = 0.35;
    public static final double WAIT_CURVE_LEVEL_SPAN = 0.65;
    public static final double WAIT_CURVE_LEVEL_EXPONENT = 1.15;
    public static final double WAIT_CURVE_SATURATION = 0.62;
    public static final int WAIT_CURVE_MIN_TICKS = 100;

    // ---- library_resonance.json / crossline_slip ----
    public static final double CROSSLINE_MIN_LOAD_THRESHOLD = 3.0;
    public static final double CROSSLINE_PER_LOAD_SLOPE = 0.035;
    public static final double CROSSLINE_MAX_SLIP_CHANCE = 0.18;
    public static final int CROSSLINE_SHELF_TIER1_COUNT = 32;
    public static final double CROSSLINE_SHELF_TIER1_BONUS = 0.01;
    public static final int CROSSLINE_SHELF_TIER2_COUNT = 38;
    public static final double CROSSLINE_SHELF_TIER2_BONUS = 0.02;
    public static final int CROSSLINE_SHELF_TIER3_COUNT = 42;
    public static final double CROSSLINE_SHELF_TIER3_BONUS = 0.03;
    public static final double CROSSLINE_TREASURE_SCORE = 1.0;
    public static final double CROSSLINE_SINGLE_TREASURE_MIN_LOAD = 3.0;
    public static final int CROSSLINE_HIGH_RARITY_MAX_WEIGHT = 2;
    public static final double CROSSLINE_HIGH_RARITY_SCORE = 1.0;
    public static final int CROSSLINE_MID_RARITY_MAX_WEIGHT = 5;
    public static final double CROSSLINE_MID_RARITY_SCORE = 0.5;
    public static final int CROSSLINE_BROAD_APPLICABILITY_MIN_ITEMS = 12;
    public static final double CROSSLINE_BROAD_APPLICABILITY_SCORE = 0.5;
    public static final double CROSSLINE_RELATIVE_LEVEL_MIN_RATIO = 0.6;
    public static final double CROSSLINE_RELATIVE_LEVEL_SCORE = 0.5;
    public static final int CROSSLINE_UNBREAKING_HIGH_MIN_LEVEL = 3;
    public static final double CROSSLINE_UNBREAKING_HIGH_SCORE = 1.0;
    public static final double CROSSLINE_TIER_DROP_SHARE = 0.7;
    public static final double CROSSLINE_JUNK_SHARE = 0.25;
    public static final double CROSSLINE_CURSE_SHARE = 0.05;
    public static final double CROSSLINE_POLLUTION_REFUND_RATIO = 0.5;

    // ---- shelf_gates.json ----
    public static final String VANILLA_TABLE_MODE = "books";
    public static final int SCAN_HORIZONTAL_RADIUS = 3;
    public static final int SCAN_VERTICAL_RADIUS = 2;
    public static final boolean REQUIRE_BREATHING_ROOM = true;
    public static final int MIN_OPEN_ADJACENT_BLOCKS = 6;
    public static final boolean REQUIRE_LINE_OF_SIGHT = false;
    public static final boolean DIRECT_GATES_ENABLED = true;
    public static final int MAX_DIRECT_BOOKSHELVES = 38;
    public static final double DIRECT_TIER_GAMMA = 1.25;
    public static final String BLANK_MAX_TIER_MODE = "half_ceil";
    public static final int DEPLETED_TIER1_BOOKSHELVES = 12;
    public static final int DEPLETED_TIER2_BOOKSHELVES = 32;
    public static final double DEPLETED_TIER2_MATERIAL_MULTIPLIER = 0.5;
    public static final int DEPLETED_TIER3_QUALIFYING_ENCHANTMENTS = 6;
    public static final int DEPLETED_BASE_BATCH_SIZE = 64;
    public static final int DEPLETED_TIER3_BATCH_SIZE = 64;
}
