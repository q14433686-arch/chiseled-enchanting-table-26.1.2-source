package chiseled_enchanting_table.mechanics.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.Identifier;

import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.*;
import static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.MAX_QUEUE_STEPS;
import static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.MAX_QUEUE_CHARACTERS;

/** Versioned queue encoding and fail-closed recovery, separate from ritual algorithms. */
final class RitualQueuePersistence {
    private RitualQueuePersistence() {}
    public static String serializeQueue(List<QueuedInscription> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < queue.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(queue.get(i).encode());
        }
        return sb.toString();
    }

    public static List<QueuedInscription> deserializeQueue(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        if (raw.length() > MAX_QUEUE_CHARACTERS) throw new IllegalArgumentException("Oversized persisted ritual");
        List<QueuedInscription> out = new ArrayList<>();
        long refund = 0;
        for (String part : raw.split(";", -1)) {
            if (out.size() >= MAX_QUEUE_STEPS) throw new IllegalArgumentException("Too many ritual steps");
            QueuedInscription parsed = QueuedInscription.decode(part);
            if (parsed == null) throw new IllegalArgumentException("Malformed ritual step");
            refund += parsed.conflictRefundPoints();
            if (refund > Integer.MAX_VALUE) throw new IllegalArgumentException("Ritual refund exceeds budget");
            out.add(parsed);
        }
        return out;
    }

    static String encode(QueuedInscription step) {
        StringBuilder itemsEnc = new StringBuilder();
        for (int i = 0; i < step.paidItems().size(); i++) {
            if (i > 0) itemsEnc.append(',');
            PaidItemEntry e = step.paidItems().get(i);
            itemsEnc.append(e.itemId()).append('*').append(e.count());
        }
        return step.enchantmentId().toString()
            + "|" + step.targetLevel()
            + "|" + step.previousLevel()
            + "|" + (step.masterTomeUsed() ? 1 : 0)
            + "|" + (step.usedInfinityTome() ? 1 : 0)
            + "|" + step.conflictRefundPoints()
            + "|" + (step.consumedEpiphany() ? 1 : 0)
            + "|" + String.format(Locale.ROOT, "%.4f", step.infinityTomeLoad())
            + "|" + step.paidXpPoints()
            + "|" + itemsEnc
            + "|" + step.inheritedGeneration();
    }

    public static QueuedInscription decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\|", -1);
        if ((parts.length != 7 && parts.length != 10 && parts.length != 11) || token.length() > 2048) {
            return null;
        }
        try {
            Identifier id = Identifier.parse(parts[0].trim());
            int rawTarget = Integer.parseInt(parts[1].trim());
            int rawPrev = Integer.parseInt(parts[2].trim());
            int rawRefund = Integer.parseInt(parts[5].trim());
            if (rawTarget < 1 || rawTarget > 255 || rawPrev < 0 || rawPrev > 255 || rawRefund < 0
                || !Set.of("0", "1").contains(parts[3].trim())
                || !Set.of("0", "1").contains(parts[4].trim())
                || !Set.of("0", "1").contains(parts[6].trim())) {
                return null;
            }
            boolean tomeUsed = "1".equals(parts[3].trim());
            boolean infTome = "1".equals(parts[4].trim());
            boolean epip = "1".equals(parts[6].trim());
            if (parts.length == 7) {
                return new QueuedInscription(id, rawTarget, rawPrev, tomeUsed, infTome, rawRefund, epip);
            }
            double load = Double.parseDouble(parts[7].trim());
            int paidXp = Integer.parseInt(parts[8].trim());
            if (!Double.isFinite(load) || load < 0.0 || load > 256.0 || paidXp < 0 || paidXp > 1_000_000) {
                return null;
            }
            int inheritedGen = -1;
            if (parts.length == 11) {
                inheritedGen = Integer.parseInt(parts[10].trim());
                if (inheritedGen < -1 || inheritedGen > 255) {
                    return null;
                }
            }
            List<PaidItemEntry> items = new ArrayList<>();
            String rawItems = parts[9].trim();
            if (!rawItems.isEmpty()) {
                String[] entries = rawItems.split(",", -1);
                if (entries.length > 16) {
                    return null;
                }
                for (String itemToken : entries) {
                    int star = itemToken.indexOf('*');
                    if (star <= 0 || star >= itemToken.length() - 1) {
                        return null;
                    }
                    Identifier itemId = Identifier.parse(itemToken.substring(0, star).trim());
                    int cnt = Integer.parseInt(itemToken.substring(star + 1).trim());
                    if (cnt < 1 || cnt > 1024) {
                        return null;
                    }
                    items.add(new PaidItemEntry(itemId, cnt));
                }
            }
            return new QueuedInscription(id, rawTarget, rawPrev, tomeUsed, infTome, rawRefund, epip, load, paidXp, items, inheritedGen);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static List<QueuedInscription> recoverQueue(java.util.Optional<List<QueuedInscription>> structuredQueue, String rawQueue) {
        if (structuredQueue.isPresent() && (rawQueue.isBlank() || serializeQueue(structuredQueue.get()).equals(rawQueue))) {
            if (structuredQueue.get().size() > MAX_QUEUE_STEPS) throw new IllegalArgumentException("Too many ritual steps");
            long refund = 0L;
            for (QueuedInscription step : structuredQueue.get()) {
                refund += step.conflictRefundPoints();
                if (refund > Integer.MAX_VALUE) throw new IllegalArgumentException("Ritual refund exceeds budget");
            }
            return new ArrayList<>(structuredQueue.get());
        }
        return deserializeQueue(rawQueue);
    }
}
