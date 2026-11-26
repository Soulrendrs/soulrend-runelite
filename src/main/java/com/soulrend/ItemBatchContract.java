package com.soulrend;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Inactive v2 item-batch primitive. A raw stack contributes exactly once to
 * its canonical ID. Preserve raw stacks separately for server verification.
 * No connection to v1 matching, screenshots, networking or scoring.
 */
final class ItemBatchContract {
    static final int MAX_STACKS = 256;

    private ItemBatchContract() {}

    static Map<Integer, Integer> canonicalLoot(Iterable<int[]> stacks, IntUnaryOperator resolveAlias) {
        var counts = new HashMap<Integer, Integer>();
        int size = 0;
        for (int[] stack : stacks) {
            if (++size > MAX_STACKS) throw new IllegalArgumentException("Loot batch exceeds stack limit");
            if (stack == null || stack.length != 2 || stack[0] <= 0 || stack[1] <= 0)
                throw new IllegalArgumentException("Invalid loot stack");
            int base = resolveAlias.applyAsInt(stack[0]);
            if (base <= 0 || resolveAlias.applyAsInt(base) != base)
                throw new IllegalArgumentException("Invalid or noncanonical alias");
            long total = (long) counts.getOrDefault(base, 0) + stack[1];
            if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Canonical quantity overflow");
            counts.put(base, (int) total);
        }
        return Map.copyOf(counts);
    }
}
