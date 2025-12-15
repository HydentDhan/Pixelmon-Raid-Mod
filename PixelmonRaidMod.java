package com.example.PixelmonRaid;

import com.example.PixelmonRaid.client.RaidHUDOverlay;
import com.example.PixelmonRaid.commands.RaidRewardsCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Core mod class.
 */
@Mod(PixelmonRaidMod.MODID)
public class PixelmonRaidMod {
    public static final String MODID = "pixelmonraid";

    // Public so other classes can reference it (register it elsewhere)
    public static ContainerType<com.example.PixelmonRaid.container.RaidRewardsContainer> RAID_REWARDS_CONTAINER;

    public PixelmonRaidMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);

        // register teleport confirmation chat handler (guarded)
        try { TeleportConfirmationHandler.register(); } catch (Throwable ignored) {}

        // -------------------------
        // Forge bus registrations
        // -------------------------
        MinecraftForge.EVENT_BUS.register(new RaidBossRestrictionsHandler());
        MinecraftForge.EVENT_BUS.register(new RaidDamageHandler());
        MinecraftForge.EVENT_BUS.register(new RaidInvincibilityHandler());
        MinecraftForge.EVENT_BUS.register(new RaidBossTickHandler());
        MinecraftForge.EVENT_BUS.register(new RaidBattleHandler());
        MinecraftForge.EVENT_BUS.register(new RaidBossDeathHandler());

        // -------------------------
        // Pixelmon event bus registrations (best-effort)
        // -------------------------
        try {
            com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS.register(new RaidDamageHandler());
            com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS.register(new RaidInvincibilityHandler());
            com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS.register(new RaidBossRestrictionsHandler());
        } catch (Throwable ignored) {
            System.err.println("[PixelmonRaid] Pixelmon event registration failed (Pixelmon not present?).");
        }
    }

    private void setup(final FMLCommonSetupEvent evt) {
        try {
            // Ensure config file exists (safe)
            try { PixelmonRaidConfig.getInstance(); } catch (Throwable e) { e.printStackTrace(); }

            // Register network packets (guarded)
            try {
                PacketHandler.registerPackets();
            } catch (Throwable t) {
                System.err.println("[PixelmonRaid] Packet registration failed: " + t);
                t.printStackTrace();
            }

            System.out.println("[PixelmonRaid] Initialized");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END && evt.world instanceof ServerWorld) {
            RaidSession.getSession((ServerWorld) evt.world).tick(evt.world.getGameTime());
        }
    }

    /**
     * Keep registering miscellaneous commands here (but do NOT duplicate the /joinraid command).
     * The CommandsRegistrar class (annotated with @Mod.EventBusSubscriber) handles the main
     * command registrations including /joinraid in a version-safe way.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSource> dispatcher = evt.getDispatcher();

        // Register command groups that are simple and expected here (leave /joinraid to CommandsRegistrar)
        try {
            RaidStatusCommand.register(dispatcher);
        } catch (Throwable ignored) {}

        try {
            SetRaidDifficultyCommand.register(dispatcher);
        } catch (Throwable ignored) {}

        try {
            com.example.PixelmonRaid.StopRaidCommand.register(dispatcher);
        } catch (Throwable ignored) {}

        // Backwards-compatible admin command registration (optional)
        try {
            com.example.PixelmonRaid.commands.RaidRewardsCommand.register(dispatcher);
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] Failed to register RaidRewardsCommand: " + t.getMessage());
        }
    }
}
