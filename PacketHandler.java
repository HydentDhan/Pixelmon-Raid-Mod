package com.example.PixelmonRaid;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packet handler — client-only packets registered on client side to avoid server-classload errors.
 */
public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel CHANNEL = null;
    private static final ResourceLocation NAME = new ResourceLocation("pixelmonraid", "main");

    private static synchronized SimpleChannel getChannel() {
        if (CHANNEL == null) {
            CHANNEL = NetworkRegistry.newSimpleChannel(
                    NAME,
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );
        }
        return CHANNEL;
    }

    public static void registerPackets() {
        SimpleChannel ch = getChannel();
        final AtomicInteger id = new AtomicInteger(0);

        // Server->Client & Server-only packets that do NOT reference client-only classes:
        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.EndRaidResultsPacket.class,
                    com.example.PixelmonRaid.network.EndRaidResultsPacket::encode,
                    com.example.PixelmonRaid.network.EndRaidResultsPacket::decode,
                    com.example.PixelmonRaid.network.EndRaidResultsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        } catch (Throwable t) { t.printStackTrace(); }

        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.PlayerDamageSyncPacket.class,
                    com.example.PixelmonRaid.network.PlayerDamageSyncPacket::encode,
                    com.example.PixelmonRaid.network.PlayerDamageSyncPacket::decode,
                    com.example.PixelmonRaid.network.PlayerDamageSyncPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        } catch (Throwable t) { t.printStackTrace(); }

        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.RequestRewardsPacket.class,
                    com.example.PixelmonRaid.network.RequestRewardsPacket::encode,
                    com.example.PixelmonRaid.network.RequestRewardsPacket::decode,
                    com.example.PixelmonRaid.network.RequestRewardsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        } catch (Throwable t) { t.printStackTrace(); }

        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.SaveRewardsPacket.class,
                    com.example.PixelmonRaid.network.SaveRewardsPacket::encode,
                    com.example.PixelmonRaid.network.SaveRewardsPacket::decode,
                    com.example.PixelmonRaid.network.SaveRewardsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        } catch (Throwable t) { t.printStackTrace(); }

        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.AdminUpdateRewardsPacket.class,
                    com.example.PixelmonRaid.network.AdminUpdateRewardsPacket::encode,
                    com.example.PixelmonRaid.network.AdminUpdateRewardsPacket::decode,
                    com.example.PixelmonRaid.network.AdminUpdateRewardsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        } catch (Throwable t) { t.printStackTrace(); }

        try {
            ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.SyncRewardsPacket.class,
                    com.example.PixelmonRaid.network.SyncRewardsPacket::encode,
                    com.example.PixelmonRaid.network.SyncRewardsPacket::decode,
                    com.example.PixelmonRaid.network.SyncRewardsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        } catch (Throwable t) { t.printStackTrace(); }

        // Client-only packets: register these only on the client side to avoid server classloading client classes
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.client.OpenRewardScreenPacket.class,
                        com.example.PixelmonRaid.client.OpenRewardScreenPacket::encode,
                        com.example.PixelmonRaid.client.OpenRewardScreenPacket::decode,
                        com.example.PixelmonRaid.client.OpenRewardScreenPacket::handle,
                        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            } catch (Throwable t) { t.printStackTrace(); }

            try {
                ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.client.packets.AdminOpenRewardsPacket.class,
                        com.example.PixelmonRaid.client.packets.AdminOpenRewardsPacket::encode,
                        com.example.PixelmonRaid.client.packets.AdminOpenRewardsPacket::decode,
                        com.example.PixelmonRaid.client.packets.AdminOpenRewardsPacket::handle,
                        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            } catch (Throwable t) { t.printStackTrace(); }

            try {
                ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.BossBarPacket.class,
                        com.example.PixelmonRaid.network.BossBarPacket::encode,
                        com.example.PixelmonRaid.network.BossBarPacket::decode,
                        com.example.PixelmonRaid.network.BossBarPacket::handle,
                        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            } catch (Throwable t) { t.printStackTrace(); }

            try {
                ch.registerMessage(id.getAndIncrement(), com.example.PixelmonRaid.network.SyncLeaderboardPacket.class,
                        com.example.PixelmonRaid.network.SyncLeaderboardPacket::encode,
                        com.example.PixelmonRaid.network.SyncLeaderboardPacket::decode,
                        com.example.PixelmonRaid.network.SyncLeaderboardPacket::handle,
                        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            } catch (Throwable t) { t.printStackTrace(); }
        });

        System.out.println("[PixelmonRaid] Packet registration attempted.");
    }

    public static void sendToServer(Object packet) {
        try { getChannel().sendToServer(packet); } catch (Throwable t) { t.printStackTrace(); }
    }

    public static void sendToPlayer(ServerPlayerEntity player, Object packet) {
        try { getChannel().send(PacketDistributor.PLAYER.with(() -> player), packet); } catch (Throwable t) { t.printStackTrace(); }
    }

    public static void sendAdminOpenPacketToPlayer(ServerPlayerEntity player, List<String> rewards) {
        sendToPlayer(player, new com.example.PixelmonRaid.client.packets.AdminOpenRewardsPacket(rewards));
    }

    public static void sendRewardScreenPacketToPlayer(ServerPlayerEntity player, List<String> rewards) {
        sendToPlayer(player, new com.example.PixelmonRaid.client.OpenRewardScreenPacket(rewards));
    }

    public static void sendBossBarToPlayer(ServerPlayerEntity player, float percent, String title) {
        sendToPlayer(player, new com.example.PixelmonRaid.network.BossBarPacket(percent, title));
    }

    public static void sendEndResultsToPlayer(ServerPlayerEntity player, List<String> lines) {
        sendToPlayer(player, new com.example.PixelmonRaid.network.EndRaidResultsPacket(lines));
    }
}
