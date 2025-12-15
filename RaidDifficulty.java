package com.example.PixelmonRaid;

public enum RaidDifficulty {
    EASY(1.0f, 0.75f),
    NORMAL(1.0f, 1.0f),
    HARD(1.0f, 1.5f),
    NIGHTMARE(1.0f, 2.0f);

    private final float playerDamageMultiplier;
    private final float bossDamageMultiplier;

    RaidDifficulty(float playerDamageMultiplier, float bossDamageMultiplier) {
        this.playerDamageMultiplier = playerDamageMultiplier;
        this.bossDamageMultiplier = bossDamageMultiplier;
    }

    public float getPlayerDamageMultiplier() {
        return playerDamageMultiplier;
    }

    public float getBossDamageMultiplier() {
        return bossDamageMultiplier;
    }
}
