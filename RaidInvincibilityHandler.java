package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Prevents raid bosses from dying on the vanilla side; rely on the shared HP pool for victory.
 */
public class RaidInvincibilityHandler {

    @SubscribeEvent
    public static void onDamage(AttackEvent.Damage event) {
        if (event == null || event.target == null || event.target.entity == null) return;
        if (!(event.target.entity instanceof PixelmonEntity)) return;

        PixelmonEntity targetEntity = (PixelmonEntity) event.target.entity;
        if (!RaidSpawner.isRaidBoss(targetEntity)) return;

        try {
            if (targetEntity.getPokemon() != null) {
                int hp = targetEntity.getPokemon().getHealth();
                double incoming = event.damage;
                if (incoming <= 0) return;
                if (incoming >= hp) {
                    // clamp to leave 1 HP
                    event.damage = Math.max(0.0, (double)hp - 1.0);
                }
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent evt) {
        if (evt == null || evt.getEntity() == null) return;
        if (!(evt.getEntity() instanceof PixelmonEntity)) return;

        PixelmonEntity pe = (PixelmonEntity) evt.getEntity();
        try {
            if (RaidSpawner.isRaidBoss(pe)) {
                // Prevent the vanilla death and ensure Pokemon HP remains at least 1
                evt.setCanceled(true);
                try {
                    if (pe.getPokemon() != null) pe.getPokemon().setHealth(1);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
