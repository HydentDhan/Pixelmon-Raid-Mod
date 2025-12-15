package com.example.PixelmonRaid;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;

public final class CommandTexts {
    private CommandTexts() {}
    public static ITextComponent of(String s) { return new StringTextComponent(s); }
}
