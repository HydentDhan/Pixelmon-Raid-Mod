package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player attacks on Pixelmon entities that are our raid bosses.
 * When attacked, attempts to start the Pixelmon battle (via PixelmonApiBattleStarter).
 *
 * Includes:
 * - throttle to avoid repeated triggers (cooldownTicks)
 * - proximity filter: only players within radius are automatically included (configurable)
 * - announcement to registered players that the boss was attacked & an attempt to start a battle is in progress
 */
@Mod.EventBusSubscriber(modid = PixelmonRaidMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PixelmonBossAttackListener {
    private PixelmonBossAttackListener() {}

    // throttle map: entityId -> last trigger tick
    private static final Map<Integer, Long> lastTriggerTick = new ConcurrentHashMap<>();

    // configurable: cooldown in ticks to prevent re-trigger (20 ticks = 1 second)
    private static final long COOLDOWN_TICKS = 40L; // 2 seconds

    // configurable: proximity radius to include players automatically (squared)
    private static final double PROXIMITY_RADIUS = 16.0; // blocks
    private static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS * PROXIMITY_RADIUS;

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        try {
            if (evt.getTarget() == null) return;
            if (!(evt.getTarget() instanceof PixelmonEntity)) return;

            PixelmonEntity target = (PixelmonEntity) evt.getTarget();

            // server-side only
            if (!(target.level instanceof ServerWorld)) return;
            ServerWorld world = (ServerWorld) target.level;

            // ensure this Pixelmon is marked as raid boss (checks persistent tag or species)
            try {
                if (!RaidSpawner.isRaidBoss(target)) return;
            } catch (Throwable ignored) {
                return;
            }

            final int entId = target.getId();
            final long now = world.getGameTime();
            Long last = lastTriggerTick.get(entId);
            if (last != null && (now - last) < COOLDOWN_TICKS) {
                // throttled
                return;
            }
            lastTriggerTick.put(entId, now);

            // find session
            RaidSession session = RaidSpawner.getSession(world);
            if (session == null) return;

            // Only accept attacks when session is WAITING or IN_BATTLE
            if (session.getState() != RaidSession.State.WAITING && session.getState() != RaidSession.State.IN_BATTLE) {
                return;
            }

            // Announce to session players that boss was attacked and battle is starting
            try {
                String attackerName = "someone";
                if (evt.getEntity() instanceof ServerPlayerEntity) {
                    attackerName = ((ServerPlayerEntity) evt.getEntity()).getName().getString();
                }
                final String announce = "[PixelmonRaid] " + attackerName + " attacked the raid boss — attempting to start the Pixelmon battle...";
                session.getPlayers().forEach(uuid -> {
                    try {
                        ServerPlayerEntity p = world.getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) p.sendMessage(new StringTextComponent(announce), p.getUUID());
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}

            // Proximity filter: only include session players who are within PROXIMITY_RADIUS of the boss
            try {
                double bx = target.getX();
                double by = target.getY();
                double bz = target.getZ();

                // Build a fresh temporary player-list in session order but filtered by distance
                java.util.List<java.util.UUID> included = new java.util.ArrayList<>();
                for (java.util.UUID u : session.getPlayers()) {
                    try {
                        ServerPlayerEntity p = world.getServer().getPlayerList().getPlayer(u);
                        if (p == null) continue;
                        double dx = p.getX() - bx;
                        double dy = p.getY() - by;
                        double dz = p.getZ() - bz;
                        if (dx*dx + dy*dy + dz*dz <= PROXIMITY_RADIUS_SQ) included.add(u);
                    } catch (Throwable ignored) {}
                }

                // If none included by proximity, fall back to including only attacker (if player) or all session players
                if (included.isEmpty()) {
                    if (evt.getEntity() instanceof ServerPlayerEntity) {
                        included.add(((ServerPlayerEntity) evt.getEntity()).getUUID());
                    } else {
                        included.addAll(session.getPlayers());
                    }
                }

                // Replace session players temporarily with included set for the start call
                java.util.Set<java.util.UUID> originalPlayers = new java.util.HashSet<>(session.getPlayers());
                try {
                    // Remove all players then re-add only included (we need session to reflect who participates)
                    session.getPlayers().clear();
                    for (java.util.UUID u : included) session.getPlayers().add(u);

                    // Attempt to start battle via reflection-enabled PixelmonApiBattleStarter
                    boolean started = PixelmonApiBattleStarter.startBattleForSession(session, world);
                    if (!started) {
                        System.out.println("[PixelmonRaid] PixelmonBossAttackListener: API-based start failed. Let Pixelmon default handle the attack.");
                    } else {
                        System.out.println("[PixelmonRaid] PixelmonBossAttackListener: API-based start succeeded.");
                    }
                } finally {
                    // restore original session player set regardless of start success
                    session.getPlayers().clear();
                    session.getPlayers().addAll(originalPlayers);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
