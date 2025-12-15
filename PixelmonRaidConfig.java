package com.example.PixelmonRaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Simple JSON configuration for PixelmonRaid.
 * File: config/pixelmonraid_config.json
 *
 * This version avoids serializing runtime fields (Path etc) by using a lightweight DTO
 * for Gson serialization. This prevents Gson/TypeToken recursion/StackOverflow issues.
 */
public final class PixelmonRaidConfig {
    private static final PixelmonRaidConfig INSTANCE = new PixelmonRaidConfig();
    public static PixelmonRaidConfig getInstance() { return INSTANCE; }

    private static final String FILENAME = "pixelmonraid_config.json";

    // runtime-only (do not serialize)
    private transient final Path file;

    // --- Configurable properties with defaults ---
    public double hpMultiplier = 10.0;        // multiplier applied to base HP to make pool (default: 10x)
    public double poolMultiplier = 1.0;       // extra multiplier (kept for flexibility)
    public double[] phaseThresholds = new double[]{0.25, 0.50, 0.75}; // fractions of DONE to enter phases 1..3
    public double sendThreshold = 0.005;      // fraction change required before sending bossbar (0.005 == 0.5%)
    public double tierA_cutoff = 0.25;        // >= 25% -> Tier A
    public double tierB_cutoff = 0.10;        // >= 10% -> Tier B
    public int maxPlayersPerRaid = 50;        // cap on players in a raid
    public int defaultRaidDurationSeconds = 120; // raid duration in seconds (2 min)

    private PixelmonRaidConfig() {
        file = FMLPaths.CONFIGDIR.get().resolve(FILENAME);
        load(); // attempt to load existing config or create one
    }

    // DTO used only for (de)serialization
    private static class ConfigData {
        public double hpMultiplier = 10.0;
        public double poolMultiplier = 1.0;
        public double[] phaseThresholds = new double[]{0.25, 0.50, 0.75};
        public double sendThreshold = 0.005;
        public double tierA_cutoff = 0.25;
        public double tierB_cutoff = 0.10;
        public int maxPlayersPerRaid = 50;
        public int defaultRaidDurationSeconds = 120;
    }

    public synchronized void save() {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);

            // copy config into DTO
            ConfigData out = new ConfigData();
            out.hpMultiplier = this.hpMultiplier;
            out.poolMultiplier = this.poolMultiplier;
            out.phaseThresholds = (this.phaseThresholds == null ? new double[]{0.25, 0.5, 0.75} : Arrays.copyOf(this.phaseThresholds, this.phaseThresholds.length));
            out.sendThreshold = this.sendThreshold;
            out.tierA_cutoff = this.tierA_cutoff;
            out.tierB_cutoff = this.tierB_cutoff;
            out.maxPlayersPerRaid = this.maxPlayersPerRaid;
            out.defaultRaidDurationSeconds = this.defaultRaidDurationSeconds;

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(out);
            Files.write(file, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final synchronized void load() {
        try {
            if (Files.exists(file)) {
                Gson gson = new Gson();
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                ConfigData loaded = gson.fromJson(content, ConfigData.class);
                if (loaded != null) {
                    this.hpMultiplier = loaded.hpMultiplier;
                    this.poolMultiplier = loaded.poolMultiplier;
                    if (loaded.phaseThresholds != null && loaded.phaseThresholds.length == 3)
                        this.phaseThresholds = Arrays.copyOf(loaded.phaseThresholds, loaded.phaseThresholds.length);
                    this.sendThreshold = loaded.sendThreshold;
                    this.tierA_cutoff = loaded.tierA_cutoff;
                    this.tierB_cutoff = loaded.tierB_cutoff;
                    this.maxPlayersPerRaid = loaded.maxPlayersPerRaid;
                    this.defaultRaidDurationSeconds = loaded.defaultRaidDurationSeconds;
                }
            } else {
                // file missing — write defaults
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Utility getters (defensive)
    public double getHpMultiplier() { return Math.max(0.0, hpMultiplier); }
    public double getPoolMultiplier() { return Math.max(0.0, poolMultiplier); }
    public double[] getPhaseThresholds() {
        if (phaseThresholds == null || phaseThresholds.length != 3) return new double[]{0.25, 0.5, 0.75};
        return Arrays.copyOf(phaseThresholds, phaseThresholds.length);
    }
    public double getSendThreshold() { return Math.max(0.0, sendThreshold); }
    public double getTierACutoff() { return Math.max(0.0, Math.min(1.0, tierA_cutoff)); }
    public double getTierBCutoff() { return Math.max(0.0, Math.min(1.0, tierB_cutoff)); }
    public int getMaxPlayersPerRaid() { return Math.max(1, maxPlayersPerRaid); }
    public int getDefaultRaidDurationSeconds() { return Math.max(10, defaultRaidDurationSeconds); }
}
