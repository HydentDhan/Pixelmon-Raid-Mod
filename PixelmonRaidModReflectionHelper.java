package com.example.PixelmonRaid;

import net.minecraft.inventory.container.ContainerType;

public final class PixelmonRaidModReflectionHelper {
    // This field is used by older code paths that directly referenced PixelmonRaidMod.RAID_REWARDS_CONTAINER.
    // We provide a setter so the mod setup can assign it from RegistryObject when available.
    private PixelmonRaidModReflectionHelper() {}

    public static void assignContainer(ContainerType<?> container) {
        try {
            // reflectively set the static field if present
            java.lang.reflect.Field f = PixelmonRaidMod.class.getDeclaredField("RAID_REWARDS_CONTAINER");
            f.setAccessible(true);
            f.set(null, container);
        } catch (Throwable ignored) {}
    }
}
