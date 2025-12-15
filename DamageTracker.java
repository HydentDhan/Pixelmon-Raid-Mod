package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DamageTracker {
    // thread-safe map
    private final Map<UUID, Integer> damageMap = new ConcurrentHashMap<>();

    public void recordDamage(ServerPlayerEntity player, int amount) {
        damageMap.merge(player.getUUID(), amount, Integer::sum);
    }

    public int getDamage(ServerPlayerEntity player) {
        return damageMap.getOrDefault(player.getUUID(), 0);
    }

    public int getDamage(UUID playerId) {
        return damageMap.getOrDefault(playerId, 0);
    }

    public boolean hasDamage(UUID playerId) {
        return damageMap.containsKey(playerId);
    }

    public Map<UUID, Integer> getAllDamage() {
        return new HashMap<>(damageMap);
    }

    public ServerPlayerEntity getTopDamager(ServerPlayerEntity[] players) {
        UUID topId = null;
        int maxDamage = 0;
        for (Map.Entry<UUID, Integer> entry : damageMap.entrySet()) {
            if (entry.getValue() > maxDamage) {
                maxDamage = entry.getValue();
                topId = entry.getKey();
            }
        }

        if (topId == null) return null;

        for (ServerPlayerEntity player : players) {
            if (player != null && player.getUUID().equals(topId)) {
                return player;
            }
        }
        return null;
    }

    public void reset() {
        damageMap.clear();
    }
}
