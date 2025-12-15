package com.example.PixelmonRaid;

public class RaidState {
    private static RaidSession activeSession;
    private static RaidDifficulty currentDifficulty = RaidDifficulty.NORMAL;
    private static DamageTracker damageTracker = new DamageTracker();
    private static long battleStartTick;

    // New: boss percent for client HUD (0.0..1.0)
    private static volatile float bossPercent = 0.0f;
    // New: dynamic boss damage multiplier (enrage)
    private static volatile float bossDamageMultiplier = 1.0f;

    public static RaidDifficulty getDifficulty() {
        return currentDifficulty;
    }

    public static void setDifficulty(RaidDifficulty difficulty) {
        currentDifficulty = difficulty;
    }

    public static DamageTracker getDamageTracker() {
        return damageTracker;
    }

    public static void setDamageTracker(DamageTracker dt) {
        damageTracker = dt;
    }

    public static void setBattleStartTick(long tick) {
        battleStartTick = tick;
    }

    public static long getBattleStartTick() {
        return battleStartTick;
    }

    public static void clear() {
        activeSession = null;
        battleStartTick = 0;
        currentDifficulty = RaidDifficulty.NORMAL;
        damageTracker = new DamageTracker();
        bossPercent = 0.0f;
        bossDamageMultiplier = 1.0f;
    }

    // Boss percent accessors
    public static void setBossPercent(float p) {
        bossPercent = Math.max(0f, Math.min(1f, p));
    }

    public static float getBossPercent() {
        return bossPercent;
    }

    // Boss damage multiplier accessors (affects how much damage boss deals)
    public static void setBossDamageMultiplier(float m) {
        bossDamageMultiplier = Math.max(0f, m);
    }

    public static float getBossDamageMultiplier() {
        return bossDamageMultiplier;
    }
}
