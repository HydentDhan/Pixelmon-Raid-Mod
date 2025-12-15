package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class RaidRewardHandler {
    private static final Random RAND = new Random();

    public static void distributeRewards(RaidSession session) {
        if (session == null) return;

        List<RaidRewardsConfig.RewardEntry> configs = RaidRewardsConfig.getInstance().getRewards();
        if (configs == null || configs.isEmpty()) {
            broadcastLeaderboard(session);
            return;
        }

        for (UUID uuid : session.getPlayers()) {
            ServerPlayerEntity player = session.getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            List<ItemStack> toGive = new ArrayList<>();

            for (RaidRewardsConfig.RewardEntry re : configs) {
                if (re == null || re.item == null) continue;

                float chance = Math.max(0f, Math.min(1f, re.chance));
                if (RAND.nextFloat() > chance) continue;

                try {
                    ResourceLocation rl = new ResourceLocation(re.item);
                    Item item = ForgeRegistries.ITEMS.getValue(rl);
                    if (item != null) {
                        int count = Math.max(1, re.count);
                        toGive.add(new ItemStack(item, count));
                    }
                } catch (Exception ignored) { }
            }

            // Give items (try inventory first, otherwise drop)
            for (ItemStack st : toGive) {
                if (st == null || st.isEmpty()) continue;
                ItemStack copy = st.copy();
                boolean added = player.inventory.add(copy);
                if (!added) player.drop(copy, false);
            }

            if (!toGive.isEmpty()) {
                player.sendMessage(new StringTextComponent("You received raid rewards! Check your inventory."), player.getUUID());
            }

            try {
                List<String> rewardStrings = toGive.stream()
                        .map(s -> s.getCount() + "x " + s.getHoverName().getString())
                        .collect(Collectors.toList());

                PacketHandler.sendRewardScreenPacketToPlayer(player, rewardStrings);
            } catch (Throwable ignored) {}
        }

        broadcastLeaderboard(session);
    }

    private static void broadcastLeaderboard(RaidSession session) {
        if (session == null) return;
        DamageTracker tracker = RaidState.getDamageTracker();
        if (tracker == null) return;

        Map<UUID, Integer> damageMap = tracker.getAllDamage();
        if (damageMap == null || damageMap.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> lines = new ArrayList<>();
        lines.add("§6===== §eRaid Leaderboard §6=====");
        int rank = 1;
        for (Map.Entry<UUID, Integer> e : sorted) {
            if (rank > 3) break;
            UUID id = e.getKey();
            int dmg = e.getValue();
            String name = id.toString();
            ServerPlayerEntity player = session.getWorld().getServer().getPlayerList().getPlayer(id);
            if (player != null) name = player.getName().getString();
            lines.add(String.format("§b#%d §f%s §7— §c%d dmg", rank, name, dmg));
            rank++;
        }
        lines.add("§6===============================");

        for (String s : lines) {
            session.getWorld().getServer().getPlayerList().broadcastMessage(
                    new StringTextComponent(s),
                    ChatType.SYSTEM,
                    Util.NIL_UUID
            );
        }
    }
}
