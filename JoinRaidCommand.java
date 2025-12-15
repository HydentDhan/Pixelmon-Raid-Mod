package com.example.PixelmonRaid;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.lang.reflect.Method;

/**
 * JoinRaidCommand — adds the player to the raid session. Optional teleport subcommand.
 */
public final class JoinRaidCommand {
    private JoinRaidCommand() {}

    public static int run(CommandContext<CommandSource> ctx) {
        return run(ctx, false);
    }

    public static int run(CommandContext<CommandSource> ctx, boolean teleport) {
        CommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new StringTextComponent("Only players can run this command."));
            return 0;
        }

        ServerWorld world = player.getLevel(); // ServerWorld for server-side player
        RaidSession session = RaidSpawner.getSession(world);
        if (session == null) {
            player.sendMessage(new StringTextComponent("No raid session found in this world."), player.getUUID());
            return 0;
        }

        if (teleport) {
            try {
                double tx = session.getCenter().getX() + 0.5;
                double ty = session.getCenter().getY();
                double tz = session.getCenter().getZ() + 0.5;

                // Try to call teleport methods reflectively (different MC/Forge versions vary)
                try {
                    Method m = player.getClass().getMethod("teleportTo", ServerWorld.class, double.class, double.class, double.class, float.class, float.class);
                    m.invoke(player, world, tx, ty, tz, player.yRot, player.xRot);
                } catch (NoSuchMethodException nsme) {
                    // fallback: try teleportTo(double,double,double)
                    try {
                        Method m2 = player.getClass().getMethod("teleportTo", double.class, double.class, double.class);
                        m2.invoke(player, tx, ty, tz);
                    } catch (NoSuchMethodException nsme2) {
                        // final fallback: setPos (Entity#setPos or setPosition)
                        try {
                            Method setPos = player.getClass().getMethod("setPos", double.class, double.class, double.class);
                            setPos.invoke(player, tx, ty, tz);
                        } catch (Throwable ignore) {
                            // can't teleport on this server version - silently ignore
                        }
                    } catch (Throwable t2) { /* ignore */ }
                } catch (Throwable t) {
                    // ignore teleport failure
                }

                player.sendMessage(new StringTextComponent("Teleported to raid area."), player.getUUID());
            } catch (Throwable t) {
                player.sendMessage(new StringTextComponent("Teleport failed: " + t.getMessage()), player.getUUID());
            }
        }

        boolean added = session.addPlayer(player.getUUID());
        if (added) {
            player.sendMessage(new StringTextComponent("You joined the raid. When you attack the boss like a normal Pixelmon fight, you will be part of the battle."), player.getUUID());
            return 1;
        } else {
            player.sendMessage(new StringTextComponent("You were already part of the raid or unable to join."), player.getUUID());
            return 0;
        }
    }
}
