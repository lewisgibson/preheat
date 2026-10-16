package com.lewisgibson.preheat.keep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The video-memory safety for kept pipelines. Each kept pipeline holds its own screen-sized and shadow images, so
 * several kept at once can use gigabytes. When the graphics card reports its free memory, kept pipelines that are not
 * in use are let go whenever free memory falls below a reserve, modded dimensions before the vanilla three and the one
 * used longest ago first; a dimension is not prepared at startup if it would not fit. When the card does not report
 * it, at most {@link #MODDED_WITHOUT_INFO} modded dimensions are kept besides the vanilla three.
 */
public final class KeptBudget {
    /** Free video memory always left for the game, at least: 1 GB. */
    static final long MIN_RESERVE_MB = 1024;
    /** Or this share of the card's memory, if more. */
    static final int RESERVE_PERCENT = 15;
    /** What a kept pipeline is assumed to use until one has been measured. */
    static final long DEFAULT_ESTIMATE_MB = 512;
    /** Modded dimensions kept when the card does not report its memory. */
    static final int MODDED_WITHOUT_INFO = 2;

    private KeptBudget() {}

    /** Free and total video memory in MB; total is 0 when only the free amount is known. */
    public record Memory(long availableMb, long totalMb) {
        /**
         * A reading in KB as the driver gives it, or null when it cannot be right: software renderers report these
         * extensions with numbers that change on every call, so a negative or changing total, or free memory above the
         * total, means the driver's numbers are not used at all. {@code firstTotalKb} is the session's first total (0
         * if none yet, or for a driver that reports no total).
         */
        public static @Nullable Memory of(long freeKb, long totalKb, long firstTotalKb) {
            if (freeKb < 0 || totalKb < 0 || (totalKb > 0 && freeKb > totalKb)) {
                return null;
            }
            if (firstTotalKb > 0 && totalKb != firstTotalKb) {
                return null;
            }
            return new Memory(freeKb / 1024, totalKb / 1024);
        }

        long reserveMb() {
            return Math.max(MIN_RESERVE_MB, totalMb * RESERVE_PERCENT / 100);
        }
    }

    /** A kept pipeline that is not the active one. */
    public record Kept<K>(K id, boolean vanilla, long lastUsed, long estimateMb) {}

    /** Which inactive kept pipelines to let go now, in the order to drop them. */
    public static <K> List<K> toDrop(List<Kept<K>> inactive, @Nullable Memory memory) {
        List<Kept<K>> order = new ArrayList<>(inactive);
        order.sort(Comparator.<Kept<K>, Boolean>comparing(Kept::vanilla).thenComparingLong(Kept::lastUsed));
        List<K> out = new ArrayList<>();
        if (memory == null) {
            long modded = order.stream().filter(k -> !k.vanilla()).count();
            // Vanilla dimensions sort last, so the modded ones are down to the limit before any is reached.
            for (Kept<K> k : order) {
                if (modded <= MODDED_WITHOUT_INFO) {
                    break;
                }
                out.add(k.id());
                modded--;
            }
            return out;
        }
        long deficit = memory.reserveMb() - memory.availableMb();
        for (Kept<K> k : order) {
            if (deficit <= 0) {
                break;
            }
            out.add(k.id());
            deficit -= k.estimateMb() > 0 ? k.estimateMb() : DEFAULT_ESTIMATE_MB;
        }
        return out;
    }

    /** Whether one more pipeline of about {@code estimateMb} fits before the reserve. */
    public static boolean roomFor(long estimateMb, @Nullable Memory memory) {
        if (memory == null) {
            return true;
        }
        long need = estimateMb > 0 ? estimateMb : DEFAULT_ESTIMATE_MB;
        return memory.availableMb() - need >= memory.reserveMb();
    }
}
