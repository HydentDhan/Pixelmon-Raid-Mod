package com.example.PixelmonRaid;

public class RaidDifficultyManager {
    public enum Difficulty {
        EASY(1.0f, 1.0f),
        NORMAL(0.9f, 1.1f),
        HARD(0.7f, 1.3f),
        NIGHTMARE(0.5f, 1.5f);

        public final float playerDamageMultiplier;
        public final float bossDamageMultiplier;

        Difficulty(float playerMultiplier, float bossMultiplier) {
            this.playerDamageMultiplier = playerMultiplier;
            this.bossDamageMultiplier = bossMultiplier;
        }
    }

    private static Difficulty current = Difficulty.NORMAL;

    public static void setDifficulty(Difficulty difficulty) {
        current = difficulty;
    }

    public static Difficulty getDifficulty() {
        return current;
    }
}
