package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Ensure raid bosses do not move and stay at spawn center.
 * Runs every world tick and force-applies NoAI + zero motion + position lock.
 */
@Mod.EventBusSubscriber
public class RaidBossTickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;

        ServerWorld world = (ServerWorld) event.world;

        // Collect entities to avoid concurrent modification
        List<Entity> entities = world.getEntities().collect(Collectors.toList());
        for (Entity e : entities) {
            if (!(e instanceof PixelmonEntity)) continue;
            PixelmonEntity pe = (PixelmonEntity) e;
            try {
                if (!RaidSpawner.isRaidBoss(pe)) continue;

                // 1) enforce NoAI (reflection with fallback NBT)
                boolean applied = false;
                try {
                    java.lang.reflect.Method m = pe.getClass().getMethod("setNoAI", boolean.class);
                    m.invoke(pe, true);
                    applied = true;
                } catch (NoSuchMethodException nsme) {
                    try {
                        java.lang.reflect.Method m2 = net.minecraft.entity.Entity.class.getMethod("setNoAI", boolean.class);
                        m2.invoke(pe, true);
                        applied = true;
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}

                if (!applied) {
                    try { pe.getPersistentData().putBoolean("NoAI", true); } catch (Throwable ignored) {}
                }

                // 2) zero motion
                try {
                    pe.setDeltaMovement(0.0, 0.0, 0.0);
                    pe.setDeltaMovement(pe.getDeltaMovement()); // ensure update
                } catch (Throwable t) {
                    // fallback: set motion fields reflectively
                    try {
                        java.lang.reflect.Field motionX = net.minecraft.entity.Entity.class.getDeclaredField("motionX");
                        java.lang.reflect.Field motionY = net.minecraft.entity.Entity.class.getDeclaredField("motionY");
                        java.lang.reflect.Field motionZ = net.minecraft.entity.Entity.class.getDeclaredField("motionZ");
                        motionX.setAccessible(true); motionY.setAccessible(true); motionZ.setAccessible(true);
                        motionX.setDouble(pe, 0.0);
                        motionY.setDouble(pe, 0.0);
                        motionZ.setDouble(pe, 0.0);
                    } catch (Throwable ignored) {}
                }

                // 3) lock position to session center (use reflection teleport if available for better client sync)
                try {
                    RaidSession session = RaidSpawner.getSession(world);
                    BlockPos center = session.getCenter();
                    if (center != null) {
                        double tx = center.getX() + 0.5;
                        double ty = center.getY();
                        double tz = center.getZ() + 0.5;

                        boolean teleported = false;
                        try {
                            java.lang.reflect.Method tele = pe.getClass().getMethod("teleportTo", double.class, double.class, double.class);
                            tele.invoke(pe, tx, ty, tz);
                            teleported = true;
                        } catch (NoSuchMethodException ns) {
                            try {
                                java.lang.reflect.Method abs = pe.getClass().getMethod("absMoveTo", double.class, double.class, double.class);
                                abs.invoke(pe, tx, ty, tz);
                                teleported = true;
                            } catch (NoSuchMethodException ns2) {
                                // fall back
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}

                        if (!teleported) {
                            try { pe.setPos(tx, ty, tz); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }
    }
}
