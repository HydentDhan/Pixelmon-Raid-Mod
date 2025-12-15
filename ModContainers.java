package com.example.PixelmonRaid;

import com.example.PixelmonRaid.container.RaidRewardsContainer;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Helper to register container types.
 */
public final class ModContainers {
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, PixelmonRaidMod.MODID);

    public static final RegistryObject<ContainerType<RaidRewardsContainer>> RAID_REWARDS_CONTAINER =
            CONTAINERS.register("raid_rewards", () -> new ContainerType<>((windowId, inv) -> new RaidRewardsContainer(windowId, inv)));

    public static void registerAll() {
        try {
            IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
            CONTAINERS.register(bus);
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] Failed to register containers: " + t);
        }
    }
}
