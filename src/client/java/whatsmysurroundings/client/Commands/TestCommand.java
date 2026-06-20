package whatsmysurroundings.client.Commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public class TestCommand {

    // 注册方法：在客户端初始化时调用
    public static void register() {
        // 注册客户端命令 /test
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("wmstest").executes(context -> {
                context.getSource().sendFeedback(Component.literal("Called /wmstest with no arguments."));
                return 1;
            }));
        });
        System.out.println("[WMS] 客户端命令 /test 已注册");
    }
}