package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import net.minecraft.entity.Entity;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class RaidBattleHandler {
    @SubscribeEvent
    public static void onBattleStart(BattleStartedEvent.Post event) {
        BattleController controller = event.getBattleController();
        PixelmonEntity boss = null;

        for (BattleParticipant part : controller.participants) {
            if (part.getEntity() instanceof PixelmonEntity) {
                PixelmonEntity p = (PixelmonEntity) part.getEntity();
                if (p.isLegendary()) {
                    boss = p;
                    break;
                }
            }
        }

        if (boss != null) {
            ServerWorld world = (ServerWorld) boss.getCommandSenderWorld();
            RaidSession session = RaidSpawner.getSession(world);
        }
    }

    @SubscribeEvent
    public static void onBattleEnd(com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent event) {
        com.pixelmonmod.pixelmon.battles.controller.BattleController controller = event.getBattleController();
        if (controller.participants == null || controller.participants.isEmpty()) return;

        boolean bossDied = controller.participants.stream()
                .filter(p -> p.getEntity() instanceof PixelmonEntity)
                .map(p -> (PixelmonEntity) p.getEntity())
                .anyMatch(e -> RaidSpawner.isRaidBoss(e) && e.getHealth() <= 0);

        if (bossDied) {
            Entity participantEntity = controller.participants.get(0).getEntity();
            if (participantEntity == null) return;
            ServerWorld world = (ServerWorld) participantEntity.getCommandSenderWorld();
            RaidSession session = RaidSpawner.getSession(world);
            RaidRewardHandler.distributeRewards(session);
            session.setState(RaidSession.State.COMPLETED);
        }
    }
}