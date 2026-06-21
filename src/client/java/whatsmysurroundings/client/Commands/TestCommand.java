package whatsmysurroundings.client.Commands;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class TestCommand {

    // 注册方法：在客户端初始化时调用
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmstest")
                            // '/wmstest'
                            .executes(context -> {
                                context.getSource().sendFeedback(
                                        Component.literal("[WMS Debug] Called '/wmstest' with no arguments."));
                                return 1;
                            })
                            // '/wmstest [<radius>]'
                            .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        return 0;
                                    })));
        });
    }

}