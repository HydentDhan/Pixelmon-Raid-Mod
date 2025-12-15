package com.example.PixelmonRaid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DropSet {

    private final List<ItemStack> drops;
    private final Random random = new Random();

    public DropSet() {
        this.drops = new ArrayList<>();
    }

    public void addDrop(ItemStack itemStack) {
        this.drops.add(itemStack);
    }

    public List<ItemStack> getAllDrops() {
        return new ArrayList<>(drops);
    }

    public ItemStack getRandomDrop() {
        if (drops.isEmpty()) return ItemStack.EMPTY;
        return drops.get(random.nextInt(drops.size()));
    }

    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        // If you want to serialize drops later
        return tag;
    }

    public void deserializeNBT(CompoundNBT nbt) {
        // Deserialization logic (future support)
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DropSet{");
        for (ItemStack item : drops) {
            sb.append(item.getDisplayName().getString()).append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}
