package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Scales damage dealt by raid bosses according to RaidState.getBossDamageMultiplier().
 * This creates the "enrage" feeling where boss deals more damage in later phases.
 */
public class RaidBossDamageHandler {

    @SubscribeEvent
    public void onBossDealingDamage(AttackEvent.Damage event) {
        if (event == null || event.user == null || event.user.entity == null) return;
        if (!(event.user.entity instanceof PixelmonEntity)) return;
        PixelmonEntity attacker = (PixelmonEntity) event.user.entity;
        try {
            if (!RaidSpawner.isRaidBoss(attacker)) return;
            float m = RaidState.getBossDamageMultiplier();
            if (m <= 0f) m = 1.0f;
            event.damage = event.damage * m;
        } catch (Throwable ignored) {}
    }
}
