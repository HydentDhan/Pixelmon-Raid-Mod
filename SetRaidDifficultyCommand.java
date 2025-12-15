package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;

public class SetRaidDifficultyCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("setraiddifficulty")
                .then(Commands.argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (RaidDifficulty diff : RaidDifficulty.values()) {
                                builder.suggest(diff.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String input = StringArgumentType.getString(ctx, "level").toUpperCase();
                            try {
                                RaidDifficulty diff = RaidDifficulty.valueOf(input);
                                RaidState.setDifficulty(diff);
                                ctx.getSource().sendSuccess(new StringTextComponent("Raid difficulty set to: " + diff), true);
                                return 1;
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendFailure(new StringTextComponent("Invalid difficulty!"));
                                return 0;
                            }
                        })
                ));
    }
}
