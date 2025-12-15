package com.example.PixelmonRaid;

import net.minecraft.command.CommandSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

public class StartRaidCommand {

    public static int execute(CommandSource src) {
        try {
            ServerWorld world = (ServerWorld) src.getLevel();
            // convert source position to BlockPos safely
            BlockPos pos = new BlockPos(src.getPosition());
            BlockPos spawnPos = pos.below().immutable();

            RaidSession session = RaidSpawner.getSession(world);

            if (session.getState() == RaidSession.State.IN_BATTLE) {
                src.sendFailure(new StringTextComponent("A raid is already in progress."));
                return 0;
            }

            session.setState(RaidSession.State.IN_BATTLE);
            // Set the session center to the spawnPos so boss spawns at the desired spot
            // (Note: if you want to override center, add a setter on RaidSession; currently center is final.)
            // For now we rely on existing center; you can adapt the session center logic as needed.

            RaidSpawner.spawnBoss(session);
            RaidState.setBattleStartTick(world.getGameTime());

            src.sendSuccess(new StringTextComponent("Raid boss spawned."), true);
            return 1;
        } catch (Throwable t) {
            t.printStackTrace();
            src.sendFailure(new StringTextComponent("Failed to start raid: " + t.getMessage()));
            return 0;
        }
    }
}
