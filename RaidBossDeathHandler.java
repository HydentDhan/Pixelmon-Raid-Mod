package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Ends the raid for ALL joined players immediately when the raid boss dies,
 * distributes rewards, broadcasts the leaderboard, and cleans up.
 */
@Mod.EventBusSubscriber
public class RaidBossDeathHandler {

    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent evt) {
        if (evt == null || evt.getEntity() == null) return;
        if (!(evt.getEntity() instanceof PixelmonEntity)) return;

        PixelmonEntity dead = (PixelmonEntity) evt.getEntity();
        if (!RaidSpawner.isRaidBoss(dead)) return;

        if (!(dead.level instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) dead.level;

        // Get the session for this world
        RaidSession session = RaidSpawner.getSession(world);
        if (session == null) return;

        // End ALL players' battles in this session (if any still active)
        for (UUID pid : session.getPlayers()) {
            try {
                ServerPlayerEntity pl = world.getServer().getPlayerList().getPlayer(pid);
                if (pl == null) continue;
                BattleController bc = BattleRegistry.getBattle(pl);
                if (bc != null) bc.endBattle();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // Despawn any other tracked bosses
        try {
            RaidSpawner.despawnBosses(session);
        } catch (Throwable ignored) {}

        // Distribute rewards + broadcast leaderboard (only once)
        try {
            if (session.markRewardsDistributedIfNot()) {
                RaidRewardHandler.distributeRewards(session);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Mark raid completed for this session then schedule next WAITING countdown immediately.
        try {
            session.setState(RaidSession.State.COMPLETED);
            // Start the next waiting countdown now (30 second wait defined by session)
            session.startWaitingNow(world.getGameTime());
        } catch (Throwable ignored) {}
    }
}
