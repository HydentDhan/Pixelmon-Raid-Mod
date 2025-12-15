package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class RaidBossRestrictionsHandler {

    @SubscribeEvent
    public static void onBattleStart(BattleStartedEvent event) {
        for (BattleParticipant participant : event.getBattleController().participants) {
            if (participant instanceof PlayerParticipant) {
                PlayerParticipant pp = (PlayerParticipant) participant;

                for (PixelmonWrapper pw : pp.controlledPokemon) {
                    Pokemon p = pw.pokemon;

                    if (p.getSpecies().isLegendary()) {
                        pp.player.sendMessage(new StringTextComponent("§cLegendary Pokémon are banned in raid battles!"), pp.player.getUUID());
                        event.setCanceled(true);
                        return;
                    }

                    // Removed the previous ban on player Gigantamax here so bosses and battle rules can allow it.
                    // If you still want to prevent player Gigantamax in raids, add the check back:
                    // if (p.canGigantamax()) { ... }
                }
            }
        }
    }
}
