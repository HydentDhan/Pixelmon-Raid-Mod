package com.example.PixelmonRaid;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Reflection-based Pixelmon battle starter.
 *
 * - Tries to use PlayerParticipant/EntityParticipant/BattleRegistry at runtime (no direct compile dependency).
 * - Attempts to fetch player's party (via StorageProxy) and use it when creating PlayerParticipant.
 * - Falls back to empty Pokemon[] if party-not-found.
 * - Invokes BattleRegistry.startBattle(...) reflectively, supporting 2-arg and multi-arg overloads.
 */
public final class PixelmonApiBattleStarter {
    private PixelmonApiBattleStarter() {}

    // Candidate class names for Pixelmon API differences
    private static final String[] POKEMON_CANDIDATES = new String[] {
            "com.pixelmonmod.pixelmon.api.pokemon.Pokemon",
            "com.pixelmonmod.pixelmon.pokemon.Pokemon"
    };
    private static final String[] STORAGE_PROXY_CANDIDATES = new String[] {
            "com.pixelmonmod.pixelmon.api.storage.StorageProxy",
            "com.pixelmonmod.pixelmon.storage.StorageProxy"
    };
    private static final String PLAYER_PARTICIPANT = "com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant";
    private static final String BATTLE_PARTICIPANT = "com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant";
    private static final String ENTITY_PARTICIPANT = "com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant";
    private static final String BATTLE_REGISTRY = "com.pixelmonmod.pixelmon.battles.BattleRegistry";

    /**
     * Attempt to start a Pixelmon battle for the session using Pixelmon API via reflection.
     * Returns true if battle started (controller non-null).
     *
     * This method will include only players from session who are online and within proximity filtering
     * (the caller should already have applied session membership). The method itself applies no additional
     * proximity; use the caller to filter if desired. For convenience it takes the full session and world
     * and will include all online session players.
     */
    public static boolean startBattleForSession(RaidSession session, ServerWorld world) {
        if (session == null || world == null) return false;

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            // Load runtime classes
            Class<?> ppClass = safeFindClass(cl, PLAYER_PARTICIPANT);
            Class<?> bpClass = safeFindClass(cl, BATTLE_PARTICIPANT);
            Class<?> registryClass = safeFindClass(cl, BATTLE_REGISTRY);

            if (ppClass == null || bpClass == null || registryClass == null) {
                System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: required Pixelmon classes not found (skipping API start).");
                return false;
            }

            // collect online players from session
            List<ServerPlayerEntity> players = new ArrayList<>();
            for (UUID uid : session.getPlayers()) {
                try {
                    ServerPlayerEntity p = world.getServer().getPlayerList().getPlayer(uid);
                    if (p != null) players.add(p);
                } catch (Throwable ignored) {}
            }
            if (players.isEmpty()) {
                System.out.println("[PixelmonRaid] PixelmonApiBattleStarter: no online players in session.");
                return false;
            }

            // find boss entity (Pixelmon entity) among session uuids
            Entity bossEntity = null;
            for (UUID bid : session.getBossEntityUUIDs()) {
                try {
                    Optional<? extends Entity> opt = world.getEntities().filter(e -> e.getUUID().equals(bid)).findFirst();
                    if (opt.isPresent()) { bossEntity = opt.get(); break; }
                } catch (Throwable ignored) {}
            }
            if (bossEntity == null) {
                System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: boss entity not located.");
                return false;
            }

            // Build runtimePlayers array (BattleParticipant[])
            Object runtimePlayers = Array.newInstance(bpClass, players.size());
            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity sp = players.get(i);
                Object ppart = instantiatePlayerParticipantReflective(sp, ppClass, cl);
                if (ppart == null) {
                    System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: failed to create PlayerParticipant for " + sp.getName().getString());
                    return false;
                }
                Array.set(runtimePlayers, i, ppart);
            }

            // Build boss participant array (BattleParticipant[1]) via EntityParticipant.Builder or direct ctor
            Object runtimeBoss = createBossParticipantArrayReflective(bossEntity, bpClass, cl);
            if (runtimeBoss == null) {
                System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: failed to create boss participant.");
                return false;
            }

            // Find a startBattle method on BattleRegistry with first two params arrays
            Method startBattle = null;
            for (Method m : registryClass.getMethods()) {
                if (!"startBattle".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length >= 2 && pts[0].isArray() && pts[1].isArray()) {
                    startBattle = m;
                    break;
                }
            }
            if (startBattle == null) {
                System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: startBattle method not found on BattleRegistry.");
                return false;
            }

            // Build args for invocation: first two are runtimePlayers, runtimeBoss; remaining params are null
            Object controller;
            if (startBattle.getParameterCount() == 2) {
                controller = startBattle.invoke(null, runtimePlayers, runtimeBoss);
            } else {
                Object[] args = new Object[startBattle.getParameterCount()];
                args[0] = runtimePlayers;
                args[1] = runtimeBoss;
                for (int i = 2; i < args.length; i++) args[i] = null;
                controller = startBattle.invoke(null, args);
            }

            if (controller == null) {
                System.out.println("[PixelmonRaid] PixelmonApiBattleStarter: BattleRegistry returned null (start canceled).");
                return false;
            }

            System.out.println("[PixelmonRaid] PixelmonApiBattleStarter: Battle started (controller non-null).");
            return true;

        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] PixelmonApiBattleStarter: exception while starting battle: " + t);
            t.printStackTrace();
            return false;
        }
    }

    // Attempts to build a PlayerParticipant reflectively, preferring constructors that include the player's actual party.
    private static Object instantiatePlayerParticipantReflective(ServerPlayerEntity sp, Class<?> ppClass, ClassLoader cl) {
        try {
            // Try to fetch player's party as a List via StorageProxy if present
            List<?> partyList = fetchPlayerPartyAsList(sp, cl); // may be null

            // Try to construct Pokemon[] matching runtime Pokemon class if possible
            Object pokemonArray = null;
            Class<?> pokemonClass = findFirstClass(cl, POKEMON_CANDIDATES);
            if (pokemonClass != null && partyList != null) {
                pokemonArray = Array.newInstance(pokemonClass, partyList.size());
                for (int i = 0; i < partyList.size(); i++) {
                    Array.set(pokemonArray, i, partyList.get(i));
                }
            }

            // Try a set of constructor signatures on PlayerParticipant until one works
            for (Constructor<?> c : ppClass.getConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                try {
                    // (boolean, ServerPlayerEntity, Pokemon...)
                    if (pts.length >= 2 && pts[0] == boolean.class && ServerPlayerEntity.class.isAssignableFrom(pts[1])) {
                        // prepare args: boolean false, sp, pokemonArray or empty array of appropriate component type
                        Object[] args = new Object[pts.length];
                        args[0] = Boolean.FALSE;
                        args[1] = sp;
                        if (pokemonArray != null) {
                            // if constructor's 3rd param is array of pokemon class or Object[], try set
                            if (pts.length >= 3 && pts[2].isArray()) {
                                if (pts[2].isAssignableFrom(pokemonArray.getClass())) {
                                    args[2] = pokemonArray;
                                } else {
                                    // try empty array of constructor component type
                                    args[2] = Array.newInstance(pts[2].getComponentType(), 0);
                                }
                            }
                        } else {
                            if (pts.length >= 3 && pts[2].isArray()) {
                                args[2] = Array.newInstance(pts[2].getComponentType(), 0);
                            }
                        }
                        // fill remaining args with nulls or empty arrays
                        for (int i = 3; i < args.length; i++) args[i] = null;
                        return c.newInstance(args);
                    }

                    // (ServerPlayerEntity, Pokemon[]) or (ServerPlayerEntity)
                    if (pts.length >= 1 && ServerPlayerEntity.class.isAssignableFrom(pts[0])) {
                        if (pts.length == 1) {
                            return c.newInstance(sp);
                        } else if (pts.length >= 2 && pts[1].isArray()) {
                            Object arr = pokemonArray;
                            if (arr == null) arr = Array.newInstance(pts[1].getComponentType(), 0);
                            Object[] args = new Object[pts.length];
                            args[0] = sp;
                            args[1] = arr;
                            for (int i = 2; i < args.length; i++) args[i] = null;
                            return c.newInstance(args);
                        }
                    }

                    // (List<Pokemon>, int) or (List, int) possibilities
                    if (pts.length == 2 && java.util.List.class.isAssignableFrom(pts[0]) && (pts[1] == int.class || Integer.class.isAssignableFrom(pts[1]))) {
                        int num = Math.max(1, partyList == null ? 1 : Math.min(1, partyList.size()));
                        return c.newInstance(partyList, num);
                    }

                } catch (Throwable ctorEx) {
                    // try next constructor
                }
            }

            // last resort: try simple ctor with ServerPlayerEntity
            try {
                Constructor<?> simple = ppClass.getConstructor(ServerPlayerEntity.class);
                return simple.newInstance(sp);
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] instantiatePlayerParticipantReflective error: " + t);
            t.printStackTrace();
        }
        return null;
    }

    // Build boss participant array (length 1) reflectively
    private static Object createBossParticipantArrayReflective(Entity bossEntity, Class<?> bpClass, ClassLoader cl) {
        try {
            // Try EntityParticipant$Builder first
            Class<?> epBuilderClass = null;
            try {
                epBuilderClass = Class.forName(ENTITY_PARTICIPANT + "$Builder", true, cl);
            } catch (Throwable ignored) {
                try {
                    Class<?> epClass = Class.forName(ENTITY_PARTICIPANT, true, cl);
                    for (Class<?> inner : epClass.getDeclaredClasses()) {
                        if ("Builder".equals(inner.getSimpleName())) { epBuilderClass = inner; break; }
                    }
                } catch (Throwable ignored2) {}
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

            // If no builder or build failed, try direct EntityParticipant ctor
            if (bossParticipant == null) {
                try {
                    Class<?> epClass = Class.forName(ENTITY_PARTICIPANT, true, cl);
                    for (Constructor<?> c : epClass.getConstructors()) {
                        Class<?>[] pts = c.getParameterTypes();
                        try {
                            if (pts.length == 1 && pts[0].isAssignableFrom(bossEntity.getClass())) {
                                bossParticipant = c.newInstance(bossEntity);
                                break;
                            } else if (pts.length == 1 && pts[0].isAssignableFrom(Entity.class)) {
                                bossParticipant = c.newInstance(bossEntity);
                                break;
                            } else if (pts.length >= 1) {
                                // try any single-param ctor: attempt to use bossEntity
                                if (pts.length == 1) {
                                    bossParticipant = c.newInstance(bossEntity);
                                    break;
                                }
                            }
                        } catch (Throwable ctorEx) {
                            // continue
                        }
                    }
                } catch (Throwable ignored2) {}
            }

            if (bossParticipant == null) {
                System.err.println("[PixelmonRaid] createBossParticipantArrayReflective: EntityParticipant not found or not constructible.");
                return null;
            }

            Object runtimeBoss = Array.newInstance(bpClass, 1);
            Array.set(runtimeBoss, 0, bossParticipant);
            return runtimeBoss;
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] createBossParticipantArrayReflective error: " + t);
            t.printStackTrace();
            return null;
        }
    }

    // Try to fetch player's party using StorageProxy variations. Returns List<?> or null.
    private static List<?> fetchPlayerPartyAsList(ServerPlayerEntity sp, ClassLoader cl) {
        try {
            Class<?> storage = findFirstClass(cl, STORAGE_PROXY_CANDIDATES);
            if (storage == null) return null;

            Method getParty = null;
            for (Method m : storage.getMethods()) {
                if ("getParty".equals(m.getName()) && m.getParameterCount() == 1) {
                    getParty = m;
                    break;
                }
            }
            if (getParty == null) return null;

            Object partyObj;
            Class<?> param = getParty.getParameterTypes()[0];
            if (param.isAssignableFrom(ServerPlayerEntity.class)) {
                partyObj = getParty.invoke(null, sp);
            } else if (param.isAssignableFrom(java.util.UUID.class)) {
                partyObj = getParty.invoke(null, sp.getUUID());
            } else {
                try {
                    partyObj = getParty.invoke(null, sp);
                } catch (Throwable t) { return null; }
            }
            if (partyObj == null) return null;

            // Try getTeam() or similar
            try {
                Method getTeam = partyObj.getClass().getMethod("getTeam");
                Object team = getTeam.invoke(partyObj);
                if (team instanceof List) return (List<?>) team;
            } catch (Throwable ignored) {}

            // try other names
            for (String name : new String[]{"getTeamPokemon", "getAll", "getList"}) {
                try {
                    Method m = partyObj.getClass().getMethod(name);
                    Object team = m.invoke(partyObj);
                    if (team instanceof List) return (List<?>) team;
                } catch (Throwable ignored2) {}
            }

            // If partyObj is Iterable, convert
            if (partyObj instanceof Iterable) {
                List<Object> out = new ArrayList<>();
                for (Object o : (Iterable<?>) partyObj) out.add(o);
                return out;
            }
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] fetchPlayerPartyAsList error: " + t);
            t.printStackTrace();
        }
        return null;
    }

    // utility to try multiple candidate class names
    private static Class<?> findFirstClass(ClassLoader cl, String[] candidates) {
        for (String s : candidates) {
            try {
                return Class.forName(s, true, cl);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Class<?> safeFindClass(ClassLoader cl, String name) {
        try { return Class.forName(name, true, cl); } catch (Throwable t) { return null; }
    }
}
