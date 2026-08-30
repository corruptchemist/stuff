package dev.protomanly.pmweather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.protomanly.pmweather.shaders.ModShadersVeil;
import dev.protomanly.pmweather.shaders.data.FBOManager;
import dev.protomanly.pmweather.shaders.data.TextureManager3D;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ClientWeatherCommands {
   public ClientWeatherCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("pmweatherc").then(Commands.literal("shaders").then(Commands.literal("reset").executes(cmdcontext -> {
            ModShadersVeil.ClearShaders();
            FBOManager.reset();
            TextureManager3D.refresh();
            ModShadersVeil.InitShaders();
            return 1;
         })))
      );
   }
}
