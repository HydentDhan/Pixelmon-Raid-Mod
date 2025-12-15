package com.example.PixelmonRaid;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Listen for projectile impacts (players throwing Poké Balls).
 *
 * When a projectile hits a Pixelmon instance that we flagged as a raid boss,
 * we add the throwing player to the RaidSession (if not already) so they participate.
 *
 * This implementation avoids any direct references to mapping-dependent methods or fields
 * (such as getPosX/getPosY/posX, etc.) by using reflection for those calls, so it compiles
 * across different mappings/versions.
 */
@Mod.EventBusSubscriber(modid = PixelmonRaidMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaidCaptureHandler {
    private RaidCaptureHandler() {}

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        try {
            if (event == null) return;
            Entity projectile = event.getEntity();
            if (projectile == null) return;

            // Extract hit entity robustly
            RayTraceResult rtr = event.getRayTraceResult();
            Entity hitEntity = null;
            if (rtr instanceof EntityRayTraceResult) {
                hitEntity = ((EntityRayTraceResult) rtr).getEntity();
            } else if (rtr != null) {
                // Reflection fallback: try getEntity()
                try {
                    Method m = rtr.getClass().getMethod("getEntity");
                    Object maybe = m.invoke(rtr);
                    if (maybe instanceof Entity) hitEntity = (Entity) maybe;
                } catch (Throwable ignored) {}
            }
            if (hitEntity == null) return;

            // Determine server world (robustly)
            ServerWorld serverWorld = null;
            try {
                // try projectile.getLevel()
                try {
                    Method m = projectile.getClass().getMethod("getLevel");
                    Object lvl = m.invoke(projectile);
                    if (lvl instanceof ServerWorld) serverWorld = (ServerWorld) lvl;
                } catch (Throwable ignored) {}

                // fallback: hitEntity.getLevel()
                if (serverWorld == null) {
                    try {
                        Method m2 = hitEntity.getClass().getMethod("getLevel");
                        Object lvl2 = m2.invoke(hitEntity);
                        if (lvl2 instanceof ServerWorld) serverWorld = (ServerWorld) lvl2;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            if (serverWorld == null) return; // can't proceed

            // Check if hitEntity is a Pixelmon raid boss
            boolean isRaidBoss = false;
            Object pixelmonEntity = null;
            try {
                Class<?> pixClass = Class.forName("com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity");
                if (pixClass.isInstance(hitEntity)) {
                    pixelmonEntity = hitEntity;
                    // call RaidSpawner.isRaidBoss(PixelmonEntity) if exists
                    try {
                        Method isRaid = RaidSpawner.class.getMethod("isRaidBoss", pixClass);
                        Object res = isRaid.invoke(null, pixelmonEntity);
                        if (res instanceof Boolean && ((Boolean) res)) isRaidBoss = true;
                    } catch (NoSuchMethodException nsme) {
                        // try isRaidBoss(Object)
                        try {
                            Method isRaidAny = RaidSpawner.class.getMethod("isRaidBoss", Object.class);
                            Object res = isRaidAny.invoke(null, pixelmonEntity);
                            if (res instanceof Boolean && ((Boolean) res)) isRaidBoss = true;
                        } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }
            } catch (ClassNotFoundException cnf) {
                // Pixelmon not present on compile classpath -> nothing to do
            } catch (Throwable ignored) {}

            if (!isRaidBoss) return;

            // Determine the throwing player (owner) via reflection - try multiple method names
            ServerPlayerEntity throwingPlayer = null;
            try {
                // try getThrower()
                try {
                    Method m = projectile.getClass().getMethod("getThrower");
                    Object t = m.invoke(projectile);
                    if (t instanceof ServerPlayerEntity) throwingPlayer = (ServerPlayerEntity) t;
                } catch (Throwable ignored) {}
                // try getOwner()
                if (throwingPlayer == null) {
                    try {
                        Method m2 = projectile.getClass().getMethod("getOwner");
                        Object t2 = m2.invoke(projectile);
                        if (t2 instanceof ServerPlayerEntity) throwingPlayer = (ServerPlayerEntity) t2;
                        // some entity types return UUID from getOwner()
                        if (throwingPlayer == null && t2 instanceof UUID) {
                            throwingPlayer = serverWorld.getServer().getPlayerList().getPlayer((UUID) t2);
                        }
                    } catch (Throwable ignored) {}
                }
                // try getOwnerId()
                if (throwingPlayer == null) {
                    try {
                        Method m3 = projectile.getClass().getMethod("getOwnerId");
                        Object t3 = m3.invoke(projectile);
                        if (t3 instanceof UUID) throwingPlayer = serverWorld.getServer().getPlayerList().getPlayer((UUID) t3);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // Last resort - nearest player heuristic (small radius)
            if (throwingPlayer == null) {
                try {
                    double px = getEntityX(projectile);
                    double py = getEntityY(projectile);
                    double pz = getEntityZ(projectile);
                    double maxDistSq = 16.0 * 16.0; // 16 blocks
                    for (ServerPlayerEntity p : serverWorld.getPlayers(pl -> true)) {
                        double dx = getEntityX(p) - px;
                        double dy = getEntityY(p) - py;
                        double dz = getEntityZ(p) - pz;
                        if (dx*dx + dy*dy + dz*dz <= maxDistSq) {
                            throwingPlayer = p;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // If a throwing player was found, add them to the session and (if IN_BATTLE) attempt start for that player
            if (throwingPlayer != null) {
                try {
                    RaidSession session = RaidSpawner.getSession(serverWorld);
                    if (session != null) {
                        UUID pid = throwingPlayer.getUUID();
                        boolean added = session.addPlayer(pid);
                        throwingPlayer.sendMessage(new StringTextComponent("You joined the raid!"), throwingPlayer.getUUID());
                        System.out.println("[PixelmonRaid] Projectile hit raid boss -> projectile=" +
                                safeRegistryName(projectile) + " hit=" + safeRegistryName(hitEntity) +
                                " owner=" + throwingPlayer.getName().getString() + " added=" + added);

                        // If session already IN_BATTLE, try per-player start (best-effort)
                        try {
                            if (session.getState() == RaidSession.State.IN_BATTLE) {
                                boolean started = RaidBattleStarter.startBattleForPlayer(session, serverWorld, throwingPlayer);
                                // don't spam player; only send on failure/success optionally
                                if (started) {
                                    throwingPlayer.sendMessage(new StringTextComponent("You have joined the ongoing raid battle."), throwingPlayer.getUUID());
                                } else {
                                    // best-effort: no action
                                }
                            }
                        } catch (Throwable sb) {
                            // ignore - just a best-effort attempt
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            } else {
                System.out.println("[PixelmonRaid] Projectile hit raid boss but thrower unknown.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // Helper to get registry name safely
    private static String safeRegistryName(Entity e) {
        try {
            if (e == null) return "null";
            try {
                Method mt = e.getType().getClass().getMethod("getRegistryName");
                Object rn = mt.invoke(e.getType());
                if (rn != null) return rn.toString();
            } catch (Throwable ignored) {}
            try {
                Object t = e.getType();
                return t.toString();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return "unknown";
    }

    // --- Reflection-based position access helpers ---
    private static double getEntityX(Entity e) {
        if (e == null) return 0.0;
        try {
            // Try method getX()
            try {
                Method m = e.getClass().getMethod("getX");
                Object v = m.invoke(e);
                if (v instanceof Double) return (Double) v;
                if (v instanceof Float) return ((Float) v).doubleValue();
            } catch (Throwable ignored) {}

            // Try method getPosX() via reflection (may not exist)
            try {
                Method m2 = e.getClass().getMethod("getPosX");
                Object v2 = m2.invoke(e);
                if (v2 instanceof Double) return (Double) v2;
            } catch (Throwable ignored) {}

            // Try common field names via reflection
            try {
                Field f = e.getClass().getField("posX");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
            try {
                Field f = e.getClass().getField("x");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private static double getEntityY(Entity e) {
        if (e == null) return 0.0;
        try {
            try {
                Method m = e.getClass().getMethod("getY");
                Object v = m.invoke(e);
                if (v instanceof Double) return (Double) v;
                if (v instanceof Float) return ((Float) v).doubleValue();
            } catch (Throwable ignored) {}

            try {
                Method m2 = e.getClass().getMethod("getPosY");
                Object v2 = m2.invoke(e);
                if (v2 instanceof Double) return (Double) v2;
            } catch (Throwable ignored) {}

            try {
                Field f = e.getClass().getField("posY");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
            try {
                Field f = e.getClass().getField("y");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private static double getEntityZ(Entity e) {
        if (e == null) return 0.0;
        try {
            try {
                Method m = e.getClass().getMethod("getZ");
                Object v = m.invoke(e);
                if (v instanceof Double) return (Double) v;
                if (v instanceof Float) return ((Float) v).doubleValue();
            } catch (Throwable ignored) {}

            try {
                Method m2 = e.getClass().getMethod("getPosZ");
                Object v2 = m2.invoke(e);
                if (v2 instanceof Double) return (Double) v2;
            } catch (Throwable ignored) {}

            try {
                Field f = e.getClass().getField("posZ");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
            try {
                Field f = e.getClass().getField("z");
                Object fv = f.get(e);
                if (fv instanceof Double) return (Double) fv;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0.0;
    }
}
