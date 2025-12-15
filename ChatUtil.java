package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ChatType;

public final class ChatUtil {
    public static ITextComponent styled(String content, TextFormatting color) {
        return new StringTextComponent(color + content + TextFormatting.RESET);
    }

    public static void sendInfo(ServerPlayerEntity player, String msg) {
        try {
            player.sendMessage(styled("§b[Raid] §f" + msg, TextFormatting.BLUE), player.getUUID());
        } catch (Throwable ignored) {}
    }

    public static void sendSuccess(ServerPlayerEntity player, String msg) {
        try {
            player.sendMessage(styled("§a[Raid] §f" + msg, TextFormatting.GREEN), player.getUUID());
        } catch (Throwable ignored) {}
    }

    public static void sendWarn(ServerPlayerEntity player, String msg) {
        try {
            player.sendMessage(styled("§e[Raid] §f" + msg, TextFormatting.YELLOW), player.getUUID());
        } catch (Throwable ignored) {}
    }

    public static void broadcast(ServerPlayerEntity player, String msg) {
        try {
            player.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§6[Raid] §f" + msg), ChatType.SYSTEM, Util.NIL_UUID);
        } catch (Throwable ignored) {}
    }
}
