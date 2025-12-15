package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class StopRaidCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("stopraid")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    try {
                        CommandSource src = ctx.getSource();
                        ServerWorld world = src.getLevel();
                        RaidSession session = RaidSpawner.getSession(world);
                        if (session == null) {
                            src.sendSuccess(new StringTextComponent("No raid session found."), false);
                            return 0;
                        }

                        // End active battles for players
                        session.getPlayers().forEach(uuid -> {
                            try {
                                net.minecraft.entity.player.ServerPlayerEntity sp = world.getServer().getPlayerList().getPlayer(uuid);
                                if (sp == null) return;
                                com.pixelmonmod.pixelmon.battles.controller.BattleController bc = com.pixelmonmod.pixelmon.battles.BattleRegistry.getBattle(sp);
                                if (bc != null) bc.endBattle();
                            } catch (Throwable ignored) {}
                        });

                        // Despawn bosses immediately
                        RaidSpawner.despawnBosses(session);

                        // Mark session completed and start next waiting
                        session.setState(RaidSession.State.COMPLETED);
                        session.startWaitingNow(world.getGameTime());

                        src.sendSuccess(new StringTextComponent("Raid stopped."), true);
                        return 1;
                    } catch (Throwable t) {
                        ctx.getSource().sendFailure(new StringTextComponent("Failed to stop raid: " + t.getMessage()));
                        return 0;
                    }
                }));
    }
}
