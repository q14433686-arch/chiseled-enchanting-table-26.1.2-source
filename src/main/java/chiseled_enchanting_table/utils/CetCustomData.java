package chiseled_enchanting_table.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Unified {@link DataComponents#CUSTOM_DATA} gateway for all {@code cet_*} item metadata
 * (audit {@code API-04} &amp; {@code CODEC-01}).
 *
 * <p>Key invariants:</p>
 * <ul>
 *   <li>Writes {@link #NBT_SCHEMA_KEY} ({@code cet_schema = 1}) whenever at least one
 *       non-schema {@code cet_*} key is persisted on an {@link ItemStack}.</li>
 *   <li>Automatically strips {@link #NBT_SCHEMA_KEY} when the last {@code cet_*} payload key
 *       is removed, and removes {@link DataComponents#CUSTOM_DATA} altogether when the root
 *       {@link CompoundTag} becomes empty — preserving 100% canonical vanilla stackability
 *       ({@code stack64}) for clean and fully regenerated books!</li>
 *   <li>Maintains 100% backward read compatibility with pre-schema legacy worlds ({@code v0})
 *       that persisted {@code cet_tome_wear}, {@code cet_generation}, {@code cet_lock_*},
 *       or {@code cet_provenance} without {@code cet_schema}.</li>
 * </ul>
 */
public final class CetCustomData {
    public static final String CET_KEY_PREFIX = "cet_";
    public static final String NBT_SCHEMA_KEY = "cet_schema";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private CetCustomData() {}

    /**
     * Reads a defensive copy of {@link DataComponents#CUSTOM_DATA} if non-empty.
     */
    public static Optional<CompoundTag> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(data.copyTag());
    }

    /**
     * Returns the persisted {@code cet_schema} version on {@code tag}, or {@code 0} for
     * pre-schema legacy item data (or when no {@code cet_*} data exists).
     */
    public static int readSchemaVersion(CompoundTag tag) {
        if (tag == null || !tag.contains(NBT_SCHEMA_KEY)) {
            return 0;
        }
        return Math.max(0, tag.getIntOr(NBT_SCHEMA_KEY, 0));
    }

    /**
     * Returns {@code true} iff {@code root} contains at least one {@code cet_*} key other than
     * {@link #NBT_SCHEMA_KEY}.
     */
    public static boolean hasAnyPayloadKey(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return false;
        }
        for (String key : root.keySet()) {
            if (key.startsWith(CET_KEY_PREFIX) && !NBT_SCHEMA_KEY.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes {@code cet_schema} on {@code root} in-place (pure NBT helper, testable headlessly):
     * stamps {@link #CURRENT_SCHEMA_VERSION} if any {@code cet_*} payload key is present, or removes
     * {@link #NBT_SCHEMA_KEY} if all {@code cet_*} payload keys have been cleared.
     */
    public static void normalizeSchemaTag(CompoundTag root) {
        if (root == null) {
            return;
        }
        if (hasAnyPayloadKey(root)) {
            root.putInt(NBT_SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        } else {
            root.remove(NBT_SCHEMA_KEY);
        }
    }

    /**
     * Removes every {@code cet_*} key (including {@code cet_schema}) from {@code root}, leaving
     * any third-party mod {@code CUSTOM_DATA} keys untouched.
     */
    public static void clearAllCetKeys(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.startsWith(CET_KEY_PREFIX)) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            root.remove(key);
        }
    }

    /**
     * Mutates {@link DataComponents#CUSTOM_DATA} on {@code stack}, stamping {@code cet_schema}
     * when {@code cet_*} payload keys exist and removing {@code CUSTOM_DATA} completely when empty.
     */
    public static void update(ItemStack stack, Consumer<CompoundTag> mutator) {
        if (stack == null || stack.isEmpty() || mutator == null) {
            return;
        }
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = existing != null ? existing.copyTag() : new CompoundTag();
        mutator.accept(root);
        normalizeSchemaTag(root);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    public static void mutate(ItemStack stack, Consumer<CompoundTag> mutator) {
        update(stack, mutator);
    }
}
