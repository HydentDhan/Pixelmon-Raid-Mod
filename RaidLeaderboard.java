package com.example.PixelmonRaid;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.Map.Entry;

/**
 * Simple server-side leaderboard for raid damage.
 * Thread-safe (synchronized) and kept entirely in memory.
 * You can extend it to persist to disk if you want.
 */
public final class RaidLeaderboard {
    private static final Map<UUID, Long> damageMap = new HashMap<>();

    private RaidLeaderboard() {}

    /** Adds damage to the player's total. Pass positive damage numbers only. */
    public static synchronized void recordDamage(UUID playerId, long damage) {
        if (playerId == null || damage <= 0) return;
        damageMap.put(playerId, damageMap.getOrDefault(playerId, 0L) + damage);
    }

    /** Get a snapshot of the top N players as a list of Map.Entry<UUID,damage> sorted desc. */
    public static synchronized List<Entry<UUID, Long>> top(int n) {
        List<Entry<UUID, Long>> list = new ArrayList<>(damageMap.entrySet());
        list.sort(Comparator.comparingLong((Entry<UUID, Long> e) -> e.getValue()).reversed());
        if (n <= 0 || n >= list.size()) return list;
        return list.subList(0, n);
    }

    /** Clear leaderboard for next raid. */
    public static synchronized void reset() {
        damageMap.clear();
    }

    /** Return a defensive copy of the full map. */
    public static synchronized Map<UUID, Long> snapshot() {
        return new HashMap<>(damageMap);
    }
}
