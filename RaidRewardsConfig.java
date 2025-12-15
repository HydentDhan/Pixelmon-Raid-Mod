package com.example.PixelmonRaid;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Simple file-backed reward config.
 * Each line in config/pixelmonraid_rewards.txt is: itemRegistryName,count,chance
 * Example: pixelmon:rare_candy,2,0.75
 */
public class RaidRewardsConfig {
    public static final String FILENAME = "pixelmonraid_rewards.txt";
    private static final RaidRewardsConfig INSTANCE = new RaidRewardsConfig();

    public static RaidRewardsConfig getInstance() { return INSTANCE; }

    public static class RewardEntry {
        public final String item;
        public final int count;
        public final float chance;

        public RewardEntry(String item, int count, float chance) {
            this.item = item;
            this.count = Math.max(1, count);
            this.chance = Math.max(0f, Math.min(1f, chance));
        }

        public String toLine() {
            return item + "," + count + "," + chance;
        }

        public static RewardEntry fromLine(String line) {
            if (line == null) return null;
            String[] parts = line.trim().split(",");
            if (parts.length < 1) return null;
            String item = parts[0].trim();
            int count = 1;
            float chance = 1.0f;
            try {
                if (parts.length >= 2) count = Integer.parseInt(parts[1].trim());
                if (parts.length >= 3) chance = Float.parseFloat(parts[2].trim());
            } catch (NumberFormatException ignored) {}
            return new RewardEntry(item, count, chance);
        }
    }

    private final List<RewardEntry> rewards = new ArrayList<>();
    private final Path file;

    private RaidRewardsConfig() {
        file = FMLPaths.CONFIGDIR.get().resolve(FILENAME);
        load();
    }

    // ---- Public helpers expected by other code ----

    /** Return a defensive copy of the RewardEntry list. */
    public synchronized List<RewardEntry> getRewards() {
        return new ArrayList<>(rewards);
    }

    /** Return a list of lines (string) used by older callers like getRewardLines(). */
    public synchronized List<String> getRewardLines() {
        List<String> lines = new ArrayList<>();
        for (RewardEntry r : rewards) lines.add(r.toLine());
        return lines;
    }

    /** Replace full list atomically. */
    public synchronized void setRewards(List<RewardEntry> newRewards) {
        rewards.clear();
        if (newRewards != null) rewards.addAll(newRewards);
    }

    /** Add a single reward entry (append). */
    public synchronized void addReward(RewardEntry re) {
        if (re != null) rewards.add(re);
    }

    /** Remove a reward by index (returns true when removed). */
    public synchronized boolean removeReward(int index) {
        if (index < 0 || index >= rewards.size()) return false;
        rewards.remove(index);
        return true;
    }

    /** Save current rewards list to file. */
    public synchronized void save() {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            List<String> lines = new ArrayList<>();
            for (RewardEntry re : rewards) lines.add(re.toLine());
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Load config from file, or create a sensible default if missing. */
    public final void load() {
        rewards.clear();
        try {
            if (Files.exists(file)) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String l : lines) {
                    if (l == null || l.trim().isEmpty()) continue;
                    RewardEntry r = RewardEntry.fromLine(l);
                    if (r != null) rewards.add(r);
                }
            } else {
                // default sample items (pixelmon items only)
                rewards.add(new RewardEntry("pixelmon:rare_candy", 2, 0.8f));
                rewards.add(new RewardEntry("pixelmon:xl_exp_candy", 1, 0.4f));
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
