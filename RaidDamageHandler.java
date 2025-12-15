package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Records player damage dealt to raid bosses and updates the shared HP pool stored
 * in the boss entity's persistent data. Sends boss-bar updates to session players
 * when percent changes beyond the configured threshold and finishes the raid when
 * the pool is exhausted.
 */
public class RaidDamageHandler {

    @SubscribeEvent
    public void onDamage(AttackEvent.Damage event) {
        if (event == null) return;
        try {
            PixelmonWrapper attackerWrapper = event.user;
            PixelmonWrapper targetWrapper = event.target;
            if (attackerWrapper == null || targetWrapper == null) return;

            ServerPlayerEntity player = attackerWrapper.getPlayerOwner();
            if (player == null) return;

            PixelmonEntity targetEntity = targetWrapper.entity;
            if (targetEntity == null) return;
            if (!RaidSpawner.isRaidBoss(targetEntity)) return;

            // Scale damage by raid difficulty multiplier
            float baseDamage = (float) event.damage;
            float scaledDamage = baseDamage * RaidState.getDifficulty().getPlayerDamageMultiplier();
            int damageToRecord = Math.max(0, Math.round(scaledDamage));

            // Record damage in tracker (per-player)
            try {
                RaidState.getDamageTracker().recordDamage(player, damageToRecord);
            } catch (Throwable ignored) {}

            // Update boss NBT pool: accumulate damage and compute percent left
            try {
                CompoundNBT tag = targetEntity.getPersistentData();
                if (tag == null) tag = new CompoundNBT();

                int accumulated = tag.contains("pixelmonraid_accumulated_damage") ? tag.getInt("pixelmonraid_accumulated_damage") : 0;
                int pool = tag.contains("pixelmonraid_hp_pool") ? Math.max(1, tag.getInt("pixelmonraid_hp_pool")) : 1;

                accumulated += damageToRecord;
                tag.putInt("pixelmonraid_accumulated_damage", accumulated);

                float percentLeft = Math.max(0f, 1f - (accumulated / (float) pool));
                // Update RaidState so client overlay (if present) can use it
                try { RaidState.setBossPercent(percentLeft); } catch (Throwable ignored) {}

                // Send boss-bar update to session players only if change > threshold
                try {
                    ServerWorld sw = (ServerWorld) targetEntity.getCommandSenderWorld();
                    if (sw != null && sw.getServer() != null) {
                        RaidSession session = RaidSpawner.getSession(sw);
                        if (session != null) {
                            float last = session.getLastBossPercent();
                            double sendThreshold = PixelmonRaidConfig.getInstance().getSendThreshold();
                            if (last < 0 || Math.abs(last - percentLeft) > sendThreshold) {
                                session.setLastBossPercent(percentLeft);
                                // send to each session player (defensive)
                                for (java.util.UUID puid : session.getPlayers()) {
                                    try {
                                        ServerPlayerEntity p = sw.getServer().getPlayerList().getPlayer(puid);
                                        if (p != null) PacketHandler.sendBossBarToPlayer(p, percentLeft, "Raid Boss");
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // If the accumulated damage meets/exceeds the pool, mark raid as victory
                if (accumulated >= pool) {
                    try {
                        ServerWorld sw = (ServerWorld) targetEntity.getCommandSenderWorld();
                        if (sw != null) {
                            RaidSession session = RaidSpawner.getSession(sw);
                            if (session != null) {
                                session.finishRaid(true);
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // Clamp vanilla/Pixelmon side damage so the Pixelmon entity doesn't actually die on the vanilla side:
            try {
                Pokemon pk = null;
                try { pk = targetEntity.getPokemon(); } catch (Throwable ignored) {}
                if (pk != null) {
                    int hp = pk.getHealth(); // use int to match Pixelmon's setHealth signature
                    if (scaledDamage >= hp) {
                        // leave the Pokemon with 1 HP on the Pixelmon side; event.damage is a double
                        event.damage = Math.max(0.0, (double) hp - 1.0);
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
