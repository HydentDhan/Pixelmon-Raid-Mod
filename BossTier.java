package com.example.PixelmonRaid;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraft.nbt.CompoundNBT;

public class BossTier implements INBTSerializable<CompoundNBT> {

    private final String id;
    private final float healthMultiplier;
    private final float damageMultiplier;

    public BossTier(String id, float healthMultiplier, float damageMultiplier) {
        this.id = id;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
    }

    public String getName() {
        return id;
    }

    public float getHealthMultiplier() {
        return healthMultiplier;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void toBuffer(PacketBuffer buffer) {
        buffer.writeUtf(id);  // UTF-8 encoded string
        buffer.writeFloat(healthMultiplier);
        buffer.writeFloat(damageMultiplier);
    }

    public static BossTier fromBuffer(PacketBuffer buffer) {
        String id = buffer.readUtf(32767);
        float health = buffer.readFloat();
        float damage = buffer.readFloat();
        return new BossTier(id, health, damage);
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Name", id);
        tag.putFloat("HealthMultiplier", healthMultiplier);
        tag.putFloat("DamageMultiplier", damageMultiplier);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        // Normally used in dynamic instances; can be omitted if you're just using constructor-based init
        // Left blank intentionally
    }

    @Override
    public String toString() {
        return "BossTier{" +
                "name='" + id + '\'' +
                ", healthMultiplier=" + healthMultiplier +
                ", damageMultiplier=" + damageMultiplier +
                '}';
    }
}
