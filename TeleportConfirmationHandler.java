package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple server-side chat-driven teleport confirmation helper.
 * Offer teleport -> player responds "yes"/"no" in chat -> handle accordingly.
 */
public final class TeleportConfirmationHandler {
    // pending teleports: playerUUID -> (targetPos, expireTick)
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 1000L * 30L; // 30s expiry

    private TeleportConfirmationHandler() {}

    private static final class Pending {
        final BlockPos pos;
        final long expiry;

        Pending(BlockPos pos, long expiry) { this.pos = pos; this.expiry = expiry; }
    }

    /** Register the chat listener on the Forge bus. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new TeleportConfirmationHandler());
    }

    /** Offer a teleport to the given player (server-side). Player must reply "yes" to accept. */
    public static void offerTeleport(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) return;
        long expire = System.currentTimeMillis() + EXPIRE_MS;
        PENDING.put(player.getUUID(), new Pending(pos, expire));
        try {
            player.sendMessage(new net.minecraft.util.text.StringTextComponent(
                            String.format("You are far from the raid. Type 'yes' to teleport to the raid now, or 'no' to cancel.")),
                    player.getUUID());
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event == null) return;
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        UUID uid = player.getUUID();
        Pending p = PENDING.get(uid);
        if (p == null) return;

        String msg = event.getMessage();
        if (msg == null) return;
        String m = msg.trim().toLowerCase();

        // consume the chat event only for yes/no
        if (m.equals("yes") || m.equals("y")) {
            // teleport
            try {
                teleportPlayerTo(player, p.pos);
                player.sendMessage(new net.minecraft.util.text.StringTextComponent("Teleported to the raid."), player.getUUID());
            } catch (Throwable t) {
                player.sendMessage(new net.minecraft.util.text.StringTextComponent("Teleport failed: " + t.getMessage()), player.getUUID());
                t.printStackTrace();
            } finally {
                PENDING.remove(uid);
            }
            event.setCanceled(true);
        } else if (m.equals("no") || m.equals("n")) {
            player.sendMessage(new net.minecraft.util.text.StringTextComponent("Teleport cancelled."), player.getUUID());
            PENDING.remove(uid);
            event.setCanceled(true);
        } else {
            // ignore other messages, let them pass through
        }
    }

    private static void teleportPlayerTo(ServerPlayerEntity player, BlockPos pos) throws Exception {
        // Best-effort: use teleportTo(...) if available, otherwise setPos + connection teleport
        try {
            java.lang.reflect.Method tele = player.getClass().getMethod("teleportTo", double.class, double.class, double.class);
            tele.invoke(player, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return;
        } catch (NoSuchMethodException ignored) {}

        try {
            java.lang.reflect.Field connectionField = net.minecraft.entity.player.ServerPlayerEntity.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            Object connection = connectionField.get(player);
            // Connection teleport signature differs between versions. Try common ones:
            try {
                java.lang.reflect.Method connTele = connection.getClass().getMethod("teleport", double.class, double.class, double.class, float.class, float.class);
                connTele.invoke(connection, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.yRot, player.xRot);
                return;
            } catch (NoSuchMethodException ignored2) {}
        } catch (Throwable ignored) {}

        // fallback
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
