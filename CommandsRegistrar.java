package com.example.PixelmonRaid;

import com.example.PixelmonRaid.container.RaidRewardsContainerProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Centralized command registration on the Forge event bus.
 */
@Mod.EventBusSubscriber(modid = PixelmonRaidMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandsRegistrar {

    private CommandsRegistrar() {}

    private static java.util.function.Predicate<CommandSource> adminPermPredicate() {
        return src -> {
            try {
                if (src.hasPermission(2)) return true;
                if (src.getLevel() != null && src.getLevel().getServer() != null && !src.getLevel().getServer().isDedicatedServer()) return true;
            } catch (Throwable ignored) {}
            return false;
        };
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSource> dispatcher = evt.getDispatcher();

        // /joinraid (no admin requirement)
        dispatcher.register(Commands.literal("joinraid")
                .executes((CommandContext<CommandSource> ctx) -> JoinRaidCommand.run(ctx))
                .then(Commands.literal("teleport")
                        .executes((CommandContext<CommandSource> ctx) -> JoinRaidCommand.run(ctx, true))
                )
        );

        // /startraid (admin - relaxed in singleplayer)
        dispatcher.register(Commands.literal("startraid")
                .requires(adminPermPredicate())
                .executes((CommandContext<CommandSource> ctx) -> {
                    try {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        ServerWorld world = (player.getLevel() instanceof ServerWorld) ? (ServerWorld) player.getLevel() : null;
                        if (world == null) {
                            ctx.getSource().sendFailure(new StringTextComponent("Unable to determine server world for player."));
                            return 0;
                        }
                        RaidSession session = RaidSpawner.getSession(world);
                        if (session.getState() == RaidSession.State.IN_BATTLE) {
                            ctx.getSource().sendFailure(new StringTextComponent("A raid is already in progress."));
                            return 0;
                        }

                        session.setState(RaidSession.State.IN_BATTLE);
                        RaidSpawner.spawnBoss(session);
                        RaidState.setBattleStartTick(world.getGameTime());
                        ctx.getSource().sendSuccess(new StringTextComponent("Raid has been manually started."), false);
                        return 1;
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(new StringTextComponent("Error: " + e.getMessage()));
                        return 0;
                    }
                })
        );

        // /cancelraid
        dispatcher.register(Commands.literal("cancelraid")
                .requires(adminPermPredicate())
                .executes((CommandContext<CommandSource> ctx) -> {
                    try {
                        ServerWorld world = (ctx.getSource().getLevel() instanceof ServerWorld) ? (ServerWorld) ctx.getSource().getLevel() : null;
                        if (world == null) {
                            ctx.getSource().sendFailure(new StringTextComponent("Unable to determine server world."));
                            return 0;
                        }
                        RaidSession session = RaidSpawner.getSession(world);
                        if (session.getState() == RaidSession.State.WAITING) {
                            session.setState(RaidSession.State.COMPLETED);
                            ctx.getSource().sendSuccess(new StringTextComponent("Upcoming raid cancelled."), false);
                            return 1;
                        } else {
                            ctx.getSource().sendFailure(new StringTextComponent("No upcoming raid to cancel."));
                            return 0;
                        }
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(new StringTextComponent("Error: " + e.getMessage()));
                        return 0;
                    }
                })
        );

        // /stopraid
        dispatcher.register(Commands.literal("stopraid")
                .requires(adminPermPredicate())
                .executes((CommandContext<CommandSource> ctx) -> {
                    try {
                        ServerWorld world = (ctx.getSource().getLevel() instanceof ServerWorld) ? (ServerWorld) ctx.getSource().getLevel() : null;
                        if (world == null) {
                            ctx.getSource().sendFailure(new StringTextComponent("Unable to determine server world."));
                            return 0;
                        }
                        RaidSession session = RaidSpawner.getSession(world);
                        if (session.getState() != RaidSession.State.IN_BATTLE && session.getState() != RaidSession.State.WAITING) {
                            ctx.getSource().sendFailure(new StringTextComponent("No active or waiting raid to stop."));
                            return 0;
                        }

                        // Attempt to end pixelmon battles for all players in session (robust reflection)
                        for (UUID uuid : session.getPlayers()) {
                            try {
                                ServerPlayerEntity pl = world.getServer().getPlayerList().getPlayer(uuid);
                                if (pl == null) continue;
                                try {
                                    Class<?> brClass = Class.forName("com.pixelmonmod.pixelmon.battles.BattleRegistry");
                                    // Try BattleRegistry.getBattle(player)
                                    try {
                                        Method getBattle = brClass.getMethod("getBattle", ServerPlayerEntity.class);
                                        Object bc = getBattle.invoke(null, pl);
                                        if (bc != null) {
                                            try {
                                                Method end = bc.getClass().getMethod("endBattle");
                                                end.invoke(bc);
                                            } catch (Throwable e) {
                                                // fallback: try static endBattle(player)
                                                for (Method m : brClass.getMethods()) {
                                                    if ("endBattle".equals(m.getName()) && m.getParameterCount() == 1
                                                            && m.getParameterTypes()[0].isAssignableFrom(ServerPlayerEntity.class)) {
                                                        try { m.invoke(null, pl); } catch (Throwable ignored) {}
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (NoSuchMethodException nsme) {
                                        // fallback: try static endBattle(player)
                                        for (Method m : brClass.getMethods()) {
                                            if ("endBattle".equals(m.getName()) && m.getParameterCount() == 1
                                                    && m.getParameterTypes()[0].isAssignableFrom(ServerPlayerEntity.class)) {
                                                try { m.invoke(null, pl); } catch (Throwable ignored) {}
                                                break;
                                            }
                                        }
                                    }
                                } catch (ClassNotFoundException cnf) {
                                    // Pixelmon not on compile classpath - nothing to do
                                } catch (Throwable ignored) {}
                            } catch (Throwable ignored) {}
                        }

                        RaidSpawner.despawnBosses(session);
                        session.setState(RaidSession.State.COMPLETED);

                        ctx.getSource().sendSuccess(new StringTextComponent("Raid has been stopped."), false);
                        return 1;
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(new StringTextComponent("Error: " + e.getMessage()));
                        return 0;
                    }
                })
        );

        // /raidrewards (admin UI)
        dispatcher.register(Commands.literal("raidrewards")
                .requires(adminPermPredicate())
                .executes((CommandContext<CommandSource> ctx) -> {
                    try {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        NetworkHooks.openGui(player, RaidRewardsContainerProvider.provider(), buf -> { });
                        try {
                            PacketHandler.sendAdminOpenPacketToPlayer(player, RaidRewardsConfig.getInstance().getRewardLines());
                        } catch (Throwable ignored) {}
                        ctx.getSource().sendSuccess(new StringTextComponent("Opening Raid Rewards GUI..."), false);
                        return 1;
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(new StringTextComponent("Error: " + e.getMessage()));
                        return 0;
                    }
                })
        );
    }
}
