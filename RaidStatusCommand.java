package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class RaidStatusCommand {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("raidstatus")
                .executes(ctx -> {
                    ServerWorld world = (ServerWorld) ctx.getSource().getLevel();
                    RaidSession session = RaidSpawner.getSession(world);
                    if (session == null) {
                        ctx.getSource().sendSuccess(new StringTextComponent("No raid session for this world."), false);
                        return 1;
                    }

                    RaidSession.State s = session.getState();
                    if (s == RaidSession.State.WAITING) {
                        long ticksLeft = session.getTicksUntilStart(world.getGameTime());
                        long seconds = ticksLeft / 20;
                        long minutes = seconds / 60;
                        long remainingSeconds = seconds % 60;
                        ctx.getSource().sendSuccess(new StringTextComponent("Raid starts in " +
                                (minutes > 0 ? minutes + "m " : "") +
                                remainingSeconds + "s"), false);
                    } else if (s == RaidSession.State.IN_BATTLE) {
                        long start = RaidState.getBattleStartTick();
                        long elapsed = world.getGameTime() - start;
                        long durationTicks = 20L * PixelmonRaidConfig.getInstance().getDefaultRaidDurationSeconds();
                        long remaining = Math.max(0, durationTicks - elapsed);
                        long seconds = remaining / 20;
                        long minutes = seconds / 60;
                        long secondsPart = seconds % 60;
                        ctx.getSource().sendSuccess(new StringTextComponent("Raid in progress — time left: " +
                                (minutes > 0 ? minutes + "m " : "") + secondsPart + "s"), false);
                    } else {
                        ctx.getSource().sendSuccess(new StringTextComponent("No raid starting right now."), false);
                    }
                    return 1;
                }));
    }
}
