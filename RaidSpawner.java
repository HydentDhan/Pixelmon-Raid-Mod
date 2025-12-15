package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.api.registry.RegistryValue;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Robust RaidSpawner that:
 *  - Attempts several Pixelmon API spawn pathways to create a PixelmonEntity from a Pokemon instance
 *  - Ensures anchor player is non-null before spawn attempts
 *  - Sets position and persistent data safely
 */
public class RaidSpawner {

    private static final Map<ServerWorld, RaidSession> SESSIONS = new HashMap<>();

    public static RaidSession getSession(ServerWorld world) {
        return SESSIONS.computeIfAbsent(world, w -> new RaidSession(w, new BlockPos(0, 80, 0)));
    }

    /**
     * Spawn the raid boss for the given session.
     * Will try a handful of Pixelmon API calls reflectively to maximize compatibility.
     */
    public static void spawnBoss(RaidSession session) {
        if (session == null) return;
        ServerWorld world = session.getWorld();
        BlockPos pos = session.getCenter();

        final String speciesName = "zacian"; // TODO: make configurable later

        try {
            Optional<RegistryValue<Species>> maybe = PixelmonSpecies.get(speciesName);
            if (!maybe.isPresent()) {
                System.err.println("[PixelmonRaid] Pixelmon species not found: " + speciesName);
                return;
            }
            Species spec = maybe.get().getValueUnsafe();

            // Build Pokemon instance
            Pokemon boss;
            try {
                boss = PokemonBuilder.builder().species(spec).level(100).shiny(true).build();
            } catch (Throwable t) {
                t.printStackTrace();
                System.err.println("[PixelmonRaid] Failed to build Pokemon for " + speciesName);
                return;
            }

            // Choose an anchor player (needed by some spawn APIs). Prefer any online server player.
            ServerPlayerEntity anchorPlayer = null;
            try {
                if (world.getServer() != null && world.getServer().getPlayerList() != null) {
                    List<ServerPlayerEntity> players = world.getServer().getPlayerList().getPlayers();
                    if (!players.isEmpty()) anchorPlayer = players.get(0);
                }
            } catch (Throwable ignored) {}

            if (anchorPlayer == null) {
                // Try any world player (fallback)
                try {
                    List<? extends PlayerEntity> players = world.getPlayers(p -> true);
                    if (!players.isEmpty() && players.get(0) instanceof ServerPlayerEntity) anchorPlayer = (ServerPlayerEntity) players.get(0);
                } catch (Throwable ignored) {}
            }

            PixelmonEntity entity = null;

            // 1) Common / straightforward API: getOrSpawnPixelmon(anchor)
            try {
                if (boss != null) {
                    try {
                        Method m = boss.getClass().getMethod("getOrSpawnPixelmon", PlayerEntity.class);
                        Object ret = m.invoke(boss, anchorPlayer);
                        if (ret instanceof PixelmonEntity) entity = (PixelmonEntity) ret;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}

            // 2) Try getOrSpawnPixelmon() with ServerWorld param or no param (some versions)
            if (entity == null) {
                try {
                    for (Method m : boss.getClass().getMethods()) {
                        String n = m.getName().toLowerCase();
                        if ((n.contains("getorspawn") || n.contains("spawn") || n.contains("getorspawnpixelmon")) && m.getReturnType() != void.class) {
                            try {
                                Object ret;
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts.length == 1 && ServerWorld.class.isAssignableFrom(pts[0])) {
                                    ret = m.invoke(boss, world);
                                } else if (pts.length == 1 && PlayerEntity.class.isAssignableFrom(pts[0])) {
                                    ret = m.invoke(boss, anchorPlayer);
                                } else if (pts.length == 0) {
                                    ret = m.invoke(boss);
                                } else {
                                    continue;
                                }
                                if (ret instanceof PixelmonEntity) { entity = (PixelmonEntity) ret; break; }
                            } catch (Throwable inner) { /* try next */ }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 3) Fallback: some Pixelmon versions provide Pokemon#createEntity or Pokemon#getEntity
            if (entity == null) {
                try {
                    for (Method m : boss.getClass().getMethods()) {
                        String n = m.getName().toLowerCase();
                        if (n.contains("create") || n.contains("getentity") || n.contains("toentity") || n.contains("spawnentity")) {
                            try {
                                Object ret;
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts.length == 1 && PlayerEntity.class.isAssignableFrom(pts[0])) {
                                    ret = m.invoke(boss, anchorPlayer);
                                } else if (pts.length == 1 && ServerWorld.class.isAssignableFrom(pts[0])) {
                                    ret = m.invoke(boss, world);
                                } else if (pts.length == 0) {
                                    ret = m.invoke(boss);
                                } else {
                                    continue;
                                }
                                if (ret instanceof PixelmonEntity) { entity = (PixelmonEntity) ret; break; }
                            } catch (Throwable inner) { /* try next */ }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 4) If still null: attempt to call PixelmonEntity's static helpers reflectively (some versions)
            if (entity == null) {
                try {
                    Class<?> pixelmonEntityClass = Class.forName("com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity");
                    for (Method m : pixelmonEntityClass.getMethods()) {
                        String n = m.getName().toLowerCase();
                        if ((n.contains("create") || n.contains("from") || n.contains("spawn")) && m.getReturnType() == pixelmonEntityClass) {
                            try {
                                Object ret;
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts.length == 2 && ServerWorld.class.isAssignableFrom(pts[0]) && boss.getClass().isAssignableFrom(pts[1])) {
                                    ret = m.invoke(null, world, boss); // unlikely but try
                                } else if (pts.length == 1 && boss.getClass().isAssignableFrom(pts[0])) {
                                    ret = m.invoke(null, boss);
                                } else if (pts.length == 0) {
                                    ret = m.invoke(null);
                                } else {
                                    continue;
                                }
                                if (ret instanceof PixelmonEntity) { entity = (PixelmonEntity) ret; break; }
                            } catch (Throwable inner) { /* try next */ }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (entity == null) {
                System.err.println("[PixelmonRaid] Could not create Pixelmon entity instance for " + speciesName);
                return;
            }

            // Position & persistent data
            double px = pos.getX() + 0.5;
            double py = pos.getY();
            double pz = pos.getZ() + 0.5;
            try { entity.setPos(px, py, pz); } catch (Throwable ignored) {}

            try {
                CompoundNBT tag = entity.getPersistentData();
                if (tag == null) tag = new CompoundNBT();
                tag.putBoolean("pixelmonraid_boss", true);

                int baseHp = 100;
                try {
                    if (entity.getPokemon() != null) baseHp = Math.max(1, entity.getPokemon().getHealth());
                } catch (Throwable ignored) {}

                PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
                double poolD = baseHp * cfg.getHpMultiplier() * cfg.getPoolMultiplier();
                int pool = Math.max(1, (int) Math.round(poolD));

                tag.putInt("pixelmonraid_hp_pool", pool);
                tag.putInt("pixelmonraid_accumulated_damage", 0);
                tag.putInt("pixelmonraid_phase", 0);
            } catch (Throwable ignored) {}

            // Add to world (try reliably)
            boolean added = false;
            try {
                added = world.addFreshEntity(entity);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }

            if (!added) {
                // try alternative add approach: ensure chunk loaded & attempt again
                try {
                    world.getChunkAt(pos);
                    added = world.addFreshEntity(entity);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }

            if (!added) {
                System.out.println("[PixelmonRaid] addFreshEntity returned false (continuing).");
            }

            try {
                // disable AI if method exists (safe reflection)
                try { entity.getClass().getMethod("setNoAI", boolean.class).invoke(entity, true); } catch (Throwable ignored) {}
                entity.setDeltaMovement(0.0, 0.0, 0.0);
            } catch (Throwable ignored) {}

            try {
                UUID id = entity.getUUID();
                session.addBossEntityUUID(id);
            } catch (Throwable ignored) {}

            System.out.println("[PixelmonRaid] Spawned raid boss '" + speciesName + "' at " + pos + " (pool set).");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isRaidBoss(PixelmonEntity e) {
        if (e == null) return false;
        try {
            if (e.getPersistentData() != null && e.getPersistentData().getBoolean("pixelmonraid_boss")) return true;
        } catch (Throwable ignored) {}
        try {
            Pokemon pokemon = e.getPokemon();
            return pokemon != null && pokemon.getSpecies() != null && "zacian".equalsIgnoreCase(pokemon.getSpecies().getName());
        } catch (Throwable t) { return false; }
    }

    public static void despawnBosses(RaidSession session) {
        if (session == null) return;
        ServerWorld world = session.getWorld();
        for (UUID id : session.getBossEntityUUIDs()) {
            try {
                world.getEntities().filter(e -> e.getUUID().equals(id)).findFirst().ifPresent(e -> {
                    try { e.remove(); } catch (Throwable t) { t.printStackTrace(); }
                });
            } catch (Throwable ignored) {}
        }
        session.clearBossEntities();
    }
}
