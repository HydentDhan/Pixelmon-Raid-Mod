package com.example.PixelmonRaid;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Robust RaidBattleStarter that:
 *  - Attempts to use BattleBuilder API (if present)
 *  - Falls back to reflective BattleRegistry.startBattle(...) if Builder not available
 *  - Tries multiple candidate class / method names for Pixelmon (StorageProxy package differences)
 *  - Ensures PlayerParticipant is instantiated using the player's actual party (avoids empty-array NPE)
 *
 * This file uses reflection so the mod compiles without Pixelmon on the compile classpath.
 */
public final class RaidBattleStarter {
    private RaidBattleStarter() {}

    // candidate storage proxy class names (covering common Pixelmon version differences)
    private static final String[] STORAGE_PROXY_CANDIDATES = new String[] {
            "com.pixelmonmod.pixelmon.api.storage.StorageProxy",
            "com.pixelmonmod.pixelmon.storage.StorageProxy"
    };

    private static final String[] POKEMON_CANDIDATES = new String[] {
            "com.pixelmonmod.pixelmon.api.pokemon.Pokemon",
            "com.pixelmonmod.pixelmon.pokemon.Pokemon"
    };

    private static final String BATTLE_BUILDER_CLASS = "com.pixelmonmod.pixelmon.battles.api.BattleBuilder";
    private static final String ENTITY_PARTICIPANT_CLASS = "com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant";
    private static final String PLAYER_PARTICIPANT_CLASS = "com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant";
    private static final String BATTLE_PARTICIPANT_CLASS = "com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant";
    private static final String BATTLE_REGISTRY_CLASS = "com.pixelmonmod.pixelmon.battles.BattleRegistry";

    public static boolean startBattleForSession(RaidSession session, ServerWorld world) {
        if (session == null || world == null) return false;
        try {
            List<ServerPlayerEntity> online = new ArrayList<>();
            for (UUID u : session.getPlayers()) {
                try {
                    ServerPlayerEntity p = world.getServer().getPlayerList().getPlayer(u);
                    if (p != null) online.add(p);
                } catch (Throwable ignored) {}
            }
            if (online.isEmpty()) {
                System.out.println("[PixelmonRaid] No online players in session to start battle.");
                return false;
            }

            // Find boss entity by session stored UUIDs
            Entity bossEntity = null;
            for (UUID id : session.getBossEntityUUIDs()) {
                try {
                    Optional<? extends Entity> opt = world.getEntities().filter(e -> e.getUUID().equals(id)).findFirst();
                    if (opt.isPresent()) { bossEntity = opt.get(); break; }
                } catch (Throwable ignored) {}
            }

            if (bossEntity == null) {
                System.out.println("[PixelmonRaid] Boss entity missing; attempting spawn.");
                try { RaidSpawner.spawnBoss(session); } catch (Throwable spawnEx) { spawnEx.printStackTrace(); }
                for (UUID id : session.getBossEntityUUIDs()) {
                    try {
                        Optional<? extends Entity> opt = world.getEntities().filter(e -> e.getUUID().equals(id)).findFirst();
                        if (opt.isPresent()) { bossEntity = opt.get(); break; }
                    } catch (Throwable ignored) {}
                }
            }

            if (bossEntity == null) {
                System.err.println("[PixelmonRaid] Could not locate boss entity for battle start.");
                return false;
            }

            return startBattleWithBuilderOrReflect(online, bossEntity);
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static boolean startBattleForPlayer(RaidSession session, ServerWorld world, ServerPlayerEntity player) {
        if (session == null || world == null || player == null) return false;
        try { session.addPlayer(player.getUUID()); } catch (Throwable ignored) {}
        return startBattleForSession(session, world);
    }

    // ---------------------------
    // Main logic: try builder then fallback
    // ---------------------------
    private static boolean startBattleWithBuilderOrReflect(List<ServerPlayerEntity> players, Entity bossEntity) {
        if (players == null || players.isEmpty() || bossEntity == null) return false;

        System.out.println("[PixelmonRaid] Attempting BattleBuilder path (reflection).");
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> builderClass;
            try { builderClass = Class.forName(BATTLE_BUILDER_CLASS, true, cl); } catch (ClassNotFoundException e) { builderClass = null; }

            if (builderClass != null) {
                try {
                    if (tryUseBuilder(builderClass, players, bossEntity)) {
                        return true;
                    } else {
                        System.out.println("[PixelmonRaid] Builder path attempted but returned false — falling back.");
                    }
                } catch (Throwable builderEx) {
                    System.err.println("[PixelmonRaid] BattleBuilder path error: " + builderEx);
                    builderEx.printStackTrace();
                }
            } else {
                System.out.println("[PixelmonRaid] BattleBuilder class not found - skipping builder path.");
            }
        } catch (Throwable outer) {
            System.err.println("[PixelmonRaid] Unexpected error while attempting BattleBuilder path: " + outer);
            outer.printStackTrace();
        }

        System.out.println("[PixelmonRaid] Attempting reflective BattleRegistry startBattle fallback.");
        return startBattleReflective(players, bossEntity);
    }

    // Try to use the BattleBuilder reflection API
    private static boolean tryUseBuilder(Class<?> builderClass, List<ServerPlayerEntity> players, Entity bossEntity) {
        try {
            Object builderInstance = null;
            try {
                Method mcreate = builderClass.getMethod("create");
                builderInstance = mcreate.invoke(null);
            } catch (NoSuchMethodException ignored) {}
            if (builderInstance == null) {
                try {
                    Method mbuilder = builderClass.getMethod("builder");
                    builderInstance = mbuilder.invoke(null);
                } catch (NoSuchMethodException ignored) {}
            }
            if (builderInstance == null) {
                try {
                    Constructor<?> ctor = builderClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    builderInstance = ctor.newInstance();
                } catch (NoSuchMethodException ignored) {}
            }
            if (builderInstance == null) {
                System.err.println("[PixelmonRaid] BattleBuilder found but cannot instantiate it.");
                return false;
            }

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> bpClass = Class.forName(BATTLE_PARTICIPANT_CLASS, true, cl);
            Class<?> ppClass = Class.forName(PLAYER_PARTICIPANT_CLASS, true, cl);

            Object runtimePlayers = Array.newInstance(bpClass, players.size());
            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity sp = players.get(i);
                Object playerParticipant = instantiatePlayerParticipant(sp, ppClass, cl);
                if (playerParticipant == null) {
                    System.err.println("[PixelmonRaid] instantiatePlayerParticipant failed for player " + sp.getName().getString());
                    return false;
                }
                Array.set(runtimePlayers, i, playerParticipant);
            }

            Object runtimeBoss = createBossParticipantArray(bossEntity, bpClass, cl);
            if (runtimeBoss == null) {
                System.err.println("[PixelmonRaid] Could not create boss participant for builder path.");
                return false;
            }

            Method playersMethod = null, opponentsMethod = null, startMethod = null, buildMethod = null;
            for (Method m : builderInstance.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("player") && playersMethod == null) playersMethod = m;
                if (name.contains("opponent") && opponentsMethod == null) opponentsMethod = m;
                if ("start".equals(m.getName()) && startMethod == null) startMethod = m;
                if ("build".equals(m.getName()) && buildMethod == null) buildMethod = m;
            }

            if (playersMethod == null || opponentsMethod == null) {
                System.err.println("[PixelmonRaid] Builder missing players/opponents methods.");
                return false;
            }

            Object afterPlayers = playersMethod.invoke(builderInstance, runtimePlayers);
            Object afterOpp = opponentsMethod.invoke(afterPlayers, runtimeBoss);

            if (startMethod != null) {
                startMethod.invoke(afterOpp);
                System.out.println("[PixelmonRaid] Battle started via BattleBuilder.start().");
                return true;
            } else if (buildMethod != null) {
                Object controller = buildMethod.invoke(afterOpp);
                if (controller != null) {
                    System.out.println("[PixelmonRaid] Battle started via BattleBuilder.build().");
                    return true;
                }
            } else {
                System.err.println("[PixelmonRaid] Builder has no start/build method.");
                return false;
            }
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] tryUseBuilder error: " + t);
            t.printStackTrace();
            return false;
        }
        return false;
    }

    // Instantiate PlayerParticipant using player's party if possible
    private static Object instantiatePlayerParticipant(ServerPlayerEntity sp, Class<?> ppClass, ClassLoader cl) {
        try {
            List<?> partyList = fetchPlayerPartyAsList(sp, cl);
            if (partyList == null) {
                System.out.println("[PixelmonRaid] Player " + sp.getName().getString() + " party not found (or no Pixelmon storage).");
            } else {
                if (partyList.isEmpty()) {
                    System.err.println("[PixelmonRaid] Player " + sp.getName().getString() + " has no Pokémon in party. Aborting participant creation.");
                    return null;
                }
            }

            // Try constructor (ServerPlayerEntity, List, int)
            for (Constructor<?> c : ppClass.getConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                try {
                    if (pts.length == 3 && ServerPlayerEntity.class.isAssignableFrom(pts[0]) &&
                            java.util.List.class.isAssignableFrom(pts[1]) && (pts[2] == int.class || Integer.class.isAssignableFrom(pts[2]))) {
                        int numControlled = Math.min(1, Math.max(1, partyList == null ? 1 : Math.min(1, partyList.size())));
                        return c.newInstance(sp, partyList, numControlled);
                    }
                } catch (Throwable ignored) {}
            }

            // Try (List, int)
            for (Constructor<?> c : ppClass.getConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                try {
                    if (pts.length == 2 && java.util.List.class.isAssignableFrom(pts[0]) && (pts[1] == int.class || Integer.class.isAssignableFrom(pts[1]))) {
                        int numControlled = Math.min(1, Math.max(1, partyList == null ? 1 : Math.min(1, partyList.size())));
                        return c.newInstance(partyList, numControlled);
                    }
                } catch (Throwable ignored) {}
            }

            // Build Pokemon[] if possible
            Class<?> pokemonClass = null;
            Object pokemonArray = null;
            try {
                pokemonClass = findFirstClass(cl, POKEMON_CANDIDATES);
                if (pokemonClass != null && partyList != null) {
                    pokemonArray = Array.newInstance(pokemonClass, partyList.size());
                    for (int i = 0; i < partyList.size(); i++) Array.set(pokemonArray, i, partyList.get(i));
                }
            } catch (Throwable ignored) {}

            // Try constructors that accept (ServerPlayerEntity, Pokemon[]) or (boolean, ServerPlayerEntity, Pokemon[])
            for (Constructor<?> c : ppClass.getConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                try {
                    if (pts.length == 2 && ServerPlayerEntity.class.isAssignableFrom(pts[0]) && pokemonArray != null && pts[1].isAssignableFrom(pokemonArray.getClass())) {
                        return c.newInstance(sp, pokemonArray);
                    } else if (pts.length == 3 && pts[0] == boolean.class && ServerPlayerEntity.class.isAssignableFrom(pts[1]) && pokemonArray != null && pts[2].isAssignableFrom(pokemonArray.getClass())) {
                        return c.newInstance(Boolean.FALSE, sp, pokemonArray);
                    } else if (pts.length == 1 && ServerPlayerEntity.class.isAssignableFrom(pts[0])) {
                        return c.newInstance(sp);
                    }
                } catch (Throwable ctorEx) {
                    // continue
                }
            }

            // last resort: simple ctor(ServerPlayerEntity)
            try {
                Constructor<?> simple = ppClass.getConstructor(ServerPlayerEntity.class);
                return simple.newInstance(sp);
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] instantiatePlayerParticipant failed: " + t);
            t.printStackTrace();
        }
        return null;
    }

    private static Object createBossParticipantArray(Entity bossEntity, Class<?> bpClass, ClassLoader cl) {
        try {
            // Try EntityParticipant.Builder inner class
            Class<?> epBuilderClass = null;
            try {
                epBuilderClass = Class.forName(ENTITY_PARTICIPANT_CLASS + "$Builder", true, cl);
            } catch (ClassNotFoundException ignored) {
                try {
                    Class<?> epClass = Class.forName(ENTITY_PARTICIPANT_CLASS, true, cl);
                    for (Class<?> inner : epClass.getDeclaredClasses()) {
                        if ("Builder".equals(inner.getSimpleName())) { epBuilderClass = inner; break; }
                    }
                } catch (ClassNotFoundException ignored2) {}
            }

            Object bossParticipant = null;
            if (epBuilderClass != null) {
                Object epBuilder = epBuilderClass.getDeclaredConstructor().newInstance();
                boolean setEntity = false;
                for (Method m : epBuilderClass.getMethods()) {
                    if ("entity".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.invoke(epBuilder, bossEntity);
                        setEntity = true;
                        break;
                    }
                }
                if (!setEntity) {
                    for (Method m : epBuilderClass.getMethods()) {
                        if (m.getName().toLowerCase().contains("entity") && m.getParameterCount() == 1) {
                            m.invoke(epBuilder, bossEntity);
                            setEntity = true;
                            break;
                        }
                    }
                }
                if (setEntity) {
                    Method build = epBuilderClass.getMethod("build");
                    bossParticipant = build.invoke(epBuilder);
                }
            }

            if (bossParticipant == null) {
                try {
                    Class<?> epClass = Class.forName(ENTITY_PARTICIPANT_CLASS, true, cl);
                    for (Constructor<?> c : epClass.getConstructors()) {
                        Class<?>[] pts = c.getParameterTypes();
                        if (pts.length == 1 && pts[0].isAssignableFrom(bossEntity.getClass())) {
                            bossParticipant = c.newInstance(bossEntity);
                            break;
                        } else if (pts.length == 1 && pts[0].isAssignableFrom(Entity.class)) {
                            bossParticipant = c.newInstance(bossEntity);
                            break;
                        }
                    }
                } catch (ClassNotFoundException ignored) {}
            }

            if (bossParticipant == null) {
                System.err.println("[PixelmonRaid] EntityParticipant class not found or could not be constructed.");
                return null;
            }

            Object runtimeBoss = Array.newInstance(bpClass, 1);
            Array.set(runtimeBoss, 0, bossParticipant);
            return runtimeBoss;
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] createBossParticipantArray error: " + t);
            t.printStackTrace();
            return null;
        }
    }

    private static boolean startBattleReflective(List<ServerPlayerEntity> players, Entity bossEntity) {
        if (players == null || players.isEmpty() || bossEntity == null) return false;

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> ppClass = Class.forName(PLAYER_PARTICIPANT_CLASS, true, cl);
            Class<?> bpClass = Class.forName(BATTLE_PARTICIPANT_CLASS, true, cl);
            Class<?> battleRegistryClass = Class.forName(BATTLE_REGISTRY_CLASS, true, cl);

            Object runtimePlayers = Array.newInstance(bpClass, players.size());
            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity sp = players.get(i);
                Object ppart = instantiatePlayerParticipant(sp, ppClass, cl);
                if (ppart == null) {
                    System.err.println("[PixelmonRaid] startBattleReflective: failed to create PlayerParticipant for " + sp.getName().getString());
                    return false;
                }
                Array.set(runtimePlayers, i, ppart);
            }

            Object runtimeBoss = createBossParticipantArray(bossEntity, bpClass, cl);
            if (runtimeBoss == null) {
                System.err.println("[PixelmonRaid] startBattleReflective: failed to create boss participant.");
                return false;
            }

            Method startBattleMethod = null;
            for (Method m : battleRegistryClass.getMethods()) {
                if (!"startBattle".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length >= 2 && pts[0].isArray() && pts[1].isArray()) {
                    startBattleMethod = m;
                    break;
                }
            }
            if (startBattleMethod == null) {
                System.err.println("[PixelmonRaid] startBattleReflective: startBattle method not found.");
                return false;
            }

            Object controller;
            if (startBattleMethod.getParameterCount() == 2) {
                controller = startBattleMethod.invoke(null, runtimePlayers, runtimeBoss);
            } else {
                Object[] args = new Object[startBattleMethod.getParameterCount()];
                args[0] = runtimePlayers; args[1] = runtimeBoss;
                for (int i = 2; i < args.length; i++) args[i] = null;
                controller = startBattleMethod.invoke(null, args);
            }

            if (controller == null) {
                System.out.println("[PixelmonRaid] startBattle returned null (likely Pixelmon declined to start).");
                return false;
            }

            System.out.println("[PixelmonRaid] Battle started (reflective).");
            return true;
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] startBattleReflective error: " + t);
            t.printStackTrace();
            return false;
        }
    }

    // Helpers
    private static Class<?> findFirstClass(ClassLoader cl, String[] candidates) {
        for (String n : candidates) {
            try { return Class.forName(n, true, cl); } catch (Throwable ignored) {}
        }
        return null;
    }

    // Attempt to fetch player's party as a java.util.List using StorageProxy candidates
    private static List<?> fetchPlayerPartyAsList(ServerPlayerEntity sp, ClassLoader cl) {
        try {
            Class<?> storageProxyClass = findFirstClass(cl, STORAGE_PROXY_CANDIDATES);
            if (storageProxyClass == null) {
                System.err.println("[PixelmonRaid] No StorageProxy found in Pixelmon API (tried candidates).");
                return null;
            }

            Method getPartyMethod = null;
            try { getPartyMethod = storageProxyClass.getMethod("getParty", ServerPlayerEntity.class); } catch (NoSuchMethodException ignored) {}
            if (getPartyMethod == null) {
                try { getPartyMethod = storageProxyClass.getMethod("getParty", java.util.UUID.class); } catch (NoSuchMethodException ignored) {}
            }
            if (getPartyMethod == null) {
                for (Method m : storageProxyClass.getMethods()) {
                    if ("getParty".equals(m.getName()) && m.getParameterCount() == 1) { getPartyMethod = m; break; }
                }
            }

            if (getPartyMethod == null) {
                System.err.println("[PixelmonRaid] StorageProxy.getParty(...) not found.");
                return null;
            }

            Object partyObj;
            if (getPartyMethod.getParameterTypes()[0].isAssignableFrom(ServerPlayerEntity.class)) {
                partyObj = getPartyMethod.invoke(null, sp);
            } else if (getPartyMethod.getParameterTypes()[0].isAssignableFrom(java.util.UUID.class)) {
                partyObj = getPartyMethod.invoke(null, sp.getUUID());
            } else {
                try { partyObj = getPartyMethod.invoke(null, sp); } catch (Throwable t) { return null; }
            }

            if (partyObj == null) return null;

            Class<?> partyClass = partyObj.getClass();
            try {
                Method getTeam = partyClass.getMethod("getTeam");
                Object team = getTeam.invoke(partyObj);
                if (team instanceof List) return (List<?>) team;
            } catch (NoSuchMethodException ignored) {}

            for (String possible : new String[]{"getTeam", "getTeamPokemon", "getAll", "getList"}) {
                try {
                    Method m = partyClass.getMethod(possible);
                    Object team = m.invoke(partyObj);
                    if (team instanceof List) return (List<?>) team;
                } catch (Throwable ignored) {}
            }

            if (partyObj instanceof Iterable) {
                List<Object> out = new ArrayList<>();
                for (Object o : (Iterable<?>) partyObj) out.add(o);
                return out;
            }
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] Failed to fetch player's Pokemon array: " + t);
            t.printStackTrace();
        }
        return null;
    }
}
