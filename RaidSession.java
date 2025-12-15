package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Session state for a raid. Controls lifecycle: IDLE -> WAITING -> IN_BATTLE -> COMPLETED
 */
public class RaidSession {
    public enum State { IDLE, WAITING, IN_BATTLE, COMPLETED }

    private final ServerWorld world;
    private final BlockPos center;
    private State state = State.IDLE;
    private long startTick;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> bossEntityUUIDs = new HashSet<>();
    private long lastAnnouncedSeconds = Long.MIN_VALUE;
    private boolean rewardsDistributed = false;

    // last known boss percent sent to players (0..1)
    private float lastBossPercent = -1f;

    public static RaidSession getSession(ServerWorld world) {
        return RaidSpawner.getSession(world);
    }

    public RaidSession(ServerWorld world, BlockPos center) {
        this.world = world;
        this.center = center;
    }

    public ServerWorld getWorld() { return world; }
    public BlockPos getCenter() { return center; }
    public Set<UUID> getPlayers() { return players; }
    public State getState() { return state; }

    public boolean addPlayer(UUID playerId) {
        if (players.size() >= 50) return false;
        boolean added = players.add(playerId);
        if (added) {
            try {
                ServerPlayerEntity sp = world.getServer().getPlayerList().getPlayer(playerId);
                if (sp != null) sp.sendMessage(new StringTextComponent("You joined the raid!"), sp.getUUID());
            } catch (Throwable ignored) {}
        }
        return added;
    }

    public void removePlayer(UUID playerId) { players.remove(playerId); }

    public void addBossEntityUUID(UUID id) { if (id != null) bossEntityUUIDs.add(id); }
    public Set<UUID> getBossEntityUUIDs() { return new HashSet<>(bossEntityUUIDs); }
    public void clearBossEntities() { bossEntityUUIDs.clear(); }

    public void setState(State s) {
        this.state = s;
        if (s == State.IN_BATTLE) RaidState.setBattleStartTick(world.getGameTime());
        if (s == State.WAITING) lastAnnouncedSeconds = Long.MIN_VALUE;
        if (s == State.COMPLETED) rewardsDistributed = false;
    }

    public void startWaitingNow(long currentTick) {
        this.state = State.WAITING;
        this.startTick = currentTick;
        this.lastAnnouncedSeconds = Long.MIN_VALUE;
        this.rewardsDistributed = false;
    }

    public synchronized boolean markRewardsDistributedIfNot() {
        if (this.rewardsDistributed) return false;
        this.rewardsDistributed = true;
        return true;
    }

    public synchronized boolean hasDistributedRewards() { return this.rewardsDistributed; }

    public long getTicksUntilStart(long currentTick) { return Math.max(0, startTick - currentTick); }

    public float getLastBossPercent() { return lastBossPercent; }
    public void setLastBossPercent(float p) { lastBossPercent = p; }

    public void tick(long tick) {
        switch (state) {
            case IDLE:
                if (tick % (20L * 60L * 5L) == 0L) {
                    state = State.WAITING;
                    startTick = tick;
                    players.clear();
                    world.getPlayers(p -> true).forEach(p ->
                            p.sendMessage(new StringTextComponent("Raid starts soon! Type /joinraid"), p.getUUID())
                    );
                    lastAnnouncedSeconds = Long.MIN_VALUE;
                }
                break;

            case WAITING:
                long waited = tick - startTick;
                long waitingDurationTicks = 20L * 30L; // 30 seconds
                if (waited > waitingDurationTicks) {
                    try {
                        double cx = center.getX() + 0.5;
                        double cy = center.getY();
                        double cz = center.getZ() + 0.5;
                        double maxDistSq = 10.0 * 10.0;
                        world.getPlayers(p -> true).forEach(p -> {
                            try {
                                double dx = p.getX() - cx;
                                double dy = p.getY() - cy;
                                double dz = p.getZ() - cz;
                                if (dx*dx + dy*dy + dz*dz <= maxDistSq) players.add(p.getUUID());
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}

                    state = State.IN_BATTLE;
                    // Reset leaderboard at the start of the battle
                    try { RaidLeaderboard.reset(); } catch (Throwable ignored) {}

                    RaidSpawner.spawnBoss(this);
                    RaidState.setBattleStartTick(tick);

                    // Best-effort attempt to auto-start battles (but you told me you prefer manual start by ball).
                    try {
                        boolean started = RaidBattleStarter.startBattleForSession(this, world);
                        if (!started) {
                            System.out.println("[PixelmonRaid] Auto-start of battle for session failed (see RaidBattleStarter logs).");
                            for (UUID u : new HashSet<>(players)) {
                                try {
                                    ServerPlayerEntity pl = world.getServer().getPlayerList().getPlayer(u);
                                    if (pl != null) {
                                        RaidBattleStarter.startBattleForPlayer(this, world, pl);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                } else {
                    long secondsLeft = Math.max(0, (waitingDurationTicks - waited) / 20L);
                    boolean shouldAnnounce = (secondsLeft % 60 == 0) || secondsLeft == 10 || secondsLeft == 5;
                    if (shouldAnnounce && secondsLeft != lastAnnouncedSeconds) {
                        final String msg = (secondsLeft % 60 == 0 && secondsLeft != 0)
                                ? "Raid begins in " + (secondsLeft / 60) + " minute(s)!"
                                : "Raid starts in " + secondsLeft + " seconds!";
                        world.getPlayers(p -> true).forEach(p -> p.sendMessage(new StringTextComponent(msg), p.getUUID()));
                        lastAnnouncedSeconds = secondsLeft;
                    }
                }
                break;

            case IN_BATTLE:
                long start = RaidState.getBattleStartTick();
                long elapsed = tick - start;
                long durationTicks = 20L * 120L; // 2 minutes
                if (elapsed >= durationTicks) {
                    finishRaid(false);
                } else {
                    long secondsLeftBattle = Math.max(0, (durationTicks - elapsed) / 20L);
                    boolean isSpecial = secondsLeftBattle == 60 || secondsLeftBattle == 30 || secondsLeftBattle == 10;
                    if (isSpecial && secondsLeftBattle != lastAnnouncedSeconds) {
                        world.getPlayers(p -> true).forEach(p -> p.sendMessage(new StringTextComponent("Raid ends in " + secondsLeftBattle + " second(s)!"), p.getUUID()));
                        lastAnnouncedSeconds = secondsLeftBattle;
                    }
                }
                break;

            case COMPLETED:
                players.clear();
                clearBossEntities();
                RaidState.clear();
                state = State.IDLE;
                break;
        }
    }

    /**
     * Finish raid: stop any Pixelmon battles for players in this session (safely, on server thread),
     * distribute rewards if victory, despawn boss entities and mark session completed.
     * Also announce leaderboard top players when victory==true.
     */
    public void finishRaid(boolean victory) {
        try {
            // Capture list to avoid concurrent modification
            final List<UUID> playerSnapshot = new ArrayList<>(this.players);
            final ServerWorld serverWorld = this.world;
            final MinecraftServer server = serverWorld.getServer();

            // Runnable that will attempt to end battles for the players (uses reflective fallbacks)
            Runnable endBattlesTask = () -> {
                for (UUID playerId : playerSnapshot) {
                    try {
                        ServerPlayerEntity pl = server.getPlayerList().getPlayer(playerId);
                        if (pl == null) continue;

                        // Try Pixelmon BattleRegistry.getBattle(player) reflectively
                        try {
                            Class<?> brClass = Class.forName("com.pixelmonmod.pixelmon.battles.BattleRegistry");
                            Method getBattleMethod = null;
                            for (Method m : brClass.getMethods()) {
                                if ("getBattle".equals(m.getName()) && m.getParameterCount() == 1
                                        && m.getParameterTypes()[0].isAssignableFrom(ServerPlayerEntity.class)) {
                                    getBattleMethod = m;
                                    break;
                                }
                            }
                            Object bc = null;
                            if (getBattleMethod != null) {
                                try {
                                    bc = getBattleMethod.invoke(null, pl);
                                } catch (Throwable t) {
                                    // try alternative invocation patterns
                                    try { bc = getBattleMethod.invoke(null, (Object) pl); } catch (Throwable ignored) {}
                                }
                            }

                            if (bc != null) {
                                // call endBattle() reflectively
                                try {
                                    Method end = bc.getClass().getMethod("endBattle");
                                    end.invoke(bc);
                                } catch (NoSuchMethodException nsme) {
                                    for (Method m : bc.getClass().getMethods()) {
                                        String name = m.getName().toLowerCase();
                                        if (name.contains("end") || name.contains("finish") || name.contains("stop") || name.contains("close")) {
                                            try { m.invoke(bc); break; } catch (Throwable ignored) {}
                                        }
                                    }
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                }
                            }
                        } catch (ClassNotFoundException cnf) {
                            // Pixelmon BattleRegistry not present on compile path -> nothing to do
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                } // end for
            };

            // Schedule on server/main thread via common methods (execute/submit/addScheduledTask)
            boolean scheduled = false;
            try {
                Method exec = server.getClass().getMethod("execute", Runnable.class);
                exec.invoke(server, endBattlesTask);
                scheduled = true;
            } catch (Throwable ignored) {}

            if (!scheduled) {
                try {
                    Method submit = server.getClass().getMethod("submit", Runnable.class);
                    submit.invoke(server, endBattlesTask);
                    scheduled = true;
                } catch (Throwable ignored) {}
            }

            if (!scheduled) {
                try {
                    Method add = server.getClass().getMethod("addScheduledTask", Runnable.class);
                    add.invoke(server, endBattlesTask);
                    scheduled = true;
                } catch (Throwable ignored) {}
            }

            if (!scheduled) {
                // last resort: run inline
                endBattlesTask.run();
            }

            // Now reward distribution / notifications / despawn
            if (victory) {
                try {
                    // announce leaderboard top players
                    try {
                        List<Map.Entry<java.util.UUID, Long>> top = RaidLeaderboard.top(5);
                        if (!top.isEmpty()) {
                            serverWorld.getPlayers(p -> true).forEach(p -> p.sendMessage(new StringTextComponent("Raid - Top players:"), p.getUUID()));
                            int rank = 1;
                            for (Map.Entry<java.util.UUID, Long> ent : top) {
                                try {
                                    ServerPlayerEntity sp = server.getPlayerList().getPlayer(ent.getKey());
                                    String name = (sp != null) ? sp.getName().getString() : ent.getKey().toString();
                                    serverWorld.getPlayers(p -> true).forEach(pl -> pl.sendMessage(new StringTextComponent("#" + rank + " " + name + " - " + ent.getValue() + " dmg"), pl.getUUID()));
                                } catch (Throwable ignored) {}
                                rank++;
                            }
                        }
                    } catch (Throwable t) { t.printStackTrace(); }

                    if (markRewardsDistributedIfNot()) {
                        RaidRewardHandler.distributeRewards(this);
                    }
                } catch (Throwable t) { t.printStackTrace(); }
            } else {
                try {
                    serverWorld.getPlayers(p -> true).forEach(p -> p.sendMessage(new StringTextComponent("Raid ended — boss survived."), p.getUUID()));
                } catch (Throwable ignored) {}
            }

            try { RaidSpawner.despawnBosses(this); } catch (Throwable ignored) {}

            clearBossEntities();
            state = State.COMPLETED;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
